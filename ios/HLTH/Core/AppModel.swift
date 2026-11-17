import Foundation
import SwiftUI
import CryptoKit
import CommonCrypto
import UIKit

enum BackupState { case off, needsPassword, idle, syncing, error }

/// Состояние приложения: сессия, данные пользователя, настройки, облачная копия.
@MainActor
final class AppModel: ObservableObject {
    static let shared = AppModel()

    let api = Api()
    @Published var settings: AppSettings { didSet { saveSettings(); backupSoon() } }
    @Published private(set) var username: String?
    @Published var data = UserData() { didSet { scheduleSave() } }
    @Published var backupState: BackupState = .off
    @Published var lastBackupAt: Int64 = Int64(UserDefaults.standard.double(forKey: "last_backup_at"))
    @Published var toast: String?

    lazy var messenger = Messenger(app: self)

    let deviceId: String = {
        if let d = UserDefaults.standard.string(forKey: "device_id") { return d }
        let d = UUID().uuidString.lowercased()
        UserDefaults.standard.set(d, forKey: "device_id")
        return d
    }()

    private init() {
        if let raw = UserDefaults.standard.data(forKey: "settings"), let s = try? JSONDecoder().decode(AppSettings.self, from: raw) {
            settings = s
        } else {
            settings = AppSettings()
        }
        if let u = UserDefaults.standard.string(forKey: "session_user"), let t = Keychain.get("token_\(u)").flatMap({ String(data: $0, encoding: .utf8) }) {
            username = u
            api.token = t
            loadData(u)
            refreshBackupState()
        }
    }

    func start() {
        guard username != nil else { return }
        messenger.start()
    }

    var palette: Palette {
        let dark: Bool
        switch settings.themeMode {
        case .SYSTEM: dark = UITraitCollection.current.userInterfaceStyle == .dark
        case .LIGHT: dark = false
        case .DARK, .BLACK: dark = true
        }
        return Palette.make(dark: dark, black: settings.themeMode == .BLACK, accentIndex: settings.accentIndex)
    }

    func show(_ msg: String) { toast = msg }

    // MARK: - Хранение

    private var saveTask: Task<Void, Never>?
    private var loading = false

