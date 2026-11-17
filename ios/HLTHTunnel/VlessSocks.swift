import Foundation
import Network
import Security

/// Локальный SOCKS5-сервер (127.0.0.1) для hev-socks5-tunnel: каждое TCP-соединение уходит на сервер
/// по VLESS поверх WebSocket + TLS. DNS отвечает сам hev (FakeDNS), поэтому сюда приходят доменные имена.
final class VlessSocksServer {
    let config: VlessConfig
    let port: UInt16
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "hlth.vless", qos: .userInitiated, attributes: [])
    private var sessions = Set<VlessSession>()
    private let lock = NSLock()

    init(config: VlessConfig, port: UInt16) {
        self.config = config
        self.port = port
    }

    func start() throws {
        let params = NWParameters.tcp
        params.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!)
        params.allowLocalEndpointReuse = true
        let l = try NWListener(using: params)
        l.newConnectionHandler = { [weak self] conn in
            guard let self else { conn.cancel(); return }
            let s = VlessSession(client: conn, config: self.config, queue: self.queue) { [weak self] done in
                self?.lock.lock(); self?.sessions.remove(done); self?.lock.unlock()
            }
            self.lock.lock(); self.sessions.insert(s); self.lock.unlock()
            s.start()
        }
        l.start(queue: queue)
        listener = l
    }

    func stop() {
        listener?.cancel()
        lock.lock()
        let all = sessions
        sessions.removeAll()
        lock.unlock()
        all.forEach { $0.close() }
    }
}

final class VlessSession: Hashable {
    static func == (a: VlessSession, b: VlessSession) -> Bool { a === b }
    func hash(into h: inout Hasher) { h.combine(ObjectIdentifier(self)) }

    private let client: NWConnection
    private var remote: NWConnection?
    private let config: VlessConfig
    private let queue: DispatchQueue
    private let onDone: (VlessSession) -> Void
    private var buffer = Data()
    private var stage = 0          // 0 — приветствие, 1 — запрос CONNECT, 2 — передача данных
    private var gotResponseHeader = false
    private var closed = false

    init(client: NWConnection, config: VlessConfig, queue: DispatchQueue, onDone: @escaping (VlessSession) -> Void) {
        self.client = client
        self.config = config
        self.queue = queue
        self.onDone = onDone
    }

    func start() {
        client.stateUpdateHandler = { [weak self] st in
            if case .failed = st { self?.close() }
            if case .cancelled = st { self?.close() }
        }
        client.start(queue: queue)
        readHandshake()
    }

    func close() {
        guard !closed else { return }
        closed = true
        client.cancel()
        remote?.cancel()
        onDone(self)
    }

    // MARK: SOCKS5

    private func readHandshake() {
        client.receive(minimumIncompleteLength: 1, maximumLength: 4096) { [weak self] data, _, done, err in
            guard let self, !self.closed else { return }
            if let data { self.buffer.append(data) }
            if err != nil || (done && data == nil) { self.close(); return }
            if self.parse() == false { self.close(); return }
            if self.stage < 2 { self.readHandshake() }
        }
    }

    /// false — ошибка протокола.
    private func parse() -> Bool {
        let b = [UInt8](buffer)
        if stage == 0 {
            guard b.count >= 2 else { return true }
            guard b[0] == 5 else { return false }
            let n = Int(b[1])
            guard b.count >= 2 + n else { return true }
            buffer.removeFirst(2 + n)
            stage = 1
            client.send(content: Data([5, 0]), completion: .contentProcessed { _ in })
            return parse()
        }
        if stage == 1 {
            guard b.count >= 5 else { return true }
            guard b[0] == 5 else { return false }
            let cmd = b[1], atyp = b[3]
            var need = 0
            var addr = Data()
            switch atyp {
            case 1: need = 4 + 4 + 2; if b.count >= need { addr = Data([1]) + Data(b[4..<8]) }
            case 3: let l = Int(b[4]); need = 5 + l + 2; if b.count >= need { addr = Data([2, b[4]]) + Data(b[5..<(5 + l)]) }
            case 4: need = 4 + 16 + 2; if b.count >= need { addr = Data([3]) + Data(b[4..<20]) }
            default: return false
            }
            guard b.count >= need else { return true }
            let port = Data(b[(need - 2)..<need])
            buffer.removeFirst(need)
            guard cmd == 1 else {
                // UDP не поддерживается: DNS решает FakeDNS, приложения откатываются на TCP
                client.send(content: Data([5, 7, 0, 1, 0, 0, 0, 0, 0, 0]), completion: .contentProcessed { [weak self] _ in self?.close() })
                return true
            }
            stage = 2
            connectRemote(addr: addr, port: port)
        }
        return true
    }

