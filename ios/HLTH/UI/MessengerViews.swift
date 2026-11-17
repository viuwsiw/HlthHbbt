import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import QuickLook

func statusColor(_ s: String?) -> Color {
    switch s {
    case "ONLINE": return Color(hex: 0x22C55E)
    case "DND": return Color(hex: 0xEF4444)
    case "AWAY": return Color(hex: 0xF59E0B)
    default: return .gray
    }
}

func statusTitle(_ s: String?) -> String {
    switch s {
    case "ONLINE": return "В сети"
    case "DND": return "Не беспокоить"
    case "AWAY": return "Вне чата"
    default: return "Не в сети"
    }
}

struct StatusDot: View {
    let status: String?
    var size: CGFloat = 12
    var body: some View {
        Circle().fill(statusColor(status)).frame(width: size, height: size)
            .overlay(Circle().stroke(.white.opacity(0.9), lineWidth: 2))
    }
}

func timeLabel(_ ms: Int64) -> String {
    let d = Date(timeIntervalSince1970: TimeInterval(ms) / 1000)
    let f = DateFormatter()
    f.locale = Locale(identifier: "ru_RU")
    f.dateFormat = Calendar.current.isDateInToday(d) ? "HH:mm" : "d MMM"
    return f.string(from: d)
}

struct ChatsView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var m: Messenger
    @Environment(\.palette) private var p
    @State private var query = ""
    @State private var results: [RemoteUser] = []
    @State private var contacts: [RemoteUser] = []
    @State private var searching = false

    var body: some View {
        Screen(title: "Мессенджер") {
            HStack(spacing: 10) {
                Menu {
                    Button { m.setMyStatus("ONLINE") } label: { Label("В сети", systemImage: "circle.fill") }
                    Button { m.setMyStatus("DND") } label: { Label("Не беспокоить", systemImage: "moon.fill") }
                } label: {
                    HStack(spacing: 8) {
                        StatusDot(status: m.myStatus)
                        Text(statusTitle(m.myStatus)).font(.subheadline.weight(.semibold)).foregroundStyle(p.text)
                        Image(systemName: "chevron.down").font(.caption).foregroundStyle(p.text2)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 10).glass(20, interactive: true)
                }
                Spacer()
                Text(m.state == .online ? "🔒 Подключено" : (m.state == .connecting ? "Подключение…" : "Нет связи"))
                    .font(.caption).foregroundStyle(p.text2)
            }

            if !query.isEmpty {
                SectionTitle(searching ? "Ищем…" : "Найдено")
                ForEach(results) { u in
                    NavigationLink { UserProfileView(username: u.username) } label: { userRow(u.username, u.displayName, u.avatar, sub: "@\(u.username)") }.buttonStyle(.plain)
                }
                if !searching && results.isEmpty && query.count >= 2 { Text("Никого не нашли").foregroundStyle(p.text2).padding(.horizontal, 6) }
            } else {
                NavigationLink { ChatView(peer: app.username ?? "") } label: {
                    HStack(spacing: 14) {
                        Image(systemName: "bookmark.fill").font(.title3).foregroundStyle(.white)
                            .frame(width: 50, height: 50).background(Circle().fill(p.gradient))
                        VStack(alignment: .leading, spacing: 3) {
                            Text("Избранное").font(.headline).foregroundStyle(p.text)
                            Text(m.chats[app.username ?? ""]?.last?.text.nonEmpty ?? "Заметки и файлы для себя").font(.caption).foregroundStyle(p.text2).lineLimit(1)
                        }
                        Spacer()
                    }.glassCard(12)
                }.buttonStyle(.plain)

                if !m.conversations.isEmpty { SectionTitle("Диалоги") }
                ForEach(m.conversations) { c in
                    NavigationLink { ChatView(peer: c.peer) } label: {
                        let n = m.unread(c.peer)
                        HStack(spacing: 14) {
                            ZStack(alignment: .bottomTrailing) {
                                AvatarView(avatar: contacts.first { $0.username == c.peer }?.avatar, name: c.peer, size: 50)
                                StatusDot(status: m.presence[c.peer]?.status)
                            }
                            VStack(alignment: .leading, spacing: 3) {
                                Text(contacts.first { $0.username == c.peer }?.displayName.nonEmpty ?? "@\(c.peer)").font(.headline).foregroundStyle(p.text).lineLimit(1)
                                Text((c.last.outgoing ? "Вы: " : "") + (c.last.media != nil ? "📎 " : "") + (c.last.text.nonEmpty ?? c.last.media?.name ?? ""))
                                    .font(.caption).foregroundStyle(p.text2).lineLimit(1)
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 4) {
                                Text(timeLabel(c.last.createdAt)).font(.caption2).foregroundStyle(p.text2)
                                if n > 0 {
                                    Text("\(n)").font(.caption2.bold()).foregroundStyle(p.onAccent)
                                        .padding(.horizontal, 7).padding(.vertical, 2).background(Capsule().fill(p.accent))
                                }
                            }
                        }.glassCard(12)
                    }.buttonStyle(.plain)
                }

                let others = contacts.filter { u in !m.conversations.contains { $0.peer == u.username } }
                if !others.isEmpty {
                    SectionTitle("Контакты")
                    ForEach(others) { u in
                        NavigationLink { UserProfileView(username: u.username) } label: { userRow(u.username, u.displayName, u.avatar, sub: "@\(u.username)") }.buttonStyle(.plain)
                    }
                }
                if m.conversations.isEmpty && contacts.isEmpty {
                    Text("Найдите друга по логину, чтобы начать переписку. Сообщения шифруются на устройстве — сервер их не видит.")
                        .font(.footnote).foregroundStyle(p.text2).glassCard()
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink { PrivacyView() } label: { Image(systemName: "hand.raised.fill") }
            }
        }
        .searchable(text: $query, prompt: "Поиск по логину")
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .task(id: query) {
            let q = query.trimmingCharacters(in: .whitespaces)
            guard q.count >= 2 else { results = []; return }
            try? await Task.sleep(nanoseconds: 350_000_000)
            if Task.isCancelled { return }
            searching = true
            results = (try? await m.search(q)) ?? []
            searching = false
        }
        .onAppear { m.enterMessenger() }
        .onDisappear { m.leaveMessenger() }
        .task {
            contacts = (try? await app.api.contacts()) ?? []
            for c in contacts { if let pr = c.presence { m.presence[c.username] = pr } }
            await m.refreshPresence(m.conversations.map(\.peer))
            await m.sync()
        }
        .refreshable { await m.sync() }
    }

    private func userRow(_ u: String, _ name: String, _ avatar: String, sub: String) -> some View {
        HStack(spacing: 14) {
            ZStack(alignment: .bottomTrailing) {
                AvatarView(avatar: avatar, name: name.nonEmpty ?? u, size: 46)
                StatusDot(status: m.presence[u]?.status)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(name.nonEmpty ?? u).font(.headline).foregroundStyle(p.text)
                Text(sub).font(.caption).foregroundStyle(p.text2)
            }
            Spacer()
            Image(systemName: "chevron.right").foregroundStyle(p.text2)
        }.glassCard(12)
    }
}

