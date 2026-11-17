import Foundation
import CryptoKit
import Security

/// Минимальный protobuf — только то, что нужно для ключей Google Tink.
enum Proto {
    static func varint(_ v: UInt64) -> Data {
        var v = v
        var out = Data()
        repeat {
            var b = UInt8(v & 0x7F)
            v >>= 7
            if v != 0 { b |= 0x80 }
            out.append(b)
        } while v != 0
        return out
    }
    static func fieldVarint(_ n: Int, _ v: UInt64) -> Data { varint(UInt64(n << 3)) + varint(v) }
    static func fieldBytes(_ n: Int, _ d: Data) -> Data { varint(UInt64(n << 3 | 2)) + varint(UInt64(d.count)) + d }

    enum Value { case int(UInt64), bytes(Data) }

    /// Разбор сообщения в список (номер поля, значение).
    static func parse(_ d: Data) -> [(Int, Value)]? {
        let b = [UInt8](d)
        var i = 0
        var out: [(Int, Value)] = []
        func readVarint() -> UInt64? {
            var r: UInt64 = 0, shift: UInt64 = 0
            while i < b.count {
                let x = b[i]; i += 1
                r |= UInt64(x & 0x7F) << shift
                if x & 0x80 == 0 { return r }
                shift += 7
                if shift > 63 { return nil }
            }
            return nil
        }
        while i < b.count {
            guard let key = readVarint() else { return nil }
            let field = Int(key >> 3), wire = Int(key & 7)
            switch wire {
            case 0:
                guard let v = readVarint() else { return nil }
                out.append((field, .int(v)))
            case 2:
                guard let len = readVarint(), i + Int(len) <= b.count else { return nil }
                out.append((field, .bytes(Data(b[i..<(i + Int(len))]))))
                i += Int(len)
            case 1: i += 8
            case 5: i += 4
            default: return nil
            }
        }
        return out
    }
    static func bytes(_ fields: [(Int, Value)], _ n: Int) -> Data? {
        for (f, v) in fields where f == n { if case .bytes(let d) = v { return d } }
        return nil
    }
    static func int(_ fields: [(Int, Value)], _ n: Int) -> UInt64? {
        for (f, v) in fields where f == n { if case .int(let x) = v { return x } }
        return nil
    }
}

enum E2EError: Error { case badKey, badCiphertext }

/// Ключ в формате Tink (HPKE X25519 + HKDF-SHA256 + AES-256-GCM, префикс TINK) — совместим с Android-версией.
struct TinkHpkeKey {
    static let privateType = "type.googleapis.com/google.crypto.tink.HpkePrivateKey"
    static let publicType = "type.googleapis.com/google.crypto.tink.HpkePublicKey"

    let keyId: UInt32
    let publicKey: Data
    let privateKey: Data?

    static func generate() -> TinkHpkeKey {
        let k = Curve25519.KeyAgreement.PrivateKey()
        var id: UInt32 = 0
        while id == 0 { id = UInt32.random(in: 1...UInt32.max) }
        return TinkHpkeKey(keyId: id, publicKey: k.publicKey.rawRepresentation, privateKey: k.rawRepresentation)
    }

    private static var params: Data {
        // kem = DHKEM_X25519_HKDF_SHA256 (1), kdf = HKDF_SHA256 (1), aead = AES_256_GCM (2)
        Proto.fieldVarint(1, 1) + Proto.fieldVarint(2, 1) + Proto.fieldVarint(3, 2)
    }

    private var publicProto: Data { Proto.fieldBytes(2, Self.params) + Proto.fieldBytes(3, publicKey) }

    private func keyset(typeUrl: String, value: Data, material: UInt64) -> Data {
        let keyData = Proto.fieldBytes(1, Data(typeUrl.utf8)) + Proto.fieldBytes(2, value) + Proto.fieldVarint(3, material)
        let key = Proto.fieldBytes(1, keyData) + Proto.fieldVarint(2, 1) + Proto.fieldVarint(3, UInt64(keyId)) + Proto.fieldVarint(4, 1)
        return Proto.fieldVarint(1, UInt64(keyId)) + Proto.fieldBytes(2, key)
    }

    /// Открытый keyset (без секрета) — то, что публикуется на сервере.
    var publicKeyset: Data { keyset(typeUrl: Self.publicType, value: publicProto, material: 3) }

    /// Полный keyset с закрытым ключом — для облачной копии.
    var privateKeyset: Data? {
        guard let privateKey else { return nil }
        let value = Proto.fieldBytes(2, publicProto) + Proto.fieldBytes(3, privateKey)
        return keyset(typeUrl: Self.privateType, value: value, material: 2)
    }

    /// Разбор keyset (открытого или закрытого) — берётся основной ключ.
    static func parse(_ data: Data) -> TinkHpkeKey? {
        guard let ks = Proto.parse(data) else { return nil }
        let primary = Proto.int(ks, 1).map { UInt32(truncatingIfNeeded: $0) }
        for (f, v) in ks where f == 2 {
            guard case .bytes(let keyBytes) = v, let key = Proto.parse(keyBytes),
                  let kd = Proto.bytes(key, 1).flatMap(Proto.parse),
                  let typeUrl = Proto.bytes(kd, 1).flatMap({ String(data: $0, encoding: .utf8) }),
                  let value = Proto.bytes(kd, 2).flatMap(Proto.parse) else { continue }
            let id = UInt32(truncatingIfNeeded: Proto.int(key, 3) ?? 0)
            if let primary, primary != id, ks.filter({ $0.0 == 2 }).count > 1 { continue }
            if typeUrl == publicType, let pub = Proto.bytes(value, 3) {
                return TinkHpkeKey(keyId: id, publicKey: pub, privateKey: nil)
            }
            if typeUrl == privateType, let pubMsg = Proto.bytes(value, 2).flatMap(Proto.parse),
               let pub = Proto.bytes(pubMsg, 3), let priv = Proto.bytes(value, 3) {
                return TinkHpkeKey(keyId: id, publicKey: pub, privateKey: priv)
            }
        }
        return nil
    }

