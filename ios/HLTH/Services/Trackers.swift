import Foundation
import CoreLocation
import CoreMotion
import UserNotifications
import UIKit
import ActivityKit

// MARK: - Прогулка: GPS-маршрут, шаги, секундомер

@MainActor
final class WalkTracker: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = WalkTracker()
    @Published var running = false
    @Published var startedAt: Date?
    @Published var points: [CLLocationCoordinate2D] = []
    @Published var distance: Double = 0
    @Published var steps = 0
    @Published var current: CLLocationCoordinate2D?
    @Published var fixCount = 0

    private let manager = CLLocationManager()
    private let pedometer = CMPedometer()
    private var times: [Int64] = []
    private var last: CLLocation?
    var habitId: Int64?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 5
        manager.activityType = .fitness
    }

    func prepare() {
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    func start(habit: Int64?) {
        habitId = habit
        points = []; times = []; distance = 0; steps = 0; last = nil
        startedAt = Date()
        running = true
        manager.requestAlwaysAuthorization()
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
        manager.showsBackgroundLocationIndicator = true
        manager.startUpdatingLocation()
        if CMPedometer.isStepCountingAvailable() {
            pedometer.startUpdates(from: Date()) { [weak self] d, _ in
                let n = d?.numberOfSteps.intValue ?? 0
                Task { @MainActor in self?.steps = n }
            }
        }
    }

    func stop(save: Bool) {
        guard running, let started = startedAt else { return }
        running = false
        manager.allowsBackgroundLocationUpdates = false
        pedometer.stopUpdates()
        if save {
            let walk = Walk(startedAt: Int64(started.timeIntervalSince1970 * 1000), durationMs: Int64(Date().timeIntervalSince(started) * 1000),
                            distanceM: distance, steps: steps,
                            points: zip(points, times).map { WalkPoint(lat: $0.0.latitude, lon: $0.0.longitude, time: $0.1) })
            let app = AppModel.shared
            app.data.walks.append(walk)
            if let hid = habitId, let h = app.data.habits.first(where: { $0.id == hid }), !app.isDone(h) {
                app.setCount(h, Days.today, h.goalPerDay)
            }
            app.show("Прогулка сохранена: \(String(format: "%.2f", distance / 1000)) км")
        }
        startedAt = nil
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            for loc in locations {
                self.current = loc.coordinate
                self.fixCount += 1
                guard self.running, loc.horizontalAccuracy >= 0, loc.horizontalAccuracy < 40 else { continue }
                if let l = self.last { self.distance += loc.distance(from: l) }
                self.last = loc
                self.points.append(loc.coordinate)
                self.times.append(Int64(loc.timestamp.timeIntervalSince1970 * 1000))
            }
        }
    }
}

// MARK: - Таймер фокуса (сохраняется при перезапуске, Live Activity на экране блокировки и в Dynamic Island)

@MainActor
final class FocusTimer: ObservableObject {
    static let shared = FocusTimer()
    @Published var endsAt: Date? { didSet { persist() } }
    @Published var pausedLeft: TimeInterval? { didSet { persist() } }
    @Published var total: TimeInterval = UserDefaults.standard.double(forKey: "focus_total")
    @Published var habitId: Int64? = (UserDefaults.standard.object(forKey: "focus_habit") as? NSNumber)?.int64Value
    @Published var now = Date()
    /// «Не беспокоить» на время фокуса.
    @Published var dnd: Bool = UserDefaults.standard.bool(forKey: "focus_dnd") { didSet { UserDefaults.standard.set(dnd, forKey: "focus_dnd") } }
    private var ticker: Timer?
    private var activity: Activity<FocusAttributes>?

    init() {
        let e = UserDefaults.standard.double(forKey: "focus_end")
        if e > 0 { endsAt = Date(timeIntervalSince1970: e) }
        let p = UserDefaults.standard.double(forKey: "focus_paused")
        if p > 0 { pausedLeft = p }
        activity = Activity<FocusAttributes>.activities.first
        tick()
    }

    var active: Bool { endsAt != nil || pausedLeft != nil }
    var left: TimeInterval { pausedLeft ?? max(0, (endsAt ?? now).timeIntervalSince(now)) }

