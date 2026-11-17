import SwiftUI
import AVFoundation
import AVKit

func formatMs(_ ms: Int64) -> String {
    let s = max(0, ms / 1000)
    return String(format: "%d:%02d", s / 60, s % 60)
}

// MARK: - Фото профиля с сервера

final class RemoteImageCache {
    static let shared = NSCache<NSString, UIImage>()
}

/// "remote:<логин>:<версия>" — фото другого пользователя, загружается с авторизацией.
struct RemoteAvatarImage: View {
    let spec: String
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image { Image(uiImage: image).resizable().scaledToFill() } else { Color.gray.opacity(0.2) }
        }
        .task(id: spec) {
            if let c = RemoteImageCache.shared.object(forKey: spec as NSString) { image = c; return }
            let parts = spec.split(separator: ":").map(String.init)
            guard parts.count >= 3 else { return }
            let req = AppModel.shared.api.avatarRequest(parts[1], version: parts[2])
            if let (data, resp) = try? await URLSession.shared.data(for: req),
               (resp as? HTTPURLResponse)?.statusCode == 200, let img = UIImage(data: data) {
                RemoteImageCache.shared.setObject(img, forKey: spec as NSString)
                image = img
            }
        }
    }
}

// MARK: - Запись голосовых

/// Запись голосового: AAC 48 кГц, 256 кбит/с (максимальное качество), до 5 минут.
@MainActor
final class VoiceRecorder: NSObject, ObservableObject {
    @Published var recording = false
    @Published var elapsed: TimeInterval = 0
    private var recorder: AVAudioRecorder?
    private var timer: Timer?
    private var url: URL?

    func start() async -> Bool {
        let granted: Bool = await withCheckedContinuation { cont in
            AVAudioApplication.requestRecordPermission { cont.resume(returning: $0) }
        }
        guard granted else { AppModel.shared.show("Разрешите доступ к микрофону в Настройках"); return false }
        do {
            let s = AVAudioSession.sharedInstance()
            try s.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker, .allowBluetooth])
            try s.setActive(true)
            let u = FileManager.default.temporaryDirectory.appendingPathComponent("voice_\(Int(Date().timeIntervalSince1970)).m4a")
            let settings: [String: Any] = [
                AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 48_000, AVNumberOfChannelsKey: 1,
                AVEncoderBitRateKey: 256_000, AVEncoderAudioQualityKey: AVAudioQuality.max.rawValue,
            ]
            let r = try AVAudioRecorder(url: u, settings: settings)
            r.record(forDuration: TimeInterval(maxVoiceMs / 1000))
            recorder = r
            url = u
            recording = true
            elapsed = 0
            timer = Timer.scheduledTimer(withTimeInterval: 0.2, repeats: true) { [weak self] _ in
                Task { @MainActor in
                    guard let self, let r = self.recorder else { return }
                    self.elapsed = r.currentTime
                    if !r.isRecording && self.recording { self.recording = false }
                }
            }
            return true
        } catch {
            AppModel.shared.show("Не удалось начать запись")
            return false
        }
    }

    /// Остановить; nil — слишком коротко.
    func stop() -> (URL, Int64)? {
        let dur = max(recorder?.currentTime ?? 0, elapsed)
        recorder?.stop()
        recorder = nil
        timer?.invalidate()
        recording = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        let u = url
        url = nil
        guard let u, dur >= 0.7 else { if let u { try? FileManager.default.removeItem(at: u) }; return nil }
        return (u, min(Int64(dur * 1000), maxVoiceMs))
    }

    func cancel() {
        recorder?.stop()
        recorder?.deleteRecording()
        timer?.invalidate()
        recording = false
    }
}

// MARK: - Голосовое сообщение

@MainActor
final class VoicePlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published var playing = false
    @Published var progress: Double = 0
    @Published var position: TimeInterval = 0
    private var player: AVAudioPlayer?
    private var timer: Timer?

    func toggle(_ data: Data) {
        if let p = player {
            if p.isPlaying { p.pause(); playing = false } else { p.play(); playing = true }
            return
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        guard let p = try? AVAudioPlayer(data: data) else { return }
        p.delegate = self
        p.play()
        player = p
        playing = true
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, let p = self.player else { return }
                self.position = p.currentTime
                self.progress = p.duration > 0 ? p.currentTime / p.duration : 0
            }
        }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in self.playing = false; self.progress = 0; self.position = 0 }
    }

    func stop() { player?.stop(); timer?.invalidate() }
}

