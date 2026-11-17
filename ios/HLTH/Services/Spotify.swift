import Foundation
import AuthenticationServices
import CryptoKit
import UIKit

struct SpotifyPlaylist: Identifiable, Hashable { let id: String; let name: String; let image: String?; let total: Int; let uri: String }
struct SpotifyTrack: Identifiable, Hashable { var id: String { uri }; let uri: String; let name: String; let artist: String; let image: String?; let durationMs: Int }
struct SpotifyPlayback { let track: SpotifyTrack?; let isPlaying: Bool; let progressMs: Int; let device: String? }

/// Spotify Web API: вход по PKCE (без секрета), плейлисты и управление воспроизведением.
@MainActor
final class Spotify: NSObject, ObservableObject, ASWebAuthenticationPresentationContextProviding {
    static let shared = Spotify()
    static let clientId = "a7e8c10ea7964e41972f196c305112cd"
    static let redirect = "hlthhbbty://spotify-callback"
    static let scopes = ["user-read-private", "user-read-playback-state", "user-modify-playback-state",
                         "user-read-currently-playing", "playlist-read-private", "playlist-read-collaborative"]

    @Published var userName: String?
    @Published var playlists: [SpotifyPlaylist] = []
    @Published var playback: SpotifyPlayback?
    @Published var error: String?

    private var access: String? { get { UserDefaults.standard.string(forKey: "sp_access") } set { UserDefaults.standard.set(newValue, forKey: "sp_access") } }
    private var refresh: String? { get { Keychain.get("sp_refresh").flatMap { String(data: $0, encoding: .utf8) } } set { Keychain.set(newValue.map { Data($0.utf8) }, "sp_refresh") } }
    private var expiresAt: Double { get { UserDefaults.standard.double(forKey: "sp_exp") } set { UserDefaults.standard.set(newValue, forKey: "sp_exp") } }

