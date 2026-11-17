import SwiftUI
import NetworkExtension

/// VPN через системный API Apple (NetworkExtension): профиль появляется в Настройки → VPN.
@MainActor
final class VPNManager: ObservableObject {
    static let shared = VPNManager()
    @Published var status: NEVPNStatus = .invalid
    @Published var key: String = UserDefaults.standard.string(forKey: "vpn_key") ?? "" {
        didSet { UserDefaults.standard.set(key, forKey: "vpn_key") }
    }
    @Published var error: String?
    @Published var serverMessage: String?
    private var manager: NETunnelProviderManager?
    private var observer: NSObjectProtocol?

    /// Идентификатор встроенного расширения туннеля (после переподписи он может отличаться).
    var providerBundleId: String? {
        guard let dir = Bundle.main.builtInPlugInsURL,
              let items = try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) else { return nil }
        for u in items where u.pathExtension == "appex" {
            if let b = Bundle(url: u),
               let ext = b.object(forInfoDictionaryKey: "NSExtension") as? [String: Any],
               ext["NSExtensionPointIdentifier"] as? String == "com.apple.networkextension.packet-tunnel" {
                return b.bundleIdentifier
            }
        }
        return nil
    }

    func load() async {
        do {
            let list = try await NETunnelProviderManager.loadAllFromPreferences()
            manager = list.first
            status = manager?.connection.status ?? .invalid
        } catch { self.error = error.localizedDescription }
        if observer == nil {
            observer = NotificationCenter.default.addObserver(forName: .NEVPNStatusDidChange, object: nil, queue: .main) { [weak self] n in
                guard let conn = n.object as? NEVPNConnection else { return }
                Task { @MainActor in
                    self?.status = conn.status
                    if conn.status == .disconnected { self?.lastDisconnectReason(conn) }
                }
            }
        }
    }

    func fetchKeyFromServer() async {
        error = nil
        do {
            let j = try await AppModel.shared.api.call("GET", "/vpn")
            if j["enabled"] as? Bool == true, let k = j["key"] as? String { key = k; serverMessage = nil }
            else { serverMessage = j["message"] as? String ?? "VPN на сервере недоступен" }
        } catch { self.error = error.localizedDescription }
    }

    func connect() async {
        error = nil
        guard let cfg = VlessConfig(key) else { error = "Ключ не распознан. Нужен vless://… с type=ws"; return }
        guard let ext = providerBundleId else {
            error = "В этой сборке нет расширения VPN. Оно удаляется, если подписать приложение профилем без Network Extensions."
            return
        }
        let m = manager ?? NETunnelProviderManager()
        let proto = NETunnelProviderProtocol()
        proto.providerBundleIdentifier = ext
        proto.serverAddress = cfg.host
        proto.providerConfiguration = ["key": key]
        m.protocolConfiguration = proto
        m.localizedDescription = "HLTH VPN"
        m.isEnabled = true
        do {
            try await m.saveToPreferences()
            try await m.loadFromPreferences()
            manager = m
            try m.connection.startVPNTunnel()
        } catch {
            let ns = error as NSError
            if ns.domain == NEVPNErrorDomain && ns.code == NEVPNError.configurationInvalid.rawValue || ns.localizedDescription.contains("permission") {
                self.error = "iOS не разрешила VPN: приложение подписано без права Network Extensions. Нужен профиль с Network Extensions (packet tunnel)."
            } else {
                self.error = error.localizedDescription
            }
        }
    }

    func disconnect() { manager?.connection.stopVPNTunnel() }

    @Published var diagnostics: String?

    /// Почему iOS отключила туннель (ошибка из расширения).
    private func lastDisconnectReason(_ conn: NEVPNConnection) {
        conn.fetchLastDisconnectError { [weak self] err in
            guard let err else { return }
            Task { @MainActor in self?.error = "Отключено: " + err.localizedDescription }
        }
    }

    /// Проверка сервера без VPN: WebSocket до пути VLESS должен открываться (ответ 101 и pong).
    func testServer() async {
        diagnostics = "Проверяем…"
        guard let cfg = VlessConfig(key), let url = cfg.url else { diagnostics = "Ключ не распознан"; return }
        var req = URLRequest(url: url)
        req.setValue(cfg.wsHost, forHTTPHeaderField: "Host")
        req.timeoutInterval = 10
        let task = URLSession.shared.webSocketTask(with: req)
        task.resume()
        let ok: Bool = await withCheckedContinuation { cont in
            var resumed = false
            task.sendPing { e in
                if !resumed { resumed = true; cont.resume(returning: e == nil) }
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 10) { if !resumed { resumed = true; cont.resume(returning: false) } }
        }
        let code = (task.response as? HTTPURLResponse)?.statusCode
        task.cancel(with: .goingAway, reason: nil)
        if ok {
            diagnostics = "✅ Сервер отвечает: WebSocket на \(cfg.host)\(cfg.path) открыт. Если VPN всё равно без интернета — пришлите текст ошибки ниже."
        } else if let code {
            diagnostics = "❌ Сервер ответил HTTP \(code) вместо WebSocket. " + (code == 404 || code == 502 ? "Скорее всего, на сервере не запущен Xray или путь VPN не совпадает — обновите сервер командой установки." : "")
        } else {
            diagnostics = "❌ Сервер \(cfg.host) недоступен (нет ответа за 10 секунд)."
        }
    }

    func remove() async {
        try? await manager?.removeFromPreferences()
        manager = nil
        status = .invalid
    }

    var statusText: String {
        switch status {
        case .connected: return "Подключено"
        case .connecting: return "Подключение…"
        case .disconnecting: return "Отключение…"
        case .reasserting: return "Переподключение…"
        case .disconnected: return "Отключено"
        default: return "Не настроено"
        }
    }
}