struct VoiceBubble: View {
    @EnvironmentObject var m: Messenger
    @Environment(\.palette) private var p
    let msg: ChatMessage
    let media: MediaInfo
    @StateObject private var player = VoicePlayer()
    @State private var loading = false

    var body: some View {
        let color = msg.outgoing ? p.onAccent : p.text
        HStack(spacing: 10) {
            Button {
                Task {
                    loading = true
                    if let d = try? await m.mediaData(media) {
                        player.toggle(d)
                        m.markListened(msg)
                    }
                    loading = false
                }
            } label: {
                ZStack {
                    Circle().fill(color.opacity(0.18))
                    if loading { ProgressView().tint(color) }
                    else { Image(systemName: player.playing ? "pause.fill" : "play.fill").foregroundStyle(color) }
                }
                .frame(width: 40, height: 40)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 4) {
                ProgressView(value: player.progress).tint(color)
                HStack(spacing: 4) {
                    Text(player.playing || player.position > 0 ? formatMs(Int64(player.position * 1000)) : formatMs(media.duration))
                    Spacer()
                    if msg.listened {
                        Image(systemName: "headphones")
                        if msg.outgoing { Text("прослушано") }
                    } else if !msg.outgoing {
                        Circle().fill(p.accent).frame(width: 7, height: 7)
                    }
                }
                .font(.caption2)
                .foregroundStyle(color.opacity(0.8))
            }
        }
        .frame(width: 220)
        .onDisappear { player.stop() }
    }
}

// MARK: - Встроенный просмотр фото и видео

struct MediaViewerItem: Identifiable { let id = UUID(); let url: URL; let isVideo: Bool }

struct MediaViewer: View {
    let item: MediaViewerItem
    @Environment(\.dismiss) private var dismiss
    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Color.black.ignoresSafeArea()
            if item.isVideo {
                VideoPlayer(player: AVPlayer(url: item.url)).ignoresSafeArea()
            } else if let img = UIImage(contentsOfFile: item.url.path) {
                Image(uiImage: img).resizable().scaledToFit()
                    .scaleEffect(scale)
                    .offset(offset)
                    .gesture(MagnificationGesture()
                        .onChanged { scale = max(1, min(6, lastScale * $0)) }
                        .onEnded { _ in lastScale = scale; if scale == 1 { offset = .zero } })
                    .simultaneousGesture(DragGesture().onChanged { if scale > 1 { offset = $0.translation } })
                    .onTapGesture(count: 2) { withAnimation { scale = scale > 1 ? 1 : 2.5; lastScale = scale; offset = .zero } }
            }
            Button { dismiss() } label: {
                Image(systemName: "xmark").font(.headline.bold()).foregroundStyle(.white)
                    .frame(width: 44, height: 44).glass(22, interactive: true)
            }
            .padding()
        }
    }
}

// MARK: - Предпросмотр чата (долгое нажатие) — без отметки «прочитано»

struct ChatPreview: View {
    @EnvironmentObject var m: Messenger
    @Environment(\.palette) private var p
    let peer: String

    var body: some View {
        let list = Array((m.chats[peer] ?? []).suffix(12))
        VStack(alignment: .leading, spacing: 6) {
            Text(m.chatTitle(peer)).font(.headline).foregroundStyle(p.text)
            Text("Предпросмотр — собеседник не узнает, что вы прочитали").font(.caption2).foregroundStyle(p.text2)
            ForEach(list) { msg in
                HStack {
                    if msg.outgoing { Spacer() }
                    Text((!msg.outgoing && isGroupConv(peer) ? "@\(msg.sender): " : "") + previewText(msg))
                        .font(.footnote).lineLimit(3)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .foregroundStyle(msg.outgoing ? p.onAccent : p.text)
                        .background(RoundedRectangle(cornerRadius: 12).fill(msg.outgoing ? AnyShapeStyle(p.gradient) : AnyShapeStyle(Color.gray.opacity(0.2))))
                    if !msg.outgoing { Spacer() }
                }
            }
            if list.isEmpty { Text("Сообщений нет").font(.footnote).foregroundStyle(p.text2) }
        }
        .padding(16)
        .frame(width: 320)
        .background(p.base)
    }
}