extension String {
    var nonEmpty: String? { isEmpty ? nil : self }
}

struct ChatView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var m: Messenger
    @Environment(\.palette) private var p
    let peer: String
    @State private var text = ""
    @State private var sending = false
    @State private var photo: PhotosPickerItem?
    @State private var importing = false
    @State private var profile: RemoteProfile?
    @State private var preview: URL?

    private var isSelf: Bool { peer == app.username }

    var body: some View {
        let messages = m.chats[peer] ?? []
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 6) {
                    if isSelf {
                        Text("Сохраняйте сюда заметки, ссылки и файлы — они зашифрованы вашим ключом.")
                            .font(.caption).foregroundStyle(p.text2).multilineTextAlignment(.center).padding()
                    } else {
                        Text("🔒 Сообщения защищены сквозным шифрованием").font(.caption).foregroundStyle(p.text2).padding(.vertical, 8)
                    }
                    ForEach(messages) { msg in bubble(msg).id(msg.clientId) }
                }
                .padding(.horizontal, 12).padding(.bottom, 8)
            }
            .scrollDismissesKeyboard(.interactively)
            .onAppear { proxy.scrollTo(messages.last?.clientId, anchor: .bottom) }
            .onChange(of: messages.count) { _, _ in
                withAnimation { proxy.scrollTo(messages.last?.clientId, anchor: .bottom) }
                m.markRead(peer)
            }
        }
        .safeAreaInset(edge: .bottom) { inputBar }
        .background(LiquidBackground(animated: app.settings.animatedBackground))
        .toolbar(.hidden, for: .tabBar)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) { header }
            if !isSelf {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink { UserProfileView(username: peer) } label: {
                        AvatarView(avatar: profile?.avatar, name: profile?.displayName.nonEmpty ?? peer, size: 32)
                    }
                }
            }
        }
        .onAppear { m.openChat = peer; m.markRead(peer); m.enterMessenger() }
        .onDisappear { if m.openChat == peer { m.openChat = nil }; m.leaveMessenger() }
        .task { if !isSelf { profile = try? await m.profile(peer) } }
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    let type = item.supportedContentTypes.first ?? .jpeg
                    let ext = type.preferredFilenameExtension ?? "jpg"
                    await sendMedia(data, mime: type.preferredMIMEType ?? "image/jpeg", name: "IMG_\(Int(Date().timeIntervalSince1970)).\(ext)")
                }
                photo = nil
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.item]) { r in
            guard case .success(let url) = r else { return }
            let ok = url.startAccessingSecurityScopedResource()
            defer { if ok { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url) else { return }
            let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
            Task { await sendMedia(data, mime: mime, name: url.lastPathComponent) }
        }
        .quickLookPreview($preview)
    }

    private var header: some View {
        VStack(spacing: 1) {
            Text(isSelf ? "Избранное" : (profile?.displayName.nonEmpty ?? "@\(peer)")).font(.headline).foregroundStyle(p.text).lineLimit(1)
            if !isSelf {
                let pr = m.presence[peer]
                if let np = pr?.nowPlaying {
                    Text("🎵 \(np.title) — \(np.artist)").font(.caption2).foregroundStyle(p.accent).lineLimit(1)
                } else {
                    HStack(spacing: 4) {
                        Circle().fill(statusColor(pr?.status)).frame(width: 7, height: 7)
                        Text(statusTitle(pr?.status)).font(.caption2).foregroundStyle(p.text2)
                    }
                }
            }
        }
        .frame(maxWidth: 240)
    }

    private func bubble(_ msg: ChatMessage) -> some View {
        HStack {
            if msg.outgoing { Spacer(minLength: 50) }
            VStack(alignment: msg.outgoing ? .trailing : .leading, spacing: 4) {
                if let media = msg.media { MediaContent(media: media) { preview = $0 } }
                if !msg.text.isEmpty {
                    Text(msg.text).foregroundStyle(msg.outgoing ? p.onAccent : p.text).italic(!msg.readable)
                        .textSelection(.enabled)
                }
                HStack(spacing: 4) {
                    Text(timeLabel(msg.createdAt)).font(.system(size: 10))
                    if msg.outgoing { Image(systemName: msg.serverId == 0 ? "clock" : "checkmark").font(.system(size: 9, weight: .bold)) }
                }
                .foregroundStyle(msg.outgoing ? p.onAccent.opacity(0.75) : p.text2)
            }
            .padding(.horizontal, 14).padding(.vertical, 9)
            .background {
                if msg.outgoing { RoundedRectangle(cornerRadius: 20, style: .continuous).fill(p.gradient) }
            }
            .glass(20, tint: msg.outgoing ? p.accent.opacity(0.5) : nil)
            if !msg.outgoing { Spacer(minLength: 50) }
        }
    }

    private var inputBar: some View {
        VStack(alignment: .trailing, spacing: 4) {
            if text.count > 4000 {
                Text("\(text.count)/\(maxChars)").font(.caption2).foregroundStyle(text.count > maxChars ? p.danger : p.text2).padding(.trailing, 24)
            }
            HStack(alignment: .bottom, spacing: 8) {
                Button { importing = true } label: {
                    Image(systemName: "paperclip").font(.title3).foregroundStyle(p.text).frame(width: 44, height: 44).glass(22, interactive: true)
                }.buttonStyle(.plain)
                PhotosPicker(selection: $photo, matching: .any(of: [.images, .videos])) {
                    Image(systemName: "photo").font(.title3).foregroundStyle(p.text).frame(width: 44, height: 44).glass(22, interactive: true)
                }
                TextField("Сообщение", text: $text, axis: .vertical)
                    .lineLimit(1...6)
                    .foregroundStyle(p.text)
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .glass(22)
                Button { send() } label: {
                    Image(systemName: sending ? "hourglass" : "arrow.up").font(.headline.bold()).foregroundStyle(p.onAccent)
                        .frame(width: 44, height: 44).background(Circle().fill(p.gradient))
                }
                .buttonStyle(.plain)
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || text.count > maxChars)
            }
            .padding(.horizontal, 12).padding(.bottom, 8)
        }
    }

    private func send() {
        let t = text
        text = ""
        Task {
            sending = true
            do { try await m.send(peer, t) } catch { app.show(error.localizedDescription); if text.isEmpty { text = t } }
            sending = false
        }
    }

    private func sendMedia(_ data: Data, mime: String, name: String) async {
        sending = true
        let caption = text
        text = ""
        do { try await m.sendMedia(peer, data: data, mime: mime, name: name, caption: caption) } catch { app.show(error.localizedDescription) }
        sending = false
    }
}

