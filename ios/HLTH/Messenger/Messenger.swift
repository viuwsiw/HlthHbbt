import Foundation
import CryptoKit
import UserNotifications
import UIKit

let maxChars = 5000
let maxMediaBytes = 30 * 1024 * 1024

let maxVoiceMs: Int64 = 5 * 60_000

struct MediaInfo: Codable, Hashable {
    var id: String
    var key: String
    var nonce: String
    var mime: String
    var name: String
    var size: Int
    var voice: Bool = false
    var duration: Int64 = 0

    var isImage: Bool { mime.hasPrefix("image/") }
    var isVideo: Bool { mime.hasPrefix("video/") }

    var json: JSON {
        var j: JSON = ["id": id, "key": key, "nonce": nonce, "mime": mime, "name": name, "size": size]
        if voice { j["voice"] = true; j["duration"] = duration }
        return j
    }

    static func parse(_ o: JSON?) -> MediaInfo? {
        guard let o, let id = o["id"] as? String, let key = o["key"] as? String, let nonce = o["nonce"] as? String else { return nil }
        return MediaInfo(id: id, key: key, nonce: nonce, mime: o["mime"] as? String ?? "application/octet-stream",
                         name: o["name"] as? String ?? "file", size: (o["size"] as? NSNumber)?.intValue ?? 0,
                         voice: o["voice"] as? Bool ?? false, duration: (o["duration"] as? NSNumber)?.int64Value ?? 0)
    }

    init(id: String, key: String, nonce: String, mime: String, name: String, size: Int, voice: Bool = false, duration: Int64 = 0) {
        self.id = id; self.key = key; self.nonce = nonce; self.mime = mime; self.name = name; self.size = size; self.voice = voice; self.duration = duration
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        key = try c.decode(String.self, forKey: .key)
        nonce = try c.decode(String.self, forKey: .nonce)
        mime = try c.decode(String.self, forKey: .mime)
        name = try c.decode(String.self, forKey: .name)
        size = try c.decode(Int.self, forKey: .size)
        voice = try c.decodeIfPresent(Bool.self, forKey: .voice) ?? false
        duration = try c.decodeIfPresent(Int64.self, forKey: .duration) ?? 0
    }
}

struct ChatMessage: Codable, Identifiable, Hashable {
    var id: String { clientId }
    var clientId: String
    var peer: String
    var outgoing: Bool
    var text: String
    var createdAt: Int64
    var serverId: Int64 = 0
    var readable = true
    var media: MediaInfo? = nil
    /// Автор (важно для групп).
    var sender: String = ""
    /// Голосовое прослушано.
    var listened = false

    init(clientId: String, peer: String, outgoing: Bool, text: String, createdAt: Int64, serverId: Int64 = 0,
         readable: Bool = true, media: MediaInfo? = nil, sender: String = "", listened: Bool = false) {
        self.clientId = clientId; self.peer = peer; self.outgoing = outgoing; self.text = text; self.createdAt = createdAt
        self.serverId = serverId; self.readable = readable; self.media = media; self.sender = sender; self.listened = listened
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        clientId = try c.decode(String.self, forKey: .clientId)
        peer = try c.decode(String.self, forKey: .peer)
        outgoing = try c.decode(Bool.self, forKey: .outgoing)
        text = try c.decode(String.self, forKey: .text)
        createdAt = try c.decode(Int64.self, forKey: .createdAt)
        serverId = try c.decodeIfPresent(Int64.self, forKey: .serverId) ?? 0
        readable = try c.decodeIfPresent(Bool.self, forKey: .readable) ?? true
        media = try c.decodeIfPresent(MediaInfo.self, forKey: .media)
        sender = try c.decodeIfPresent(String.self, forKey: .sender) ?? ""
        listened = try c.decodeIfPresent(Bool.self, forKey: .listened) ?? false
    }
}

enum LinkState { case notLinked, connecting, online, offline }

/// Мессенджер: E2E-шифрование, синхронизация, WebSocket, статусы — протокол как у Android.
@MainActor
final class Messenger: ObservableObject {
    unowned let app: AppModel
    @Published var chats: [String: [ChatMessage]] = [:]
    @Published var presence: [String: Presence] = [:]
    @Published var state: LinkState = .notLinked
    @Published var myStatus: String = UserDefaults.standard.string(forKey: "my_status") ?? "ONLINE" {
        didSet { UserDefaults.standard.set(myStatus, forKey: "my_status") }
    }
    @Published var readMarks: [String: Int64] = [:]
    @Published var groups: [Int64: GroupInfo] = [:]
    @Published var pins: Set<String> = []
    /// До какого сообщения собеседник (или кто-то в группе) прочитал диалог.
    @Published var peerRead: [String: Int64] = [:]
    var openChat: String?

