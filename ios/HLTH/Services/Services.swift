import Foundation
import HealthKit
import CoreMotion
import UserNotifications
import MediaPlayer
import UIKit

// MARK: - Здоровье: шаги и сон

@MainActor
final class Health: ObservableObject {
    static let shared = Health()
    @Published var steps: Int?
    @Published var sleepMinutes: Int?
    @Published var authorized = UserDefaults.standard.bool(forKey: "health_asked")

    private let store = HKHealthStore()
    private let pedometer = CMPedometer()

    func request() async {
        if HKHealthStore.isHealthDataAvailable() {
            let types: Set<HKObjectType> = [HKQuantityType(.stepCount), HKCategoryType(.sleepAnalysis)]
            try? await store.requestAuthorization(toShare: [], read: types)
        }
        UserDefaults.standard.set(true, forKey: "health_asked")
        authorized = true
        await refresh()
    }

    func refresh() async {
        guard authorized else { return }
        let start = Calendar.current.startOfDay(for: Date())
        var s = await healthSteps(since: start)
        if s == nil || s == 0 { s = await pedometerSteps(since: start) }
        steps = s
        sleepMinutes = await lastNightSleep()
        AppModel.shared.applyHealth(steps: steps, sleepMinutes: sleepMinutes)
    }

    /// Настроение → «Здоровье» (Состояние духа, iOS 18+). Эти данные общие с приложением «Дневник».
    func saveMood(_ mood: Int) async {
        guard UserDefaults.standard.bool(forKey: "mood_to_health"), (1...5).contains(mood), HKHealthStore.isHealthDataAvailable() else { return }
        if #available(iOS 18.0, *) {
            let type = HKSampleType.stateOfMindType()
            try? await store.requestAuthorization(toShare: [type], read: [type])
            let labels: [[HKStateOfMind.Label]] = [[.sad], [.discouraged], [.indifferent], [.content], [.happy]]
            let sample = HKStateOfMind(date: Date(), kind: .dailyMood, valence: Double(mood - 3) / 2,
                                       labels: labels[mood - 1], associations: [.selfCare])
            do {
                try await store.save(sample)
            } catch {
                AppModel.shared.show("Не удалось записать настроение в «Здоровье»")
            }
        }
    }

    private func healthSteps(since start: Date) async -> Int? {
        guard HKHealthStore.isHealthDataAvailable() else { return nil }
        return await withCheckedContinuation { cont in
            let pred = HKQuery.predicateForSamples(withStart: start, end: Date())
            let q = HKStatisticsQuery(quantityType: HKQuantityType(.stepCount), quantitySamplePredicate: pred, options: .cumulativeSum) { _, r, _ in
                cont.resume(returning: r?.sumQuantity().map { Int($0.doubleValue(for: .count())) })
            }
            store.execute(q)
        }
    }

    private func pedometerSteps(since start: Date) async -> Int? {
        guard CMPedometer.isStepCountingAvailable() else { return nil }
        return await withCheckedContinuation { cont in
            pedometer.queryPedometerData(from: start, to: Date()) { d, _ in cont.resume(returning: d?.numberOfSteps.intValue) }
        }
    }

    private func lastNightSleep() async -> Int? {
        guard HKHealthStore.isHealthDataAvailable() else { return nil }
        let end = Date()
        let start = Calendar.current.date(byAdding: .hour, value: -20, to: Calendar.current.startOfDay(for: end))!
        return await withCheckedContinuation { cont in
            let pred = HKQuery.predicateForSamples(withStart: start, end: end)
            let q = HKSampleQuery(sampleType: HKCategoryType(.sleepAnalysis), predicate: pred, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { _, samples, _ in
                let asleep = (samples as? [HKCategorySample] ?? []).filter {
                    $0.value != HKCategoryValueSleepAnalysis.inBed.rawValue && $0.value != HKCategoryValueSleepAnalysis.awake.rawValue
                }
                let total = asleep.reduce(0.0) { $0 + $1.endDate.timeIntervalSince($1.startDate) }
                cont.resume(returning: total > 0 ? Int(total / 60) : nil)
            }
            store.execute(q)
        }
    }
}

// MARK: - Напоминания

final class Reminders {
    static let shared = Reminders()

