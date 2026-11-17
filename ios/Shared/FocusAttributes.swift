import ActivityKit
import Foundation

/// Live Activity таймера фокуса (общая для приложения и виджета).
struct FocusAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var endsAt: Date
        /// Осталось секунд, если таймер на паузе.
        var pausedLeft: Double?
        var total: Double
    }
    var title: String
    var emoji: String
}
