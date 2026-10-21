import SwiftUI
import AppKit

extension Color {
    static let cyber = Color(red: 0.68, green: 0.86, blue: 0.68)
    static let teal = Color(red: 0.40, green: 0.71, blue: 0.55)
}
struct GrowingTree: View {
    var phase: Stopwatch.Phase
    var animated = true
    var elapsed: () -> TimeInterval = { 0 }
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0/20, paused: !animated || reduceMotion || phase != .running)) { _ in
            let seconds=max(0,elapsed())
            let n=grownTrees(seconds)
            let remainder=seconds.truncatingRemainder(dividingBy:3600)
            let dry=phase == .paused
            let motion=animated && !reduceMotion && !dry
            let t=motion ? seconds : 0
            Canvas { context,size in
                var ctx=context
                ctx.scaleBy(x:size.width/110,y:size.height/58)
                func ellipse(_ rect:CGRect,_ color:Color) { ctx.fill(Path(ellipseIn:rect),with:.color(color)) }
                func smooth(_ x:Double)->Double { let v=min(1,max(0,x)); return v*v*(3-2*v) }
                ellipse(CGRect(x:25,y:45,width:60,height:12),Color.teal.opacity(0.09))
                for i in 0..<min(n,3) {
                    let x:Double=[15,94,103][i]
                    ellipse(CGRect(x:x-5,y:35,width:10,height:12),Color.teal.opacity(0.25))
                    ctx.fill(Path(roundedRect:CGRect(x:x-1,y:43,width:2,height:9),cornerRadius:1),with:.color(.teal.opacity(0.25)))
                }
                func tree(_ g:Double,_ alpha:Double) {
                    guard alpha>0 else { return }
                    var c=ctx; c.opacity=alpha
                    c.translateBy(x:55,y:51); c.rotate(by:.degrees(motion ? sin(t*1.1)*1.2 : 0)); c.translateBy(x:-55,y:-51)
                    let top=51-(7+22*g), canopy=smooth((g-0.5)*2)
                    var stem=Path(); stem.move(to:CGPoint(x:55,y:51)); stem.addCurve(to:CGPoint(x:55,y:top),control1:CGPoint(x:53,y:43),control2:CGPoint(x:57,y:top+7))
                    c.stroke(stem,with:.color(Color(red:0.76,green:0.63,blue:0.44)),style:StrokeStyle(lineWidth:2+2*g,lineCap:.round))
                    for side:Double in [-1,1] {
                        let y=top+4
                        var leaf=Path(); leaf.move(to:CGPoint(x:55,y:y+3))
                        leaf.addCurve(to:CGPoint(x:55+side*(12+8*g),y:y-5),control1:CGPoint(x:55+side*3,y:y-7),control2:CGPoint(x:55+side*(9+7*g),y:y-8))
                        leaf.addCurve(to:CGPoint(x:55,y:y+3),control1:CGPoint(x:55+side*(10+6*g),y:y+4),control2:CGPoint(x:55+side*3,y:y+8))
                        var foliage=c
                        if dry { foliage.translateBy(x:55,y:y+3); foliage.rotate(by:.degrees(side*18)); foliage.translateBy(x:-55,y:-(y+3)) }
                        foliage.fill(leaf,with:.color(dry ? Color(red:0.66,green:0.57,blue:0.39) : side<0 ? Color(red:0.55,green:0.83,blue:0.64) : Color(red:0.27,green:0.59,blue:0.47)))
                    }
                    if canopy>0 {
                        c.translateBy(x:55,y:top+2); c.scaleBy(x:canopy,y:canopy); c.translateBy(x:-55,y:-(top+2))
                        let fill=GraphicsContext.Shading.linearGradient(Gradient(colors:dry ? [Color(red:0.84,green:0.75,blue:0.55),Color(red:0.70,green:0.61,blue:0.43),Color(red:0.48,green:0.45,blue:0.35)] : [Color(red:0.70,green:0.89,blue:0.68),Color(red:0.40,green:0.71,blue:0.55),Color(red:0.17,green:0.47,blue:0.36)]),startPoint:CGPoint(x:40,y:top-20),endPoint:CGPoint(x:65,y:top+12))
                        for r in [CGRect(x:30,y:top-14,width:30,height:29),CGRect(x:52,y:top-14,width:29,height:29),CGRect(x:39,y:top-21,width:32,height:31)] { c.fill(Path(ellipseIn:r),with:fill) }
                        c.fill(Path(ellipseIn:CGRect(x:43,y:top-16,width:14,height:10)),with:.color(Color(red:0.8,green:0.95,blue:0.7).opacity(0.35)))
                    }
                }
                let blend=n>0 ? smooth(remainder/8) : 1
                if n>0 { tree(1,1-blend) }
                tree(remainder/3600,blend)
                ellipse(CGRect(x:30,y:50,width:51,height:7),Color(red:0.29,green:0.24,blue:0.20))
                ellipse(CGRect(x:31,y:49,width:49,height:4),(dry ? Color(red:0.55,green:0.47,blue:0.35) : Color(red:0.42,green:0.61,blue:0.44)))
                if motion && phase == .running {
                    let u=t.truncatingRemainder(dividingBy:3.6)/3.6, fall=min(1,u/0.78), y=3+44*fall*fall
                    let water=ctx
                    if u<0.78 {
                        var p=Path(); p.move(to:CGPoint(x:84,y:y-5)); p.addCurve(to:CGPoint(x:81,y:y+5),control1:CGPoint(x:82,y:y-2),control2:CGPoint(x:78,y:y+3)); p.addCurve(to:CGPoint(x:84,y:y-5),control1:CGPoint(x:87,y:y+9),control2:CGPoint(x:90,y:y+2))
                        water.fill(p,with:.color(Color(red:0.65,green:0.85,blue:0.80)))
                        water.fill(Path(ellipseIn:CGRect(x:82,y:y+1,width:1.5,height:2.5)),with:.color(.white.opacity(0.8)))
                    } else {
                        let r=(u-0.78)/0.22
                        water.stroke(Path(ellipseIn:CGRect(x:84-10*r,y:51-2*r,width:20*r,height:4*r)),with:.color(Color.cyber.opacity((1-r)*0.65)),lineWidth:0.8)
                    }
                }
                if n>0 { ctx.draw(Text("\(n)×").font(.system(size:8,weight:.semibold)).foregroundStyle(Color(red:0.66,green:0.85,blue:0.71)),at:CGPoint(x:13,y:45)) }
            }
        }.frame(width:110,height:58)
        .accessibilityLabel(treeText(elapsed()) + (phase == .paused ? ", dry tree, watering paused" : isGarden(elapsed()) ? ", garden grown" : ", growing your grove"))
    }
}
struct PetControlStyle: ButtonStyle {
    var accent: Color
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 9, weight: .semibold, design: .rounded))
            .frame(width: 26, height: 26)
            .foregroundStyle(accent)
            .background(Color(red: 0.07, green: 0.11, blue: 0.09), in: RoundedRectangle(cornerRadius: 4))
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
            GrowingTree(phase: model.phase, animated: model.petVisible && petAnimations, elapsed: { model.growthElapsed })
                .scaleEffect(0.76).frame(width: 110, height: 42)
                .overlay(PetInteraction(showOptions: showOptions))
            Text(model.menuTime)
                .font(.system(size: 14, weight: .semibold, design: .monospaced))
                .foregroundStyle(model.phase == .paused ? Color.white.opacity(0.75) : Color.cyber)
                .padding(.horizontal, 8).padding(.vertical, 2)
                .background(Color(red:0.07,green:0.11,blue:0.09).opacity(0.95), in: Capsule())
                .overlay(PetInteraction(showOptions: showOptions))
                .accessibilityLabel("Elapsed time " + model.menuTime)
            HStack(spacing: 5) {
                FloatingAction(symbol:primaryIcon,title:primaryTitle,enabled:true,accent:.cyber,action:primary)
                    .frame(width:26,height:26)
                if model.phase != .finished {
                    FloatingAction(symbol:"checkmark",title:"Finish and name session",enabled:model.phase != .idle,accent:.teal,action:finish)
                        .frame(width:26,height:26)
                }
            }.labelStyle(.iconOnly).fixedSize()
        }.frame(width: 150, height: 100).background(PetInteraction(showOptions: showOptions)).opacity(min(1, max(0.3, petOpacity)))
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
    private let drag=FloatingDragTracker()
    var showOptions: ((NSView) -> Void)?
    override init(frame: NSRect) {
        super.init(frame: frame)
        toolTip = "Drag to move · click for history, hide, and close"
        setAccessibilityElement(true); setAccessibilityRole(.button); setAccessibilityLabel("Growing tree controls. Drag to move.")
    }
    required init?(coder: NSCoder) { fatalError() }
    override func acceptsFirstMouse(for event: NSEvent?) -> Bool { true }
    override func mouseDown(with event:NSEvent) { drag.begin(event,window:window) }
    override func mouseDragged(with event:NSEvent) { drag.move(event,window:window) }
    override func mouseUp(with event:NSEvent) { if drag.end(event,window:window) { openMenu() } }
    override func rightMouseDown(with event: NSEvent) { openMenu() }
    override func accessibilityPerformPress() -> Bool { openMenu(); return true }
    func openMenu() { showOptions?(self) }
}
// A single drag threshold serves artwork, timer, empty space and native controls.
@MainActor final class FloatingDragTracker {
    private var start:NSPoint?
    private var origin=NSPoint.zero
    private(set) var moved=false
    func begin(_ event:NSEvent,window:NSWindow?) {
        guard let window else { start=nil;return }
        start=window.convertPoint(toScreen:event.locationInWindow);origin=window.frame.origin;moved=false
    }
    func move(_ event:NSEvent,window:NSWindow?) {
        guard let window,let start else { return }
        let now=window.convertPoint(toScreen:event.locationInWindow)
        if hypot(now.x-start.x,now.y-start.y)>4 { moved=true }
        if moved { window.setFrameOrigin(NSPoint(x:origin.x+now.x-start.x,y:origin.y+now.y-start.y)) }
    }
    // Returns true only for a click. Every drag, including a release outside, suppresses the action.
    func end(_ event:NSEvent,window:NSWindow?)->Bool {
        guard start != nil else { return false }
        move(event,window:window);let clicked = !moved;start=nil;return clicked
    }
}
struct FloatingAction:NSViewRepresentable {
    var symbol:String; var title:String; var enabled:Bool; var accent:Color; var action:()->Void
    func makeNSView(context:Context)->FloatingActionButton { FloatingActionButton() }
    func updateNSView(_ view:FloatingActionButton,context:Context) {
        view.image=NSImage(systemSymbolName:symbol,accessibilityDescription:title)?.withSymbolConfiguration(.init(pointSize:10,weight:.semibold))
        view.isEnabled=enabled; view.alphaValue=enabled ? 1 : 0.45; view.contentTintColor=NSColor(accent)
        view.setAccessibilityLabel(title); view.toolTip=title+" · Drag to move"; view.clickAction=action
        view.layer?.borderColor=NSColor(accent.opacity(0.5)).cgColor
    }
}
final class FloatingActionButton:NSButton {
    private let drag=FloatingDragTracker()
    var clickAction:(()->Void)?
    override init(frame:NSRect) {
        super.init(frame:frame); isBordered=false; title=""; imagePosition = .imageOnly; focusRingType = .none
        wantsLayer=true; layer?.cornerRadius=4; layer?.borderWidth=1
        layer?.backgroundColor=NSColor(red:0.07,green:0.11,blue:0.09,alpha:1).cgColor
        target=self; action=#selector(pressed)
    }
    required init?(coder:NSCoder) { fatalError() }
    override func acceptsFirstMouse(for event:NSEvent?)->Bool { true }
    override func mouseDown(with event:NSEvent) { drag.begin(event,window:window);isHighlighted=isEnabled }
    override func mouseDragged(with event:NSEvent) { drag.move(event,window:window);if drag.moved { isHighlighted=false } }
    override func mouseUp(with event:NSEvent) { let click=drag.end(event,window:window);isHighlighted=false;if click && isEnabled { performClick(nil) } }
    @objc private func pressed() { clickAction?() }
}
struct HistoryView: View {
    @State private var accountOpen = false
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
    private let surface = Color(red: 0.067, green: 0.106, blue: 0.09)
    private let card = Color(red: 0.12, green: 0.17, blue: 0.14)
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
                Button("Account / Sync") { accountOpen=true }.sheet(isPresented:$accountOpen) { AccountView(model:model) }
                Button("Today") { selectedDate = Date(); period = .day }.buttonStyle(.bordered)
            }
            HStack(spacing: 12) {
                metric("Today", date: Date(), scope: .day, icon: "sun.max", tint: .cyber)
                metric("Yesterday", date: Calendar.current.date(byAdding: .day, value: -1, to: Date())!, scope: .day, icon: "moon", tint: Color(red: 0.64, green: 0.73, blue: 0.54))
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
                    Text(Dictionary(grouping: records, by: { $0.deviceLabel }).sorted { $0.key < $1.key }.map { $0.key + " " + focusDuration($0.value.reduce(0) { $0 + $1.duration }) }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
                    Text(period == .day && isGarden(total) ? "Garden grown · " + treeText(total) : forestSummary(total,daily:Array(Dictionary(grouping:records,by:{ Calendar.current.startOfDay(for:$0.start) }).values.map { $0.reduce(0) { $0+$1.duration } }))).font(.system(size:15,weight:.semibold,design:.rounded)).foregroundStyle(Color.teal)
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
        }.frame(minWidth: 860, minHeight: 680).background(surface).tint(.cyber).preferredColorScheme(.dark)
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
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text("\(calendar.component(.day, from: day))").font(.system(size: 12, weight: selected || today ? .bold : .medium))
                    Spacer(minLength: 0)
                    if today { Circle().fill(Color.cyber).frame(width: 4, height: 4) }
                }
                Text(duration > 0 ? focusDuration(duration) : "—").font(.system(size: 11, weight: duration > 0 ? .semibold : .regular, design: .rounded)).lineLimit(1).minimumScaleFactor(0.7)
                    .foregroundStyle(duration > 0 ? Color.cyber : Color.white.opacity(0.2))
                Text(treeCellText(duration)).font(.system(size:9,weight:.semibold)).foregroundStyle(isGarden(duration) ? Color.yellow : Color.teal).lineLimit(1).minimumScaleFactor(0.6)
            }.padding(8).frame(maxWidth: .infinity, minHeight: 60, alignment: .leading)
                .background(Color.cyber.opacity(duration > 0 ? 0.10 + min(duration / 14400, 1) * 0.32 : 0.025), in: RoundedRectangle(cornerRadius: 9))
                .overlay(RoundedRectangle(cornerRadius: 9).stroke(selected ? Color.cyber : Color.white.opacity(0.035), lineWidth: selected ? 1.5 : 1))
                .opacity(inMonth ? 1 : 0.3)
        }.buttonStyle(.plain)
            .accessibilityLabel(day.formatted(date: .complete, time: .omitted) + ", " + focusDuration(duration) + " tracked, " + treeText(duration) + (isGarden(duration) ? ", garden" : ""))
            .accessibilityAddTraits(selected ? .isSelected : [])
            .help(day.formatted(date: .complete, time: .omitted) + " · " + timeText(duration))
    }
    func journalRow(_ session: Session) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(treeText(session.duration)).font(.caption).foregroundStyle(Color.teal)
            Text(session.name).font(.system(size: 13, weight: .semibold)).fixedSize(horizontal: false, vertical: true).textSelection(.enabled)
            HStack(spacing: 5) {
                Text(session.deviceLabel + " · " + session.start.formatted(date: .omitted, time: .shortened)).font(.system(size: 10)).foregroundStyle(.secondary)
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
                        Toggle("Animate tree and water", isOn: $petAnimations).font(.system(size: 12)).toggleStyle(.switch).controlSize(.small)
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
