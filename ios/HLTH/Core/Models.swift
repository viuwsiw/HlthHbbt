import Foundation

// MARK: - Дни

/// День как в Android-версии: число дней с 1970-01-01 по локальному календарю.
typealias EpochDay = Int64

enum Days {
    static var cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.firstWeekday = 2
        c.minimumDaysInFirstWeek = 4
        return c
    }()

    static func of(_ date: Date) -> EpochDay {
        let comps = cal.dateComponents([.year, .month, .day], from: date)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let d = utc.date(from: comps)!
        return EpochDay(floor(d.timeIntervalSince1970 / 86400))
    }

    static var today: EpochDay { of(Date()) }

    static func date(_ day: EpochDay) -> Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let d = Date(timeIntervalSince1970: TimeInterval(day) * 86400)
        let c = utc.dateComponents([.year, .month, .day], from: d)
        return cal.date(from: c)!
    }

    /// 0 — понедельник … 6 — воскресенье.
    static func weekdayIndex(_ day: EpochDay) -> Int {
        // 1970-01-01 — четверг (индекс 3)
        Int(((day % 7) + 7 + 3) % 7)
    }

    static func isoWeek(_ day: EpochDay) -> (Int, Int) {
        let c = cal.dateComponents([.weekOfYear, .yearForWeekOfYear], from: date(day))
        return (c.weekOfYear ?? 0, c.yearForWeekOfYear ?? 0)
    }
}

func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

// MARK: - Сущности

enum HabitKind {
    static let good = "GOOD"
    static let quit = "QUIT"
    static let everyDay = 0b1111111
    static let metricSteps = "STEPS"
    static let metricSleep = "SLEEP"
    static let metricBedtime = "BEDTIME"
    static let activityWalk = "WALK"
}

struct Habit: Codable, Identifiable, Hashable {
    var id: Int64
    var title: String
    var emoji: String
    var colorIndex: Int = 0
    var goalPerDay: Int = 1
    var createdAt: Int64 = nowMs()
    var archived: Bool = false
    var scheduleMask: Int = HabitKind.everyDay
    var reminderMinutes: Int? = nil
    var kind: String = HabitKind.good
    var quitSince: Int64? = nil
    var healthMetric: String? = nil
    var healthTarget: Int? = nil
    var focusMinutes: Int? = nil
    var activity: String? = nil

    var isQuit: Bool { kind == HabitKind.quit }
    var createdDay: EpochDay { Days.of(Date(timeIntervalSince1970: TimeInterval(createdAt) / 1000)) }
    func scheduled(on day: EpochDay) -> Bool { !isQuit && isScheduled(scheduleMask, day) }
}

struct HabitCheck: Codable, Hashable { var habitId: Int64; var day: EpochDay; var count: Int }
struct Freeze: Codable, Hashable { var habitId: Int64; var day: EpochDay }
struct WaterLog: Codable, Hashable { var ml: Int; var day: EpochDay; var ts: Int64 }
struct DayNote: Codable, Hashable { var day: EpochDay; var mood: Int; var note: String }

struct WalkPoint: Codable, Hashable { var lat: Double; var lon: Double; var time: Int64 }
struct Walk: Codable, Identifiable, Hashable {
    var id = UUID()
    var startedAt: Int64
    var durationMs: Int64
    var distanceM: Double
    var steps: Int
    var points: [WalkPoint]
}

enum Gender: String, Codable, CaseIterable { case MALE, FEMALE }

struct Profile: Codable {
    var displayName: String = ""
    var age: Int? = nil
    var gender: Gender? = nil
    var waterGoalMl: Int = 2000
    var profileCompleted: Bool = false
    /// "preset:N" — встроенный аватар; фото хранится отдельно (avatarPhoto).
    var avatar: String? = nil
    var avatarPhoto: Data? = nil
}

enum Visibility: String, Codable, CaseIterable {
    case ALL, CONTACTS, NOBODY
    var title: String {
        switch self { case .ALL: return "Все"; case .CONTACTS: return "Контакты"; case .NOBODY: return "Никто" }
    }
}

struct Privacy: Codable, Equatable {
    var discoverable = true
    var bio = ""
    var streak: Visibility = .ALL
    var achievements: Visibility = .ALL
    var habits: Visibility = .CONTACTS
    var water: Visibility = .NOBODY
    var nowPlaying: Visibility = .ALL
}

struct AppSettings: Codable, Equatable {
    var themeMode: ThemeMode = .SYSTEM
    var accentIndex = 0
    var waterReminders = false
    var waterIntervalHours = 2
    var waterFromHour = 9
    var waterToHour = 21
    var health = false
    var autoUpdate = true
    var bugButton = false
    var animatedBackground = false
    var betaChannel = false
}