func previewText(_ msg: ChatMessage) -> String {
    if let media = msg.media {
        if media.voice { return "🎤 Голосовое · " + formatMs(media.duration) }
        return "📎 " + (msg.text.isEmpty ? media.name : msg.text)
    }
    return msg.text
}

// MARK: - Новая группа

struct NewGroupSheet: View {
    @EnvironmentObject var m: Messenger
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    var onCreated: (String) -> Void
    @State private var name = ""
    @State private var member = ""
    @State private var members: [String] = []
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    GlassField(title: "Название группы", text: $name).textInputAutocapitalization(.sentences)
                    HStack {
                        GlassField(title: "Логин участника", text: $member)
                        Button { add() } label: {
                            Image(systemName: "plus").font(.headline).foregroundStyle(p.onAccent)
                                .frame(width: 48, height: 48).background(Circle().fill(p.gradient))
                        }
                        .buttonStyle(.plain).disabled(member.count < 3 || members.count >= 4)
                    }
                    ForEach(members, id: \.self) { u in
                        HStack {
                            Text("@\(u)").foregroundStyle(p.text)
                            Spacer()
                            Button { members.removeAll { $0 == u } } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(p.text2) }
                        }
                        .glassCard(12)
                    }
                    Text("До 5 участников вместе с вами. Сообщения шифруются для каждого участника отдельно.")
                        .font(.caption).foregroundStyle(p.text2)
                    if let error { Text(error).font(.footnote).foregroundStyle(p.danger) }
                }
                .padding(16)
            }
            .background(LiquidBackground())
            .navigationTitle("Новая группа")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(busy ? "…" : "Создать") { create() }
                        .disabled(busy || name.trimmingCharacters(in: .whitespaces).isEmpty || members.isEmpty)
                }
            }
        }
    }

    private func add() {
        let u = member.trimmingCharacters(in: .whitespaces).lowercased().replacingOccurrences(of: "@", with: "")
        guard u.count >= 3, !members.contains(u), members.count < 4 else { return }
        members.append(u)
        member = ""
    }

    private func create() {
        busy = true
        error = nil
        Task {
            do {
                if let g = try await m.createGroup(name: name, members: members) { dismiss(); onCreated(g.conv) }
            } catch { self.error = error.localizedDescription }
            busy = false
        }
    }
}

// MARK: - Вложения из переписки: медиа, ссылки, голосовые

struct SharedContentView: View {
    @EnvironmentObject var m: Messenger
    @Environment(\.palette) private var p
    let peer: String
    @State private var tab = 0
    @State private var viewer: MediaViewerItem?

    var body: some View {
        let list = m.chats[peer] ?? []
        let media = list.filter { $0.media.map { $0.isImage || $0.isVideo } ?? false }.reversed()
        let voices = list.filter { $0.media?.voice == true }.reversed()
        let links: [(ChatMessage, String)] = list.reversed().flatMap { msg -> [(ChatMessage, String)] in
            let det = try? NSDataDetector(types: NSTextCheckingResult.CheckingType.link.rawValue)
            let ns = msg.text as NSString
            return (det?.matches(in: msg.text, range: NSRange(location: 0, length: ns.length)) ?? []).compactMap { r in
                r.url.map { (msg, $0.absoluteString) }
            }
        }
        VStack(alignment: .leading, spacing: 12) {
            Text("Вложения").font(.headline).foregroundStyle(p.text)
            Picker("", selection: $tab) {
                Text("Медиа \(media.count)").tag(0)
                Text("Ссылки \(links.count)").tag(1)
                Text("Голосовые \(voices.count)").tag(2)
            }
            .pickerStyle(.segmented)
            switch tab {
            case 0:
                if media.isEmpty { empty("Фото и видео пока нет") } else {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), spacing: 6) {
                        ForEach(Array(media)) { msg in SharedThumb(media: msg.media!) { viewer = $0 } }
                    }
                }
            case 1:
                if links.isEmpty { empty("Ссылок пока нет") } else {
                    ForEach(links.indices, id: \.self) { i in
                        Link(destination: URL(string: links[i].1)!) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(links[i].1).font(.footnote).foregroundStyle(p.accent).lineLimit(2)
                                Text(timeLabel(links[i].0.createdAt)).font(.caption2).foregroundStyle(p.text2)
                            }
                        }
                    }
                }
            default:
                if voices.isEmpty { empty("Голосовых пока нет") } else {
                    ForEach(Array(voices)) { msg in
                        HStack {
                            Text(msg.outgoing ? "Вы" : "@\(msg.sender.isEmpty ? peer : msg.sender)").font(.caption).foregroundStyle(p.text2).frame(width: 70, alignment: .leading)
                            VoiceBubble(msg: { var x = msg; x.outgoing = false; return x }(), media: msg.media!)
                        }
                    }
                }
            }
        }
        .glassCard()
        .fullScreenCover(item: $viewer) { MediaViewer(item: $0) }
    }

    private func empty(_ t: String) -> some View { Text(t).font(.footnote).foregroundStyle(p.text2) }
}

