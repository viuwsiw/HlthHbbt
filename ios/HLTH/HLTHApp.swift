import SwiftUI
import UserNotifications

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }
}

@main
struct HLTHApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var app = AppModel.shared
    @Environment(\.scenePhase) private var phase

    var body: some Scene {
        WindowGroup {
            ThemedRoot()
                .environmentObject(app)
                .environmentObject(app.messenger)
                .onAppear { app.start() }
                .onOpenURL { _ in }
        }
        .onChange(of: phase) { _, p in
            if p == .active {
                app.messenger.appDidBecomeActive()
                app.checkBedtime()
                Task { await Health.shared.refresh() }
            } else if p == .background {
                Task { await app.backupNow() }
            }
        }
    }
}

struct ThemedRoot: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let dark: Bool = {
            switch app.settings.themeMode {
            case .SYSTEM: return scheme == .dark
            case .LIGHT: return false
            case .DARK, .BLACK: return true
            }
        }()
        let p = Palette.make(dark: dark, black: app.settings.themeMode == .BLACK, accentIndex: app.settings.accentIndex)
        RootView()
            .environment(\.palette, p)
            .preferredColorScheme(app.settings.themeMode == .SYSTEM ? nil : (dark ? .dark : .light))
            .tint(p.accent)
    }
}

struct RootView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p

    var body: some View {
        ZStack {
            LiquidBackground(animated: app.settings.animatedBackground)
            if app.username == nil {
                AuthView().transition(.opacity)
            } else {
                MainView().transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: app.username)
        .overlay(alignment: .top) {
            if let t = app.toast {
                Text(t)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(p.text)
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .glass(22)
                    .padding(.top, 8).padding(.horizontal, 20)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .task(id: t) {
                        try? await Task.sleep(nanoseconds: 2_600_000_000)
                        withAnimation { app.toast = nil }
                    }
            }
        }
        .animation(.spring(duration: 0.35), value: app.toast)
    }
}