    var isLoggedIn: Bool { refresh != nil }
    private var session: ASWebAuthenticationSession?

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first ?? ASPresentationAnchor()
        }
    }

    func login() {
        let verifier = (0..<64).map { _ in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~".randomElement()! }.map(String.init).joined()
        let challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        var c = URLComponents(string: "https://accounts.spotify.com/authorize")!
        c.queryItems = [
            .init(name: "client_id", value: Self.clientId), .init(name: "response_type", value: "code"),
            .init(name: "redirect_uri", value: Self.redirect), .init(name: "code_challenge_method", value: "S256"),
            .init(name: "code_challenge", value: challenge), .init(name: "scope", value: Self.scopes.joined(separator: " ")),
            .init(name: "show_dialog", value: "true"),
        ]
        let s = ASWebAuthenticationSession(url: c.url!, callbackURLScheme: "hlthhbbty") { [weak self] url, err in
            Task { @MainActor in
                guard let self else { return }
                guard let url, let code = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "code" })?.value else {
                    if let e = URLComponents(url: url ?? URL(string: "x:")!, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "error" })?.value {
                        self.error = "Spotify: \(e)"
                    }
                    return
                }
                await self.token(["grant_type": "authorization_code", "code": code, "redirect_uri": Self.redirect, "code_verifier": verifier])
                await self.load()
            }
        }
        s.presentationContextProvider = self
        s.prefersEphemeralWebBrowserSession = false
        session = s
        s.start()
    }

    func logout() {
        access = nil; refresh = nil; expiresAt = 0
        userName = nil; playlists = []; playback = nil
    }

    private func token(_ params: [String: String]) async {
        var req = URLRequest(url: URL(string: "https://accounts.spotify.com/api/token")!)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        var all = params
        all["client_id"] = Self.clientId
        req.httpBody = all.map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? "")" }.joined(separator: "&").data(using: .utf8)
        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let j = (try? JSONSerialization.jsonObject(with: data)) as? JSON else { error = "Spotify: нет связи"; return }
        if let a = j["access_token"] as? String {
            access = a
            if let r = j["refresh_token"] as? String { refresh = r }
            expiresAt = Date().timeIntervalSince1970 + Double(j["expires_in"] as? Int ?? 3600) - 60
        } else {
            error = "Spotify: " + (j["error_description"] as? String ?? j["error"] as? String ?? "ошибка входа")
            if j["error"] as? String == "invalid_grant" { logout() }
        }
    }

    private func validToken() async -> String? {
        guard let r = refresh else { return nil }
        if access == nil || Date().timeIntervalSince1970 > expiresAt { await token(["grant_type": "refresh_token", "refresh_token": r]) }
        return access
    }

    private func request(_ method: String, _ path: String, _ body: JSON? = nil) async -> (Int, JSON?) {
        guard let t = await validToken() else { return (401, nil) }
        var req = URLRequest(url: URL(string: "https://api.spotify.com/v1" + path)!)
        req.httpMethod = method
        req.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization")
        if let body { req.httpBody = try? JSONSerialization.data(withJSONObject: body); req.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        else if method != "GET" { req.httpBody = Data() }
        guard let (data, resp) = try? await URLSession.shared.data(for: req) else { return (0, nil) }
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let j = (try? JSONSerialization.jsonObject(with: data)) as? JSON
        if code == 403, let e = j?["error"] as? JSON, let m = e["message"] as? String {
            error = m.lowercased().contains("premium") ? "Управление воспроизведением доступно только с Spotify Premium" :
                (m.lowercased().contains("registered") ? "Аккаунт Spotify не добавлен разработчиком — напишите нам свой email" : "Spotify: \(m)")
        }
        return (code, j)
    }

    private func parseTrack(_ t: JSON?) -> SpotifyTrack? {
        guard let t, let uri = t["uri"] as? String else { return nil }
        let artists = (t["artists"] as? [JSON] ?? []).compactMap { $0["name"] as? String }.joined(separator: ", ")
        let img = ((t["album"] as? JSON)?["images"] as? [JSON])?.first?["url"] as? String
        return SpotifyTrack(uri: uri, name: t["name"] as? String ?? "", artist: artists, image: img, durationMs: t["duration_ms"] as? Int ?? 0)
    }

    func load() async {
        guard isLoggedIn else { return }
        let (_, me) = await request("GET", "/me")
        userName = me?["display_name"] as? String ?? me?["id"] as? String
        let (_, pl) = await request("GET", "/me/playlists?limit=50")
        playlists = (pl?["items"] as? [JSON] ?? []).compactMap { p in
            guard let id = p["id"] as? String else { return nil }
            return SpotifyPlaylist(id: id, name: p["name"] as? String ?? "", image: (p["images"] as? [JSON])?.first?["url"] as? String,
                                   total: ((p["tracks"] as? JSON)?["total"] as? Int) ?? 0, uri: p["uri"] as? String ?? "spotify:playlist:\(id)")
        }
        await refreshPlayback()
    }

    func tracks(_ playlist: SpotifyPlaylist) async -> [SpotifyTrack] {
        let (_, j) = await request("GET", "/playlists/\(playlist.id)/tracks?limit=100")
        return (j?["items"] as? [JSON] ?? []).compactMap { parseTrack($0["track"] as? JSON) }
    }

    func refreshPlayback() async {
        guard isLoggedIn else { return }
        let (code, j) = await request("GET", "/me/player?additional_types=track")
        if code == 204 || j == nil { playback = nil; return }
        playback = SpotifyPlayback(track: parseTrack(j?["item"] as? JSON), isPlaying: j?["is_playing"] as? Bool ?? false,
                                   progressMs: j?["progress_ms"] as? Int ?? 0, device: (j?["device"] as? JSON)?["name"] as? String)
    }

    func currentTrack() async -> Track? {
        guard isLoggedIn else { return nil }
        await refreshPlayback()
        guard let p = playback, p.isPlaying, let t = p.track else { return nil }
        return Track(title: t.name, artist: t.artist, app: "Spotify")
    }

    /// Воспроизведение: на активном устройстве; если его нет — открываем Spotify, чтобы он стал устройством.
    func play(context: String? = nil, track: String? = nil) async {
        var body: JSON = [:]
        if let context { body["context_uri"] = context }
        if let context, let track { _ = context; body["offset"] = ["uri": track] }
        if context == nil, let track { body["uris"] = [track] }
        var (code, _) = await request("PUT", "/me/player/play", body.isEmpty ? nil : body)
        if code == 404 {
            let (_, d) = await request("GET", "/me/player/devices")
            if let dev = (d?["devices"] as? [JSON])?.first?["id"] as? String {
                (code, _) = await request("PUT", "/me/player/play?device_id=\(dev)", body.isEmpty ? nil : body)
            } else if let url = URL(string: "spotify:"), UIApplication.shared.canOpenURL(url) {
                await UIApplication.shared.open(URL(string: context ?? track ?? "spotify:")!)
            } else {
                error = "Откройте Spotify на любом устройстве — и музыка заиграет"
            }
        }
        try? await Task.sleep(nanoseconds: 600_000_000)
        await refreshPlayback()
    }

    func toggle() async {
        if playback?.isPlaying == true { _ = await request("PUT", "/me/player/pause") } else { await play() }
        try? await Task.sleep(nanoseconds: 400_000_000)
        await refreshPlayback()
    }
    func next() async { _ = await request("POST", "/me/player/next"); try? await Task.sleep(nanoseconds: 500_000_000); await refreshPlayback() }
    func previous() async { _ = await request("POST", "/me/player/previous"); try? await Task.sleep(nanoseconds: 500_000_000); await refreshPlayback() }
}