    private func persist() {
        UserDefaults.standard.set(endsAt?.timeIntervalSince1970 ?? 0, forKey: "focus_end")
        UserDefaults.standard.set(pausedLeft ?? 0, forKey: "focus_paused")
        UserDefaults.standard.set(total, forKey: "focus_total")
        UserDefaults.standard.set(habitId.map { NSNumber(value: $0) }, forKey: "focus_habit")
    }

    private func tick() {
        ticker?.invalidate()
        ticker = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            Task { @MainActor in FocusTimer.shared.step() }
        }
    }

    private func step() {
        now = Date()
        if let e = endsAt, e <= now { finish() }
        if !active, activity != nil { endActivity() }
    }

    private var habit: Habit? { habitId.flatMap { id in AppModel.shared.data.habits.first { $0.id == id } } }

    func start(minutes: Int, habit: Int64?) {
        total = TimeInterval(minutes * 60)
        habitId = habit
        pausedLeft = nil
        endsAt = Date().addingTimeInterval(total)
        schedule()
        startActivity()
        if dnd { enableDND(minutes: minutes) }
    }

    func pause() {
        guard let e = endsAt else { return }
        pausedLeft = max(0, e.timeIntervalSinceNow)
        endsAt = nil
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["focus_end"])
        updateActivity()
    }

    func resume() {
        guard let l = pausedLeft else { return }
        endsAt = Date().addingTimeInterval(l)
        pausedLeft = nil
        schedule()
        updateActivity()
    }

    func cancel() {
        endsAt = nil; pausedLeft = nil
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["focus_end"])
        endActivity()
        restoreStatus()
    }

    private func finish() {
        endsAt = nil; pausedLeft = nil
        let app = AppModel.shared
        if let h = habit, !app.isDone(h) { app.setCount(h, Days.today, h.goalPerDay) }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        app.show("🎯 Фокус завершён — отлично!")
        endActivity()
        restoreStatus()
    }

    private func schedule() {
        guard let e = endsAt else { return }
        let c = UNMutableNotificationContent()
        c.title = "🎯 Фокус завершён"
        c.body = "Отличная работа! Привычка отмечена."
        c.sound = .default
        c.interruptionLevel = .timeSensitive
        let t = UNTimeIntervalNotificationTrigger(timeInterval: max(1, e.timeIntervalSinceNow), repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "focus_end", content: c, trigger: t))
    }

    // MARK: Live Activity

    private var state: FocusAttributes.ContentState {
        FocusAttributes.ContentState(endsAt: endsAt ?? Date().addingTimeInterval(pausedLeft ?? 0), pausedLeft: pausedLeft, total: total)
    }

    private func startActivity() {
        endActivity()
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        let attrs = FocusAttributes(title: habit?.title ?? "Фокус", emoji: habit?.emoji ?? "🎯")
        activity = try? Activity.request(attributes: attrs, content: ActivityContent(state: state, staleDate: endsAt), pushType: nil)
    }

    private func updateActivity() {
        guard let activity else { return }
        let content = ActivityContent(state: state, staleDate: endsAt)
        Task { await activity.update(content) }
    }

    private func endActivity() {
        let all = Activity<FocusAttributes>.activities
        activity = nil
        for a in all { Task { await a.end(nil, dismissalPolicy: .immediate) } }
    }

    // MARK: «Не беспокоить»

    /// iOS не позволяет приложениям включать режим фокусировки напрямую, поэтому:
    /// статус мессенджера → «Не беспокоить» и запуск быстрой команды «HLTH Фокус» (включает режим на N минут).
    private func enableDND(minutes: Int) {
        let m = AppModel.shared.messenger
        UserDefaults.standard.set(m.myStatus, forKey: "focus_prev_status")
        m.setMyStatus("DND")
        let name = "HLTH Фокус".addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
        if let url = URL(string: "shortcuts://x-callback-url/run-shortcut?name=\(name)&input=text&text=\(minutes)&x-success=hlthhbbty://focus"),
           UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        }
    }

    private func restoreStatus() {
        guard let prev = UserDefaults.standard.string(forKey: "focus_prev_status") else { return }
        UserDefaults.standard.removeObject(forKey: "focus_prev_status")
        AppModel.shared.messenger.setMyStatus(prev)
    }
}
