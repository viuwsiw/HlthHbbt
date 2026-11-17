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
                Task { @MainActor in self?.status = conn.status }
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

            GlassButton(title: "Удалить профиль VPN из Настроек", systemImage: "trash", role: .destructive) { Task { await vpn.remove() } }
        }
        .task { await vpn.load() }
    }
}