struct MediaContent: View {
    @EnvironmentObject var m: Messenger
    @Environment(\.palette) private var p
    let media: MediaInfo
    var open: (URL) -> Void
    @State private var image: UIImage?
    @State private var failed: String?

    var body: some View {
        Group {
            if media.isImage {
                if let image {
                    Image(uiImage: image).resizable().scaledToFill().frame(maxWidth: 240, maxHeight: 300)
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .onTapGesture { openFile() }
                } else {
                    RoundedRectangle(cornerRadius: 14).fill(.white.opacity(0.08)).frame(width: 200, height: 150)
                        .overlay { if let failed { Text(failed).font(.caption).padding() } else { ProgressView() } }
                }
            } else {
                Button { openFile() } label: {
                    HStack(spacing: 10) {
                        Image(systemName: media.isVideo ? "play.rectangle.fill" : "doc.fill").font(.title2)
                        VStack(alignment: .leading) {
                            Text(media.name).font(.subheadline.weight(.semibold)).lineLimit(1)
                            Text(ByteCountFormatter.string(fromByteCount: Int64(media.size), countStyle: .file)).font(.caption2)
                        }
                    }
                }.buttonStyle(.plain)
            }
        }
        .task(id: media.id) {
            guard media.isImage, image == nil else { return }
            do { image = UIImage(data: try await m.mediaData(media)) } catch { failed = error.localizedDescription }
        }
    }

