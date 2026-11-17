import SwiftUI

struct HomeView: View {
    @EnvironmentObject var app: AppModel
    @ObservedObject var health = Health.shared
    @ObservedObject var focus = FocusTimer.shared
    @ObservedObject var walk = WalkTracker.shared
    @Environment(\.palette) private var p
    var openTab: (MainTab) -> Void
    @State private var showNote = false
    @AppStorage("home_habits_open") private var habitsOpen = false

    private var greeting: String {
        let h = Calendar.current.component(.hour, from: Date())
        switch h {
        case 5..<12: return "Доброе утро"
        case 12..<17: return "Добрый день"
        case 17..<23: return "Добрый вечер"
        default: return "Доброй ночи"
        }
    }

    var body: some View {
        let today = Days.today
        let habits = app.data.habits.filter { !$0.archived && $0.scheduled(on: today) }
        Screen(title: "Главная") {
            VStack(alignment: .leading, spacing: 4) {
                Text("\(greeting), \(app.data.profile.displayName.isEmpty ? (app.username ?? "") : app.data.profile.displayName)")
                    .font(.title2.bold()).foregroundStyle(p.text)
                Text("💡 " + dailyTips[Int(today % Int64(dailyTips.count))])
                    .font(.caption).foregroundStyle(p.text2)
            }
            .padding(.horizontal, 4)

            if focus.active { NavigationLink { FocusView(habit: nil) } label: { activeBanner("🎯 Фокус", timeString(focus.left)) }.buttonStyle(.plain) }
            if walk.running { NavigationLink { WalkView(habit: nil) } label: { activeBanner("🚶 Прогулка", String(format: "%.2f км", walk.distance / 1000)) }.buttonStyle(.plain) }

            WaterCard()

            HStack(spacing: 12) {
                metric("figure.walk", "Шаги", health.steps.map { "\($0)" } ?? "—", p.accent)
                metric("bed.double.fill", "Сон", health.sleepMinutes.map { "\($0 / 60) ч \($0 % 60) м" } ?? "—", Color(hex: 0x8B5CF6))
            }
            if !health.authorized {
                GlassButton(title: "Подключить «Здоровье»", systemImage: "heart.fill") { Task { await health.request() } }
            }

            let doneCount = habits.filter { app.isDone($0) }.count
            Button {
                withAnimation(.spring(duration: 0.35)) { habitsOpen.toggle() }
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "checklist").font(.title3).foregroundStyle(p.accent)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Привычки на сегодня").font(.headline).foregroundStyle(p.text)
                        Text(habits.isEmpty ? "На сегодня ничего не запланировано" : "Выполнено \(doneCount) из \(habits.count)")
                            .font(.caption).foregroundStyle(p.text2)
                    }
                    Spacer()
                    Image(systemName: "chevron.down").font(.subheadline.bold()).foregroundStyle(p.text2)
                        .rotationEffect(.degrees(habitsOpen ? 180 : 0))
                }
                .glassCard()
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if habitsOpen {
                VStack(spacing: 10) {
                    ForEach(habits) { h in HabitRow(habit: h) }
                    Button { openTab(.habits) } label: {
                        Label("Все привычки", systemImage: "chevron.right").font(.subheadline.weight(.semibold)).foregroundStyle(p.accent)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 6)
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }

            SectionTitle("Музыка")
            MusicCard()

            SectionTitle("Настроение")
            Button { showNote = true } label: {
                HStack {
                    Text(moodEmoji(app.note(today)?.mood ?? 0)).font(.largeTitle)
                    VStack(alignment: .leading) {
                        Text("Как вы сегодня?").font(.headline).foregroundStyle(p.text)
                        Text(app.note(today)?.note.isEmpty == false ? app.note(today)!.note : "Отметьте настроение и пару слов о дне")
                            .font(.caption).foregroundStyle(p.text2).lineLimit(2)
                    }
                    Spacer()
                }
                .glassCard()
            }.buttonStyle(.plain)
        }
        .sheet(isPresented: $showNote) { NoteSheet(day: today).presentationDetents([.medium]) }
    }

    private func activeBanner(_ title: String, _ value: String) -> some View {
        HStack {
            Text(title).font(.headline)
            Spacer()
            Text(value).font(.headline.monospacedDigit())
        }
        .foregroundStyle(p.onAccent)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(p.gradient))
        .glass(22, tint: p.accent.opacity(0.4))
    }

    private func metric(_ icon: String, _ title: String, _ value: String, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon).font(.title3).foregroundStyle(color)
            Text(value).font(.title3.bold()).foregroundStyle(p.text).lineLimit(1).minimumScaleFactor(0.6)
            Text(title).font(.caption).foregroundStyle(p.text2)
        }
        .glassCard()
    }
}

