import SwiftUI
import UniformTypeIdentifiers

/// Подпись обновлений своим сертификатом (.p12 + .mobileprovision) — подписывает сервер, iOS ставит «по воздуху».
struct SigningSection: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @State private var info: JSON?
    @State private var p12: Data?
    @State private var p12Name = ""
    @State private var prov: Data?
    @State private var provName = ""
    @State private var password = ""
    @State private var picking: Kind?
    @State private var busy = false
    @State private var status: String?

    enum Kind: Identifiable { case p12, prov; var id: Int { self == .p12 ? 0 : 1 } }

    private var configured: Bool { info?["configured"] as? Bool ?? false }
    private var profile: JSON? { info?["profile"] as? JSON }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "signature").foregroundStyle(p.accent)
                Text("Подпись обновлений").font(.headline).foregroundStyle(p.text)
                Spacer()
                if configured { Text("Настроено").font(.caption.weight(.semibold)).foregroundStyle(.green) }
            }
            Text("Загрузите свой сертификат .p12 и профиль .mobileprovision — сервер подпишет обновление из GitHub, и iPhone установит его сам, без компьютера.")
                .font(.caption).foregroundStyle(p.text2)

            if configured, let pr = profile {
                VStack(alignment: .leading, spacing: 3) {
                    Text(pr["name"] as? String ?? "Профиль").font(.subheadline.weight(.semibold)).foregroundStyle(p.text)
                    Text("\(pr["team"] as? String ?? "") · \(pr["appId"] as? String ?? "")").font(.caption2).foregroundStyle(p.text2).lineLimit(1)
                    if let exp = pr["expires"] as? String { Text("Действует до \(String(exp.prefix(10)))").font(.caption2).foregroundStyle(p.text2) }
                    if (pr["wildcard"] as? Bool) == false {
                        Text("Профиль на одно приложение: Live Activity в подписанной версии будет отключена.").font(.caption2).foregroundStyle(.orange)
                    }
                }
                GlassButton(title: busy ? "Подписываем…" : "Подписать и установить", systemImage: "arrow.down.app", prominent: true) { install() }
                    .disabled(busy)
                GlassButton(title: "Удалить сертификаты", systemImage: "trash", role: .destructive) {
                    Task { try? await app.api.deleteSigning(); await load() }
                }
            } else {
                fileRow(".p12", p12Name, .p12)
                fileRow(".mobileprovision", provName, .prov)
                GlassField(title: "Пароль от .p12", text: $password, secure: true)
                GlassButton(title: busy ? "Загружаем…" : "Сохранить на сервере", systemImage: "lock.shield", prominent: true) { upload() }
                    .disabled(busy || p12 == nil || prov == nil)
                Text("Файлы хранятся только на вашем сервере HLTH и используются лишь для подписи обновлений.")
                    .font(.caption2).foregroundStyle(p.text2)
            }
            if let status { Text(status).font(.caption).foregroundStyle(p.text2) }
        }
        .glassCard()
        .task { await load() }
        .fileImporter(isPresented: Binding(get: { picking != nil }, set: { if !$0 { picking = nil } }),
                      allowedContentTypes: [.data, .item]) { result in
            let kind = picking
            picking = nil
            guard case .success(let url) = result else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            guard let d = try? Data(contentsOf: url) else { status = "Не удалось прочитать файл"; return }
            let ext = url.pathExtension.lowercased()
            switch kind {
            case .p12:
                guard ext == "p12" || ext == "pfx" else { status = "Нужен файл .p12"; return }
                p12 = d; p12Name = url.lastPathComponent
            case .prov:
                guard ext == "mobileprovision" else { status = "Нужен файл .mobileprovision"; return }
                prov = d; provName = url.lastPathComponent
            case nil: break
            }
            status = nil
        }
    }

    private func fileRow(_ title: String, _ name: String, _ kind: Kind) -> some View {
        Button { picking = kind } label: {
            HStack {
                Image(systemName: name.isEmpty ? "doc.badge.plus" : "checkmark.seal.fill").foregroundStyle(name.isEmpty ? p.text2 : .green)
                Text(name.isEmpty ? "Выбрать \(title)" : name).foregroundStyle(p.text).lineLimit(1)
                Spacer()
            }
            .padding(14).glass(18, interactive: true)
        }.buttonStyle(.plain)
    }

    private func load() async { info = try? await app.api.signingInfo() }

    private func upload() {
        guard let p12, let prov else { return }
        busy = true
        Task {
            do {
                info = try await app.api.uploadSigning(p12: p12, password: password, provision: prov)
                password = ""
                status = "Сертификаты сохранены"
            } catch { status = error.localizedDescription }
            busy = false
        }
    }

    private func install() {
        busy = true
        status = "Скачиваем обновление и подписываем на сервере…"
        Task {
            do {
                guard let u = await Updates.latest() else { throw ApiError(code: 0, error: "update", message: "Не удалось получить версию с GitHub") }
                try await Updates.signAndInstall(u)
                status = "Подтвердите установку \(u.version) в появившемся окне. Если вместо приложения появился значок без загрузки — профиль не подходит этому iPhone."
            } catch { status = error.localizedDescription }
            busy = false
        }
    }
}