private struct SharedThumb: View {
    @EnvironmentObject var m: Messenger
    let media: MediaInfo
    var open: (MediaViewerItem) -> Void
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color.gray.opacity(0.2)
            if let image { Image(uiImage: image).resizable().scaledToFill() }
            if media.isVideo { Image(systemName: "play.circle.fill").font(.title).foregroundStyle(.white) }
        }
        .frame(height: 100)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .onTapGesture { Task { if let url = try? await m.mediaFileURL(media) { open(MediaViewerItem(url: url, isVideo: media.isVideo)) } } }
        .task(id: media.id) { if media.isImage, let d = try? await m.mediaData(media) { image = UIImage(data: d) } }
    }
}

// MARK: - Информация о группе

struct GroupInfoView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var m: Messenger
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    let id: Int64
    @State private var newMember = ""
    @State private var newName = ""
    @State private var renaming = false
    @State private var confirmLeave = false

    var body: some View {
        let g = m.groups[id]
        let isOwner = g?.owner == app.username
        Screen(title: g?.name ?? "Группа", large: false) {
            if let g {
                Text("\(g.members.count) из 5 участников").font(.subheadline).foregroundStyle(p.text2)
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(g.members, id: \.username) { mem in
                        HStack(spacing: 12) {
                            AvatarView(avatar: mem.avatar, name: mem.displayName.isEmpty ? mem.username : mem.displayName, size: 40)
                            VStack(alignment: .leading) {
                                Text((mem.displayName.isEmpty ? mem.username : mem.displayName) + (mem.username == g.owner ? " · создатель" : ""))
                                    .foregroundStyle(p.text).lineLimit(1)
                                Text("@\(mem.username)").font(.caption).foregroundStyle(p.text2)
                            }
                            Spacer()
                            if mem.username != app.username {
                                NavigationLink { UserProfileView(username: mem.username) } label: { Image(systemName: "person.crop.circle").foregroundStyle(p.accent) }
                                if isOwner {
                                    Button { Task { try? await m.removeGroupMember(id, mem.username) } } label: {
                                        Image(systemName: "person.badge.minus").foregroundStyle(p.danger)
                                    }.buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
                .glassCard()
                if isOwner && g.members.count < 5 {
                    HStack {
                        GlassField(title: "Добавить по логину", text: $newMember)
                        Button { Task { try? await m.addGroupMember(id, newMember); newMember = "" } } label: {
                            Image(systemName: "person.badge.plus").foregroundStyle(p.onAccent).frame(width: 48, height: 48).background(Circle().fill(p.gradient))
                        }.buttonStyle(.plain).disabled(newMember.count < 3)
                    }
                }
                HStack(spacing: 10) {
                    GlassButton(title: "Переименовать", systemImage: "pencil") { newName = g.name; renaming = true }
                    GlassButton(title: "Выйти", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) { confirmLeave = true }
                }
                SharedContentView(peer: g.conv)
            } else {
                ProgressView().frame(maxWidth: .infinity)
            }
        }
        .task { await m.refreshGroups() }
        .alert("Название группы", isPresented: $renaming) {
            TextField("Название", text: $newName)
            Button("Сохранить") { Task { try? await m.renameGroup(id, newName) } }
            Button("Отмена", role: .cancel) {}
        }
        .confirmationDialog("Выйти из группы?", isPresented: $confirmLeave, titleVisibility: .visible) {
            Button("Выйти", role: .destructive) {
                Task { if let u = app.username { try? await m.removeGroupMember(id, u) }; dismiss() }
            }
        } message: { Text("Переписка группы удалится с этого устройства.") }
    }
}
