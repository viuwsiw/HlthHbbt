import SwiftUI

enum MainTab: Int, CaseIterable, Hashable {
    case home, habits, chats, profile
    var title: String { ["Главная", "Привычки", "Мессенджер", "Профиль"][rawValue] }
    var icon: String { ["house.fill", "checkmark.circle.fill", "bubble.left.and.bubble.right.fill", "person.crop.circle.fill"][rawValue] }
}

/// Главный экран: системная панель вкладок (на iOS 26 — Liquid Glass, сворачивается при прокрутке).
struct MainView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var messenger: Messenger
    @Environment(\.palette) private var p
    @State private var tab: MainTab = .home
    @State private var homeTaps = 0
    @State private var lastTap = Date.distantPast
    @State private var showAdmin = false
    @State private var update: IosUpdate?

    /// Привязка видит и повторные нажатия на текущую вкладку — для входа в админ-панель.
    private var selection: Binding<MainTab> {
        Binding(get: { tab }, set: { t in
            if t == .home {
                let now = Date()
                homeTaps = now.timeIntervalSince(lastTap) < 0.7 ? homeTaps + 1 : 1
                lastTap = now
                if homeTaps >= 10 {
                    homeTaps = 0
                    if app.username == "developer" { showAdmin = true }
                }
            } else {
                homeTaps = 0
            }
            if t != tab { UISelectionFeedbackGenerator().selectionChanged() }
            tab = t
        })
    }

    var body: some View {
        let unread = messenger.conversations.reduce(0) { $0 + messenger.unread($1.peer) }
        TabView(selection: selection) {
            NavigationStack { HomeView(openTab: { tab = $0 }) }
                .tabItem { Label(MainTab.home.title, systemImage: MainTab.home.icon) }
                .tag(MainTab.home)
            NavigationStack { HabitsView() }
                .tabItem { Label(MainTab.habits.title, systemImage: MainTab.habits.icon) }
                .tag(MainTab.habits)
            NavigationStack { ChatsView() }
                .tabItem { Label(MainTab.chats.title, systemImage: MainTab.chats.icon) }
                .badge(unread)
                .tag(MainTab.chats)
            NavigationStack { ProfileView() }
                .tabItem { Label(MainTab.profile.title, systemImage: MainTab.profile.icon) }
                .tag(MainTab.profile)
        }
        .modifier(TabBarMinimize())
        .sheet(isPresented: $showAdmin) { NavigationStack { AdminView() }.glassSheet() }
        .alert("Доступна версия \(update?.version ?? "")", isPresented: Binding(get: { update != nil }, set: { if !$0 { update = nil } })) {
            Button("Подписать и установить") {
                if let u = update {
                    Task { do { try await Updates.signAndInstall(u) } catch { app.show(error.localizedDescription) } }
                }
                update = nil
            }
            Button("Скачать IPA") { if let u = update, let url = URL(string: u.url) { UIApplication.shared.open(url) }; update = nil }
            Button("Позже", role: .cancel) { update = nil }
        } message: {
            Text((update?.changelog ?? "") + "\n\n«Подписать и установить» — если в Настройках загружены сертификаты. Иначе скачайте IPA и поставьте через Sideloadly/AltStore.")
        }
        .task {
            NowPlaying.shared.start()
            await Health.shared.refresh()
            if app.settings.autoUpdate { update = await Updates.check() }
            Reminders.shared.reschedule(app.data.habits, settings: app.settings)
        }
    }
}

private struct TabBarMinimize: ViewModifier {
    func body(content: Content) -> some View {
        #if compiler(>=6.2)
        if #available(iOS 26.0, *) {
            content.tabBarMinimizeBehavior(.onScrollDown)
        } else {
            content
        }
        #else
        content
        #endif
    }
}

extension View {
    /// Лист: на iOS 26 — системное стекло, раньше — материал.
    @ViewBuilder func glassSheet() -> some View {
        if #available(iOS 26.0, *) { self } else { self.presentationBackground(.ultraThinMaterial) }
    }
}

/// Общий каркас экрана: фон-аврора, системная навигационная панель (на iOS 26 — с эффектом края при прокрутке).
struct Screen<Content: View>: View {
    @EnvironmentObject var app: AppModel
    let title: String
    var large = true
    @ViewBuilder var content: () -> Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) { content() }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 110)
        }
        .scrollIndicators(.hidden)
        .background(LiquidBackground(animated: app.settings.animatedBackground))
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(large ? .large : .inline)
    }
}