/// Все данные одного пользователя на устройстве.
struct UserData: Codable {
    var profile = Profile()
    var habits: [Habit] = []
    var checks: [HabitCheck] = []
    var freezes: [Freeze] = []
    var water: [WaterLog] = []
    var notes: [DayNote] = []
    var walks: [Walk] = []
    var privacy = Privacy()
    var nextHabitId: Int64 = 1
    var seeded = false
}

// MARK: - Логика привычек (как в Android)

let weekdayShort = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"]

func isScheduled(_ mask: Int, _ day: EpochDay) -> Bool { mask & (1 << Days.weekdayIndex(day)) != 0 }

func scheduleLabel(_ mask: Int) -> String {
    if mask & HabitKind.everyDay == HabitKind.everyDay { return "Каждый день" }
    if mask == 0b0011111 { return "По будням" }
    if mask == 0b1100000 { return "По выходным" }
    return weekdayShort.enumerated().filter { mask & (1 << $0.offset) != 0 }.map(\.element).joined(separator: ", ")
}

func habitStreak(done: Set<EpochDay>, frozen: Set<EpochDay>, mask: Int, today: EpochDay = Days.today, limit: Int = 400) -> Int {
    var day = today
    if !done.contains(day) { day -= 1 }
    var streak = 0
    var steps = 0
    while steps < limit {
        steps += 1
        if done.contains(day) { streak += 1 }
        else if !isScheduled(mask, day) || frozen.contains(day) { }
        else { break }
        day -= 1
    }
    return streak
}

func habitBestStreak(done: Set<EpochDay>, frozen: Set<EpochDay>, mask: Int, from: EpochDay, to: EpochDay) -> Int {
    guard from <= to else { return 0 }
    var best = 0, run = 0
    for day in from...to {
        if done.contains(day) { run += 1; best = max(best, run) }
        else if !isScheduled(mask, day) || frozen.contains(day) || day == to { }
        else { run = 0 }
    }
    return best
}

func freezeAvailable(_ frozen: Set<EpochDay>, today: EpochDay = Days.today) -> Bool {
    let w = Days.isoWeek(today)
    return !frozen.contains { Days.isoWeek($0) == w }
}

func formatDuration(_ ms: Int64) -> String {
    let totalMin = max(0, ms / 60_000)
    let days = totalMin / (60 * 24), hours = (totalMin / 60) % 24, mins = totalMin % 60
    if days > 0 { return "\(days) \(daysWord(Int(days))) \(hours) ч" }
    if hours > 0 { return "\(hours) ч \(mins) мин" }
    return "\(mins) мин"
}

func daysWord(_ n: Int) -> String {
    let m10 = n % 10, m100 = n % 100
    if m10 == 1 && m100 != 11 { return "день" }
    if (2...4).contains(m10) && !(12...14).contains(m100) { return "дня" }
    return "дней"
}

func formatMinutesOfDay(_ m: Int) -> String { String(format: "%02d:%02d", m / 60, m % 60) }

// MARK: - История и достижения

struct Achievement: Identifiable {
    let id: String, emoji: String, title: String, description: String
    let current: Int, target: Int
    var unlocked: Bool { current >= target }
    var progress: Double { min(1, max(0, Double(current) / Double(target))) }
}

struct History {
    let today: EpochDay
    let data: UserData
    let goodHabits: [Habit]
    let doneByHabit: [Int64: Set<EpochDay>]
    let frozenByHabit: [Int64: Set<EpochDay>]
    let waterByDay: [EpochDay: Int]

    init(_ d: UserData, today: EpochDay = Days.today) {
        self.today = today
        self.data = d
        goodHabits = d.habits.filter { !$0.isQuit && !$0.archived }
        var done: [Int64: Set<EpochDay>] = [:]
        let goal = Dictionary(uniqueKeysWithValues: d.habits.map { ($0.id, $0.goalPerDay) })
        for c in d.checks where c.count >= (goal[c.habitId] ?? 1) { done[c.habitId, default: []].insert(c.day) }
        doneByHabit = done
        var fr: [Int64: Set<EpochDay>] = [:]
        for f in d.freezes { fr[f.habitId, default: []].insert(f.day) }
        frozenByHabit = fr
        var w: [EpochDay: Int] = [:]
        for l in d.water { w[l.day, default: 0] += l.ml }
        waterByDay = w
    }

