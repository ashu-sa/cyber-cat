import SwiftUI
import AppKit

struct Session: Codable, Identifiable {
    var id = UUID()
    var name: String
    var start: Date
    var end: Date
    var duration: TimeInterval
}

@MainActor final class Stopwatch: ObservableObject {
    enum Phase: String, Codable { case idle, running, paused, finished }
    @Published var phase: Phase = .idle
    @Published var sessions: [Session] = []
    @Published var error: String?
    @Published var saved = false
    @Published var petVisible = true
    @Published var menuTime = "00:00:00"
    func refreshMenu() { let text = timeText(elapsed); if menuTime != text { menuTime = text } }
    private var accumulated: TimeInterval = 0
    private var anchor: ContinuousClock.Instant?
    private var started: Date?
    private var ended: Date?
    private let clock = ContinuousClock()
    private let directory: URL
    private var historyURL: URL { directory.appendingPathComponent("sessions.json") }
    private var draftURL: URL { directory.appendingPathComponent("active-session.json") }
    struct Draft: Codable { var phase: Phase; var elapsed: Double; var start: Date; var end: Date?; var checkpoint: Date }
    init(directory: URL? = nil) {
        self.directory = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("MochiStopwatch")
        do {
            try FileManager.default.createDirectory(at: self.directory, withIntermediateDirectories: true)
            if FileManager.default.fileExists(atPath: historyURL.path) { sessions = try JSONDecoder().decode([Session].self, from: Data(contentsOf: historyURL)) }
            if FileManager.default.fileExists(atPath: draftURL.path) {
                let d = try JSONDecoder().decode(Draft.self, from: Data(contentsOf: draftURL))
                started = d.start; ended = d.end; accumulated = d.elapsed
                if d.phase == .running { accumulated += max(0, Date().timeIntervalSince(d.checkpoint)); anchor = clock.now }
                phase = d.phase
                refreshMenu()
            }
        } catch { self.error = "Couldn’t read saved data: \(error.localizedDescription)" }
    }
    var elapsed: TimeInterval {
        guard let anchor else { return accumulated }
        let c = anchor.duration(to: clock.now).components
        return accumulated + Double(c.seconds) + Double(c.attoseconds) / 1e18
    }
    func start() {
        if phase == .idle { accumulated = 0; started = Date(); ended = nil }
        guard phase == .idle || phase == .paused else { return }
        anchor = clock.now; phase = .running; saved = false; refreshMenu(); checkpoint()
    }
    func pause() { guard phase == .running else { return }; accumulated = elapsed; anchor = nil; phase = .paused; refreshMenu(); checkpoint() }
    func finish() { guard phase == .running || phase == .paused else { return }; accumulated = elapsed; anchor = nil; ended = Date(); phase = .finished; refreshMenu(); checkpoint() }
    func checkpoint() {
        guard let started, phase != .idle else { return }
        do { try JSONEncoder().encode(Draft(phase: phase, elapsed: elapsed, start: started, end: ended, checkpoint: Date())).write(to: draftURL, options: .atomic) }
        catch { self.error = "Couldn’t preserve the active timer: \(error.localizedDescription)" }
    }
    @discardableResult func discard() -> Bool {
        do {
            if FileManager.default.fileExists(atPath: draftURL.path) { try FileManager.default.removeItem(at: draftURL) }
            anchor = nil; accumulated = 0; started = nil; ended = nil; phase = .idle; refreshMenu()
            return true
        } catch { self.error = "Couldn’t discard session: \(error.localizedDescription)"; return false }
    }
    @discardableResult func replaceHistory(_ updated: [Session]) -> Bool {
        do {
            try JSONEncoder().encode(updated).write(to: historyURL, options: .atomic)
            sessions = updated
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
        let entry = Session(name: clean.isEmpty ? "A little focus time" : clean, start: started, end: ended, duration: accumulated)
        let updated = [entry] + sessions
        do {
            try JSONEncoder().encode(updated).write(to: historyURL, options: .atomic)
            try? FileManager.default.removeItem(at: draftURL)
            sessions = updated; phase = .idle; accumulated = 0; self.started = nil; self.ended = nil; saved = true; refreshMenu()
        } catch { self.error = "Your session hasn’t been saved. Please try again. \(error.localizedDescription)" }
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
