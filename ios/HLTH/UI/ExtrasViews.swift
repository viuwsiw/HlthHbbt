import SwiftUI
import MapKit

struct FocusView: View {
    @ObservedObject var timer = FocusTimer.shared
    @Environment(\.palette) private var p
    let habit: Habit?
    var minutes: Int = 25
    @State private var chosen = 25

    init(habit: Habit?, minutes: Int = 25) {
        self.habit = habit
        self.minutes = minutes
        _chosen = State(initialValue: minutes)
    }

    var body: some View {
        Screen(title: habit.map { "\($0.emoji) \($0.title)" } ?? "Фокус", large: false) {
            VStack(spacing: 24) {
                ZStack {
                    Circle().stroke(p.accent.opacity(0.15), lineWidth: 16)
                    Circle().trim(from: 0, to: timer.active ? CGFloat(timer.left / max(1, timer.total)) : 1)
                        .stroke(p.gradient, style: StrokeStyle(lineWidth: 16, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .animation(.linear(duration: 1), value: timer.left)
                    VStack(spacing: 6) {
                        Text(timeString(timer.active ? timer.left : TimeInterval(chosen * 60)))
                            .font(.system(size: 54, weight: .bold, design: .rounded).monospacedDigit()).foregroundStyle(p.text)
                        Text(timer.pausedLeft != nil ? "Пауза" : (timer.active ? "Сосредоточьтесь" : "Готовы?")).foregroundStyle(p.text2)
                    }
                }
                .frame(width: 260, height: 260)
                .padding(30)
                .glass(200)
                .padding(.top, 20)

                if !timer.active {
                    Stepper("\(chosen) минут", value: $chosen, in: 5...180, step: 5).foregroundStyle(p.text).glassCard()
                    GlassButton(title: "Начать", systemImage: "play.fill", prominent: true) { timer.start(minutes: chosen, habit: habit?.id) }
                } else {
                    HStack(spacing: 12) {
                        if timer.pausedLeft != nil {
                            GlassButton(title: "Продолжить", systemImage: "play.fill", prominent: true) { timer.resume() }
                        } else {
                            GlassButton(title: "Пауза", systemImage: "pause.fill") { timer.pause() }
                        }
                        GlassButton(title: "Стоп", systemImage: "stop.fill", role: .destructive) { timer.cancel() }
                    }
                }
                MusicCard()
                Text("Таймер продолжит идти, даже если закрыть приложение, — по окончании придёт уведомление.")
                    .font(.caption).foregroundStyle(p.text2).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

struct WalkView: View {
    @ObservedObject var walk = WalkTracker.shared
    @Environment(\.palette) private var p
    let habit: Habit?
    @State private var camera: MapCameraPosition = .userLocation(fallback: .automatic)
    @State private var now = Date()
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        ZStack(alignment: .bottom) {
            Map(position: $camera) {
                UserAnnotation()
                if walk.points.count > 1 {
                    MapPolyline(coordinates: walk.points).stroke(p.accent, lineWidth: 6)
                }
            }
            .mapControls { MapUserLocationButton(); MapCompass() }
            .ignoresSafeArea(edges: .top)

            VStack(spacing: 14) {
                HStack(spacing: 10) {
                    cell(walk.running ? timeString(now.timeIntervalSince(walk.startedAt ?? now)) : "00:00", "время")
                    cell(String(format: "%.2f", walk.distance / 1000), "км")
                    cell("\(walk.steps)", "шагов")
                }
                if walk.running {
                    HStack(spacing: 10) {
                        GlassButton(title: "Завершить", systemImage: "flag.checkered", prominent: true) { walk.stop(save: true) }
                        GlassButton(title: "Отмена", systemImage: "xmark", role: .destructive) { walk.stop(save: false) }
                    }
                } else {
                    GlassButton(title: "Начать прогулку", systemImage: "figure.walk", prominent: true) { walk.start(habit: habit?.id) }
                }
            }
            .padding(16)
            .glass(30)
            .padding(.horizontal, 12).padding(.bottom, 8)
        }
        .navigationTitle(habit?.title ?? "Прогулка")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { walk.prepare() }
        .onReceive(tick) { now = $0 }
    }

    private func cell(_ v: String, _ t: String) -> some View {
        VStack(spacing: 2) {
            Text(v).font(.title3.bold().monospacedDigit()).foregroundStyle(p.text).lineLimit(1).minimumScaleFactor(0.6)
            Text(t).font(.caption).foregroundStyle(p.text2)
        }.frame(maxWidth: .infinity)
    }
}

struct MusicView: View {
    @ObservedObject var spotify = Spotify.shared
    @Environment(\.palette) private var p
    @State private var opened: SpotifyPlaylist?
    @State private var tracks: [SpotifyTrack] = []

    var body: some View {
        Screen(title: "Музыка", large: false) {
            if !spotify.isLoggedIn {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Spotify").font(.title2.bold()).foregroundStyle(p.text)
                    Text("Войдите в Spotify, чтобы включать плейлисты прямо из приложения и показывать собеседникам, что играет.")
                        .font(.subheadline).foregroundStyle(p.text2)
                    Text("Авторизация в Spotify временно доступна через согласование с разработчиком — пришлите нам email вашего аккаунта Spotify.")
                        .font(.caption).foregroundStyle(p.text2)
                    GlassButton(title: "Войти через Spotify", systemImage: "music.note", prominent: true) { spotify.login() }
                }.glassCard()
            } else {
                player
                SectionTitle("Мои плейлисты")
                ForEach(spotify.playlists) { pl in
                    Button {
                        opened = pl
                        Task { tracks = await spotify.tracks(pl) }
                    } label: {
                        HStack(spacing: 12) {
                            AsyncImage(url: pl.image.flatMap({ URL(string: $0) })) { $0.resizable().scaledToFill() } placeholder: { Color.gray.opacity(0.2) }
                                .frame(width: 52, height: 52).clipShape(RoundedRectangle(cornerRadius: 10))
                            VStack(alignment: .leading) {
                                Text(pl.name).font(.headline).foregroundStyle(p.text).lineLimit(1)
                                Text("\(pl.total) треков").font(.caption).foregroundStyle(p.text2)
                            }
                            Spacer()
                            Button { Task { await spotify.play(context: pl.uri) } } label: {
                                Image(systemName: "play.fill").foregroundStyle(p.onAccent).frame(width: 38, height: 38).background(Circle().fill(Color(hex: 0x1DB954)))
                            }.buttonStyle(.plain)
                        }.glassCard(10)
                    }.buttonStyle(.plain)
                }
                GlassButton(title: "Выйти из Spotify", role: .destructive) { spotify.logout() }
            }
            if let e = spotify.error {
                Text(e).font(.footnote).foregroundStyle(p.danger).glassCard().onTapGesture { spotify.error = nil }
            }
        }
        .task { await spotify.load() }
        .sheet(item: $opened) { pl in
            NavigationStack {
                List(tracks) { t in
                    Button { Task { await spotify.play(context: pl.uri, track: t.uri) } } label: {
                        VStack(alignment: .leading) {
                            Text(t.name).font(.headline)
                            Text(t.artist).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                .scrollContentBackground(.hidden)
                .navigationTitle(pl.name).navigationBarTitleDisplayMode(.inline)
            }
            .presentationBackground(.ultraThinMaterial)
            .presentationDetents([.medium, .large])
        }
    }

    private var player: some View {
        VStack(spacing: 14) {
            let t = spotify.playback?.track
            AsyncImage(url: t?.image.flatMap({ URL(string: $0) })) { $0.resizable().scaledToFill() } placeholder: {
                ZStack { Color(hex: 0x1DB954).opacity(0.3); Image(systemName: "music.note").font(.largeTitle).foregroundStyle(p.text) }
            }
            .frame(width: 200, height: 200).clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .shadow(radius: 20, y: 10)
            Text(t?.name ?? "Ничего не играет").font(.title3.bold()).foregroundStyle(p.text).lineLimit(1)
            Text(t?.artist ?? (spotify.playback?.device.map { "Устройство: \($0)" } ?? "Выберите плейлист ниже")).font(.subheadline).foregroundStyle(p.text2).lineLimit(1)
            GlassGroup {
                HStack(spacing: 22) {
                    ctl("backward.fill", 50) { await spotify.previous() }
                    ctl(spotify.playback?.isPlaying == true ? "pause.fill" : "play.fill", 68) { await spotify.toggle() }
                    ctl("forward.fill", 50) { await spotify.next() }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .glassCard(20)
    }

    private func ctl(_ icon: String, _ size: CGFloat, _ action: @escaping () async -> Void) -> some View {
        Button { Task { await action() } } label: {
            Image(systemName: icon).font(.system(size: size * 0.38, weight: .bold)).foregroundStyle(p.text)
                .frame(width: size, height: size).glass(size / 2, interactive: true)
        }.buttonStyle(.plain)
    }
}

struct AdminView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    @State private var stats: JSON = [:]
    @State private var logs: [String] = []
    @State private var error: String?
    @State private var update: IosUpdate?
    @State private var confirm = ""
    @State private var askWipe = false

    var body: some View {
        Screen(title: "Админ-панель", large: false) {
            if let error { Text(error).foregroundStyle(p.danger).glassCard() }
            HStack(spacing: 10) {
                Circle().fill(error == nil && !stats.isEmpty ? Color.green : Color.red).frame(width: 12, height: 12)
                Text(error == nil && !stats.isEmpty ? "Сервер работает" : "Нет ответа").font(.headline).foregroundStyle(p.text)
                Spacer()
                Text("аптайм " + uptime).font(.caption).foregroundStyle(p.text2)
            }.glassCard()

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                cell("👥 Пользователи", "\(int("users"))", "+\(int("usersToday")) за сутки")
                cell("🟢 Онлайн", "\(int("online"))", "сессий \(int("sessions"))")
                cell("💬 Сообщения", "\(int("messages"))", "+\(int("messagesToday")) за сутки")
                cell("📎 Файлы", "\(int("mediaFiles"))", bytes("mediaBytes"))
                cell("☁️ Копии", "\(int("backups"))", "база " + bytes("dbBytes"))
                cell("⚙️ Процесс", bytes("procMemBytes"), "горутин \(int("goroutines"))")
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("Ресурсы").font(.headline).foregroundStyle(p.text)
                let load = (stats["load"] as? [Double])?.first ?? 0
                bar("Процессор", min(1, load / Double(max(1, int("cpus")))), String(format: "load %.2f · %d ядер", load, int("cpus")))
                let memT = Double(int("sysMemTotal")), memA = Double(int("sysMemAvail"))
                bar("Память", memT > 0 ? 1 - memA / memT : 0, "\(bytes("sysMemAvail")) свободно из \(bytes("sysMemTotal"))")
                let dT = Double(int("diskTotal")), dF = Double(int("diskFree"))
                bar("Диск", dT > 0 ? 1 - dF / dT : 0, "\(bytes("diskFree")) свободно из \(bytes("diskTotal"))")
            }.glassCard()

            VStack(alignment: .leading, spacing: 6) {
                Text("Обновления").font(.headline).foregroundStyle(p.text)
                Text("Установлено: iOS \(Updates.currentVersion) (\(Updates.currentBuild))").font(.subheadline).foregroundStyle(p.text2)
                Text(update.map { "На GitHub: \($0.version) (\($0.build))" } ?? "На GitHub новее нет").font(.subheadline).foregroundStyle(p.text2)
            }.glassCard()

            VStack(alignment: .leading, spacing: 4) {
                Text("Логи").font(.headline).foregroundStyle(p.text)
                ScrollView {
                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(logs.indices.reversed(), id: \.self) { i in
                            Text(logs[i]).font(.system(size: 10, design: .monospaced)).foregroundStyle(p.text2).frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }.frame(height: 260)
            }.glassCard()

            VStack(alignment: .leading, spacing: 10) {
                Text("⚠️ Экстренный сброс").font(.headline).foregroundStyle(p.danger)
                Text("Удаляет ВСЕ аккаунты, сообщения, файлы и копии на сервере (кроме администратора). Отменить нельзя.")
                    .font(.caption).foregroundStyle(p.text2)
                GlassField(title: "Введите СБРОСИТЬ ВСЁ", text: $confirm)
                GlassButton(title: "Сбросить все данные", systemImage: "exclamationmark.triangle.fill", role: .destructive) { askWipe = true }
                    .disabled(confirm != "СБРОСИТЬ ВСЁ")
            }.glassCard()
        }
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Закрыть") { dismiss() } } }
        .alert("Точно сбросить всё?", isPresented: $askWipe) {
            Button("Сбросить", role: .destructive) {
                Task {
                    do { try await app.api.adminWipe(); app.show("Все данные сброшены"); confirm = "" }
                    catch { self.error = error.localizedDescription }
                }
            }
            Button("Отмена", role: .cancel) {}
        }
        .task {
            update = await Updates.check()
            while !Task.isCancelled {
                do {
                    stats = try await app.api.adminStats()
                    logs = try await app.api.adminLogs()
                    error = nil
                } catch { self.error = error.localizedDescription }
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
    }

    private func int(_ k: String) -> Int { (stats[k] as? NSNumber)?.intValue ?? 0 }
    private func bytes(_ k: String) -> String { ByteCountFormatter.string(fromByteCount: (stats[k] as? NSNumber)?.int64Value ?? 0, countStyle: .memory) }
    private var uptime: String {
        let s = int("uptimeSec")
        return s > 86400 ? "\(s / 86400) д \(s % 86400 / 3600) ч" : "\(s / 3600) ч \(s % 3600 / 60) мин"
    }

    private func cell(_ t: String, _ v: String, _ sub: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(t).font(.caption).foregroundStyle(p.text2)
            Text(v).font(.title3.bold()).foregroundStyle(p.text).lineLimit(1).minimumScaleFactor(0.6)
            Text(sub).font(.caption2).foregroundStyle(p.text2).lineLimit(1)
        }.glassCard(12)
    }

    private func bar(_ t: String, _ v: Double, _ sub: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack { Text(t).foregroundStyle(p.text); Spacer(); Text("\(Int(v * 100))%").foregroundStyle(v > 0.85 ? p.danger : p.text2) }.font(.subheadline)
            ProgressView(value: max(0, min(1, v))).tint(v > 0.85 ? p.danger : p.accent)
            Text(sub).font(.caption2).foregroundStyle(p.text2)
        }
    }
}