    func completion(_ day: EpochDay) -> Double? {
        let planned = goodHabits.filter { $0.createdDay <= day && $0.scheduled(on: day) }
        if planned.isEmpty { return nil }
        return Double(planned.filter { doneByHabit[$0.id]?.contains(day) == true }.count) / Double(planned.count)
    }

    func streak(_ h: Habit) -> Int {
        habitStreak(done: doneByHabit[h.id] ?? [], frozen: frozenByHabit[h.id] ?? [], mask: h.scheduleMask, today: today)
    }

    var currentStreak: Int { goodHabits.map { streak($0) }.max() ?? 0 }

    var bestStreakEver: Int {
        goodHabits.map { habitBestStreak(done: doneByHabit[$0.id] ?? [], frozen: frozenByHabit[$0.id] ?? [], mask: $0.scheduleMask, from: $0.createdDay, to: today) }.max() ?? 0
    }

    var achievements: [Achievement] {
        let best = bestStreakEver
        let totalDone = doneByHabit.values.reduce(0) { $0 + $1.count }
        let goal = data.profile.waterGoalMl
        let waterGoalDays = waterByDay.values.filter { $0 >= goal }.count
        let waterLiters = waterByDay.values.reduce(0, +) / 1000
        let firstDay = goodHabits.map(\.createdDay).min() ?? today
        let start = max(firstDay, today - 365)
        let perfectDays = start <= today ? (start...today).filter { completion($0) == 1 }.count : 0
        let quitDays = data.habits.filter(\.isQuit).map { Int((nowMs() - ($0.quitSince ?? nowMs())) / 86_400_000) }.max() ?? 0
        let moodNotes = data.notes.filter { $0.mood > 0 }.count
        return [
            Achievement(id: "first_step", emoji: "🌱", title: "Первый шаг", description: "Выполните привычку впервые", current: totalDone, target: 1),
            Achievement(id: "streak_3", emoji: "🔥", title: "Разогрев", description: "Серия из 3 дней", current: best, target: 3),
            Achievement(id: "streak_7", emoji: "⚡", title: "Неделя силы", description: "Серия из 7 дней", current: best, target: 7),
            Achievement(id: "streak_30", emoji: "🏆", title: "Месяц дисциплины", description: "Серия из 30 дней", current: best, target: 30),
            Achievement(id: "streak_100", emoji: "💎", title: "Сотня", description: "Серия из 100 дней", current: best, target: 100),
            Achievement(id: "perfect_day", emoji: "⭐", title: "Идеальный день", description: "Выполните все привычки за день", current: perfectDays, target: 1),
            Achievement(id: "perfect_7", emoji: "🌟", title: "Семь звёзд", description: "7 идеальных дней", current: perfectDays, target: 7),
            Achievement(id: "water_1", emoji: "💧", title: "Водный баланс", description: "Выполните цель по воде", current: waterGoalDays, target: 1),
            Achievement(id: "water_7", emoji: "🌊", title: "Гидратация", description: "Цель по воде 7 дней", current: waterGoalDays, target: 7),
            Achievement(id: "water_100", emoji: "🐳", title: "Сто литров", description: "Выпейте 100 литров воды", current: waterLiters, target: 100),
            Achievement(id: "habits_5", emoji: "🧩", title: "Коллекционер", description: "Заведите 5 привычек", current: data.habits.count, target: 5),
            Achievement(id: "checks_100", emoji: "💯", title: "Сотня отметок", description: "100 выполнений привычек", current: totalDone, target: 100),
            Achievement(id: "quit_7", emoji: "🕊️", title: "Свобода", description: "7 дней без вредной привычки", current: quitDays, target: 7),
            Achievement(id: "quit_30", emoji: "🦅", title: "Новая жизнь", description: "30 дней без вредной привычки", current: quitDays, target: 30),
            Achievement(id: "notes_7", emoji: "📔", title: "Дневник", description: "Отметьте настроение 7 раз", current: moodNotes, target: 7),
        ]
    }
}

let dailyTips = [
    "Стакан воды сразу после пробуждения запускает обмен веществ.",
    "Маленький шаг каждый день важнее большого рывка раз в месяц.",
    "10 минут прогулки после еды помогают держать сахар ровным.",
    "Привяжите новую привычку к уже существующей: «после кофе — 5 приседаний».",
    "Экран за час до сна мешает выработке мелатонина.",
    "Не рвите цепочку дважды: один пропуск — случайность, два — новая привычка.",
    "Отмечайте настроение — так проще заметить, что на него влияет.",
    "Глубокий вдох на 4 счёта и выдох на 6 снижают стресс за минуту.",
    "Ставьте бутылку воды на видное место — пить будете больше.",
    "Сон в одно и то же время важнее его продолжительности.",
]