    private func fileURL(_ u: String) -> URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("user_\(u).json")
    }

    private func loadData(_ u: String) {
        loading = true
        if let raw = try? Data(contentsOf: fileURL(u)), let d = try? JSONDecoder().decode(UserData.self, from: raw) {
            data = d
        } else {
            data = UserData()
        }
        loading = false
    }

    private func scheduleSave() {
        guard !loading, let u = username else { return }
        saveTask?.cancel()
        let snapshot = data
        saveTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 400_000_000)
            if Task.isCancelled { return }
            if let raw = try? JSONEncoder().encode(snapshot) { try? raw.write(to: self?.fileURL(u) ?? URL(fileURLWithPath: "/dev/null"), options: .atomic) }
            self?.backupSoon()
        }
    }

    private func saveSettings() {
        if let raw = try? JSONEncoder().encode(settings) { UserDefaults.standard.set(raw, forKey: "settings") }
    }

    // MARK: - Вход и регистрация

    static func validateUsername(_ l: String) -> String? {
        if l.count < 3 { return "Логин — минимум 3 символа" }
        if l.count > 24 { return "Логин — максимум 24 символа" }
        if l.range(of: "^[a-z0-9_.]{3,24}$", options: .regularExpression) == nil { return "Только латиница, цифры, «_» и «.»" }
        return nil
    }

    static func validatePassword(_ p: String) -> String? {
        if p.count < 6 { return "Пароль — минимум 6 символов" }
        if !p.contains(where: \.isNumber) || !p.contains(where: \.isLetter) { return "Пароль должен содержать буквы и цифры" }
        return nil
    }

    func register(username raw: String, password: String, displayName: String) async throws {
        let u = raw.trimmingCharacters(in: .whitespaces).lowercased()
        if let e = Self.validateUsername(u) ?? Self.validatePassword(password) { throw ApiError(code: 0, error: "validation", message: e) }
        let name = displayName.trimmingCharacters(in: .whitespaces).isEmpty ? u : displayName.trimmingCharacters(in: .whitespaces)
        let token = try await api.register(u, password, name, deviceId: deviceId)
        await finishSignIn(u, token: token, password: password, displayName: name, isNew: true)
    }

    func login(username raw: String, password: String) async throws {
        let u = raw.trimmingCharacters(in: .whitespaces).lowercased()
        let token = try await api.login(u, password)
        await finishSignIn(u, token: token, password: password, displayName: nil, isNew: false)
    }

    private func finishSignIn(_ u: String, token: String, password: String, displayName: String?, isNew: Bool) async {
        Keychain.set(Data(token.utf8), "token_\(u)")
        UserDefaults.standard.set(u, forKey: "session_user")
        api.token = token
        rememberPassword(u, password)
        loadData(u)
        username = u
        var restored = false
        if !isNew, !FileManager.default.fileExists(atPath: fileURL(u).path) || !data.seeded {
            restored = await restore(u, password: password)
        }
        if !restored && !data.seeded {
            if data.profile.displayName.isEmpty {
                let me = try? await api.me()
                data.profile.displayName = displayName ?? (me?["displayName"] as? String) ?? u
                data.privacy.bio = me?["bio"] as? String ?? ""
            }
            seedDefaults()
        }
        refreshBackupState()
        messenger.onSignedIn()
        backupSoon()
    }

    func logout() async {
        guard let u = username else { return }
        await backupNow()
        messenger.stop()
        await api.logout()
        Keychain.set(nil, "token_\(u)")
        UserDefaults.standard.removeObject(forKey: "session_user")
        api.token = nil
        username = nil
        loading = true
        data = UserData()
        loading = false
        backupState = .off
    }

    func deleteAccount() async throws {
        guard let u = username else { return }
        try await api.deleteAccount()
        messenger.stop()
        try? FileManager.default.removeItem(at: fileURL(u))
        Keychain.set(nil, "token_\(u)")
        Keychain.set(nil, "backup_\(u)")
        UserDefaults.standard.removeObject(forKey: "session_user")
        api.token = nil
        username = nil
    }

    func changePassword(current: String, new: String) async throws {
        if let e = Self.validatePassword(new) { throw ApiError(code: 0, error: "validation", message: e) }
        try await api.changePassword(current: current, new: new)
        if let u = username { rememberPassword(u, new); backupSoon() }
    }

    private func seedDefaults() {
        func add(_ title: String, _ emoji: String, _ color: Int, focus: Int? = nil, reminder: Int? = nil, metric: String? = nil, target: Int? = nil, activity: String? = nil) {
            data.habits.append(Habit(id: data.nextHabitId, title: title, emoji: emoji, colorIndex: color, reminderMinutes: reminder, healthMetric: metric, healthTarget: target, focusMinutes: focus, activity: activity))
            data.nextHabitId += 1
        }
        add("Прогулка на свежем воздухе", "🚶", 0, activity: HabitKind.activityWalk)
        add("Хобби", "🎨", 2, focus: 30)
        add("Зарядка", "🤸", 3, reminder: 8 * 60)
        add("Лечь спать до 23:00", "🌙", 4, metric: HabitKind.metricBedtime, target: 23 * 60)
        data.seeded = true
    }

    // MARK: - Привычки, вода, заметки

    var history: History { History(data) }

    func count(_ habit: Habit, _ day: EpochDay = Days.today) -> Int {
        data.checks.first { $0.habitId == habit.id && $0.day == day }?.count ?? 0
    }

    func isDone(_ habit: Habit, _ day: EpochDay = Days.today) -> Bool { count(habit, day) >= habit.goalPerDay }

    /// Нажатие на привычку: +1 до цели, после цели — сброс.
    func tap(_ habit: Habit, _ day: EpochDay = Days.today) {
        let c = count(habit, day)
        setCount(habit, day, c >= habit.goalPerDay ? 0 : c + 1)
        if c + 1 == habit.goalPerDay { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
    }

    func setCount(_ habit: Habit, _ day: EpochDay, _ n: Int) {
        data.checks.removeAll { $0.habitId == habit.id && $0.day == day }
        if n > 0 { data.checks.append(HabitCheck(habitId: habit.id, day: day, count: n)) }
        messenger.pushProfileSoon()
    }

    func saveHabit(_ h: Habit) {
        var h = h
        if h.id == 0 {
            h.id = data.nextHabitId
            data.nextHabitId += 1
            data.habits.append(h)
        } else if let i = data.habits.firstIndex(where: { $0.id == h.id }) {
            data.habits[i] = h
        }
        Reminders.shared.reschedule(data.habits, settings: settings)
    }

    func deleteHabit(_ h: Habit) {
        data.habits.removeAll { $0.id == h.id }
        data.checks.removeAll { $0.habitId == h.id }
        data.freezes.removeAll { $0.habitId == h.id }
        Reminders.shared.reschedule(data.habits, settings: settings)
    }

    func freeze(_ h: Habit) {
        let frozen = Set(data.freezes.filter { $0.habitId == h.id }.map(\.day))
        guard freezeAvailable(frozen) else { show("Заморозка доступна раз в неделю"); return }
        data.freezes.append(Freeze(habitId: h.id, day: Days.today))
        show("❄️ Сегодня не прервёт серию")
    }

    func resetQuit(_ h: Habit) {
        var h = h
        h.quitSince = nowMs()
        saveHabit(h)
    }

    var waterToday: Int { data.water.filter { $0.day == Days.today }.reduce(0) { $0 + $1.ml } }

    func addWater(_ ml: Int) {
        data.water.append(WaterLog(ml: ml, day: Days.today, ts: nowMs()))
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        messenger.pushProfileSoon()
    }

    func undoWater() {
        if let i = data.water.lastIndex(where: { $0.day == Days.today }) { data.water.remove(at: i) }
    }

    func note(_ day: EpochDay) -> DayNote? { data.notes.first { $0.day == day } }

    func saveNote(_ day: EpochDay, mood: Int, text: String) {
        data.notes.removeAll { $0.day == day }
        data.notes.append(DayNote(day: day, mood: mood, note: text))
        if day == Days.today { Task { await Health.shared.saveMood(mood) } }
    }

    /// Автоотметка привычек по данным «Здоровья».
    func applyHealth(steps: Int?, sleepMinutes: Int?) {
        for h in data.habits where !h.archived {
            guard let metric = h.healthMetric, let target = h.healthTarget else { continue }
            if metric == HabitKind.metricSteps, let s = steps, s >= target, !isDone(h) { setCount(h, Days.today, h.goalPerDay) }
            if metric == HabitKind.metricSleep, let s = sleepMinutes, s >= target, !isDone(h) { setCount(h, Days.today, h.goalPerDay) }
        }
    }

    /// «Лечь спать до N»: если приложение открыли позже N — отметка снимается, иначе ставится утром.
    func checkBedtime() {
        let now = Date()
        let comps = Days.cal.dateComponents([.hour, .minute], from: now)
        let minutes = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)
        for h in data.habits where h.healthMetric == HabitKind.metricBedtime && !h.archived {
            let target = h.healthTarget ?? 23 * 60
            let lateKey = "bed_late_\(h.id)_\(Days.today)"
            if minutes >= target {
                UserDefaults.standard.set(true, forKey: "bed_late_\(h.id)_\(Days.today + 1)")
                setCount(h, Days.today + 1, 0)
            } else if minutes >= 4 * 60, minutes < 12 * 60, !UserDefaults.standard.bool(forKey: lateKey), !isDone(h) {
                setCount(h, Days.today, h.goalPerDay)
            }
        }
    }

    // MARK: - Профиль

    func saveProfile(_ p: Profile, bio: String) {
        data.profile = p
        data.privacy.bio = String(bio.prefix(160))
        messenger.pushProfileSoon()
    }

    func savePrivacy(_ p: Privacy) {
        data.privacy = p
        messenger.pushProfileSoon()
    }

    // MARK: - Облачная копия (совместима с Android)

    private func deriveKey(_ u: String, _ password: String) -> Data {
        var out = Data(count: 32)
        let salt = Data("hlth-backup-v1:\(u)".utf8)
        let pw = Array(password.utf8)
        _ = out.withUnsafeMutableBytes { o in
            salt.withUnsafeBytes { s in
                CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),
                                     pw.map { Int8(bitPattern: $0) }, pw.count,
                                     s.bindMemory(to: UInt8.self).baseAddress, salt.count,
                                     CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 150_000,
                                     o.bindMemory(to: UInt8.self).baseAddress, 32)
            }
        }
        return out
    }

    func rememberPassword(_ u: String, _ password: String) {
        Keychain.set(deriveKey(u, password), "backup_\(u)")
        refreshBackupState()
    }

    func refreshBackupState() {
        guard let u = username else { backupState = .off; return }
        backupState = Keychain.get("backup_\(u)") == nil ? .needsPassword : .idle
    }

    private func encryptBlob(_ key: Data, _ plain: Data, _ u: String) throws -> Data {
        let box = try AES.GCM.seal(plain, using: SymmetricKey(data: key), authenticating: Data(u.utf8))
        return Data("HBK1".utf8) + box.combined!
    }

    private func decryptBlob(_ key: Data, _ blob: Data, _ u: String) throws -> Data {
        guard blob.count > 32, blob.prefix(4) == Data("HBK1".utf8) else { throw E2EError.badCiphertext }
        let box = try AES.GCM.SealedBox(combined: blob.dropFirst(4))
        return try AES.GCM.open(box, using: SymmetricKey(data: key), authenticating: Data(u.utf8))
    }

    private var backupTask: Task<Void, Never>?

    func backupSoon() {
        guard username != nil else { return }
        backupTask?.cancel()
        backupTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 15_000_000_000)
            if Task.isCancelled { return }
            await self?.backupNow()
        }
    }

    @discardableResult
    func backupNow() async -> Bool {
        guard let u = username else { return false }
        guard let key = Keychain.get("backup_\(u)") else { backupState = .needsPassword; return false }
        backupState = .syncing
        do {
            let json = buildBackup(u)
            let raw = try JSONSerialization.data(withJSONObject: json)
            try await api.putBackup(try encryptBlob(key, raw, u))
            lastBackupAt = nowMs()
            UserDefaults.standard.set(Double(lastBackupAt), forKey: "last_backup_at")
            backupState = .idle
            return true
        } catch {
            backupState = .error
            return false
        }
    }

    private func buildBackup(_ u: String) -> JSON {
        let s = settings, p = data.profile, pv = data.privacy
        var avatar: Any = NSNull()
        if let photo = p.avatarPhoto, let jpeg = Self.jpeg512(photo) { avatar = "jpeg:" + jpeg.base64EncodedString() }
        else if let a = p.avatar, a.hasPrefix("preset:") { avatar = a }
        return [
            "format": "hlth-cloud", "v": 1, "createdAt": nowMs(), "platform": "ios",
            "settings": [
                "themeMode": s.themeMode.rawValue, "accentIndex": s.accentIndex,
                "waterReminders": s.waterReminders, "waterIntervalHours": s.waterIntervalHours,
                "waterFromHour": s.waterFromHour, "waterToHour": s.waterToHour,
                "animatedBackground": s.animatedBackground, "bugButton": s.bugButton,
                "betaChannel": s.betaChannel, "autoUpdate": s.autoUpdate,
            ] as JSON,
            "profile": [
                "displayName": p.displayName, "age": p.age.map { $0 as Any } ?? NSNull(),
                "gender": p.gender.map { $0.rawValue as Any } ?? NSNull(), "waterGoalMl": p.waterGoalMl,
                "profileCompleted": p.profileCompleted, "avatar": avatar,
            ] as JSON,
            "privacy": [
                "discoverable": pv.discoverable, "bio": pv.bio, "streak": pv.streak.rawValue,
                "achievements": pv.achievements.rawValue, "habits": pv.habits.rawValue,
                "water": pv.water.rawValue, "nowPlaying": pv.nowPlaying.rawValue,
            ] as JSON,
            "myStatus": messenger.myStatus,
            "data": exportJson(),
            "walks": data.walks.map { w -> JSON in
                ["startedAt": w.startedAt, "durationMs": w.durationMs, "distanceM": w.distanceM, "steps": w.steps,
                 "points": w.points.map { [$0.lat, $0.lon, Double($0.time)] }]
            },
            "e2eKey": E2E.exportPrivateKey(u)?.base64EncodedString() ?? "",
        ]
    }

    /// Формат «hlth-hbbty» v2 — как экспорт в Android.
    func exportJson() -> JSON {
        let p = data.profile
        return [
            "format": "hlth-hbbty", "version": 2, "exportedAt": nowMs(),
            "profile": ["displayName": p.displayName, "age": p.age.map { $0 as Any } ?? NSNull(),
                        "gender": p.gender.map { $0.rawValue as Any } ?? NSNull(), "waterGoalMl": p.waterGoalMl] as JSON,
            "habits": data.habits.map { h -> JSON in
                func n<T>(_ v: T?) -> Any { v.map { $0 as Any } ?? NSNull() }
                return ["id": h.id, "title": h.title, "emoji": h.emoji, "colorIndex": h.colorIndex, "goalPerDay": h.goalPerDay,
                        "createdAt": h.createdAt, "archived": h.archived, "scheduleMask": h.scheduleMask,
                        "reminderMinutes": n(h.reminderMinutes), "kind": h.kind, "quitSince": n(h.quitSince),
                        "healthMetric": n(h.healthMetric), "healthTarget": n(h.healthTarget),
                        "focusMinutes": n(h.focusMinutes), "activity": n(h.activity)]
            },
            "checks": data.checks.map { c -> JSON in ["habitId": c.habitId, "day": c.day, "count": c.count] },
            "freezes": data.freezes.map { f -> JSON in ["habitId": f.habitId, "day": f.day] },
            "water": data.water.map { w -> JSON in ["ml": w.ml, "day": w.day, "ts": w.ts] },
            "notes": data.notes.map { n -> JSON in ["day": n.day, "mood": n.mood, "note": n.note] },
        ]
    }

    static func jpeg512(_ data: Data) -> Data? {
        guard let img = UIImage(data: data) else { return nil }
        let scale = min(1, 512 / max(img.size.width, img.size.height))
        let size = CGSize(width: img.size.width * scale, height: img.size.height * scale)
        let out = UIGraphicsImageRenderer(size: size).image { _ in img.draw(in: CGRect(origin: .zero, size: size)) }
        return out.jpegData(compressionQuality: 0.82)
    }

    private func restore(_ u: String, password: String) async -> Bool {
        let key = deriveKey(u, password)
        guard let blob = try? await api.getBackup(),
              let plain = try? decryptBlob(key, blob, u),
              let j = (try? JSONSerialization.jsonObject(with: plain)) as? JSON else { return false }
        apply(j, u)
        return true
    }

    private func apply(_ j: JSON, _ u: String) {
        if let st = j["settings"] as? JSON {
            var s = settings
            if let t = st["themeMode"] as? String, let m = ThemeMode(rawValue: t) { s.themeMode = m }
            s.accentIndex = st["accentIndex"] as? Int ?? s.accentIndex
            s.waterReminders = st["waterReminders"] as? Bool ?? s.waterReminders
            s.waterIntervalHours = st["waterIntervalHours"] as? Int ?? s.waterIntervalHours
            s.waterFromHour = st["waterFromHour"] as? Int ?? s.waterFromHour
            s.waterToHour = st["waterToHour"] as? Int ?? s.waterToHour
            s.animatedBackground = st["animatedBackground"] as? Bool ?? s.animatedBackground
            s.betaChannel = st["betaChannel"] as? Bool ?? s.betaChannel
            s.autoUpdate = st["autoUpdate"] as? Bool ?? s.autoUpdate
            settings = s
        }
        var d = UserData()
        if let dj = j["data"] as? JSON { importJson(dj, into: &d) }
        if let pr = j["profile"] as? JSON {
            d.profile.displayName = pr["displayName"] as? String ?? d.profile.displayName
            d.profile.age = pr["age"] as? Int
            d.profile.gender = (pr["gender"] as? String).flatMap(Gender.init(rawValue:))
            d.profile.waterGoalMl = pr["waterGoalMl"] as? Int ?? d.profile.waterGoalMl
            d.profile.profileCompleted = pr["profileCompleted"] as? Bool ?? true
            if let a = pr["avatar"] as? String {
                if a.hasPrefix("preset:") { d.profile.avatar = a }
                if a.hasPrefix("jpeg:") { d.profile.avatarPhoto = Data(base64Encoded: String(a.dropFirst(5))) }
            }
        }
        if let pv = j["privacy"] as? JSON {
            func vis(_ k: String, _ def: Visibility) -> Visibility { (pv[k] as? String).flatMap(Visibility.init(rawValue:)) ?? def }
            d.privacy = Privacy(discoverable: pv["discoverable"] as? Bool ?? true, bio: pv["bio"] as? String ?? "",
                                streak: vis("streak", .ALL), achievements: vis("achievements", .ALL),
                                habits: vis("habits", .CONTACTS), water: vis("water", .NOBODY), nowPlaying: vis("nowPlaying", .ALL))
        }
        if let ms = j["myStatus"] as? String, ms == "ONLINE" || ms == "DND" { messenger.myStatus = ms }
        for w in j["walks"] as? [JSON] ?? [] {
            let pts = (w["points"] as? [[Double]] ?? []).compactMap { a -> WalkPoint? in
                a.count >= 3 ? WalkPoint(lat: a[0], lon: a[1], time: Int64(a[2])) : nil
            }
            d.walks.append(Walk(startedAt: (w["startedAt"] as? NSNumber)?.int64Value ?? 0, durationMs: (w["durationMs"] as? NSNumber)?.int64Value ?? 0,
                                distanceM: w["distanceM"] as? Double ?? 0, steps: w["steps"] as? Int ?? 0, points: pts))
        }
        if let k = j["e2eKey"] as? String, let kd = Data(base64Encoded: k), !kd.isEmpty { E2E.importPrivateKey(u, kd) }
        d.seeded = true
        data = d
    }

    func importJson(_ root: JSON, into d: inout UserData) {
        guard root["format"] as? String == "hlth-hbbty" else { return }
        func i64(_ v: Any?) -> Int64? { (v as? NSNumber)?.int64Value }
        if let p = root["profile"] as? JSON { d.profile.waterGoalMl = p["waterGoalMl"] as? Int ?? d.profile.waterGoalMl }
        var maxId: Int64 = 0
        d.habits = (root["habits"] as? [JSON] ?? []).compactMap { h in
            guard let id = i64(h["id"]), let title = h["title"] as? String else { return nil }
            maxId = max(maxId, id)
            return Habit(id: id, title: title, emoji: h["emoji"] as? String ?? "✅", colorIndex: h["colorIndex"] as? Int ?? 0,
                         goalPerDay: h["goalPerDay"] as? Int ?? 1, createdAt: i64(h["createdAt"]) ?? nowMs(),
                         archived: h["archived"] as? Bool ?? false, scheduleMask: h["scheduleMask"] as? Int ?? 127,
                         reminderMinutes: h["reminderMinutes"] as? Int, kind: h["kind"] as? String ?? "GOOD",
                         quitSince: i64(h["quitSince"]), healthMetric: h["healthMetric"] as? String,
                         healthTarget: h["healthTarget"] as? Int, focusMinutes: h["focusMinutes"] as? Int, activity: h["activity"] as? String)
        }
        d.nextHabitId = maxId + 1
        d.checks = (root["checks"] as? [JSON] ?? []).compactMap { c in
            guard let h = i64(c["habitId"]), let day = i64(c["day"]) else { return nil }
            return HabitCheck(habitId: h, day: day, count: c["count"] as? Int ?? 1)
        }
        d.freezes = (root["freezes"] as? [JSON] ?? []).compactMap { f in
            guard let h = i64(f["habitId"]), let day = i64(f["day"]) else { return nil }
            return Freeze(habitId: h, day: day)
        }
        d.water = (root["water"] as? [JSON] ?? []).compactMap { w in
            guard let ml = w["ml"] as? Int, let day = i64(w["day"]) else { return nil }
            return WaterLog(ml: ml, day: day, ts: i64(w["ts"]) ?? 0)
        }
        d.notes = (root["notes"] as? [JSON] ?? []).compactMap { n in
            guard let day = i64(n["day"]) else { return nil }
            return DayNote(day: day, mood: n["mood"] as? Int ?? 0, note: n["note"] as? String ?? "")
        }
    }
}
