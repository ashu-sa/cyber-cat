import SwiftUI
import AppKit

extension Color {
    static let cyber = Color(red: 0.12, green: 0.78, blue: 0.95)
    static let teal = Color(red: 0.20, green: 0.90, blue: 0.72)
}
struct CyberCat: View {
    var phase: Stopwatch.Phase
    var animated = true
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var accent: Color { phase == .paused ? .purple : phase == .finished ? .teal : .cyber }
    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 12.0, paused: !animated || reduceMotion)) { timeline in
        let time = animated && !reduceMotion ? timeline.date.timeIntervalSinceReferenceDate : 1
        let blink = animated && !reduceMotion && time.truncatingRemainder(dividingBy: 5.5) < 0.18
        let wag = animated && !reduceMotion ? sin(time * (phase == .running ? 2.8 : 1.4)) * 3 : 0
        Canvas { ctx, size in
            if animated && !reduceMotion { ctx.translateBy(x: 0, y: sin(time * 1.8) * 0.8) }
            func shape(_ points: [CGPoint], _ color: Color) {
                var p = Path(); p.addLines(points); p.closeSubpath(); ctx.fill(p, with: .color(color))
            }
            let black = Color(red: 0.08, green: 0.10, blue: 0.13)
            // Angular ears, compact chassis, and a curled robotic tail.
            var tail = Path(); tail.move(to: CGPoint(x: 81,y: 48)); tail.addCurve(to: CGPoint(x: 94,y: 36 + wag), control1: CGPoint(x: 102,y: 56 + wag), control2: CGPoint(x: 107,y: 32 + wag))
            ctx.stroke(tail, with: .color(black), style: StrokeStyle(lineWidth: 6, lineCap: .round))
            ctx.fill(Path(roundedRect: CGRect(x: 29,y: 29,width: 52,height: 24),cornerRadius: 10),with: .color(black))
            shape([CGPoint(x:25,y:20),CGPoint(x:26,y:1),CGPoint(x:43,y:13)],black)
            shape([CGPoint(x:64,y:12),CGPoint(x:82,y:1),CGPoint(x:83,y:24)],black)
            shape([CGPoint(x:29,y:15),CGPoint(x:30,y:7),CGPoint(x:38,y:14)],accent.opacity(0.6))
            shape([CGPoint(x:70,y:14),CGPoint(x:78,y:7),CGPoint(x:79,y:16)],accent.opacity(0.6))
            let head = Path(roundedRect: CGRect(x:24,y:12,width:60,height:31),cornerRadius: 11)
            ctx.fill(head,with: .color(black)); ctx.stroke(head,with: .color(accent.opacity(0.45)),lineWidth: 1)
            for x: CGFloat in [37,63] {
                let eye = CGRect(x:x,y:25,width:9,height:phase == .paused || blink ? 2 : 5)
                ctx.fill(Path(roundedRect: eye,cornerRadius: 2),with: .color(accent))
            }
            var mouth = Path(); mouth.move(to: CGPoint(x:49,y:34)); mouth.addLine(to: CGPoint(x:54,y:36)); mouth.addLine(to: CGPoint(x:59,y:34))
            ctx.stroke(mouth,with: .color(accent.opacity(0.8)),lineWidth: 1)
            for x: CGFloat in [34,66] { ctx.fill(Path(roundedRect: CGRect(x:x,y:47,width:12,height:7),cornerRadius: 3),with: .color(black)) }
            ctx.fill(Path(CGRect(x:51,y:44,width:6,height:3)),with: .color(accent))
        }
        }.frame(width: 110,height: 55)
        .rotationEffect(.degrees(phase == .finished ? -5 : 0))
        .scaleEffect(phase == .running ? 1.03 : 1)
        .animation(.easeOut(duration: 0.22),value: phase)
    }
}
struct PetControlStyle: ButtonStyle {
    var accent: Color
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 9, weight: .semibold, design: .rounded))
            .frame(width: 26, height: 26)
            .foregroundStyle(accent)
            .background(Color(red: 0.08, green: 0.10, blue: 0.13), in: RoundedRectangle(cornerRadius: 4))
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(accent.opacity(configuration.isPressed ? 1 : 0.45), lineWidth: 1))
            .brightness(configuration.isPressed ? 0.15 : 0)
    }
}
struct PetView: View {
    @AppStorage("petOpacity") private var petOpacity = 1.0
    @AppStorage("petAnimations") private var petAnimations = true
    @ObservedObject var model: Stopwatch
    var showOptions: (NSView) -> Void
    var primary: () -> Void
    var finish: () -> Void
    var body: some View {
        VStack(spacing: 3) {
            CyberCat(phase: model.phase, animated: model.petVisible && petAnimations)
                .scaleEffect(0.76).frame(width: 110, height: 42)
                .overlay(PetInteraction(showOptions: showOptions))
            Text(model.menuTime)
                .font(.system(size: 14, weight: .semibold, design: .monospaced))
                .foregroundStyle(model.phase == .paused ? Color.white.opacity(0.75) : Color.cyber)
                .padding(.horizontal, 8).padding(.vertical, 2)
                .background(Color.black.opacity(0.9), in: Capsule())
                .accessibilityLabel("Elapsed time " + model.menuTime)
            HStack(spacing: 5) {
                Button(action: primary) {
                    Label(primaryTitle, systemImage: primaryIcon)
                }
                .buttonStyle(PetControlStyle(accent: .cyber))
                .help(model.phase == .running ? "Pause this session" : model.phase == .paused ? "Resume this session" : model.phase == .finished ? "Name and save this session" : "Start a new session")
                if model.phase != .finished {
                    Button(action: finish) { Label("Finish", systemImage: "checkmark") }
                        .buttonStyle(PetControlStyle(accent: model.phase == .idle ? .gray : .teal))
                        .disabled(model.phase == .idle)
                        .opacity(model.phase == .idle ? 0.55 : 1)
                        .help("Finish and name your session")
                }
            }.labelStyle(.iconOnly).fixedSize()
        }.frame(width: 150, height: 100).opacity(min(1, max(0.3, petOpacity)))
    }
    var primaryTitle: String {
        switch model.phase { case .idle: return "Play"; case .running: return "Pause"; case .paused: return "Resume"; case .finished: return "Save Session" }
    }
    var primaryIcon: String {
        switch model.phase { case .running: return "pause.fill"; case .finished: return "square.and.arrow.down"; default: return "play.fill" }
    }
}
struct PetInteraction: NSViewRepresentable {
    var showOptions: (NSView) -> Void
    func makeNSView(context: Context) -> PetHitView { let view = PetHitView(); view.showOptions = showOptions; return view }
    func updateNSView(_ view: PetHitView,context: Context) { view.showOptions = showOptions }
}
final class PetHitView: NSView {
    var showOptions: ((NSView) -> Void)?
    override init(frame: NSRect) {
        super.init(frame: frame)
        toolTip = "Drag cat to move · click cat for history, hide, and close"
        setAccessibilityElement(true); setAccessibilityRole(.button); setAccessibilityLabel("Cyber cat controls. Drag to move.")
    }
    required init?(coder: NSCoder) { fatalError() }
    override func acceptsFirstMouse(for event: NSEvent?) -> Bool { true }
    override func mouseDown(with event: NSEvent) {
        guard let window else { return }
        let start = NSEvent.mouseLocation; let origin = window.frame.origin
        var dragged = false
        while let next = window.nextEvent(matching: [.leftMouseDragged,.leftMouseUp]) {
            let now = NSEvent.mouseLocation
            if hypot(now.x-start.x,now.y-start.y) > 3 { dragged = true }
            if dragged { window.setFrameOrigin(NSPoint(x:origin.x+now.x-start.x,y:origin.y+now.y-start.y)) }
            if next.type == .leftMouseUp { break }
        }
        if !dragged { openMenu() }
    }
    override func rightMouseDown(with event: NSEvent) { openMenu() }
    override func accessibilityPerformPress() -> Bool { openMenu(); return true }
    func openMenu() { showOptions?(self) }
}
struct HistoryView: View {
    @ObservedObject var model: Stopwatch
    @State private var period: HistoryPeriod = .day
    @State private var selectedDate = Date()
    @State private var editing: Session?
    @State private var newName = ""
    @State private var deleting: Session?
    @State private var deleted: Session?
    init(model: Stopwatch, initialDate: Date = Date()) {
        self.model = model
        _selectedDate = State(initialValue: initialDate)
    }
    private var records: [Session] { period.sessions(in: model.sessions, containing: selectedDate) }
    private var total: TimeInterval { records.reduce(0) { $0 + $1.duration } }
    private var days: [Date] { Array(Set(records.map { Calendar.current.startOfDay(for: $0.start) })).sorted(by: >) }
    private var range: DateInterval { period.interval(containing: selectedDate) }
    private let surface = Color(red: 0.075, green: 0.09, blue: 0.12)
    private let card = Color(red: 0.11, green: 0.13, blue: 0.17)
    private var monthRecords: [Session] { HistoryPeriod.month.sessions(in: model.sessions, containing: selectedDate) }
    private var dailyTotals: [Date: TimeInterval] {
        Dictionary(grouping: model.sessions, by: { Calendar.current.startOfDay(for: $0.start) }).mapValues { $0.reduce(0) { $0 + $1.duration } }
    }
    var body: some View {
        ScrollView {
        VStack(alignment: .leading, spacing: 22) {
            HStack(spacing: 12) {
                Image(systemName: "calendar").font(.system(size: 22)).foregroundStyle(Color.cyber)
                    .frame(width: 46, height: 46).background(Color.cyber.opacity(0.1), in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 4) {
                    Text("Focus journal").font(.system(size: 25, weight: .bold, design: .rounded))
                    Text("Your time, at a glance.").font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer()
                Button("Today") { selectedDate = Date(); period = .day }.buttonStyle(.bordered)
            }
            HStack(spacing: 12) {
                metric("Today", date: Date(), scope: .day, icon: "sun.max", tint: .cyber)
                metric("Yesterday", date: Calendar.current.date(byAdding: .day, value: -1, to: Date())!, scope: .day, icon: "moon", tint: Color(red: 0.65, green: 0.58, blue: 1))
                metric("This week", date: Date(), scope: .week, icon: "chart.bar", tint: .teal)
                metric("This month", date: Date(), scope: .month, icon: "calendar", tint: Color(red: 0.98, green: 0.74, blue: 0.42))
            }
            HStack(alignment: .top, spacing: 18) {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(selectedDate.formatted(.dateTime.month(.wide).year())).font(.system(size: 21, weight: .semibold, design: .rounded))
                            Text(focusDuration(monthRecords.reduce(0) { $0 + $1.duration }) + " tracked this month").font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button { moveMonth(-1) } label: { Image(systemName: "chevron.left").frame(width: 24, height: 26) }.help("Previous month")
                        Button { moveMonth(1) } label: { Image(systemName: "chevron.right").frame(width: 24, height: 26) }.help("Next month")
                    }.buttonStyle(.borderless)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 7), spacing: 7) {
                        ForEach(0..<7, id: \.self) { index in
                            Text(weekdayLabel(index)).font(.system(size: 10, weight: .semibold)).foregroundStyle(.secondary).frame(maxWidth: .infinity).padding(.bottom, 5)
                        }
                        ForEach(monthCalendarDates(containing: selectedDate), id: \.self) { day in calendarCell(day) }
                    }
                    HStack(spacing: 6) {
                        Circle().fill(Color.cyber).frame(width: 5, height: 5)
                        Text("Daily active time").font(.caption).foregroundStyle(.secondary)
                        Spacer()
                        Text("Less").font(.system(size: 9)).foregroundStyle(.secondary)
                        ForEach(0..<4) { i in RoundedRectangle(cornerRadius: 2).fill(Color.cyber.opacity(0.07 + Double(i) * 0.16)).frame(width: 10, height: 10) }
                        Text("More").font(.system(size: 9)).foregroundStyle(.secondary)
                    }
                }.padding(20).frame(maxWidth: .infinity).background(card, in: RoundedRectangle(cornerRadius: 20))
                VStack(alignment: .leading, spacing: 14) {
                    Picker("Session range", selection: $period) {
                        ForEach(HistoryPeriod.allCases) { Text($0.rawValue).tag($0) }
                    }.pickerStyle(.segmented).labelsHidden()
                    VStack(alignment: .leading, spacing: 6) {
                        Text(detailTitle).font(.system(size: 17, weight: .semibold, design: .rounded))
                        HStack(alignment: .firstTextBaseline) {
                            Text(focusDuration(total)).font(.system(size: 29, weight: .semibold, design: .rounded)).foregroundStyle(Color.cyber)
                            Spacer()
                            Text("\(records.count) session\(records.count == 1 ? "" : "s")").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Divider()
                    if let deleted {
                        HStack {
                            Text("Session deleted").font(.caption)
                            Spacer()
                            Button("Undo") { if model.restoreSession(deleted) { self.deleted = nil } }
                        }.padding(10).background(Color.cyber.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
                    }
                    if records.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "cup.and.saucer").font(.system(size: 26)).foregroundStyle(Color.cyber.opacity(0.6))
                            Text("A little room for focus").font(.subheadline).fontWeight(.medium)
                            Text("No sessions for this " + period.rawValue.lowercased() + ".\nPick a date to explore your time.").font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        }.frame(maxWidth: .infinity).padding(.vertical, 50)
                    } else {
                        ScrollView {
                            LazyVStack(alignment: .leading, spacing: 9) {
                                ForEach(days, id: \.self) { day in
                                    if period != .day { Text(day.formatted(.dateTime.month(.abbreviated).day())).font(.caption).foregroundStyle(.secondary).padding(.top, 5) }
                                    ForEach(records.filter { Calendar.current.isDate($0.start, inSameDayAs: day) }) { session in journalRow(session) }
                                }
                            }
                        }.frame(minHeight: 270, maxHeight: 370)
                    }
                }.padding(18).frame(width: 260).frame(minHeight: 460, alignment: .top).background(card, in: RoundedRectangle(cornerRadius: 20))
            }
            Text("Select a date to see its sessions. Totals exclude pauses; sessions belong to their start date.")
                .font(.system(size: 10)).foregroundStyle(.secondary)
        }.padding(26)
        }.frame(minWidth: 860, minHeight: 680).background(surface).preferredColorScheme(.dark)
        .sheet(item: $editing) { session in
            VStack(alignment: .leading, spacing: 16) {
                Text("Rename session").font(.headline)
                TextField("Task name", text: $newName).textFieldStyle(.roundedBorder).onSubmit { rename(session) }
                HStack { Spacer(); Button("Cancel") { editing = nil }.keyboardShortcut(.cancelAction)
                    Button("Save Name") { rename(session) }.keyboardShortcut(.defaultAction).disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }.padding(24).frame(width: 360)
        }
        .alert("Delete session?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), presenting: deleting) { session in
            Button("Cancel", role: .cancel) { deleting = nil }
            Button("Delete", role: .destructive) {
                if model.deleteSession(session.id) { deleted = session }; deleting = nil
            }
        } message: { session in Text("Delete “\(session.name)” from history? You can undo this while the history window remains open.") }
        .alert("Couldn’t update history", isPresented: Binding(get: { model.error != nil }, set: { if !$0 { model.error = nil } })) {
            Button("OK") { model.error = nil }
        } message: { Text(model.error ?? "") }
    }
    var detailTitle: String {
        if period == .day {
            if Calendar.current.isDateInToday(selectedDate) { return "Today" }
            if Calendar.current.isDateInYesterday(selectedDate) { return "Yesterday" }
            return selectedDate.formatted(.dateTime.weekday(.wide).month(.abbreviated).day())
        }
        return period == .week ? periodTitle : selectedDate.formatted(.dateTime.month(.wide))
    }
    func metric(_ title: String, date: Date, scope: HistoryPeriod, icon: String, tint: Color) -> some View {
        let duration = scope.sessions(in: model.sessions, containing: date).reduce(0) { $0 + $1.duration }
        return Button { selectedDate = date; period = scope } label: {
            VStack(alignment: .leading, spacing: 12) {
                HStack { Text(title).font(.system(size: 12, weight: .medium)).foregroundStyle(.secondary); Spacer(); Image(systemName: icon).foregroundStyle(tint) }
                Text(focusDuration(duration)).font(.system(size: 26, weight: .semibold, design: .rounded)).foregroundStyle(.white)
            }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(card, in: RoundedRectangle(cornerRadius: 16))
        }.buttonStyle(.plain).help("Show " + title.lowercased() + "’s sessions")
    }
    func weekdayLabel(_ index: Int) -> String {
        let calendar = Calendar.current
        return calendar.shortStandaloneWeekdaySymbols[(calendar.firstWeekday - 1 + index) % 7].uppercased()
    }
    func moveMonth(_ offset: Int) {
        let start = HistoryPeriod.month.interval(containing: selectedDate).start
        selectedDate = Calendar.current.date(byAdding: .month, value: offset, to: start) ?? selectedDate
    }
    func calendarCell(_ day: Date) -> some View {
        let calendar = Calendar.current
        let duration = dailyTotals[calendar.startOfDay(for: day)] ?? 0
        let selected = calendar.isDate(day, inSameDayAs: selectedDate)
        let inMonth = calendar.isDate(day, equalTo: selectedDate, toGranularity: .month)
        let today = calendar.isDateInToday(day)
        return Button { selectedDate = day; period = .day } label: {
            VStack(alignment: .leading, spacing: 9) {
                HStack {
                    Text("\(calendar.component(.day, from: day))").font(.system(size: 12, weight: selected || today ? .bold : .medium))
                    Spacer(minLength: 0)
                    if today { Circle().fill(Color.cyber).frame(width: 4, height: 4) }
                }
                Text(duration > 0 ? focusDuration(duration) : "—").font(.system(size: 11, weight: duration > 0 ? .semibold : .regular, design: .rounded)).lineLimit(1).minimumScaleFactor(0.7)
                    .foregroundStyle(duration > 0 ? Color.cyber : Color.white.opacity(0.2))
            }.padding(8).frame(maxWidth: .infinity, minHeight: 46, alignment: .leading)
                .background(Color.cyber.opacity(duration > 0 ? 0.10 + min(duration / 14400, 1) * 0.32 : 0.025), in: RoundedRectangle(cornerRadius: 9))
                .overlay(RoundedRectangle(cornerRadius: 9).stroke(selected ? Color.cyber : Color.white.opacity(0.035), lineWidth: selected ? 1.5 : 1))
                .opacity(inMonth ? 1 : 0.3)
        }.buttonStyle(.plain)
            .accessibilityLabel(day.formatted(date: .complete, time: .omitted) + ", " + focusDuration(duration) + " tracked")
            .accessibilityAddTraits(selected ? .isSelected : [])
            .help(day.formatted(date: .complete, time: .omitted) + " · " + timeText(duration))
    }
    func journalRow(_ session: Session) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(session.name).font(.system(size: 13, weight: .semibold)).fixedSize(horizontal: false, vertical: true).textSelection(.enabled)
            HStack(spacing: 5) {
                Text(session.start.formatted(date: .omitted, time: .shortened)).font(.system(size: 10)).foregroundStyle(.secondary)
                Spacer(minLength: 0)
                Text(focusDuration(session.duration)).font(.system(size: 12, weight: .semibold, design: .rounded)).foregroundStyle(Color.teal)
                Button { newName = session.name; editing = session } label: { Image(systemName: "pencil").frame(width: 24, height: 24) }.buttonStyle(.plain).help("Rename session").accessibilityLabel("Rename " + session.name)
                Button { deleting = session } label: { Image(systemName: "trash").frame(width: 24, height: 24) }.buttonStyle(.plain).foregroundStyle(.secondary).help("Delete session").accessibilityLabel("Delete " + session.name)
            }
        }.padding(12).frame(maxWidth: .infinity, alignment: .leading).background(Color.white.opacity(0.035), in: RoundedRectangle(cornerRadius: 11))
            .help("Started " + session.start.formatted() + " · Finished " + session.end.formatted() + " · " + timeText(session.duration))
    }
    var periodTitle: String {
        switch period {
        case .day: return selectedDate.formatted(date: .complete, time: .omitted)
        case .week: return range.start.formatted(.dateTime.month(.abbreviated).day()) + " – " + range.end.addingTimeInterval(-1).formatted(.dateTime.month(.abbreviated).day().year())
        case .month: return selectedDate.formatted(.dateTime.month(.wide).year())
        }
    }
    func move(_ amount: Int) { selectedDate = Calendar.current.date(byAdding: period.component, value: amount, to: range.start) ?? selectedDate }
    func rename(_ session: Session) { if model.renameSession(session.id, to: newName) { editing = nil } }

}

