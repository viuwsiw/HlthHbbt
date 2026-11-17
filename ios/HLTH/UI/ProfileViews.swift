import SwiftUI
import PhotosUI

struct ProfileView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var m: Messenger
    @Environment(\.palette) private var p
    @State private var confirmLogout = false

    var body: some View {
        let h = app.history
        let ach = h.achievements
        Screen(title: "Профиль") {
            VStack(spacing: 8) {
                ZStack(alignment: .bottomTrailing) {
                    AvatarView(avatar: app.data.profile.avatar, photo: app.data.profile.avatarPhoto, name: app.data.profile.displayName, size: 110)
                    StatusDot(status: m.myStatus, size: 22)
                }
                Text(app.data.profile.displayName.nonEmpty ?? (app.username ?? "")).font(.title2.bold()).foregroundStyle(p.text)
                Text("@\(app.username ?? "")").font(.subheadline).foregroundStyle(p.text2)
                if !app.data.privacy.bio.isEmpty {
                    Text(app.data.privacy.bio).font(.body).foregroundStyle(p.text).multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity).padding(.vertical, 6)

            HStack(spacing: 12) {
                stat("🔥", "\(h.currentStreak)", "серия")
                stat("🏆", "\(ach.filter(\.unlocked).count)/\(ach.count)", "награды")
                stat("✅", "\(h.doneByHabit.values.reduce(0) { $0 + $1.count })", "отметок")
            }

            SectionTitle("Статистика")
            StatsCard()

            SectionTitle("Аккаунт")
            VStack(spacing: 0) {
                link("Редактировать профиль", "person.crop.circle") { EditProfileView() }
                Divider().opacity(0.3)
                link("Достижения", "trophy") { AchievementsView() }
                Divider().opacity(0.3)
                link("Конфиденциальность", "hand.raised") { PrivacyView() }
                Divider().opacity(0.3)
                link("Настройки", "gearshape") { SettingsView() }
                Divider().opacity(0.3)
                link("Сменить пароль", "key") { ChangePasswordView() }
            }
            .glass()

            GlassButton(title: "Выйти", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) { confirmLogout = true }
                .confirmationDialog("Выйти из аккаунта?", isPresented: $confirmLogout, titleVisibility: .visible) {
                    Button("Выйти", role: .destructive) { Task { await app.logout() } }
                } message: { Text("Перед выходом данные сохранятся в облачную копию.") }

            Text("HLTH HBTy для iOS \(Updates.currentVersion) (\(Updates.currentBuild))").font(.caption).foregroundStyle(p.text2).frame(maxWidth: .infinity)
        }
    }

    private func stat(_ e: String, _ v: String, _ t: String) -> some View {
        VStack(spacing: 4) {
            Text(e).font(.title2)
            Text(v).font(.headline).foregroundStyle(p.text).lineLimit(1).minimumScaleFactor(0.6)
            Text(t).font(.caption).foregroundStyle(p.text2)
        }
        .frame(maxWidth: .infinity).padding(.vertical, 14).glass(20)
    }

    private func link<D: View>(_ title: String, _ icon: String, @ViewBuilder _ dest: @escaping () -> D) -> some View {
        NavigationLink { dest() } label: {
            HStack(spacing: 14) {
                Image(systemName: icon).foregroundStyle(p.accent).frame(width: 26)
                Text(title).foregroundStyle(p.text)
                Spacer()
                Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(p.text2)
            }
            .padding(.horizontal, 16).padding(.vertical, 15)
            .contentShape(Rectangle())
        }.buttonStyle(.plain)
    }
}

struct StatsCard: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p

    private func values() -> [Double] {
        let h = app.history
        let today = Days.today
        var out: [Double] = []
        for back in stride(from: 29, through: 0, by: -1) {
            out.append(h.completion(today - Int64(back)) ?? 0)
        }
        return out
    }

    private func avgWater() -> Int {
        let h = app.history
        let today = Days.today
        var sum = 0
        for back in 0..<7 { sum += h.waterByDay[today - Int64(back)] ?? 0 }
        return sum / 7
    }

    private func bar(_ v: Double) -> some View {
        let style: AnyShapeStyle = v >= 1 ? AnyShapeStyle(p.gradient) : AnyShapeStyle(p.accent.opacity(0.35))
        let height: CGFloat = max(4, 80 * CGFloat(v))
        return RoundedRectangle(cornerRadius: 3).fill(style).frame(height: height).frame(maxWidth: .infinity)
    }

    var body: some View {
        let vals = values()
        let avg: Double = vals.reduce(0, +) / Double(max(1, vals.count))
        let percent = Int(avg * 100)
        let water = avgWater()
        let walks = app.data.walks.count
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("30 дней").font(.headline).foregroundStyle(p.text)
                Spacer()
                Text("\(percent)% выполнено").font(.subheadline.weight(.semibold)).foregroundStyle(p.accent)
            }
            HStack(alignment: .bottom, spacing: 3) {
                ForEach(0..<vals.count, id: \.self) { i in bar(vals[i]) }
            }
            .frame(height: 80, alignment: .bottom)
            HStack {
                Text("💧 Сред. вода: \(water) мл").font(.caption).foregroundStyle(p.text2)
                Spacer()
                Text("🚶 Прогулок: \(walks)").font(.caption).foregroundStyle(p.text2)
            }
        }
        .glassCard()
    }
}

