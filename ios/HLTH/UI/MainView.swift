import SwiftUI

enum MainTab: Int, CaseIterable {
    case home, habits, chats, profile
    var title: String { ["Главная", "Привычки", "Мессенджер", "Профиль"][rawValue] }
    var icon: String { ["house.fill", "checkmark.circle.fill", "bubble.left.and.bubble.right.fill", "person.crop.circle.fill"][rawValue] }
}

struct MainView: View {
    @EnvironmentObject var app: AppModel
    @EnvironmentObject var messenger: Messenger
    @Environment(\.palette) private var p
    @State private var tab: MainTab = .home
    @State private var homeTaps = 0
    @State private var lastTap = Date.distantPast
    @State private var showAdmin = false
    @State private var update: IosUpdate?

    var body: some View {
        ZStack(alignment: .bottom) {
            Group {
                switch tab {
                case .home: NavigationStack { HomeView(openTab: { tab = $0 }) }
                case .habits: NavigationStack { HabitsView() }
                case .chats: NavigationStack { ChatsView() }
                case .profile: NavigationStack { ProfileView() }
                }
            }
            .safeAreaInset(edge: .bottom) { Color.clear.frame(height: 76) }

            tabBar
        }
        .sheet(isPresented: $showAdmin) { NavigationStack { AdminView() }.presentationBackground(.ultraThinMaterial) }
        .alert("Доступна версия \(update?.version ?? "")", isPresented: Binding(get: { update != nil }, set: { if !$0 { update = nil } })) {
            Button("Скачать") { if let u = update, let url = URL(string: u.url) { UIApplication.shared.open(url) }; update = nil }
            Button("Позже", role: .cancel) { update = nil }
        } message: {
            Text((update?.changelog ?? "") + "\n\nУстановите новый IPA так же, как текущий (AltStore / Sideloadly).")
        }
        .task {
            NowPlaying.shared.start()
            await Health.shared.refresh()
            if app.settings.autoUpdate { update = await Updates.check() }
            Reminders.shared.reschedule(app.data.habits, settings: app.settings)
        }
    }

    private var tabBar: some View {
        GlassGroup(spacing: 10) {
            HStack(spacing: 4) {
                ForEach(MainTab.allCases, id: \.self) { t in
                    Button { select(t) } label: {
                        VStack(spacing: 3) {
                            ZStack(alignment: .topTrailing) {
                                Image(systemName: t.icon).font(.system(size: 20, weight: .semibold))
                                if t == .chats {
                                    let n = messenger.conversations.reduce(0) { $0 + messenger.unread($1.peer) }
                                    if n > 0 {
                                        Text("\(min(n, 99))").font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                                            .padding(.horizontal, 5).padding(.vertical, 1).background(Capsule().fill(p.danger)).offset(x: 12, y: -6)
                                    }
                                }
                            }
                            Text(t.title).font(.system(size: 10, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.7)
                        }
                        .foregroundStyle(tab == t ? p.accent : p.text2)
                        .frame(maxWidth: .infinity).padding(.vertical, 8)
                        .background {
                            if tab == t { Capsule().fill(p.accent.opacity(0.16)) }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(6)
            .glass(34, interactive: true)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 6)
        .animation(.spring(duration: 0.3), value: tab)
    }

    private func select(_ t: MainTab) {
        UISelectionFeedbackGenerator().selectionChanged()
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
        tab = t
    }
}

/// Общий фон экранов внутри NavigationStack.
struct Screen<Content: View>: View {
    @EnvironmentObject var app: AppModel
    let title: String
    var large = true
    @ViewBuilder var content: () -> Content
    @Environment(\.palette) private var p

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) { content() }
                .padding(.horizontal, 16).padding(.top, 8).padding(.bottom, 24)
        }
        .scrollIndicators(.hidden)
        .background(LiquidBackground(animated: app.settings.animatedBackground))
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(large ? .large : .inline)
        .toolbarBackground(.hidden, for: .navigationBar)
    }
}