struct OptionsRow: View {
    var title: String
    var icon: String
    var destructive = false
    var action: () -> Void
    @State private var hovered = false
    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon).frame(width: 20).foregroundStyle(destructive ? Color.red : Color.cyber)
                Text(title).font(.system(size: 13, weight: .medium))
                Spacer(minLength: 0)
            }.padding(.horizontal, 12).frame(maxWidth: .infinity).frame(height: 40)
                .contentShape(Rectangle())
                .background(hovered ? Color.primary.opacity(0.08) : Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 9))
        }.buttonStyle(.plain).onHover { hovered = $0 }
    }
}
struct PetOptionsView: View {
    @AppStorage("petOpacity") private var petOpacity = 1.0
    @AppStorage("petAnimations") private var petAnimations = true
    @ObservedObject var model: Stopwatch
    var height: CGFloat
    var perform: (@escaping () -> Void) -> Void
    var start: () -> Void
    var pause: () -> Void
    var finish: () -> Void
    var cancel: () -> Void
    var hide: () -> Void
    var close: () -> Void
    var history: () -> Void
    var quit: () -> Void
    var dismiss: () -> Void
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Pet controls").font(.headline)
                    Text(model.menuTime).font(.system(size: 12, design: .monospaced)).foregroundStyle(.secondary)
                }
                Spacer()
                Button(action: dismiss) { Image(systemName: "xmark").frame(width: 28, height: 28).contentShape(Rectangle()) }
                    .buttonStyle(.plain).help("Dismiss controls").accessibilityLabel("Dismiss controls")
            }.padding(16)
            Divider()
            ScrollView {
                VStack(alignment: .leading, spacing: 6) {
                    switch model.phase {
                    case .idle: row("Play", "play.fill", start)
                    case .running: row("Pause", "pause.fill", pause); row("Finish Session…", "checkmark", finish)
                    case .paused: row("Resume", "play.fill", start); row("Finish Session…", "checkmark", finish)
                    case .finished: row("Name & Save Session…", "square.and.arrow.down", finish)
                    }
                    if model.phase != .idle { OptionsRow(title: "Cancel Session…", icon: "trash", destructive: true) { perform(cancel) } }
                    Divider().padding(.vertical, 5)
                    Text("Timer continues while the pet is hidden.").font(.caption).foregroundStyle(.secondary).padding(.horizontal, 4)
                    row("Hide Pet", "eye.slash", hide)
                    row("Close Pet", "xmark.circle", close)
                    Divider().padding(.vertical, 5)
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Text("Pet opacity").font(.system(size: 12, weight: .medium))
                            Spacer()
                            Text("\(Int(petOpacity * 100))%").font(.system(size: 12, design: .monospaced)).foregroundStyle(.secondary)
                        }
                        Slider(value: $petOpacity, in: 0.3...1, step: 0.05) { Text("Pet opacity") }.help("Adjust pet transparency from 30% to 100%")
                        Toggle("Animate cat", isOn: $petAnimations).font(.system(size: 12)).toggleStyle(.switch).controlSize(.small)
                    }.padding(12).background(Color.primary.opacity(0.035), in: RoundedRectangle(cornerRadius: 9))
                    Divider().padding(.vertical, 5)
                    row("Session History", "clock.arrow.circlepath", history)
                    row("Quit App…", "power", quit)
                }.padding(12)
            }.scrollIndicators(.visible)
        }.frame(width: 280, height: height)
    }
    func row(_ title: String, _ icon: String, _ action: @escaping () -> Void) -> some View {
        OptionsRow(title: title, icon: icon) { perform(action) }
    }
}