    private func openFile() {
        Task { if let url = try? await m.mediaFileURL(media) { open(url) } }
    }
}

struct UserProfileView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var m: Messenger
    @Environment(\.palette) private var p
    let username: String
    @State private var profile: RemoteProfile?
    @State private var error: String?
    @State private var safety: String?

    var body: some View {
        Screen(title: "", large: false) {
            if let pr = profile {
                VStack(spacing: 10) {
                    ZStack(alignment: .bottomTrailing) {
                        AvatarView(avatar: pr.avatar, name: pr.displayName.nonEmpty ?? pr.username, size: 110)
                        StatusDot(status: m.presence[username]?.status ?? pr.presence?.status, size: 22)
                    }
                    Text(pr.displayName.nonEmpty ?? pr.username).font(.title2.bold()).foregroundStyle(p.text)
                    Text("@\(pr.username) · \(statusTitle(m.presence[username]?.status ?? pr.presence?.status))").font(.subheadline).foregroundStyle(p.text2)
                    if !pr.bio.isEmpty { Text(pr.bio).font(.body).foregroundStyle(p.text).multilineTextAlignment(.center) }
                    if let np = m.presence[username]?.nowPlaying ?? pr.presence?.nowPlaying {
                        Label("\(np.title) — \(np.artist)", systemImage: "music.note").font(.footnote.weight(.semibold))
                            .foregroundStyle(p.accent).padding(.horizontal, 14).padding(.vertical, 8).glass(18)
                    }
                }
                .frame(maxWidth: .infinity).padding(.vertical, 8)

                if !pr.isSelf {
                    HStack(spacing: 10) {
                        NavigationLink { ChatView(peer: username) } label: {
                            Label("Написать", systemImage: "bubble.left.fill").fontWeight(.semibold).frame(maxWidth: .infinity).padding(.vertical, 14)
                                .foregroundStyle(p.onAccent).background(Capsule().fill(p.gradient)).glass(28, tint: p.accent.opacity(0.5), interactive: true)
                        }.buttonStyle(.plain)
                        GlassButton(title: pr.isContact ? "В контактах" : "В контакты", systemImage: pr.isContact ? "person.fill.checkmark" : "person.badge.plus") {
                            Task {
                                if pr.isContact { try? await app.api.removeContact(username) } else { try? await app.api.addContact(username) }
                                profile = try? await m.profile(username)
                            }
                        }
                    }
                }

                fieldsView(pr.fields)

                if !pr.isSelf {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Код безопасности").font(.headline).foregroundStyle(p.text)
                        Text(safety ?? (pr.publicKey == nil ? "Собеседник ещё не включил шифрование" : "…"))
                            .font(.title3.monospacedDigit()).foregroundStyle(p.accent)
                        Text("Сверьте код с собеседником: если совпадает, переписку никто не подменяет.").font(.caption).foregroundStyle(p.text2)
                    }.glassCard()
                }
            } else if let error {
                Text(error).foregroundStyle(p.danger).glassCard()
            } else {
                ProgressView().frame(maxWidth: .infinity).padding(40)
            }
        }
        .task {
            do { profile = try await m.profile(username); safety = await m.safetyCode(username) }
            catch { self.error = error.localizedDescription }
        }
    }

    @ViewBuilder
    private func fieldsView(_ f: JSON) -> some View {
        let streak = (f["streak"] as? JSON)?["value"] as? JSON
        let ach = (f["achievements"] as? JSON)?["value"] as? JSON
        let habits = (f["habits"] as? JSON)?["value"] as? [JSON]
        let water = (f["water"] as? JSON)?["value"] as? JSON
        if streak != nil || ach != nil {
            HStack(spacing: 12) {
                if let s = streak {
                    stat("🔥", "\(s["current"] as? Int ?? 0)", "серия · лучшая \(s["best"] as? Int ?? 0)")
                }
                if let a = ach {
                    stat("🏆", "\(a["unlocked"] as? Int ?? 0)/\(a["total"] as? Int ?? 0)", "достижений")
                }
            }
        }
        if let list = (ach?["list"] as? [JSON]), !list.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack { ForEach(list.indices, id: \.self) { i in Text(list[i]["emoji"] as? String ?? "").font(.title).padding(8).glass(18) } }
            }
        }
        if let habits, !habits.isEmpty {
            SectionTitle("Привычки")
            VStack(spacing: 10) {
                ForEach(habits.indices, id: \.self) { i in
                    let h = habits[i]
                    HStack {
                        Text("\(h["emoji"] as? String ?? "") \(h["title"] as? String ?? "")").foregroundStyle(p.text).lineLimit(1)
                        Spacer()
                        HStack(spacing: 3) {
                            ForEach(Array((h["week"] as? [Bool] ?? []).enumerated()), id: \.offset) { pair in
                                Circle().fill(pair.element ? p.accent : p.text2.opacity(0.25)).frame(width: 9, height: 9)
                            }
                        }
                    }
                }
            }.glassCard()
        }
        if let w = water {
            stat("💧", "\(w["todayMl"] as? Int ?? 0) мл", "сегодня · цель \(w["goalMl"] as? Int ?? 0) · в среднем \(w["avg7Ml"] as? Int ?? 0)")
        }
    }

    private func stat(_ e: String, _ v: String, _ t: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(e).font(.title2)
            Text(v).font(.title3.bold()).foregroundStyle(p.text)
            Text(t).font(.caption).foregroundStyle(p.text2)
        }.glassCard()
    }
}

