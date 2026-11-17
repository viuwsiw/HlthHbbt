import SwiftUI

struct HabitRow: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    let habit: Habit
    @State private var editing = false

    var body: some View {
        let h = app.history
        let done = app.isDone(habit)
        let count = app.count(habit)
        let color = habitColor(habit.colorIndex)
        HStack(spacing: 14) {
            Text(habit.emoji).font(.system(size: 28))
                .frame(width: 50, height: 50)
                .background(Circle().fill(color.opacity(0.18)))
            VStack(alignment: .leading, spacing: 3) {
                Text(habit.title).font(.headline).foregroundStyle(p.text).lineLimit(2)
                if habit.isQuit {
                    Text("Без срывов: " + formatDuration(nowMs() - (habit.quitSince ?? habit.createdAt))).font(.caption).foregroundStyle(p.text2)
                } else {
                    HStack(spacing: 6) {
                        let s = h.streak(habit)
                        if s > 0 { Text("🔥 \(s)").font(.caption.weight(.semibold)).foregroundStyle(Color(hex: 0xF59E0B)) }
                        Text(subtitle).font(.caption).foregroundStyle(p.text2).lineLimit(1)
                    }
                }
            }
            Spacer(minLength: 4)
            if habit.isQuit {
                Image(systemName: "shield.lefthalf.filled").font(.title2).foregroundStyle(color)
            } else if let f = habit.focusMinutes, !done {
                NavigationLink { FocusView(habit: habit, minutes: f) } label: { roundIcon("timer", color) }.buttonStyle(.plain)
            } else if habit.activity == HabitKind.activityWalk, !done {
                NavigationLink { WalkView(habit: habit) } label: { roundIcon("figure.walk", color) }.buttonStyle(.plain)
            } else {
                Button { withAnimation(.spring(duration: 0.3)) { app.tap(habit) } } label: {
                    ZStack {
                        Circle().stroke(color.opacity(0.25), lineWidth: 4)
                        Circle().trim(from: 0, to: min(1, Double(count) / Double(max(1, habit.goalPerDay))))
                            .stroke(color, style: StrokeStyle(lineWidth: 4, lineCap: .round)).rotationEffect(.degrees(-90))
                        if done { Image(systemName: "checkmark").font(.headline.bold()).foregroundStyle(color) }
                        else if habit.goalPerDay > 1 { Text("\(count)/\(habit.goalPerDay)").font(.caption2.bold()).foregroundStyle(p.text2) }
                    }
                    .frame(width: 40, height: 40)
                    .contentShape(Circle())
                }.buttonStyle(.plain)
            }
        }
        .glassCard(14)
        .contextMenu {
            if !habit.isQuit {
                Button { app.tap(habit) } label: { Label(done ? "Снять отметку" : "Отметить", systemImage: "checkmark.circle") }
                Button { app.freeze(habit) } label: { Label("Заморозить сегодня", systemImage: "snowflake") }
            } else {
                Button(role: .destructive) { app.resetQuit(habit) } label: { Label("Сорвался — начать заново", systemImage: "arrow.counterclockwise") }
            }
            Button { editing = true } label: { Label("Изменить", systemImage: "pencil") }
            Button(role: .destructive) { app.deleteHabit(habit) } label: { Label("Удалить", systemImage: "trash") }
        }
        .sheet(isPresented: $editing) { HabitEditor(habit: habit) }
    }

    private var subtitle: String {
        var parts: [String] = []
        if habit.focusMinutes != nil { parts.append("Фокус \(habit.focusMinutes!) мин") }
        if habit.activity == HabitKind.activityWalk { parts.append("GPS и шаги") }
        if habit.healthMetric == HabitKind.metricBedtime { parts.append("до " + formatMinutesOfDay(habit.healthTarget ?? 1380)) }
        if habit.healthMetric == HabitKind.metricSteps { parts.append("\(habit.healthTarget ?? 0) шагов") }
        if parts.isEmpty { parts.append(scheduleLabel(habit.scheduleMask)) }
        return parts.joined(separator: " · ")
    }

    private func roundIcon(_ name: String, _ color: Color) -> some View {
        Image(systemName: name).font(.headline).foregroundStyle(.white)
            .frame(width: 40, height: 40).background(Circle().fill(color))
    }
}