    private var api: Api { app.api }
    private var me: String? { app.username }
    private var keyCache: [String: String] = [:]
    private var socket: URLSessionWebSocketTask?
    private var wsTask: Task<Void, Never>?
    private var wantSocket = false
    private var syncing = false

    init(app: AppModel) { self.app = app }

    // MARK: lifecycle

    func start() {
        guard let u = me else { return }
        loadChats(u)
        loadLocalState(u)
        Task {
            await refreshGroups()
            try? await api.putKey(E2E.publicKey(u))
            try? await api.setStatus(myStatus)
            await pushProfile()
            connect()
            await sync()
        }
    }

    func onSignedIn() { start() }

    func stop() {
        wantSocket = false
        wsTask?.cancel()
        socket?.cancel(with: .goingAway, reason: nil)
        socket = nil
        state = .notLinked
        chats = [:]
        keyCache = [:]
        groups = [:]
        pins = []
        peerRead = [:]
    }

    // MARK: локальное состояние: закрепы, «прочитано», группы

    private func loadLocalState(_ u: String) {
        pins = Set(UserDefaults.standard.stringArray(forKey: "pins_\(u)") ?? [])
        peerRead = (UserDefaults.standard.dictionary(forKey: "peer_read_\(u)") as? [String: NSNumber] ?? [:]).mapValues(\.int64Value)
        if let raw = UserDefaults.standard.data(forKey: "groups_\(u)"),
           let arr = (try? JSONSerialization.jsonObject(with: raw)) as? [JSON] {
            groups = Dictionary(uniqueKeysWithValues: arr.compactMap(GroupInfo.parse).map { ($0.id, $0) })
        }
    }

    func setPins(_ p: Set<String>) {
        pins = p
        if let u = me { UserDefaults.standard.set(Array(p), forKey: "pins_\(u)") }
        app.backupSoon()
    }

    func togglePin(_ peer: String) { setPins(pins.contains(peer) ? pins.subtracting([peer]) : pins.union([peer])) }

    private func setPeerRead(_ conv: String, _ upTo: Int64) {
        guard upTo > (peerRead[conv] ?? 0), let u = me else { return }
        peerRead[conv] = upTo
        UserDefaults.standard.set(peerRead.mapValues { NSNumber(value: $0) }, forKey: "peer_read_\(u)")
    }

    func refreshGroups() async {
        guard let list = try? await api.groups(), let u = me else { return }
        groups = Dictionary(uniqueKeysWithValues: list.map { ($0.id, $0) })
        if let raw = try? JSONSerialization.data(withJSONObject: list.map(\.json)) { UserDefaults.standard.set(raw, forKey: "groups_\(u)") }
    }

    func group(_ peer: String) -> GroupInfo? { isGroupConv(peer) ? groups[groupIdOf(peer)] : nil }

    func chatTitle(_ peer: String) -> String { group(peer)?.name ?? "@\(peer)" }

    func createGroup(name: String, members: [String]) async throws -> GroupInfo? {
        let g = try await api.createGroup(name: name.trimmingCharacters(in: .whitespaces), members: members)
        await refreshGroups()
        return g
    }

    func renameGroup(_ id: Int64, _ name: String) async throws { try await api.renameGroup(id, name); await refreshGroups() }
    func addGroupMember(_ id: Int64, _ u: String) async throws {
        try await api.addGroupMember(id, u.trimmingCharacters(in: .whitespaces).lowercased().replacingOccurrences(of: "@", with: ""))
        await refreshGroups()
    }
    func removeGroupMember(_ id: Int64, _ u: String) async throws {
        try await api.removeGroupMember(id, u)
        if u == me { chats["g:\(id)"] = nil; saveChats(); setPins(pins.subtracting(["g:\(id)"])) }
        await refreshGroups()
    }

    /// Удалить переписку у себя или у всех.
    func clearConversation(_ peer: String, forAll: Bool) async throws {
        try await api.clearConversation(peer, all: forAll)
        chats[peer] = nil
        saveChats()
        setPins(pins.subtracting([peer]))
    }

