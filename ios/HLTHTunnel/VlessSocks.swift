import Foundation
import Network
import Security

/// Локальный SOCKS5-сервер (127.0.0.1) для hev-socks5-tunnel: каждое TCP-соединение уходит на сервер
/// по VLESS поверх WebSocket + TLS. DNS отвечает сам hev (FakeDNS), поэтому сюда приходят доменные имена.
final class VlessSocksServer {
    /// IP сервера, определённый до включения туннеля (DNS внутри туннеля отвечает поддельными адресами).
    static var serverIP: String?
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

    // MARK: VLESS поверх WebSocket (свой клиент: TLS по IP, SNI и Host из ключа, HTTP/1.1)

    private var wsBuffer = Data()
    private var wsReady = false
    private var respSkip = -1          // сколько байт заголовка ответа VLESS ещё пропустить (-1 — неизвестно)

    private func connectRemote(addr: Data, port: Data) {
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        tcp.connectionTimeout = 10
        let params: NWParameters
        if config.tls {
            let tls = NWProtocolTLS.Options()
            sec_protocol_options_set_tls_server_name(tls.securityProtocolOptions, config.sni)
            sec_protocol_options_add_tls_application_protocol(tls.securityProtocolOptions, "http/1.1")
            params = NWParameters(tls: tls, tcp: tcp)
        } else {
            params = NWParameters(tls: nil, tcp: tcp)
        }
        let target = VlessSocksServer.serverIP ?? config.host
        let r = NWConnection(host: NWEndpoint.Host(target), port: NWEndpoint.Port(rawValue: UInt16(config.port)) ?? 443, using: params)
        remote = r
        var header = Data([0])
        header.append(withUnsafeBytes(of: config.uuid.uuid) { Data($0) })
        header.append(contentsOf: [0, 1])
        header.append(port)
        header.append(addr)
        let firstPayload = header + buffer
        buffer = Data()
        r.stateUpdateHandler = { [weak self] st in
            guard let self else { return }
            switch st {
            case .ready: self.wsHandshake(firstPayload)
            case .failed, .cancelled, .waiting: self.close()
            default: break
            }
        }
        r.start(queue: queue)
    }

    private func wsHandshake(_ firstPayload: Data) {
        let key = Data((0..<16).map { _ in UInt8.random(in: 0...255) }).base64EncodedString()
        let path = config.path.hasPrefix("/") ? config.path : "/" + config.path
        let req = "GET \(path) HTTP/1.1\r\nHost: \(config.wsHost)\r\nUser-Agent: Mozilla/5.0\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: \(key)\r\nSec-WebSocket-Version: 13\r\n\r\n"
        remote?.send(content: Data(req.utf8), completion: .contentProcessed { [weak self] e in
            if e != nil { self?.close() }
        })
        readHandshakeResponse(firstPayload)
    }

    private func readHandshakeResponse(_ firstPayload: Data) {
        remote?.receive(minimumIncompleteLength: 1, maximumLength: 16384) { [weak self] data, _, done, err in
            guard let self, !self.closed else { return }
            if let data { self.wsBuffer.append(data) }
            if err != nil || (done && data == nil) { self.close(); return }
            guard let end = self.wsBuffer.range(of: Data("\r\n\r\n".utf8)) else {
                if self.wsBuffer.count > 16384 { self.close() } else { self.readHandshakeResponse(firstPayload) }
                return
            }
            let head = String(decoding: self.wsBuffer[..<end.lowerBound], as: UTF8.self)
            self.wsBuffer = Data(self.wsBuffer[end.upperBound...])
            guard head.hasPrefix("HTTP/1.1 101") || head.hasPrefix("HTTP/1.0 101") else { self.close(); return }
            self.wsReady = true
            self.sendFrame(firstPayload, opcode: 2)
            self.client.send(content: Data([5, 0, 0, 1, 0, 0, 0, 0, 0, 0]), completion: .contentProcessed { _ in })
            self.drainFrames()
            self.pumpClient()
            self.pumpRemote()
        }
    }

    /// Кадр клиента → сервер (с маской, как требует RFC 6455).
    private func sendFrame(_ payload: Data, opcode: UInt8, then: (() -> Void)? = nil) {
        var f = Data([0x80 | opcode])
        let n = payload.count
        if n < 126 { f.append(UInt8(0x80 | n)) }
        else if n < 65536 { f.append(0x80 | 126); f.append(UInt8(n >> 8)); f.append(UInt8(n & 0xFF)) }
        else { f.append(0x80 | 127); for i in (0..<8).reversed() { f.append(UInt8((n >> (i * 8)) & 0xFF)) } }
        let mask = (0..<4).map { _ in UInt8.random(in: 0...255) }
        f.append(contentsOf: mask)
        var masked = [UInt8](payload)
        for i in 0..<masked.count { masked[i] ^= mask[i & 3] }
        f.append(contentsOf: masked)
        remote?.send(content: f, completion: .contentProcessed { [weak self] err in
            if err != nil { self?.close() } else { then?() }
        })
    }

    private func pumpClient() {
        client.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, done, err in
            guard let self, !self.closed else { return }
            if let data, !data.isEmpty {
                self.sendFrame(data, opcode: 2) { [weak self] in
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
        remote?.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, done, err in
            guard let self, !self.closed else { return }
            if let data { self.wsBuffer.append(data) }
            if err != nil || (done && data == nil) { self.close(); return }
            self.drainFrames()
            if !self.closed { self.pumpRemote() }
        }
    }

    /// Разбор кадров сервера: данные — в поток VLESS, ping → pong, close → закрыть.
    private func drainFrames() {
        while true {
            let b = [UInt8](wsBuffer.prefix(14))
            guard b.count >= 2 else { return }
            let opcode = b[0] & 0x0F
            let masked = (b[1] & 0x80) != 0
            var len = Int(b[1] & 0x7F)
            var off = 2
            if len == 126 { guard b.count >= 4 else { return }; len = Int(b[2]) << 8 | Int(b[3]); off = 4 }
            else if len == 127 {
                guard b.count >= 10 else { return }
                len = 0; for i in 2..<10 { len = len << 8 | Int(b[i]) }; off = 10
            }
            if masked { off += 4 }
            guard wsBuffer.count >= off + len else { return }
            var payload = [UInt8](wsBuffer.dropFirst(off).prefix(len))
            if masked {
                let mk = [UInt8](wsBuffer.dropFirst(off - 4).prefix(4))
                for i in 0..<payload.count { payload[i] ^= mk[i & 3] }
            }
            wsBuffer = Data(wsBuffer.dropFirst(off + len))
            switch opcode {
            case 0x8: close(); return
            case 0x9: sendFrame(Data(payload), opcode: 0xA)
            case 0xA: break
            default: deliver(payload)
            }
        }
    }

    private func deliver(_ bytes: [UInt8]) {
        var d = bytes[...]
        if respSkip == -1 {
            guard d.count >= 2 else { return }
            respSkip = 2 + Int(d[d.startIndex + 1])
        }
        if respSkip > 0 {
            let k = min(respSkip, d.count)
            d = d.dropFirst(k)
            respSkip -= k
        }
        guard !d.isEmpty else { return }
        client.send(content: Data(d), completion: .contentProcessed { [weak self] e in if e != nil { self?.close() } })
    }
}