struct PrivacyView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p

    var body: some View {
        Screen(title: "Конфиденциальность", large: false) {
            Toggle("Находиться в поиске", isOn: bind(\.discoverable)).foregroundStyle(p.text).glassCard()
            Text("Что видят другие в вашем профиле").font(.footnote).foregroundStyle(p.text2).padding(.horizontal, 6)
            VStack(spacing: 14) {
                row("🔥 Серия", \.streak)
                row("🏆 Достижения", \.achievements)
                row("✅ Привычки", \.habits)
                row("💧 Вода", \.water)
                row("🎵 Что я слушаю", \.nowPlaying)
            }.glassCard()
            Text("«Никто» — данные вообще не отправляются на сервер.").font(.caption).foregroundStyle(p.text2).padding(.horizontal, 6)
        }
    }

    private func bind<T>(_ kp: WritableKeyPath<Privacy, T>) -> Binding<T> {
        Binding(get: { app.data.privacy[keyPath: kp] }, set: { var x = app.data.privacy; x[keyPath: kp] = $0; app.savePrivacy(x) })
    }

    private func row(_ title: String, _ kp: WritableKeyPath<Privacy, Visibility>) -> some View {
        HStack {
            Text(title).foregroundStyle(p.text)
            Spacer()
            Picker(title, selection: bind(kp)) {
                ForEach(Visibility.allCases, id: \.self) { Text($0.title).tag($0) }
            }.pickerStyle(.menu)
        }
    }
}