    // MARK: VLESS поверх WebSocket

    private func connectRemote(addr: Data, port: Data) {
        guard let url = config.url else { close(); return }
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        let params: NWParameters
        if config.tls {
            let tls = NWProtocolTLS.Options()
            sec_protocol_options_set_tls_server_name(tls.securityProtocolOptions, config.sni)
            params = NWParameters(tls: tls, tcp: tcp)
        } else {
            params = NWParameters(tls: nil, tcp: tcp)
        }
        let ws = NWProtocolWebSocket.Options()
        ws.autoReplyPing = true
        ws.maximumMessageSize = 1 << 20
        ws.setAdditionalHeaders([("Host", config.wsHost), ("User-Agent", "Mozilla/5.0")])
        params.defaultProtocolStack.applicationProtocols.insert(ws, at: 0)
        let r = NWConnection(to: .url(url), using: params)
        remote = r
        // Заголовок VLESS: версия 0, UUID, без доп. полей, команда 1 (TCP), порт, адрес.
        var header = Data([0])
        header.append(withUnsafeBytes(of: config.uuid.uuid) { Data($0) })
        header.append(contentsOf: [0, 1])
        header.append(port)
        header.append(addr)
        let leftover = buffer
        buffer = Data()
        r.stateUpdateHandler = { [weak self] st in
            guard let self else { return }
            switch st {
            case .ready:
                self.sendRemote(header + leftover)
                self.client.send(content: Data([5, 0, 0, 1, 0, 0, 0, 0, 0, 0]), completion: .contentProcessed { _ in })
                self.pumpClient()
                self.pumpRemote()
            case .failed, .cancelled:
                self.close()
            case .waiting:
                self.close()
            default: break
            }
        }
        r.start(queue: queue)
    }

    private func sendRemote(_ data: Data, then: (() -> Void)? = nil) {
        let meta = NWProtocolWebSocket.Metadata(opcode: .binary)
        let ctx = NWConnection.ContentContext(identifier: "vless", metadata: [meta])
        remote?.send(content: data, contentContext: ctx, isComplete: true, completion: .contentProcessed { [weak self] err in
            if err != nil { self?.close() } else { then?() }
        })
    }

    private func pumpClient() {
        client.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, done, err in
            guard let self, !self.closed else { return }
            if let data, !data.isEmpty {
                self.sendRemote(data) { [weak self] in
                    if done { self?.close() } else { self?.pumpClient() }
                }
            } else if err != nil || done {
                self.close()
            } else {
                self.pumpClient()
            }
        }
    }

    private func pumpRemote() {
        remote?.receiveMessage { [weak self] data, _, _, err in
            guard let self, !self.closed else { return }
            if err != nil { self.close(); return }
            guard var d = data else { self.pumpRemote(); return }
            if !self.gotResponseHeader {
                // Ответ VLESS: версия + длина доп. полей + сами поля
                guard d.count >= 2 else { self.pumpRemote(); return }
                let addons = Int(d[d.startIndex + 1])
                guard d.count >= 2 + addons else { self.close(); return }
                d = d.dropFirst(2 + addons)
                self.gotResponseHeader = true
            }
            if d.isEmpty { self.pumpRemote(); return }
            self.client.send(content: Data(d), completion: .contentProcessed { [weak self] e in
                if e != nil { self?.close() } else { self?.pumpRemote() }
            })
        }
    }
}