struct HabitsView: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @State private var creating = false
    @State private var showArchived = false

    var body: some View {
        let good = app.data.habits.filter { !$0.isQuit && !$0.archived }
        let quit = app.data.habits.filter { $0.isQuit && !$0.archived }
        let archived = app.data.habits.filter(\.archived)
        Screen(title: "Привычки") {
            WeekStrip()
            if !good.isEmpty {
                SectionTitle("Полезные")
                ForEach(good) { HabitRow(habit: $0) }
            }
            if !quit.isEmpty {
                SectionTitle("Отказ от вредного")
                ForEach(quit) { HabitRow(habit: $0) }
            }
            if good.isEmpty && quit.isEmpty {
                VStack(spacing: 8) {
                    Text("🌱").font(.system(size: 48))
                    Text("Добавьте первую привычку").font(.headline).foregroundStyle(p.text)
                }.frame(maxWidth: .infinity).padding(30).glass()
            }
            if !archived.isEmpty {
                Button { withAnimation { showArchived.toggle() } } label: {
                    Label("Архив (\(archived.count))", systemImage: showArchived ? "chevron.up" : "chevron.down")
                        .font(.subheadline.weight(.semibold)).foregroundStyle(p.text2)
                }.padding(.horizontal, 6)
                if showArchived { ForEach(archived) { HabitRow(habit: $0).opacity(0.6) } }
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { creating = true } label: { Image(systemName: "plus").font(.headline) }
            }
        }
        .sheet(isPresented: $creating) { HabitEditor(habit: nil) }
    }
}

