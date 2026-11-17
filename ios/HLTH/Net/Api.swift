import Foundation

let defaultServerURL = "https://213-108-21-54.sslip.io"

struct ApiError: LocalizedError {
    let code: Int
    let error: String
    let message: String
    var errorDescription: String? { message }
}

typealias JSON = [String: Any]

struct Track: Equatable { let title: String; let artist: String; let app: String }

struct Presence: Equatable {
    let status: String
    let lastSeen: Int64
    let nowPlaying: Track?

    static func parse(_ o: JSON?) -> Presence? {
        guard let o, let s = o["status"] as? String, !s.isEmpty else { return nil }
        var np: Track? = nil
        if let t = o["nowPlaying"] as? JSON, let title = t["title"] as? String, !title.isEmpty {
            np = Track(title: title, artist: t["artist"] as? String ?? "", app: t["app"] as? String ?? "")
        }
        return Presence(status: s, lastSeen: (o["lastSeen"] as? NSNumber)?.int64Value ?? 0, nowPlaying: np)
    }
}

struct RemoteUser: Identifiable, Equatable {
    var id: String { username }
    let username: String
    let displayName: String
    let avatar: String
    let presence: Presence?
}

struct RemoteProfile {
    let username: String
    let displayName: String
    let avatar: String
    let bio: String
    let createdAt: Int64
    let fields: JSON
    let publicKey: String?
    let isContact: Bool
    let isSelf: Bool
    let presence: Presence?
    let manualStatus: String?
}

struct RemoteMessage {
    let id: Int64
    let from: String
    let to: String
    let ciphertext: String
    let clientId: String
    let createdAt: Int64
    var group: Int64 = 0

    static func parse(_ o: JSON) -> RemoteMessage? {
        guard let id = (o["id"] as? NSNumber)?.int64Value, let from = o["from"] as? String, let to = o["to"] as? String,
              let ct = o["ciphertext"] as? String, let cid = o["clientId"] as? String else { return nil }
        return RemoteMessage(id: id, from: from, to: to, ciphertext: ct, clientId: cid, createdAt: (o["createdAt"] as? NSNumber)?.int64Value ?? 0,
                             group: (o["group"] as? NSNumber)?.int64Value ?? 0)
    }
}

struct GroupMember: Hashable { let username: String; let displayName: String; let avatar: String; let publicKey: String? }

/// Групповой чат (до 5 участников). Ключ диалога — "g:<id>".
struct GroupInfo: Hashable, Identifiable {
    let id: Int64
    let name: String
    let owner: String
    let members: [GroupMember]
    var conv: String { "g:\(id)" }

    static func parse(_ o: JSON) -> GroupInfo? {
        guard let id = (o["id"] as? NSNumber)?.int64Value else { return nil }
        let members = (o["members"] as? [JSON] ?? []).compactMap { m -> GroupMember? in
            guard let u = m["username"] as? String else { return nil }
            let k = m["publicKey"] as? String
            return GroupMember(username: u, displayName: m["displayName"] as? String ?? "", avatar: remoteAvatar(u, m["avatar"] as? String ?? ""),
                               publicKey: (k?.isEmpty ?? true) ? nil : k)
        }
        return GroupInfo(id: id, name: o["name"] as? String ?? "", owner: o["owner"] as? String ?? "", members: members)
    }

    var json: JSON {
        ["id": id, "name": name, "owner": owner, "members": members.map { m -> JSON in
            ["username": m.username, "displayName": m.displayName, "publicKey": m.publicKey ?? "",
             "avatar": m.avatar.hasPrefix("remote:") ? "photo:" + (m.avatar.split(separator: ":").last.map(String.init) ?? "") : m.avatar]
        }]
    }
}

func isGroupConv(_ peer: String) -> Bool { peer.hasPrefix("g:") }
func groupIdOf(_ peer: String) -> Int64 { Int64(peer.dropFirst(2)) ?? 0 }

/// "photo:<ts>" с сервера → "remote:<логин>:<ts>": фото загружается по ссылке с авторизацией.
func remoteAvatar(_ username: String, _ avatar: String) -> String {
    avatar.hasPrefix("photo:") ? "remote:\(username):\(avatar.dropFirst(6))" : avatar
}

