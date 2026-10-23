import SwiftUI
import AppKit

struct Session: Codable, Identifiable {
    var id = UUID()
    var name: String
    var start: Date
    var end: Date
    var duration: TimeInterval
    var platform: String? = nil
    var deviceId: String? = nil
    var deviceName: String? = nil
    var deviceLabel: String {
        deviceName ?? ["macos": "Mac", "android": "Phone", "windows": "Windows", "linux": "Linux"][platform ?? ""] ?? "This device"
    }
}

@MainActor final class Stopwatch: ObservableObject {
    enum Phase: String, Codable { case idle, running, paused, finished }
    @Published var phase: Phase = .idle
    @Published var pomodoroEnabled=false
    @Published var pomodoro=PomodoroState()
    @Published var focusNotice:String?
    private var breakAnchor:ContinuousClock.Instant?
    private var breakRemaining:TimeInterval=0
    private var focusSettingsURL:URL { directory.appendingPathComponent("focus-settings.json") }
    struct FocusSettings:Codable { var enabled:Bool;var breakMinutes:Int }
    var displayTime:TimeInterval {
        guard pomodoroEnabled,phase != .finished else { return elapsed }
        if pomodoro.stage=="dead" { return 1500 }
        if pomodoro.stage=="ready" { return 0 }
        if pomodoro.stage=="break" {
            guard let breakAnchor else { return max(0,pomodoro.deadline?.timeIntervalSinceNow ?? 0) }
            let c=breakAnchor.duration(to:clock.now).components
            return max(0,breakRemaining-Double(c.seconds)-Double(c.attoseconds)/1e18)
        }
        return max(0,pomodoro.target-elapsed)
    }
    func selectMode(_ enabled:Bool,minutes:Int=5) {
        if pomodoro.stage=="dead" { guard discard() else { return } }
        guard phase == .idle else { error="Finish or reset your session before changing modes."; return }
        let minutes=minutes==10 ? 10:5
        do { try JSONEncoder().encode(FocusSettings(enabled:enabled,breakMinutes:minutes)).write(to:focusSettingsURL,options:.atomic)
            pomodoroEnabled=enabled;pomodoro=PomodoroState(breakMinutes:minutes);refreshMenu()
        } catch { self.error=error.localizedDescription }
    }
    func advancePomodoro() {
        guard pomodoroEnabled,phase != .idle,phase != .finished else { return }
        var changed=false
        if pomodoro.stage=="focus",phase == .running,elapsed>=pomodoro.target {
            guard creditPomodoro() else { return }
            let remaining=Double(pomodoro.breakMinutes*60)-(elapsed-pomodoro.target)
            accumulated=pomodoro.target;anchor=nil;phase = .paused;pomodoro.stage="break"
            pomodoro.deadline=Date().addingTimeInterval(remaining);breakAnchor=clock.now;breakRemaining=remaining
            focusNotice="Your flower bloomed! Stop watering, stretch and move a little.";changed=true
        }
        if pomodoro.stage=="break",displayTime<=0 {
            pomodoro.stage="ready";focusNotice="Come back when you’re ready — your next flower needs water.";changed=true
        }
        if changed { checkpoint() }
    }
    private func creditPomodoro()->Bool {
        guard let id=sessionId,let start=started else { return false }
        if sessions.contains(where:{$0.id==id && $0.duration>=pomodoro.target}) { return true }
        guard checkpoint() else { return false }
        let defaults=UserDefaults.standard
        let device=defaults.string(forKey:"cybercat.deviceId") ?? UUID().uuidString
        defaults.set(device,forKey:"cybercat.deviceId")
        let entry=Session(id:id,name:sessions.first(where:{$0.id==id})?.name ?? "Pomodoro focus",start:start,end:Date().addingTimeInterval(-max(0,elapsed-pomodoro.target)),duration:pomodoro.target,platform:"macos",deviceId:device,deviceName:"Mac")
        return replaceHistory([entry]+sessions.filter{$0.id != id})
    }
    func cancelPomodoro() {
        guard pomodoroEnabled,phase != .idle else { return }
        advancePomodoro()
        if pomodoro.stage=="focus" { accumulated=elapsed;anchor=nil;phase = .paused;pomodoro.stage="dead";pomodoro.deadline=nil;breakAnchor=nil;focusNotice="This tree died. Start a new 25-minute round when you’re ready.";refreshMenu();checkpoint() }
        else if pomodoro.stage != "dead" { _=discard() }
    }
    private func startRoundIfNeeded() {
        if pomodoroEnabled && (pomodoro.stage=="break" || pomodoro.stage=="ready") {
            pomodoro.stage="focus";pomodoro.target=elapsed+1500;pomodoro.deadline=nil;breakAnchor=nil;focusNotice=nil
        }
    }
    @Published var sessions: [Session] = [] { didSet { growthCache=nil } }
    @Published var error: String?
    @Published var saved = false
    @Published var petVisible = true
    @Published var menuTime = "00:00:00"
    func refreshMenu() { let text = timeText(displayTime); if menuTime != text { menuTime = text } }
    private var accumulated: TimeInterval = 0
    private var anchor: ContinuousClock.Instant?
    private var started: Date?
    private var ended: Date?
    private var sessionId: UUID?
    private var growthCache: (Date, UUID?, TimeInterval)?
    private let clock = ContinuousClock()
    let cloud: CloudAccount
    private let directory: URL
    private var historyURL: URL { directory.appendingPathComponent("sessions.json") }
    private var draftURL: URL { directory.appendingPathComponent("active-session.json") }
    struct Draft: Codable { var phase: Phase; var elapsed: Double; var start: Date; var end: Date?; var checkpoint: Date; var sessionId: UUID? = nil;var pomodoroEnabled:Bool? = nil;var pomodoro:PomodoroState? = nil }
    init(directory: URL? = nil) {
        self.directory = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("MochiStopwatch")
        self.cloud = CloudAccount(directory: self.directory, enabled: directory == nil)
        do {
            try FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
            if let settings=try? JSONDecoder().decode(FocusSettings.self,from:Data(contentsOf:focusSettingsURL)) { pomodoroEnabled=settings.enabled;pomodoro.breakMinutes=settings.breakMinutes }
            if FileManager.default.fileExists(atPath: historyURL.path) { sessions = try JSONDecoder().decode([Session].self, from: Data(contentsOf: historyURL)) }
            if FileManager.default.fileExists(atPath: draftURL.path) {
                let d = try JSONDecoder().decode(Draft.self, from: Data(contentsOf: draftURL))
                pomodoroEnabled=d.pomodoroEnabled ?? pomodoroEnabled;pomodoro=d.pomodoro ?? PomodoroState(breakMinutes:pomodoro.breakMinutes)
                if let deadline=pomodoro.deadline { breakRemaining=max(0,deadline.timeIntervalSinceNow);breakAnchor=clock.now }
                started = d.start; ended = d.end; accumulated = d.elapsed; sessionId = d.sessionId ?? UUID()
                if d.phase == .running { accumulated += max(0, Date().timeIntervalSince(d.checkpoint)); anchor = clock.now }
                phase = d.phase
                refreshMenu()
            }
        } catch { self.error = "Couldn’t read saved data: \(error.localizedDescription)" }
        advancePomodoro();refreshMenu()
        if cloud.owner != nil { sessions=cloud.visible }
        cloud.onChange = { [weak self] in
            guard let self else { return }
            if self.cloud.owner != nil { self.sessions=self.cloud.visible }
            else { self.sessions=(try? JSONDecoder().decode([Session].self,from:Data(contentsOf:self.historyURL))) ?? [] }
        }
    }
    var growthElapsed: TimeInterval {
        let day=Calendar.current.startOfDay(for: phase != .idle ? (started ?? Date()) : Date())
        let active=phase != .idle ? sessionId : nil
        if growthCache?.0 != day || growthCache?.1 != active {
            growthCache=(day,active,sessions.filter { Calendar.current.isDate($0.start,inSameDayAs:day) && $0.id != active }.reduce(0) { $0+$1.duration })
        }
        return (growthCache?.2 ?? 0)+elapsed
    }
    var elapsed: TimeInterval {
        guard let anchor else { return accumulated }
        let c = anchor.duration(to: clock.now).components
        return accumulated + Double(c.seconds) + Double(c.attoseconds) / 1e18
    }
    func start() {
        guard !cloud.authenticating else { return }
        if pomodoroEnabled && pomodoro.stage=="dead" { guard discard() else { return } }
        if phase == .idle { accumulated = 0; started = Date(); ended = nil; sessionId = UUID() }
        guard phase == .idle || phase == .paused else { return }
        startRoundIfNeeded();anchor = clock.now; phase = .running; saved = false; refreshMenu(); checkpoint()
    }
    func resumeFinished() {
        guard phase == .finished, !cloud.authenticating else { return }
        if pomodoroEnabled && elapsed>=pomodoro.target { pomodoro.stage="ready" };startRoundIfNeeded();ended=nil;anchor=clock.now;phase = .running;saved=false;refreshMenu();checkpoint()
    }
    func pause() { advancePomodoro();guard phase == .running else { return }; accumulated = elapsed; anchor = nil; phase = .paused; refreshMenu(); checkpoint() }
    func finish() { guard !pomodoroEnabled else { return };advancePomodoro();guard phase == .running || phase == .paused else { return }; accumulated = elapsed; anchor = nil; ended = Date(); phase = .finished; refreshMenu(); checkpoint() }
    @discardableResult func checkpoint() -> Bool {
        guard let started, phase != .idle else { return true }
        do { try JSONEncoder().encode(Draft(phase: phase, elapsed: elapsed, start: started, end: ended, checkpoint: Date(), sessionId: sessionId,pomodoroEnabled:pomodoroEnabled,pomodoro:pomodoro)).write(to: draftURL, options: .atomic); return true }
        catch { self.error = "Couldn’t preserve the active timer: \(error.localizedDescription)"; return false }
    }
    @discardableResult func discard() -> Bool {
        do {
            if FileManager.default.fileExists(atPath: draftURL.path) { try FileManager.default.removeItem(at: draftURL) }
            anchor = nil; accumulated = 0; started = nil; ended = nil; sessionId = nil; phase = .idle;pomodoro=PomodoroState(breakMinutes:pomodoro.breakMinutes);breakAnchor=nil;focusNotice=nil; refreshMenu()
            return true
        } catch { self.error = "Couldn’t discard session: \(error.localizedDescription)"; return false }
    }
    @discardableResult func replaceHistory(_ updated: [Session]) -> Bool {
        do {
            if cloud.owner != nil { try cloud.replace(updated); sessions=cloud.visible; Task { await cloud.sync() } }
            else { try JSONEncoder().encode(updated).write(to: historyURL, options: .atomic); sessions = updated }
            return true
        } catch { self.error = "Couldn’t update history: \(error.localizedDescription)"; return false }
    }
    @discardableResult func renameSession(_ id: UUID, to name: String) -> Bool {
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty, let index = sessions.firstIndex(where: { $0.id == id }) else { return false }
        var updated = sessions; updated[index].name = clean
        return replaceHistory(updated)
    }
    @discardableResult func deleteSession(_ id: UUID) -> Bool {
        replaceHistory(sessions.filter { $0.id != id })
    }
    @discardableResult func restoreSession(_ session: Session) -> Bool {
        guard !sessions.contains(where: { $0.id == session.id }) else { return false }
        return replaceHistory((sessions + [session]).sorted { $0.start > $1.start })
    }
    func save(_ name: String) {
        guard phase == .finished, let started, let ended else { return }
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let defaults = UserDefaults.standard
        let deviceId = defaults.string(forKey: "cybercat.deviceId") ?? UUID().uuidString
        defaults.set(deviceId, forKey: "cybercat.deviceId")
        if sessionId == nil { sessionId=UUID() }
        guard checkpoint() else { return } // Persist the UUID before committing history.
        let entry = Session(id: sessionId!, name: clean.isEmpty ? "A little focus time" : clean, start: started, end: ended, duration: accumulated,
                            platform: "macos", deviceId: deviceId, deviceName: "Mac")
        let updated = [entry] + sessions.filter { $0.id != entry.id }
        guard replaceHistory(updated) else { return }
        try? FileManager.default.removeItem(at: draftURL)
        phase = .idle; accumulated = 0; self.started = nil; self.ended = nil; sessionId = nil; saved = true;pomodoro=PomodoroState(breakMinutes:pomodoro.breakMinutes);breakAnchor=nil;focusNotice=nil; refreshMenu()
    }
}
func timeText(_ value: TimeInterval) -> String {
    let s = max(0, Int(value)); return String(format: "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
}

enum HistoryPeriod: String, CaseIterable, Identifiable {
    case day = "Day", week = "Week", month = "Month"
    var id: String { rawValue }
    var component: Calendar.Component {
        switch self { case .day: return .day; case .week: return .weekOfYear; case .month: return .month }
    }
    func interval(containing date: Date, calendar: Calendar = .current) -> DateInterval {
        calendar.dateInterval(of: component, for: date)!
    }
    func sessions(in records: [Session], containing date: Date, calendar: Calendar = .current) -> [Session] {
        let range = interval(containing: date, calendar: calendar)
        return records.filter { $0.start >= range.start && $0.start < range.end }.sorted { $0.start > $1.start }
    }
}

func focusDuration(_ duration: TimeInterval) -> String {
    let seconds = max(0, Int(duration))
    let hours = seconds / 3600, minutes = (seconds / 60) % 60
    if hours > 0 { return minutes > 0 ? "\(hours)h \(minutes)m" : "\(hours)h" }
    if minutes > 0 { return "\(minutes)m" }
    return seconds > 0 ? "\(seconds)s" : "0m"
}
func monthCalendarDates(containing date: Date, calendar: Calendar = .current) -> [Date] {
    let start = calendar.dateInterval(of: .month, for: date)!.start
    let offset = (calendar.component(.weekday, from: start) - calendar.firstWeekday + 7) % 7
    let first = calendar.date(byAdding: .day, value: -offset, to: start)!
    return (0..<42).compactMap { calendar.date(byAdding: .day, value: $0, to: first) }
}

// Rewards derive from focus time, including fragments from separate sessions.
func grownTrees(_ seconds: TimeInterval) -> Int { seconds.isFinite ? Int(max(0, seconds) / 3600) : 0 }
func isGarden(_ seconds: TimeInterval) -> Bool { seconds.isFinite && seconds >= 28800 }
func treeText(_ seconds: TimeInterval) -> String { let n=grownTrees(seconds); return "\(n) tree\(n == 1 ? "" : "s")" }
func treeCellText(_ seconds: TimeInterval) -> String { isGarden(seconds) ? "Garden" : grownTrees(seconds)>0 ? treeText(seconds) : seconds>0 ? "Growing" : "—" }
func forestSummary(_ seconds: TimeInterval, daily: [TimeInterval]) -> String {
    let n=daily.filter(isGarden).count
    return treeText(seconds) + (n>0 ? " · \(n) garden day\(n == 1 ? "" : "s")" : "")
}

struct PomodoroState: Codable {
    static let focus:TimeInterval=1500
    var stage="focus"
    var target:TimeInterval=1500
    var breakMinutes=5
    var deadline:Date?
}

/// Device-local blocking policy. Exclusions win over both strategies.
struct FocusAppPolicy {
    static func blocks(id:String,path:String,all:Bool,selected:[String],selectedPaths:[String],excluded:[String],excludedPaths:[String],browsers:Set<String>)->Bool {
        func matches(_ ids:[String],_ paths:[String])->Bool { ids.contains(id) || paths.contains { path == $0 || path.hasPrefix($0+"/") } }
        if matches(excluded,excludedPaths) { return false }
        return all ? !browsers.contains(id) : matches(selected,selectedPaths)
    }
}
