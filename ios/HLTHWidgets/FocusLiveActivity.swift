import ActivityKit
import SwiftUI
import WidgetKit

@main
struct HLTHWidgetsBundle: WidgetBundle {
    var body: some Widget {
        FocusLiveActivity()
    }
}

private let accent = Color(red: 0.18, green: 0.83, blue: 0.58)

struct FocusTimerText: View {
    let state: FocusAttributes.ContentState
    var body: some View {
        if let left = state.pausedLeft {
            Text(String(format: "%02d:%02d", Int(left) / 60, Int(left) % 60))
        } else {
            Text(timerInterval: Date()...max(Date(), state.endsAt), countsDown: true)
        }
    }
}

struct FocusLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FocusAttributes.self) { context in
            HStack(spacing: 14) {
                Text(context.attributes.emoji).font(.system(size: 36))
                VStack(alignment: .leading, spacing: 4) {
                    Text(context.attributes.title).font(.headline).lineLimit(1)
                    Text(context.state.pausedLeft != nil ? "Пауза" : "Фокус · HLTH HBTy").font(.caption).foregroundStyle(.secondary)
                    if context.state.pausedLeft == nil {
                        ProgressView(timerInterval: context.state.endsAt.addingTimeInterval(-context.state.total)...max(context.state.endsAt, Date().addingTimeInterval(1)), countsDown: true) {
                            EmptyView()
                        } currentValueLabel: { EmptyView() }
                        .tint(accent)
                    }
                }
                Spacer()
                FocusTimerText(state: context.state)
                    .font(.system(size: 34, weight: .bold, design: .rounded).monospacedDigit())
                    .multilineTextAlignment(.trailing)
                    .frame(width: 110, alignment: .trailing)
            }
            .padding(16)
            .activityBackgroundTint(Color.black.opacity(0.55))
            .activitySystemActionForegroundColor(accent)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text(context.attributes.emoji).font(.title)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    FocusTimerText(state: context.state)
                        .font(.title2.bold().monospacedDigit()).foregroundStyle(accent)
                        .frame(width: 90, alignment: .trailing)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(context.attributes.title).font(.headline).lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(context.state.pausedLeft != nil ? "На паузе" : "Сосредоточьтесь — уведомления подождут")
                        .font(.caption).foregroundStyle(.secondary)
                }
            } compactLeading: {
                Text(context.attributes.emoji)
            } compactTrailing: {
                FocusTimerText(state: context.state)
                    .monospacedDigit().foregroundStyle(accent).frame(width: 46)
            } minimal: {
                Image(systemName: "timer").foregroundStyle(accent)
            }
            .keylineTint(accent)
        }
    }
}
