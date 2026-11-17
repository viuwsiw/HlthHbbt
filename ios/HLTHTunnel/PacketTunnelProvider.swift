import NetworkExtension

/// VPN-туннель HLTH: пакеты из системы → hev-socks5-tunnel (TCP/IP-стек, FakeDNS) → локальный SOCKS5 →
/// VLESS поверх WebSocket + TLS на сервер. Подключение видно в Настройках → VPN.
final class PacketTunnelProvider: NEPacketTunnelProvider {
    private var socks: VlessSocksServer?
    private let socksPort: UInt16 = 10808

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        let proto = protocolConfiguration as? NETunnelProviderProtocol
        guard let key = proto?.providerConfiguration?["key"] as? String, let cfg = VlessConfig(key) else {
            completionHandler(NSError(domain: "HLTHVPN", code: 1, userInfo: [NSLocalizedDescriptionKey: "Нет ключа VLESS или он не поддерживается (нужен type=ws)"]))
            return
        }
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        settings.mtu = 1500
        let ipv4 = NEIPv4Settings(addresses: ["198.18.0.1"], subnetMasks: ["255.255.255.0"])
        ipv4.includedRoutes = [NEIPv4Route.default()]
        settings.ipv4Settings = ipv4
        let dns = NEDNSSettings(servers: ["198.18.0.2"])
        dns.matchDomains = [""]
        settings.dnsSettings = dns

        setTunnelNetworkSettings(settings) { [weak self] error in
            guard let self else { return }
            if let error { completionHandler(error); return }
            do {
                let s = VlessSocksServer(config: cfg, port: self.socksPort)
                try s.start()
                self.socks = s
            } catch {
                completionHandler(error)
                return
            }
            let fd = hlth_tun_fd()
            guard fd >= 0 else {
                completionHandler(NSError(domain: "HLTHVPN", code: 2, userInfo: [NSLocalizedDescriptionKey: "Не найден интерфейс туннеля"]))
                return
            }
            let yaml = """
            tunnel:
              mtu: 1500
            socks5:
              port: \(self.socksPort)
              address: 127.0.0.1
              udp: 'udp'
            mapdns:
              address: 198.18.0.2
              port: 53
              network: 100.64.0.0
              netmask: 255.192.0.0
              cache-size: 10000
            misc:
              task-stack-size: 24576
              tcp-buffer-size: 4096
              max-session-count: 1200
              connect-timeout: 10000
            """
            Thread.detachNewThread {
                let bytes = Array(yaml.utf8)
                _ = bytes.withUnsafeBufferPointer { buf in
                    hev_socks5_tunnel_main_from_str(buf.baseAddress, UInt32(buf.count), fd)
                }
            }
            completionHandler(nil)
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        hev_socks5_tunnel_quit()
        socks?.stop()
        socks = nil
        completionHandler()
    }
}
