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
    @Published var sessions: [Session] = [] { didSet { growthCache=nil } }
    @Published var error: String?
    @Published var saved = false
    @Published var petVisible = true
    @Published var menuTime = "00:00:00"
    func refreshMenu() { let text = timeText(elapsed); if menuTime != text { menuTime = text } }
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
    struct Draft: Codable { var phase: Phase; var elapsed: Double; var start: Date; var end: Date?; var checkpoint: Date; var sessionId: UUID? = nil }
    init(directory: URL? = nil) {
        self.directory = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("MochiStopwatch")
        self.cloud = CloudAccount(directory: self.directory, enabled: directory == nil)
        do {
            try FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
            if FileManager.default.fileExists(atPath: historyURL.path) { sessions = try JSONDecoder().decode([Session].self, from: Data(contentsOf: historyURL)) }
            if FileManager.default.fileExists(atPath: draftURL.path) {
                let d = try JSONDecoder().decode(Draft.self, from: Data(contentsOf: draftURL))
                started = d.start; ended = d.end; accumulated = d.elapsed; sessionId = d.sessionId ?? UUID()
                if d.phase == .running { accumulated += max(0, Date().timeIntervalSince(d.checkpoint)); anchor = clock.now }
                phase = d.phase
                refreshMenu()
            }
        } catch { self.error = "Couldn’t read saved data: \(error.localizedDescription)" }
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
        if phase == .idle { accumulated = 0; started = Date(); ended = nil; sessionId = UUID() }
        guard phase == .idle || phase == .paused else { return }
        anchor = clock.now; phase = .running; saved = false; refreshMenu(); checkpoint()
    }
    func pause() { guard phase == .running else { return }; accumulated = elapsed; anchor = nil; phase = .paused; refreshMenu(); checkpoint() }
    func finish() { guard phase == .running || phase == .paused else { return }; accumulated = elapsed; anchor = nil; ended = Date(); phase = .finished; refreshMenu(); checkpoint() }
    @discardableResult func checkpoint() -> Bool {
        guard let started, phase != .idle else { return true }
        do { try JSONEncoder().encode(Draft(phase: phase, elapsed: elapsed, start: started, end: ended, checkpoint: Date(), sessionId: sessionId)).write(to: draftURL, options: .atomic); return true }
        catch { self.error = "Couldn’t preserve the active timer: \(error.localizedDescription)"; return false }
    }
    @discardableResult func discard() -> Bool {
        do {
            if FileManager.default.fileExists(atPath: draftURL.path) { try FileManager.default.removeItem(at: draftURL) }
            anchor = nil; accumulated = 0; started = nil; ended = nil; sessionId = nil; phase = .idle; refreshMenu()
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
        phase = .idle; accumulated = 0; self.started = nil; self.ended = nil; sessionId = nil; saved = true; refreshMenu()
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
