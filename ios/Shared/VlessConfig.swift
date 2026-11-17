import Foundation

/// Ключ VLESS: vless://UUID@host:port?type=ws&security=tls&path=/...&host=...&sni=...#Имя
struct VlessConfig: Equatable {
    let uuid: UUID
    let host: String
    let port: Int
    let path: String
    let wsHost: String
    let sni: String
    let tls: Bool
    let name: String

    init?(_ key: String) {
        let s = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard s.lowercased().hasPrefix("vless://"), let c = URLComponents(string: s),
              let user = c.user, let id = UUID(uuidString: user), let host = c.host, !host.isEmpty else { return nil }
        let q = Dictionary((c.queryItems ?? []).map { ($0.name.lowercased(), $0.value ?? "") }, uniquingKeysWith: { a, _ in a })
        let type = (q["type"] ?? "ws").lowercased()
        guard type == "ws" else { return nil }  // поддерживается транспорт WebSocket
        uuid = id
        self.host = host
        port = c.port ?? 443
        path = (q["path"]?.isEmpty == false ? q["path"]! : "/")
        wsHost = q["host"]?.isEmpty == false ? q["host"]! : host
        sni = q["sni"]?.isEmpty == false ? q["sni"]! : wsHost
        tls = (q["security"] ?? "tls").lowercased() == "tls"
        name = c.fragment?.removingPercentEncoding ?? "VLESS"
    }

    var url: URL? {
        var c = URLComponents()
        c.scheme = tls ? "wss" : "ws"
        c.host = host
        c.port = port
        c.percentEncodedPath = path.hasPrefix("/") ? path : "/" + path
        return c.url
    }
}