func moodEmoji(_ m: Int) -> String { ["😶", "😞", "😕", "😐", "🙂", "😄"][max(0, min(5, m))] }

func timeString(_ t: TimeInterval) -> String {
    let s = Int(t.rounded())
    return s >= 3600 ? String(format: "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String(format: "%02d:%02d", s / 60, s % 60)
}

struct WaterCard: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p

    var body: some View {
        let goal = max(1, app.data.profile.waterGoalMl)
        let today = app.waterToday
        let progress = min(1, Double(today) / Double(goal))
        HStack(spacing: 16) {
            ZStack {
                Circle().stroke(p.water.opacity(0.18), lineWidth: 10)
                Circle().trim(from: 0, to: progress)
                    .stroke(LinearGradient(colors: [p.water, Color(hex: 0x2470E8)], startPoint: .top, endPoint: .bottom), style: StrokeStyle(lineWidth: 10, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                Image(systemName: "drop.fill").font(.title2).foregroundStyle(p.water)
            }
            .frame(width: 74, height: 74)
            .animation(.spring(duration: 0.6), value: progress)
            VStack(alignment: .leading, spacing: 6) {
                Text("Вода").font(.headline).foregroundStyle(p.text)
                Text("\(today) / \(goal) мл").font(.subheadline.monospacedDigit()).foregroundStyle(p.text2)
                HStack(spacing: 8) {
                    pill("+250") { app.addWater(250) }
                    pill("+500") { app.addWater(500) }
                    if today > 0 { pill("↺") { app.undoWater() } }
                }
            }
            Spacer(minLength: 0)
        }
        .glassCard()
    }

    private func pill(_ t: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(t).font(.subheadline.weight(.bold)).foregroundStyle(p.water)
                .padding(.horizontal, 12).padding(.vertical, 7)
                .glass(16, tint: p.water.opacity(0.2), interactive: true)
        }.buttonStyle(.plain)
    }
}

struct NoteSheet: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    let day: EpochDay
    @State private var mood = 0
    @State private var text = ""

    var body: some View {
        VStack(spacing: 18) {
            Text("Настроение").font(.title3.bold()).foregroundStyle(p.text)
            HStack(spacing: 10) {
                ForEach(1...5, id: \.self) { m in
                    Button { mood = m } label: {
                        Text(moodEmoji(m)).font(.system(size: 34))
                            .padding(8)
                            .background(Circle().fill(mood == m ? p.accent.opacity(0.25) : .clear))
                    }.buttonStyle(.plain)
                }
            }
            TextField("Пара слов о дне", text: $text, axis: .vertical).lineLimit(3...6)
                .padding(14).glass(18)
            GlassButton(title: "Сохранить", prominent: true) {
                app.saveNote(day, mood: mood, text: text)
                dismiss()
            }
        }
        .padding(24)
        .glassSheet()
        .onAppear { mood = app.note(day)?.mood ?? 0; text = app.note(day)?.note ?? "" }
    }
}

struct MusicCard: View {
    @ObservedObject var spotify = Spotify.shared
    @ObservedObject var nowPlaying = NowPlaying.shared
    @Environment(\.palette) private var p

    var body: some View {
        NavigationLink { MusicView() } label: {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color(hex: 0x1DB954).opacity(0.9))
                    if let img = spotify.playback?.track?.image, let url = URL(string: img) {
                        AsyncImage(url: url) { $0.resizable().scaledToFill() } placeholder: { Color.clear }
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    } else {
                        Image(systemName: "music.note").font(.title2).foregroundStyle(.white)
                    }
                }
                .frame(width: 54, height: 54)
                VStack(alignment: .leading, spacing: 3) {
                    if let t = nowPlaying.track {
                        Text(t.title).font(.headline).foregroundStyle(p.text).lineLimit(1)
                        Text("\(t.artist) · \(t.app)").font(.caption).foregroundStyle(p.text2).lineLimit(1)
                    } else {
                        Text(spotify.isLoggedIn ? "Spotify" : "Музыка для привычек").font(.headline).foregroundStyle(p.text)
                        Text(spotify.isLoggedIn ? "Плейлисты и плеер" : "Подключите Spotify").font(.caption).foregroundStyle(p.text2)
                    }
                }
                Spacer()
                if spotify.isLoggedIn {
                    Button { Task { await spotify.toggle() } } label: {
                        Image(systemName: spotify.playback?.isPlaying == true ? "pause.fill" : "play.fill")
                            .font(.title3).foregroundStyle(p.text).frame(width: 44, height: 44).glass(22, interactive: true)
                    }.buttonStyle(.plain)
                }
            }
            .glassCard(12)
        }
        .buttonStyle(.plain)
    }
}