struct VPNView: View {
    @ObservedObject var vpn = VPNManager.shared
    @Environment(\.palette) private var p

    var body: some View {
        let on = vpn.status == .connected || vpn.status == .connecting || vpn.status == .reasserting
        Screen(title: "VPN", large: false) {
            VStack(spacing: 14) {
                ZStack {
                    Circle().fill(on ? AnyShapeStyle(p.gradient) : AnyShapeStyle(Color.gray.opacity(0.2))).frame(width: 130, height: 130)
                    Image(systemName: on ? "lock.shield.fill" : "shield.slash").font(.system(size: 50)).foregroundStyle(on ? p.onAccent : p.text2)
                }
                .glass(65)
                .animation(.spring(duration: 0.4), value: on)
                Text(vpn.statusText).font(.title3.bold()).foregroundStyle(p.text)
                if let c = VlessConfig(vpn.key) {
                    Text("\(c.name) · \(c.host)").font(.caption).foregroundStyle(p.text2)
                }
                if on {
                    GlassButton(title: "Отключить", systemImage: "stop.fill", role: .destructive) { vpn.disconnect() }
                } else {
                    GlassButton(title: "Подключить", systemImage: "bolt.fill", prominent: true) { Task { await vpn.connect() } }
                        .disabled(vpn.key.isEmpty)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)

            if let e = vpn.error { Text(e).font(.footnote).foregroundStyle(p.danger).glassCard() }

            SectionTitle("Ключ")
            VStack(alignment: .leading, spacing: 10) {
                TextField("vless://…", text: $vpn.key, axis: .vertical)
                    .font(.footnote.monospaced())
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .lineLimit(2...5)
                    .foregroundStyle(p.text)
                    .padding(12).glass(16)
                HStack(spacing: 10) {
                    GlassButton(title: "С сервера HLTH", systemImage: "icloud.and.arrow.down") { Task { await vpn.fetchKeyFromServer() } }
                    GlassButton(title: "Вставить", systemImage: "doc.on.clipboard") { if let s = UIPasteboard.general.string { vpn.key = s } }
                }
                if let m = vpn.serverMessage { Text(m).font(.caption).foregroundStyle(p.text2) }
                Text("Протокол: VLESS поверх WebSocket + TLS (порт 443, маскируется под обычный HTTPS). Подключение и отключение — здесь или в Настройки → VPN.")
                    .font(.caption).foregroundStyle(p.text2)
            }
            .glassCard()

            VStack(alignment: .leading, spacing: 8) {
                GlassButton(title: "Проверить сервер", systemImage: "stethoscope") { Task { await vpn.testServer() } }
                if let d = vpn.diagnostics { Text(d).font(.caption).foregroundStyle(p.text2).textSelection(.enabled) }
                if let ext = vpn.providerBundleId { Text("Расширение: \(ext)").font(.caption2).foregroundStyle(p.text2) }
                else { Text("⚠️ В этой сборке нет расширения VPN (удалено при подписи)").font(.caption2).foregroundStyle(p.danger) }
            }
            .glassCard()

            GlassButton(title: "Удалить профиль VPN из Настроек", systemImage: "trash", role: .destructive) { Task { await vpn.remove() } }
        }
        .task { await vpn.load() }
    }
}