    private static let suite = HPKE.Ciphersuite(kem: .Curve25519_HKDF_SHA256, kdf: .HKDF_SHA256, aead: .AES_GCM_256)

    func encrypt(_ plaintext: Data, info: Data) throws -> Data {
        let pk = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: publicKey)
        var sender = try HPKE.Sender(recipientKey: pk, ciphersuite: Self.suite, info: info)
        let ct = try sender.seal(plaintext)
        var prefix = Data([0x01])
        withUnsafeBytes(of: keyId.bigEndian) { prefix.append(contentsOf: $0) }
        return prefix + sender.encapsulatedKey + ct
    }

    func decrypt(_ ciphertext: Data, info: Data) throws -> Data {
        guard let privateKey else { throw E2EError.badKey }
        var body = ciphertext
        if body.count > 5, body.first == 0x01 { body = body.dropFirst(5) }
        guard body.count > 32 else { throw E2EError.badCiphertext }
        let enc = body.prefix(32)
        let ct = body.dropFirst(32)
        let sk = try Curve25519.KeyAgreement.PrivateKey(rawRepresentation: privateKey)
        var r = try HPKE.Recipient(privateKey: sk, ciphersuite: Self.suite, info: info, encapsulatedKey: Data(enc))
        return try r.open(Data(ct))
    }
}

/// Хранение секретов в Keychain.
enum Keychain {
    static func set(_ data: Data?, _ key: String) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "hlth", kSecAttrAccount as String: key]
        SecItemDelete(q as CFDictionary)
        guard let data else { return }
        var add = q
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }
    static func get(_ key: String) -> Data? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "hlth",
                                kSecAttrAccount as String: key, kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess else { return nil }
        return out as? Data
    }
}

/// Сквозное шифрование сообщений, совместимое с Android (Tink).
enum E2E {
    private static var cache: [String: TinkHpkeKey] = [:]

    static func key(_ username: String) -> TinkHpkeKey {
        if let k = cache[username] { return k }
        if let d = Keychain.get("e2e_\(username)"), let k = TinkHpkeKey.parse(d), k.privateKey != nil {
            cache[username] = k
            return k
        }
        let k = TinkHpkeKey.generate()
        Keychain.set(k.privateKeyset, "e2e_\(username)")
        cache[username] = k
        return k
    }

    static func publicKey(_ username: String) -> String { key(username).publicKeyset.base64EncodedString() }

    private static func info(_ from: String, _ to: String) -> Data { Data("hlth-e2e-v1:\(from):\(to)".utf8) }

    static func encrypt(recipientPublicKey: String, _ text: String, from: String, to: String) throws -> String {
        guard let d = Data(base64Encoded: recipientPublicKey), let k = TinkHpkeKey.parse(d) else { throw E2EError.badKey }
        return try k.encrypt(Data(text.utf8), info: info(from, to)).base64EncodedString()
    }

    static func decrypt(me: String, _ ciphertext: String, from: String, to: String) -> String? {
        guard let d = Data(base64Encoded: ciphertext),
              let plain = try? key(me).decrypt(d, info: info(from, to)) else { return nil }
        return String(data: plain, encoding: .utf8)
    }

    static func exportPrivateKey(_ username: String) -> Data? { key(username).privateKeyset }

    static func importPrivateKey(_ username: String, _ keyset: Data) {
        guard let k = TinkHpkeKey.parse(keyset), k.privateKey != nil else { return }
        Keychain.set(keyset, "e2e_\(username)")
        cache[username] = k
    }

    // Файлы: AES-256-GCM, отдельный ключ и nonce на файл (шифротекст = ct || tag, как в Java).
    static func encryptFile(_ plain: Data) throws -> (Data, String, String) {
        let key = SymmetricKey(size: .bits256)
        let nonce = AES.GCM.Nonce()
        let box = try AES.GCM.seal(plain, using: key, nonce: nonce)
        let keyData = key.withUnsafeBytes { Data($0) }
        return (box.ciphertext + box.tag, keyData.base64EncodedString(), Data(nonce).base64EncodedString())
    }

    static func decryptFile(_ cipher: Data, key: String, nonce: String) throws -> Data {
        guard let k = Data(base64Encoded: key), let n = Data(base64Encoded: nonce), cipher.count >= 16 else { throw E2EError.badCiphertext }
        let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: n), ciphertext: cipher.dropLast(16), tag: cipher.suffix(16))
        return try AES.GCM.open(box, using: SymmetricKey(data: k))
    }

    static func safetyCode(_ a: String, _ b: String) -> String {
        let sorted = [a, b].sorted()
        let digest = SHA256.hash(data: Data((sorted[0] + "|" + sorted[1]).utf8))
        let digits = digest.prefix(12).map { String(format: "%02d", Int($0) % 100) }.joined()
        return stride(from: 0, to: digits.count, by: 4).map { i -> String in
            let s = digits.index(digits.startIndex, offsetBy: i)
            let e = digits.index(s, offsetBy: min(4, digits.count - i))
            return String(digits[s..<e])
        }.joined(separator: " ")
    }
}