/// Неделя: доля выполненных привычек по дням.
struct WeekStrip: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p

    var body: some View {
        let h = app.history
        let today = Days.today
        HStack(spacing: 6) {
            ForEach((0...6).reversed(), id: \.self) { back in
                let day = today - Int64(back)
                let c = h.completion(day)
                VStack(spacing: 6) {
                    Text(weekdayShort[Days.weekdayIndex(day)]).font(.caption2.weight(.semibold)).foregroundStyle(p.text2)
                    ZStack {
                        Circle().stroke(p.accent.opacity(0.18), lineWidth: 4)
                        Circle().trim(from: 0, to: c ?? 0).stroke(p.accent, style: StrokeStyle(lineWidth: 4, lineCap: .round)).rotationEffect(.degrees(-90))
                        Text("\(Calendar.current.component(.day, from: Days.date(day)))")
                            .font(.caption.weight(back == 0 ? .bold : .regular)).foregroundStyle(p.text)
                    }
                    .frame(width: 34, height: 34)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .glassCard(12)
    }
}

struct HabitEditor: View {
    @EnvironmentObject var app: AppModel
    @Environment(\.palette) private var p
    @Environment(\.dismiss) private var dismiss
    let original: Habit?
    @State private var h: Habit
    @State private var reminderOn: Bool
    @State private var reminder: Date

    init(habit: Habit?) {
        original = habit
        let base = habit ?? Habit(id: 0, title: "", emoji: "✅")
        _h = State(initialValue: base)
        _reminderOn = State(initialValue: base.reminderMinutes != nil)
        let m = base.reminderMinutes ?? 9 * 60
        _reminder = State(initialValue: Calendar.current.date(bySettingHour: m / 60, minute: m % 60, second: 0, of: Date()) ?? Date())
    }

    private let emojis = ["✅", "💧", "🏃", "🚶", "🤸", "🧘", "📚", "🎨", "🎸", "🍎", "🥦", "😴", "🌙", "💊", "🦷", "🧹", "💻", "✍️", "🚭", "🍷", "📵", "🍬", "☕", "🙏"]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Picker("", selection: $h.kind) {
                        Text("Полезная").tag(HabitKind.good)
                        Text("Отказ от вредной").tag(HabitKind.quit)
                    }.pickerStyle(.segmented)

                    GlassField(title: "Название", text: $h.title).textInputAutocapitalization(.sentences)

                    SectionTitle("Значок")
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 8), spacing: 8) {
                        ForEach(emojis, id: \.self) { e in
                            Button { h.emoji = e } label: {
                                Text(e).font(.title2).frame(width: 38, height: 38)
                                    .background(Circle().fill(h.emoji == e ? p.accent.opacity(0.25) : .clear))
                            }.buttonStyle(.plain)
                        }
                    }.glassCard(10)

                    SectionTitle("Цвет")
                    HStack {
                        ForEach(habitColors.indices, id: \.self) { i in
                            Button { h.colorIndex = i } label: {
                                Circle().fill(habitColors[i]).frame(width: 30, height: 30)
                                    .overlay(Circle().stroke(.white, lineWidth: h.colorIndex == i ? 3 : 0))
                            }.buttonStyle(.plain).frame(maxWidth: .infinity)
                        }
                    }.glassCard(12)

                    if h.kind == HabitKind.good {
                        SectionTitle("Дни")
                        HStack(spacing: 6) {
                            ForEach(0..<7, id: \.self) { i in
                                let on = h.scheduleMask & (1 << i) != 0
                                Button { h.scheduleMask ^= (1 << i); if h.scheduleMask == 0 { h.scheduleMask = 1 << i } } label: {
                                    Text(weekdayShort[i]).font(.caption.bold()).frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .foregroundStyle(on ? p.onAccent : p.text2)
                                        .background(Capsule().fill(on ? AnyShapeStyle(p.gradient) : AnyShapeStyle(Color.clear)))
                                }.buttonStyle(.plain)
                            }
                        }.glassCard(8)

                        VStack(spacing: 12) {
                            Stepper("Раз в день: \(h.goalPerDay)", value: $h.goalPerDay, in: 1...20)
                            Toggle("Напоминание", isOn: $reminderOn)
                            if reminderOn { DatePicker("Время", selection: $reminder, displayedComponents: .hourAndMinute) }
                            Toggle("Таймер фокуса", isOn: Binding(get: { h.focusMinutes != nil }, set: { h.focusMinutes = $0 ? 25 : nil }))
                            if let f = h.focusMinutes {
                                Stepper("Длительность: \(f) мин", value: Binding(get: { f }, set: { h.focusMinutes = $0 }), in: 5...180, step: 5)
                            }
                            Toggle("Прогулка с GPS", isOn: Binding(get: { h.activity == HabitKind.activityWalk }, set: { h.activity = $0 ? HabitKind.activityWalk : nil }))
                            Toggle("Автоотметка по шагам", isOn: Binding(get: { h.healthMetric == HabitKind.metricSteps }, set: {
                                h.healthMetric = $0 ? HabitKind.metricSteps : nil; h.healthTarget = $0 ? 8000 : nil
                            }))
                            if h.healthMetric == HabitKind.metricSteps {
                                Stepper("Цель: \(h.healthTarget ?? 8000) шагов", value: Binding(get: { h.healthTarget ?? 8000 }, set: { h.healthTarget = $0 }), in: 1000...40000, step: 500)
                            }
                        }
                        .foregroundStyle(p.text)
                        .glassCard()
                    }

                    if original != nil {
                        Toggle("В архиве", isOn: $h.archived).foregroundStyle(p.text).glassCard()
                    }
                }
                .padding(16)
            }
            .background(LiquidBackground())
            .navigationTitle(original == nil ? "Новая привычка" : "Привычка")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { save() }.disabled(h.title.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private func save() {
        var x = h
        x.title = x.title.trimmingCharacters(in: .whitespaces)
        if reminderOn {
            let c = Calendar.current.dateComponents([.hour, .minute], from: reminder)
            x.reminderMinutes = (c.hour ?? 0) * 60 + (c.minute ?? 0)
        } else { x.reminderMinutes = nil }
        if x.isQuit && x.quitSince == nil { x.quitSince = nowMs() }
        app.saveHabit(x)
        dismiss()
    }
}