struct AchievementsView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p

    var body: some View {
        Screen(title: "Достижения", large: false) {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(app.history.achievements) { a in
                    VStack(spacing: 6) {
                        Text(a.emoji).font(.system(size: 40)).grayscale(a.unlocked ? 0 : 1).opacity(a.unlocked ? 1 : 0.5)
                        Text(a.title).font(.subheadline.bold()).foregroundStyle(p.text).multilineTextAlignment(.center)
                        Text(a.description).font(.caption2).foregroundStyle(p.text2).multilineTextAlignment(.center)
                        ProgressView(value: a.progress).tint(p.accent)
                        Text("\(min(a.current, a.target))/\(a.target)").font(.caption2.monospacedDigit()).foregroundStyle(p.text2)
                    }
                    .padding(12).frame(maxWidth: .infinity, minHeight: 170).glass(22)
                }
            }
        }
    }
}

struct EditProfileView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    @State private var profile = Profile()
    @State private var bio = ""
    @State private var ageText = ""
    @State private var photoItem: PhotosPickerItem?

    var body: some View {
        Screen(title: "Профиль", large: false) {
            VStack(spacing: 12) {
                AvatarView(avatar: profile.avatar, photo: profile.avatarPhoto, name: profile.displayName, size: 110)
                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label("Выбрать фото", systemImage: "photo").font(.subheadline.weight(.semibold)).foregroundStyle(p.accent)
                }
            }.frame(maxWidth: .infinity)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(0..<16, id: \.self) { i in
                        Button { profile.avatar = "preset:\(i)"; profile.avatarPhoto = nil } label: {
                            Image("avatar_\(i + 1)").resizable().frame(width: 54, height: 54).clipShape(Circle())
                                .overlay(Circle().stroke(p.accent, lineWidth: profile.avatar == "preset:\(i)" && profile.avatarPhoto == nil ? 3 : 0))
                        }.buttonStyle(.plain)
                    }
                }.padding(10)
            }.glass()

            GlassField(title: "Имя", text: $profile.displayName).textInputAutocapitalization(.words)
            VStack(alignment: .trailing, spacing: 4) {
                TextField("О себе", text: $bio, axis: .vertical).lineLimit(2...5)
                    .foregroundStyle(p.text).padding(16).glass(20)
                    .onChange(of: bio) { _, v in if v.count > 160 { bio = String(v.prefix(160)) } }
                Text("\(bio.count)/160").font(.caption2).foregroundStyle(p.text2).padding(.trailing, 8)
            }
            GlassField(title: "Возраст", text: $ageText, keyboard: .numberPad)
            Picker("Пол", selection: $profile.gender) {
                Text("Не указан").tag(Gender?.none)
                Text("Мужской").tag(Gender?.some(.MALE))
                Text("Женский").tag(Gender?.some(.FEMALE))
            }.pickerStyle(.segmented)
            Stepper("Цель по воде: \(profile.waterGoalMl) мл", value: $profile.waterGoalMl, in: 500...6000, step: 250)
                .foregroundStyle(p.text).glassCard()

            GlassButton(title: "Сохранить", prominent: true) {
                var x = profile
                x.displayName = x.displayName.trimmingCharacters(in: .whitespaces)
                x.age = Int(ageText)
                x.profileCompleted = true
                app.saveProfile(x, bio: bio)
                app.show("Профиль сохранён")
                dismiss()
            }
            .disabled(profile.displayName.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .onAppear {
            profile = app.data.profile
            bio = app.data.privacy.bio
            ageText = profile.age.map(String.init) ?? ""
        }
        .onChange(of: photoItem) { _, item in
            guard let item else { return }
            Task {
                if let d = try? await item.loadTransferable(type: Data.self), let jpeg = AppModel.jpeg512(d) { profile.avatarPhoto = jpeg }
            }
        }
    }
}