    // MARK: storage

    private func storeURL(_ u: String) -> URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("chats_\(u).json")
    }

    private func loadChats(_ u: String) {
        if let raw = try? Data(contentsOf: storeURL(u)), let d = try? JSONDecoder().decode([String: [ChatMessage]].self, from: raw) { chats = d }
        readMarks = (UserDefaults.standard.dictionary(forKey: "read_\(u)") as? [String: NSNumber] ?? [:]).mapValues(\.int64Value)
    }

    private var saveTask: Task<Void, Never>?
    private func saveChats() {
        guard let u = me else { return }
        saveTask?.cancel()
        let snapshot = chats
        let url = storeURL(u)
        saveTask = Task.detached {
            try? await Task.sleep(nanoseconds: 300_000_000)
            if Task.isCancelled { return }
            if let raw = try? JSONEncoder().encode(snapshot) { try? raw.write(to: url, options: .atomic) }
        }
    }

    private var lastMessageId: Int64 {
        get { Int64(UserDefaults.standard.double(forKey: "last_id_\(me ?? "")")) }
        set { UserDefaults.standard.set(Double(newValue), forKey: "last_id_\(me ?? "")") }
    }

    private var notifiedId: Int64 {
        get { Int64(UserDefaults.standard.double(forKey: "notified_\(me ?? "")")) }
        set { UserDefaults.standard.set(Double(newValue), forKey: "notified_\(me ?? "")") }
    }

    /// Чат открыт и сообщения видны — отмечаем прочитанным (у собеседника появятся две галочки).
    func markRead(_ peer: String) {
        guard let u = me, let maxId = chats[peer]?.map(\.serverId).max(), maxId > (readMarks[peer] ?? 0) else { return }
        readMarks[peer] = maxId
        UserDefaults.standard.set(readMarks.mapValues { NSNumber(value: $0) }, forKey: "read_\(u)")
        if peer != u { Task { try? await api.read(peer, upTo: maxId) } }
    }

    /// Входящее голосовое впервые прослушано.
    func markListened(_ m: ChatMessage) {
        guard !m.outgoing, !m.listened, m.serverId > 0 else { return }
        var x = m
        x.listened = true
        upsert(x)
        Task { try? await api.listened(m.serverId) }
    }

    func unread(_ peer: String) -> Int {
        let r = readMarks[peer] ?? 0
        return chats[peer]?.filter { !$0.outgoing && $0.serverId > r }.count ?? 0
    }

    struct Conversation: Identifiable { var id: String { peer }; let peer: String; let last: ChatMessage }

    var conversations: [Conversation] {
        var out: [Conversation] = chats.compactMap { peer, list in
            guard peer != me, let last = list.max(by: { $0.createdAt < $1.createdAt }) else { return nil }
            return Conversation(peer: peer, last: last)
        }
        // Группы без сообщений тоже видны
        for g in groups.values where chats[g.conv]?.isEmpty ?? true {
            out.append(Conversation(peer: g.conv, last: ChatMessage(clientId: "empty_\(g.id)", peer: g.conv, outgoing: false, text: "Группа создана", createdAt: 0)))
        }
        return out.sorted { a, b in
            let pa = pins.contains(a.peer), pb = pins.contains(b.peer)
            if pa != pb { return pa }
            return a.last.createdAt > b.last.createdAt
        }
    }

    private func upsert(_ m: ChatMessage) {
        var list = chats[m.peer] ?? []
        if let i = list.firstIndex(where: { $0.clientId == m.clientId }) { list[i] = m } else { list.append(m) }
        list.sort { $0.createdAt < $1.createdAt }
        chats[m.peer] = list
        saveChats()
    }

    // MARK: sending

    private func peerKey(_ peer: String, refresh: Bool = false) async throws -> String? {
        if !refresh, let k = keyCache[peer] { return k }
        guard let k = try await api.profile(peer).publicKey else { return nil }
        keyCache[peer] = k
        return k
    }

    func send(_ peer: String, _ text: String) async throws {
        let clean = String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(maxChars))
        guard !clean.isEmpty else { return }
        try await sendPayload(peer, clean, nil)
    }

    private func sendPayload(_ peer: String, _ text: String, _ media: MediaInfo?) async throws {
        guard let u = me else { throw ApiError(code: 0, error: "not_linked", message: "Мессенджер не подключён") }
        if isGroupConv(peer) { return try await sendGroupPayload(u, peer, text, media) }
        let isSelf = peer == u
        let myKey = E2E.publicKey(u)
        let theirKey: String
        if isSelf { theirKey = myKey } else {
            guard let k = try await peerKey(peer, refresh: true) else {
                throw ApiError(code: 0, error: "no_key", message: "@\(peer) ещё не включил шифрование — попросите его открыть «Мессенджер»")
            }
            theirKey = k
        }
        var payload: JSON = ["v": 1, "text": text]
        if let media { payload["media"] = media.json }
        let payloadStr = String(data: try JSONSerialization.data(withJSONObject: payload), encoding: .utf8)!
        let clientId = UUID().uuidString.lowercased()
        var msg = ChatMessage(clientId: clientId, peer: peer, outgoing: true, text: text, createdAt: nowMs(), media: media, sender: u)
        upsert(msg)
        await throttle()
        let ct = try E2E.encrypt(recipientPublicKey: theirKey, payloadStr, from: u, to: peer)
        let ctSelf = try isSelf ? ct : E2E.encrypt(recipientPublicKey: myKey, payloadStr, from: u, to: peer)
        let (id, createdAt) = try await api.send(to: peer, ctRecipient: ct, ctSender: ctSelf, clientId: clientId, mediaIds: media.map { [$0.id] } ?? [])
        msg.serverId = id
        msg.createdAt = createdAt
        upsert(msg)
        markRead(peer)
    }

    /// Групповое сообщение: шифруется отдельно ключом каждого участника (и своим).
    private func sendGroupPayload(_ u: String, _ peer: String, _ text: String, _ media: MediaInfo?) async throws {
        if group(peer) == nil { await refreshGroups() }
        guard var g = group(peer) else { throw ApiError(code: 404, error: "no_group", message: "Группа не найдена") }
        var payload: JSON = ["v": 1, "text": text]
        if let media { payload["media"] = media.json }
        let payloadStr = String(data: try JSONSerialization.data(withJSONObject: payload), encoding: .utf8)!
        let clientId = UUID().uuidString.lowercased()
        var msg = ChatMessage(clientId: clientId, peer: peer, outgoing: true, text: text, createdAt: nowMs(), media: media, sender: u)
        upsert(msg)
        await throttle()
        var attempt = 0
        while true {
            var cts: [String: String] = [:]
            for m in g.members {
                let key: String
                if m.username == u { key = E2E.publicKey(u) }
                else if let k = m.publicKey { key = k }
                else if let k = try await peerKey(m.username, refresh: true) { key = k }
                else { throw ApiError(code: 0, error: "no_key", message: "@\(m.username) ещё не включил шифрование") }
                cts[m.username] = try E2E.encrypt(recipientPublicKey: key, payloadStr, from: u, to: peer)
            }
            do {
                let (id, createdAt) = try await api.sendGroup(g.id, clientId: clientId, cts: cts, mediaIds: media.map { [$0.id] } ?? [])
                msg.serverId = id
                msg.createdAt = createdAt
                upsert(msg)
                markRead(peer)
                return
            } catch let e as ApiError where e.error == "members_changed" && attempt == 0 {
                attempt += 1
                await refreshGroups()
                guard let ng = group(peer) else { throw e }
                g = ng
            }
        }
    }

    /// Голосовое (AAC до 5 минут): шифруется как файл, в сообщении — пометка voice и длительность.
    func sendVoice(_ peer: String, url: URL, durationMs: Int64) async throws {
        let data = try Data(contentsOf: url)
        guard data.count <= maxMediaBytes else { throw ApiError(code: 413, error: "file_too_large", message: "Голосовое больше 30 МБ") }
        let (cipher, key, nonce) = try E2E.encryptFile(data)
        let id = try await api.uploadMedia(cipher)
        try? data.write(to: mediaDir.appendingPathComponent(id))
        try? FileManager.default.removeItem(at: url)
        let media = MediaInfo(id: id, key: key, nonce: nonce, mime: "audio/mp4", name: "voice.m4a", size: data.count, voice: true, duration: min(durationMs, maxVoiceMs))
        try await sendPayload(peer, "", media)
    }

    private var sendTimes: [Date] = []

    /// Сервер принимает не больше 2 сообщений/файлов за 5 секунд — лишнее ждёт очереди.
    private func throttle() async {
        while true {
            sendTimes.removeAll { Date().timeIntervalSince($0) > 5.1 }
            if sendTimes.count < 2 { break }
            let wait = 5.1 - Date().timeIntervalSince(sendTimes[0])
            try? await Task.sleep(nanoseconds: UInt64(max(0.05, wait) * 1_000_000_000))
        }
        sendTimes.append(Date())
    }

    private var mediaDir: URL {
        let d = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("chat_media")
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    func sendMedia(_ peer: String, data: Data, mime: String, name: String, caption: String) async throws {
        guard data.count <= maxMediaBytes else { throw ApiError(code: 413, error: "file_too_large", message: "Файл больше 30 МБ") }
        let (cipher, key, nonce) = try E2E.encryptFile(data)
        let id = try await api.uploadMedia(cipher)
        try? data.write(to: mediaDir.appendingPathComponent(id))
        let media = MediaInfo(id: id, key: key, nonce: nonce, mime: mime, name: name, size: data.count)
        try await sendPayload(peer, String(caption.trimmingCharacters(in: .whitespacesAndNewlines).prefix(maxChars)), media)
    }

    /// Расшифрованное вложение (скачивается при первом открытии).
    func mediaData(_ m: MediaInfo) async throws -> Data {
        let file = mediaDir.appendingPathComponent(m.id)
        if let d = try? Data(contentsOf: file) { return d }
        let plain = try E2E.decryptFile(try await api.downloadMedia(m.id), key: m.key, nonce: m.nonce)
        try? plain.write(to: file)
        return plain
    }

    func mediaFileURL(_ m: MediaInfo) async throws -> URL {
        let data = try await mediaData(m)
        let ext = (m.name as NSString).pathExtension
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(m.id + (ext.isEmpty ? "" : "." + ext))
        try data.write(to: url)
        return url
    }

    // MARK: sync

    func sync() async {
        guard let u = me, !syncing else { return }
        syncing = true
        defer { syncing = false }
        while true {
            let page: (list: [RemoteMessage], cursor: Int64, more: Bool)
            do { page = try await api.messages(since: lastMessageId) } catch {
                if let e = error as? ApiError, e.code == 401 { await sessionExpired() }
                return
            }
            for m in page.list { process(u, m) }
            if page.cursor > lastMessageId { lastMessageId = page.cursor }
            if !page.more { break }
        }
        if let r = try? await api.receipts() {
            for x in r.reads { setPeerRead(x.conv, x.upTo) }
            applyListened(Set(r.listened))
        }
    }

    private func applyListened(_ ids: Set<Int64>) {
        guard !ids.isEmpty else { return }
        var changed = false
        for (peer, list) in chats {
            var l = list
            for i in l.indices where !l[i].listened && ids.contains(l[i].serverId) { l[i].listened = true; changed = true }
            chats[peer] = l
        }
        if changed { saveChats() }
    }

    private func sessionExpired() async {
        app.show("Сессия истекла — войдите заново")
        await app.logout()
    }

    private func process(_ u: String, _ m: RemoteMessage) {
        let outgoing = m.from == u
        let peer = m.group > 0 ? "g:\(m.group)" : (outgoing ? m.to : m.from)
        if m.group > 0 && groups[m.group] == nil { Task { await refreshGroups() } }
        let wasListened = chats[peer]?.first { $0.clientId == m.clientId }?.listened ?? false
        let payload = E2E.decrypt(me: u, m.ciphertext, from: m.from, to: m.to)
            .flatMap { $0.data(using: .utf8) }
            .flatMap { (try? JSONSerialization.jsonObject(with: $0)) as? JSON }
        let text = payload?["text"] as? String ?? ""
        let media = MediaInfo.parse(payload?["media"] as? JSON)
        upsert(ChatMessage(clientId: m.clientId, peer: peer, outgoing: outgoing,
                           text: payload == nil ? "🔒 Не удалось расшифровать (сообщение для другого ключа)" : text,
                           createdAt: m.createdAt, serverId: m.id, readable: payload != nil, media: media,
                           sender: m.from, listened: wasListened))
        if outgoing || openChat == peer { markRead(peer) }
        else if m.id > notifiedId {
            notifiedId = m.id
            if myStatus != "DND" && peer != u {
                let preview = media?.voice == true ? "🎤 Голосовое сообщение" :
                    (media != nil ? "📎 " + (text.isEmpty ? media!.name : text) : (payload == nil ? "Новое сообщение" : text))
                notify(peer, isGroupConv(peer) ? "@\(m.from): \(preview)" : preview)
            }
        }
    }

    private func notify(_ peer: String, _ text: String) {
        let c = UNMutableNotificationContent()
        c.title = chatTitle(peer)
        c.body = text
        c.sound = .default
        c.threadIdentifier = "chat_\(peer)"
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: UUID().uuidString, content: c, trigger: nil))
    }

    // MARK: WebSocket

    private func connect() {
        wantSocket = true
        if wsTask != nil { return }
        wsTask = Task { [weak self] in
            var backoff: UInt64 = 1
            while let self, self.wantSocket, self.me != nil, !Task.isCancelled {
                guard let url = self.api.wsURL() else { break }
                self.state = .connecting
                let ws = self.api.session.webSocketTask(with: url)
                self.socket = ws
                ws.resume()
                ws.sendPing { _ in }
                var first = true
                while !Task.isCancelled {
                    do {
                        let msg = try await ws.receive()
                        if first {
                            first = false
                            self.state = .online
                            backoff = 1
                            Task { await self.sync() }
                        }
                        if case .string(let s) = msg { self.onFrame(s) }
                    } catch { break }
                }
                if !self.wantSocket { break }
                self.state = .offline
                try? await Task.sleep(nanoseconds: backoff * 1_000_000_000)
                backoff = min(30, backoff * 2)
            }
            self?.wsTask = nil
        }
        // Пинги держат соединение и сразу отмечают «онлайн».
        Task { [weak self] in
            while let self, self.wantSocket {
                self.socket?.sendPing { [weak self] err in
                    if err == nil { Task { @MainActor in if self?.state == .connecting { self?.state = .online } } }
                }
                try? await Task.sleep(nanoseconds: 25_000_000_000)
            }
        }
    }

    private func onFrame(_ s: String) {
        guard let u = me, let d = s.data(using: .utf8), let o = (try? JSONSerialization.jsonObject(with: d)) as? JSON else { return }
        switch o["type"] as? String {
        case "presence":
            if let name = o["username"] as? String, let p = Presence.parse(o) { presence[name] = p }
        case "read":
            if let conv = o["conv"] as? String { setPeerRead(conv, (o["upTo"] as? NSNumber)?.int64Value ?? 0) }
        case "listened":
            if let id = (o["id"] as? NSNumber)?.int64Value { applyListened([id]) }
        case "cleared":
            if let conv = o["conv"] as? String {
                let upTo = (o["upTo"] as? NSNumber)?.int64Value ?? 0
                chats[conv] = chats[conv]?.filter { $0.serverId == 0 || $0.serverId > upTo }
                saveChats()
            }
        case "group":
            Task { await refreshGroups() }
        case "message":
            if let m = (o["message"] as? JSON).flatMap(RemoteMessage.parse) {
                process(u, m)
                if m.id > lastMessageId { lastMessageId = m.id }
            }
        default: break
        }
    }

    func appDidBecomeActive() {
        guard me != nil else { return }
        if socket == nil || state == .offline { connect() }
        Task { await sync() }
    }

    // MARK: presence

    private var activeScreens = 0
    private var presenceTask: Task<Void, Never>?

    func enterMessenger() {
        activeScreens += 1
        guard activeScreens == 1 else { return }
        presenceTask?.cancel()
        presenceTask = Task { [weak self] in
            while let self, !Task.isCancelled {
                if self.me != nil { try? await self.api.setPresence(true) }
                try? await Task.sleep(nanoseconds: 60_000_000_000)
            }
        }
    }

    func leaveMessenger() {
        activeScreens = max(0, activeScreens - 1)
        guard activeScreens == 0 else { return }
        presenceTask?.cancel()
        presenceTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            guard let self, !Task.isCancelled, self.activeScreens == 0, self.me != nil else { return }
            try? await self.api.setPresence(false)
        }
    }

    func setMyStatus(_ s: String) {
        myStatus = s
        Task { try? await api.setStatus(s) }
        app.backupSoon()
    }

    func refreshPresence(_ users: [String]) async {
        if let fresh = try? await api.presence(users) { presence.merge(fresh) { $1 } }
    }

    private var lastShared: String?
    private var shareTask: Task<Void, Never>?

    func shareNowPlaying(title: String, artist: String, app appName: String) {
        let vis = app.data.privacy.nowPlaying.rawValue
        let key = "\(title)|\(artist)|\(appName)|\(vis)"
        guard key != lastShared, me != nil else { return }
        shareTask?.cancel()
        shareTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 1_500_000_000)
            guard let self, !Task.isCancelled else { return }
            if (try? await self.api.setNowPlaying(title, artist, appName, vis: vis)) != nil { self.lastShared = key }
        }
    }

    // MARK: profile

    private var pushTask: Task<Void, Never>?

    func pushProfileSoon() {
        pushTask?.cancel()
        pushTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            if Task.isCancelled { return }
            await self?.pushProfile()
        }
    }

    /// Публикует профиль. Поля с видимостью «Никто» не отправляются вовсе.
    func pushProfile() async {
        guard me != nil else { return }
        let d = app.data, p = d.privacy, h = History(d)
        let day = h.today
        var fields: JSON = [:]
        func put(_ name: String, _ vis: Visibility, _ value: () -> Any) {
            if vis != .NOBODY { fields[name] = ["vis": vis.rawValue, "value": value()] as JSON }
        }
        put("streak", p.streak) { ["current": h.currentStreak, "best": h.bestStreakEver] as JSON }
        put("achievements", p.achievements) {
            let list = h.achievements
            return ["unlocked": list.filter(\.unlocked).count, "total": list.count,
                    "list": list.filter(\.unlocked).map { ["emoji": $0.emoji, "title": $0.title] }] as JSON
        }
        put("habits", p.habits) {
            h.goodHabits.map { habit -> JSON in
                let done = h.doneByHabit[habit.id] ?? []
                return ["title": habit.title, "emoji": habit.emoji,
                        "week": (0...6).reversed().map { done.contains(day - Int64($0)) },
                        "today": habit.scheduled(on: day) && done.contains(day)]
            }
        }
        put("water", p.water) {
            let avg = (0...6).map { h.waterByDay[day - Int64($0)] ?? 0 }.reduce(0, +) / 7
            return ["todayMl": h.waterByDay[day] ?? 0, "goalMl": d.profile.waterGoalMl, "avg7Ml": avg] as JSON
        }
        let avatar = await syncAvatar(d.profile)
        try? await api.putProfile(["displayName": d.profile.displayName.isEmpty ? (me ?? "") : d.profile.displayName,
                                   "avatar": avatar, "bio": p.bio, "discoverable": p.discoverable, "fields": fields])
    }

    /// Фото профиля загружается на сервер один раз (и при смене) — его видят собеседники.
    private func syncAvatar(_ p: Profile) async -> String {
        guard let u = me else { return "" }
        let key = "uploaded_avatar_\(u)"
        if let photo = p.avatarPhoto {
            let hash = SHA256.hash(data: photo).prefix(12).map { String(format: "%02x", $0) }.joined()
            if UserDefaults.standard.string(forKey: key) != hash {
                if (try? await api.putAvatar(AppModel.jpeg512(photo) ?? photo)) != nil { UserDefaults.standard.set(hash, forKey: key) }
            }
            return UserDefaults.standard.string(forKey: key) == hash ? "photo" : ""
        }
        if let a = p.avatar, a.hasPrefix("preset:") { UserDefaults.standard.removeObject(forKey: key); return a }
        if UserDefaults.standard.string(forKey: key) != nil {
            try? await api.deleteAvatar()
            UserDefaults.standard.removeObject(forKey: key)
        }
        return ""
    }

    // MARK: users

    func search(_ q: String) async throws -> [RemoteUser] {
        let list = try await api.search(q)
        for u in list { if let p = u.presence { presence[u.username] = p } }
        return list
    }

    func profile(_ u: String) async throws -> RemoteProfile {
        let p = try await api.profile(u)
        if let pr = p.presence { presence[u] = pr }
        if let k = p.publicKey { keyCache[u] = k }
        return p
    }

    func safetyCode(_ peer: String) async -> String? {
        guard let u = me, let theirs = try? await peerKey(peer) else { return nil }
        return E2E.safetyCode(E2E.publicKey(u), theirs)
    }
}