    func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    func reschedule(_ habits: [Habit], settings: AppSettings) {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { reqs in
            center.removePendingNotificationRequests(withIdentifiers: reqs.map(\.identifier).filter { $0.hasPrefix("habit_") || $0.hasPrefix("water_") })
            for h in habits where !h.archived {
                guard let m = h.reminderMinutes else { continue }
                for wd in 0..<7 where h.scheduleMask & (1 << wd) != 0 {
                    var dc = DateComponents()
                    dc.hour = m / 60; dc.minute = m % 60
                    dc.weekday = wd == 6 ? 1 : wd + 2 // 1 — воскресенье
                    let c = UNMutableNotificationContent()
                    c.title = "\(h.emoji) \(h.title)"
                    c.body = "Время для привычки"
                    c.sound = .default
                    center.add(UNNotificationRequest(identifier: "habit_\(h.id)_\(wd)", content: c, trigger: UNCalendarNotificationTrigger(dateMatching: dc, repeats: true)))
                }
            }
            if settings.waterReminders {
                var hour = settings.waterFromHour
                while hour <= settings.waterToHour {
                    var dc = DateComponents(); dc.hour = hour; dc.minute = 0
                    let c = UNMutableNotificationContent()
                    c.title = "💧 Пора выпить воды"
                    c.body = "Стакан воды — и цель ближе"
                    c.sound = .default
                    center.add(UNNotificationRequest(identifier: "water_\(hour)", content: c, trigger: UNCalendarNotificationTrigger(dateMatching: dc, repeats: true)))
                    hour += max(1, settings.waterIntervalHours)
                }
            }
        }
    }
}

// MARK: - Что играет («Музыка» на iPhone)

@MainActor
final class NowPlaying: ObservableObject {
    static let shared = NowPlaying()
    @Published var track: Track?
    private var timer: Timer?

    func start() {
        guard timer == nil else { return }
        MPMediaLibrary.requestAuthorization { _ in }
        timer = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { _ in
            Task { @MainActor in await NowPlaying.shared.poll() }
        }
        Task { await poll() }
    }

    func poll() async {
        var t: Track? = nil
        if let sp = await Spotify.shared.currentTrack() {
            t = sp
        } else if MPMediaLibrary.authorizationStatus() == .authorized {
            let player = MPMusicPlayerController.systemMusicPlayer
            if player.playbackState == .playing, let item = player.nowPlayingItem {
                t = Track(title: item.title ?? "", artist: item.artist ?? "", app: "Apple Music")
            }
        }
        if t?.title.isEmpty == true { t = nil }
        if t != track { track = t }
        AppModel.shared.messenger.shareNowPlaying(title: t?.title ?? "", artist: t?.artist ?? "", app: t?.app ?? "")
    }
}

// MARK: - Обновления (GitHub)

struct IosUpdate { let version: String; let build: Int; let url: String; let changelog: String }

enum Updates {
    static let repo = "viuwsiw/HlthHbbt"
    static let branch = "claude/quirky-goodall-is99zk"

    static var currentBuild: Int { Int(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0") ?? 0 }
    static var currentVersion: String { Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?" }

    /// Последняя версия на GitHub (даже если не новее установленной).
    static func latest() async -> IosUpdate? {
        guard let url = URL(string: "https://api.github.com/repos/\(repo)/contents/update-ios.json?ref=\(branch)") else { return nil }
        var req = URLRequest(url: url)
        req.setValue("application/vnd.github.raw+json", forHTTPHeaderField: "Accept")
        req.cachePolicy = .reloadIgnoringLocalCacheData
        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let j = (try? JSONSerialization.jsonObject(with: data)) as? JSON,
              let build = j["build"] as? Int else { return nil }
        return IosUpdate(version: j["versionName"] as? String ?? "", build: build, url: j["ipaUrl"] as? String ?? "", changelog: j["changelog"] as? String ?? "")
    }

    /// Подписать IPA обновления своим сертификатом на сервере и установить «по воздуху».
    @MainActor
    static func signAndInstall(_ u: IosUpdate) async throws {
        let bundle = Bundle.main.bundleIdentifier ?? "com.tshk.hlthhbbty"
        let r = try await AppModel.shared.api.sign(ipaUrl: u.url, bundleId: bundle, version: u.version)
        guard let s = r["installUrl"] as? String, let url = URL(string: s) else { throw ApiError(code: 0, error: "sign", message: "Сервер не вернул ссылку установки") }
        await UIApplication.shared.open(url)
    }

    static func check() async -> IosUpdate? {
        guard let url = URL(string: "https://api.github.com/repos/\(repo)/contents/update-ios.json?ref=\(branch)") else { return nil }
        var req = URLRequest(url: url)
        req.setValue("application/vnd.github.raw+json", forHTTPHeaderField: "Accept")
        req.cachePolicy = .reloadIgnoringLocalCacheData
        guard let (data, _) = try? await URLSession.shared.data(for: req),
              let j = (try? JSONSerialization.jsonObject(with: data)) as? JSON,
              let build = j["build"] as? Int, build > currentBuild else { return nil }
        return IosUpdate(version: j["versionName"] as? String ?? "", build: build, url: j["ipaUrl"] as? String ?? "", changelog: j["changelog"] as? String ?? "")
    }
}