struct ChangePasswordView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    @State private var current = ""
    @State private var new1 = ""
    @State private var new2 = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        Screen(title: "Пароль", large: false) {
            GlassField(title: "Текущий пароль", text: $current, secure: true)
            GlassField(title: "Новый пароль", text: $new1, secure: true)
            GlassField(title: "Повторите новый", text: $new2, secure: true)
            if let error { Text(error).font(.footnote).foregroundStyle(p.danger) }
            GlassButton(title: busy ? "…" : "Сменить пароль", prominent: true) {
                guard new1 == new2 else { error = "Пароли не совпадают"; return }
                busy = true
                Task {
                    do { try await app.changePassword(current: current, new: new1); app.show("Пароль изменён"); dismiss() }
                    catch { self.error = error.localizedDescription }
                    busy = false
                }
            }.disabled(busy || current.isEmpty || new1.isEmpty)
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @State private var password = ""
    @State private var checking = false
    @State private var updateText: String?
    @State private var confirmDelete = false

    var body: some View {
        Screen(title: "Настройки", large: false) {
            SectionTitle("Оформление")
            VStack(alignment: .leading, spacing: 14) {
                Picker("Тема", selection: $app.settings.themeMode) {
                    ForEach(ThemeMode.allCases, id: \.self) { Text($0.title).tag($0) }
                }.pickerStyle(.segmented)
                Text("Акцентный цвет").font(.subheadline).foregroundStyle(p.text2)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(accentOptions.indices, id: \.self) { i in
                            let o = accentOptions[i]
                            let pair = p.isDark ? o.dark : o.light
                            Button { app.settings.accentIndex = i } label: {
                                VStack(spacing: 4) {
                                    Circle().fill(LinearGradient(colors: [Color(hex: pair.0), Color(hex: pair.1)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                        .frame(width: 40, height: 40)
                                        .overlay(Circle().stroke(p.text, lineWidth: app.settings.accentIndex == i ? 3 : 0))
                                    Text(o.title).font(.caption2).foregroundStyle(p.text2)
                                }
                            }.buttonStyle(.plain)
                        }
                    }
                }
                Toggle("Живой фон", isOn: $app.settings.animatedBackground).foregroundStyle(p.text)
            }.glassCard()

            SectionTitle("Напоминания о воде")
            VStack(spacing: 12) {
                Toggle("Напоминать пить воду", isOn: $app.settings.waterReminders)
                if app.settings.waterReminders {
                    Stepper("Каждые \(app.settings.waterIntervalHours) ч", value: $app.settings.waterIntervalHours, in: 1...6)
                    Stepper("С \(app.settings.waterFromHour):00", value: $app.settings.waterFromHour, in: 5...12)
                    Stepper("До \(app.settings.waterToHour):00", value: $app.settings.waterToHour, in: 17...23)
                }
            }
            .foregroundStyle(p.text).glassCard()
            .onChange(of: app.settings) { _, s in
                Reminders.shared.requestPermission()
                Reminders.shared.reschedule(app.data.habits, settings: s)
            }

            SectionTitle("Данные")
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "icloud.fill").foregroundStyle(p.accent)
                    Text("Облачная копия").font(.headline).foregroundStyle(p.text)
                    Spacer()
                    Text(backupLabel).font(.caption).foregroundStyle(p.text2)
                }
                Text("Всё шифруется на устройстве ключом из пароля — сервер хранит только шифротекст. Копия общая с Android-версией.")
                    .font(.caption).foregroundStyle(p.text2)
                if app.backupState == .needsPassword {
                    GlassField(title: "Пароль от аккаунта", text: $password, secure: true)
                    GlassButton(title: "Включить копию", prominent: true) {
                        guard let u = app.username else { return }
                        Task {
                            do { _ = try await app.api.login(u, password); app.rememberPassword(u, password); password = ""; await app.backupNow() }
                            catch { app.show("Неверный пароль") }
                        }
                    }
                } else {
                    GlassButton(title: "Сохранить сейчас", systemImage: "arrow.clockwise") { Task { if await app.backupNow() { app.show("Копия сохранена") } } }
                }
            }.glassCard()

            SectionTitle("Обновления")
            VStack(alignment: .leading, spacing: 10) {
                Toggle("Проверять при запуске", isOn: $app.settings.autoUpdate).foregroundStyle(p.text)
                GlassButton(title: checking ? "Проверяем…" : "Проверить сейчас", systemImage: "arrow.down.circle") {
                    checking = true
                    Task {
                        if let u = await Updates.check() {
                            updateText = "Доступна \(u.version)"
                            if let url = URL(string: u.url) { await UIApplication.shared.open(url) }
                        } else { updateText = "У вас последняя версия \(Updates.currentVersion)" }
                        checking = false
                    }
                }
                if let updateText { Text(updateText).font(.caption).foregroundStyle(p.text2) }
            }.glassCard()

            SectionTitle("Опасная зона")
            GlassButton(title: "Удалить аккаунт", systemImage: "trash", role: .destructive) { confirmDelete = true }
                .confirmationDialog("Удалить аккаунт навсегда?", isPresented: $confirmDelete, titleVisibility: .visible) {
                    Button("Удалить", role: .destructive) { Task { do { try await app.deleteAccount() } catch { app.show(error.localizedDescription) } } }
                } message: { Text("Аккаунт, переписка и облачная копия будут удалены с сервера.") }
        }
    }

    private var backupLabel: String {
        switch app.backupState {
        case .off: return "Выключена"
        case .needsPassword: return "Нужен пароль"
        case .syncing: return "Сохраняем…"
        case .error: return "Ошибка"
        case .idle:
            guard app.lastBackupAt > 0 else { return "Включена" }
            let f = DateFormatter(); f.locale = Locale(identifier: "ru_RU"); f.dateFormat = "d MMM, HH:mm"
            return f.string(from: Date(timeIntervalSince1970: TimeInterval(app.lastBackupAt) / 1000))
        }
    }
}