/// HTTP-клиент сервера HLTH (тот же API, что у Android).
final class Api {
    var baseURL: String = UserDefaults.standard.string(forKey: "server_url") ?? defaultServerURL
    var token: String?

    let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 20
        c.timeoutIntervalForResource = 600
        return URLSession(configuration: c)
    }()

    private func url(_ path: String) -> URL { URL(string: baseURL.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/v1" + path)! }

    @discardableResult
    func call(_ method: String, _ path: String, _ body: JSON? = nil, auth: Bool = true, tokenOverride: String? = nil) async throws -> JSON {
        var req = URLRequest(url: url(path))
        req.httpMethod = method
        if auth, let t = tokenOverride ?? token { req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization") }
        if method != "GET" && method != "DELETE" {
            req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: body ?? [:])
        }
        let (data, resp): (Data, URLResponse)
        do { (data, resp) = try await session.data(for: req) } catch {
            throw ApiError(code: 0, error: "network", message: "Нет связи с сервером")
        }
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let obj = (try? JSONSerialization.jsonObject(with: data)) as? JSON ?? [:]
        if !(200..<300).contains(code) {
            let msg = (obj["message"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "Ошибка сервера (\(code))"
            throw ApiError(code: code, error: obj["error"] as? String ?? "", message: msg)
        }
        return obj
    }

    func raw(_ method: String, _ path: String, body: Data? = nil) async throws -> (Int, Data) {
        var req = URLRequest(url: url(path))
        req.httpMethod = method
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body {
            req.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
            req.httpBody = body
            req.timeoutInterval = 300
        }
        do {
            let (data, resp) = try await session.data(for: req)
            return ((resp as? HTTPURLResponse)?.statusCode ?? 0, data)
        } catch {
            throw ApiError(code: 0, error: "network", message: "Нет связи с сервером")
        }
    }

    // MARK: auth
    func register(_ u: String, _ p: String, _ name: String, deviceId: String) async throws -> String {
        let j = try await call("POST", "/auth/register", ["username": u, "password": p, "displayName": name, "deviceId": deviceId], auth: false)
        return j["token"] as? String ?? ""
    }
    func login(_ u: String, _ p: String) async throws -> String {
        let j = try await call("POST", "/auth/login", ["username": u, "password": p], auth: false)
        return j["token"] as? String ?? ""
    }
    func logout() async { _ = try? await call("POST", "/auth/logout") }
    func changePassword(current: String, new: String) async throws { try await call("PUT", "/me/password", ["current": current, "new": new]) }
    func deleteAccount() async throws { try await call("DELETE", "/me") }
    func me() async throws -> JSON { try await call("GET", "/me") }
    func limits() async throws -> JSON { try await call("GET", "/me/limits") }

    // MARK: profile / presence
    func putKey(_ k: String) async throws { try await call("PUT", "/me/key", ["publicKey": k]) }
    func putProfile(_ body: JSON) async throws { try await call("PUT", "/me/profile", body) }
    func setStatus(_ s: String) async throws { try await call("PUT", "/me/status", ["status": s]) }
    func setPresence(_ active: Bool) async throws { try await call("PUT", "/me/presence", ["active": active]) }
    func setNowPlaying(_ t: String, _ a: String, _ app: String, vis: String) async throws {
        try await call("PUT", "/me/nowplaying", ["title": t, "artist": a, "app": app, "vis": vis])
    }
    func presence(_ users: [String]) async throws -> [String: Presence] {
        guard !users.isEmpty else { return [:] }
        let q = users.prefix(100).joined(separator: ",").addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let o = try await call("GET", "/presence?u=\(q)")["presence"] as? JSON ?? [:]
        var out: [String: Presence] = [:]
        for (k, v) in o { if let p = Presence.parse(v as? JSON) { out[k] = p } }
        return out
    }

    // MARK: users
    private func users(_ arr: Any?) -> [RemoteUser] {
        (arr as? [JSON] ?? []).compactMap { o in
            guard let u = o["username"] as? String else { return nil }
            return RemoteUser(username: u, displayName: o["displayName"] as? String ?? "", avatar: remoteAvatar(u, o["avatar"] as? String ?? ""), presence: Presence.parse(o))
        }
    }
    func search(_ q: String) async throws -> [RemoteUser] {
        let e = q.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return users(try await call("GET", "/users/search?q=\(e)")["users"])
    }
    func contacts() async throws -> [RemoteUser] { users(try await call("GET", "/contacts")["contacts"]) }
    func addContact(_ u: String) async throws { try await call("PUT", "/contacts/\(u)") }
    func removeContact(_ u: String) async throws { try await call("DELETE", "/contacts/\(u)") }
    func profile(_ u: String) async throws -> RemoteProfile {
        let e = u.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? u
        let j = try await call("GET", "/users/\(e)")
        let key = j["publicKey"] as? String
        let ms = j["manualStatus"] as? String
        return RemoteProfile(
            username: j["username"] as? String ?? u, displayName: j["displayName"] as? String ?? "",
            avatar: remoteAvatar(j["username"] as? String ?? u, j["avatar"] as? String ?? ""), bio: j["bio"] as? String ?? "",
            createdAt: (j["createdAt"] as? NSNumber)?.int64Value ?? 0, fields: j["fields"] as? JSON ?? [:],
            publicKey: (key?.isEmpty ?? true) ? nil : key, isContact: j["isContact"] as? Bool ?? false,
            isSelf: j["isSelf"] as? Bool ?? false, presence: Presence.parse(j), manualStatus: (ms?.isEmpty ?? true) ? nil : ms)
    }

    // MARK: messages
    func send(to: String, ctRecipient: String, ctSender: String, clientId: String, mediaIds: [String]) async throws -> (Int64, Int64) {
        let j = try await call("POST", "/messages", ["to": to, "ctRecipient": ctRecipient, "ctSender": ctSender, "clientId": clientId, "mediaIds": mediaIds])
        return ((j["id"] as? NSNumber)?.int64Value ?? 0, (j["createdAt"] as? NSNumber)?.int64Value ?? nowMs())
    }
    func messages(since: Int64) async throws -> (list: [RemoteMessage], cursor: Int64, more: Bool) {
        let j = try await call("GET", "/messages?since=\(since)")
        let list = (j["messages"] as? [JSON] ?? []).compactMap(RemoteMessage.parse)
        let cursor = (j["cursor"] as? NSNumber)?.int64Value ?? (list.map(\.id).max() ?? since)
        let more = j["more"] as? Bool ?? (list.count >= 500)
        return (list, cursor, more)
    }

    // MARK: группы
    func groups() async throws -> [GroupInfo] { (try await call("GET", "/groups")["groups"] as? [JSON] ?? []).compactMap(GroupInfo.parse) }
    func createGroup(name: String, members: [String]) async throws -> GroupInfo? {
        GroupInfo.parse(try await call("POST", "/groups", ["name": name, "members": members]))
    }
    func renameGroup(_ id: Int64, _ name: String) async throws { try await call("PUT", "/groups/\(id)", ["name": name]) }
    func addGroupMember(_ id: Int64, _ u: String) async throws { try await call("POST", "/groups/\(id)/members", ["username": u]) }
    func removeGroupMember(_ id: Int64, _ u: String) async throws { try await call("DELETE", "/groups/\(id)/members/\(u)") }
    func sendGroup(_ id: Int64, clientId: String, cts: [String: String], mediaIds: [String]) async throws -> (Int64, Int64) {
        let j = try await call("POST", "/groups/\(id)/messages", ["clientId": clientId, "cts": cts, "mediaIds": mediaIds])
        return ((j["id"] as? NSNumber)?.int64Value ?? 0, (j["createdAt"] as? NSNumber)?.int64Value ?? nowMs())
    }

    // MARK: прочитано / прослушано / удаление
    func read(_ conv: String, upTo: Int64) async throws { try await call("POST", "/read", ["conv": conv, "upTo": upTo]) }
    func listened(_ id: Int64) async throws { try await call("POST", "/messages/\(id)/listened") }
    func receipts() async throws -> (reads: [(conv: String, upTo: Int64)], listened: [Int64]) {
        let j = try await call("GET", "/receipts")
        let reads = (j["reads"] as? [JSON] ?? []).compactMap { o -> (conv: String, upTo: Int64)? in
            guard let c = o["conv"] as? String else { return nil }
            return (c, (o["upTo"] as? NSNumber)?.int64Value ?? 0)
        }
        let listened = (j["listened"] as? [NSNumber] ?? []).map(\.int64Value)
        return (reads, listened)
    }
    func clearConversation(_ conv: String, all: Bool) async throws {
        let e = conv.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? conv
        try await call("DELETE", "/conversations/\(e)" + (all ? "?all=1" : ""))
    }

    // MARK: фото профиля
    func putAvatar(_ jpeg: Data) async throws {
        let (code, _) = try await raw("PUT", "/me/avatar", body: jpeg)
        guard (200..<300).contains(code) else { throw ApiError(code: code, error: "avatar", message: "Не удалось загрузить фото (\(code))") }
    }
    func deleteAvatar() async throws { try await call("DELETE", "/me/avatar") }
    func avatarRequest(_ username: String, version: String) -> URLRequest {
        var r = URLRequest(url: url("/users/\(username)/avatar?v=\(version)"))
        if let token { r.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        return r
    }
    func uploadMedia(_ bytes: Data) async throws -> String {
        let (code, data) = try await raw("POST", "/media", body: bytes)
        let obj = (try? JSONSerialization.jsonObject(with: data)) as? JSON ?? [:]
        guard (200..<300).contains(code), let id = obj["id"] as? String else {
            throw ApiError(code: code, error: obj["error"] as? String ?? "", message: obj["message"] as? String ?? "Ошибка загрузки (\(code))")
        }
        return id
    }
    func downloadMedia(_ id: String) async throws -> Data {
        let (code, data) = try await raw("GET", "/media/\(id)")
        guard (200..<300).contains(code) else {
            throw ApiError(code: code, error: "download", message: code == 404 ? "Файл удалён с сервера (хранится 30 дней)" : "Ошибка загрузки (\(code))")
        }
        return data
    }

    // MARK: backup
    func putBackup(_ blob: Data) async throws {
        let (code, _) = try await raw("PUT", "/me/backup", body: blob)
        guard (200..<300).contains(code) else { throw ApiError(code: code, error: "backup", message: "Сервер не принял копию (\(code))") }
    }
    func getBackup() async throws -> Data? {
        let (code, data) = try await raw("GET", "/me/backup")
        if code == 404 { return nil }
        guard (200..<300).contains(code) else { throw ApiError(code: code, error: "backup", message: "Не удалось получить копию (\(code))") }
        return data
    }

    // MARK: подпись обновлений
    func signingInfo() async throws -> JSON { try await call("GET", "/me/signing") }
    func uploadSigning(p12: Data, password: String, provision: Data) async throws -> JSON {
        try await call("PUT", "/me/signing", ["p12": p12.base64EncodedString(), "password": password, "provision": provision.base64EncodedString()])
    }
    func deleteSigning() async throws { try await call("DELETE", "/me/signing") }
    func sign(ipaUrl: String, bundleId: String, version: String) async throws -> JSON {
        var req = URLRequest(url: url("/me/sign"))
        req.httpMethod = "POST"
        req.timeoutInterval = 300
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["ipaUrl": ipaUrl, "bundleId": bundleId, "version": version, "title": "HLTH HBTy"])
        let (data, resp) = try await session.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let obj = (try? JSONSerialization.jsonObject(with: data)) as? JSON ?? [:]
        guard (200..<300).contains(code) else {
            throw ApiError(code: code, error: obj["error"] as? String ?? "", message: obj["message"] as? String ?? "Ошибка подписи (\(code))")
        }
        return obj
    }

    // MARK: admin
    func adminStats() async throws -> JSON { try await call("GET", "/admin/stats") }
    func adminLogs(_ n: Int = 200) async throws -> [String] { try await call("GET", "/admin/logs?n=\(n)")["lines"] as? [String] ?? [] }
    func adminWipe() async throws { try await call("POST", "/admin/wipe", ["confirm": "СБРОСИТЬ ВСЁ"]) }

    func wsURL() -> URL? {
        guard let token else { return nil }
        let base = baseURL.replacingOccurrences(of: "https://", with: "wss://").replacingOccurrences(of: "http://", with: "ws://")
        let t = token.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? token
        return URL(string: base.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/v1/ws?token=\(t)")
    }
}
