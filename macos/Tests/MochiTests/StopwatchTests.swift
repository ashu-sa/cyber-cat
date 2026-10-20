import Foundation
func XCTAssertEqual<T: Equatable>(_ a: T, _ b: T) { precondition(a == b) }
func XCTAssertEqual(_ a: Double, _ b: Double, accuracy: Double) { precondition(abs(a-b) <= accuracy) }
func XCTAssertGreaterThan(_ a: Double, _ b: Double) { precondition(a > b) }

@main struct StopwatchTests {
    @MainActor static func main() async throws {
        let tests = StopwatchTests()
        try await tests.testPauseResumeSaveAndReload()
        try tests.testRecoverPausedAndFinished()
        try await tests.testRunningRecoveryAndDiscard()
        try tests.testHistoryEditingAndPeriods()
        tests.testMonthCalendar()
        tests.testFormatting()
        print("PASS: timing, recovery, rename persistence, delete/restore, calendar periods and totals, formatting")
    }
    @MainActor func testPauseResumeSaveAndReload() async throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let model = Stopwatch(directory: dir)
        model.start()
        try await Task.sleep(for: .milliseconds(120))
        model.pause()
        let paused = model.elapsed
        try await Task.sleep(for: .milliseconds(100))
        XCTAssertEqual(model.elapsed, paused, accuracy: 0.001)
        model.start()
        try await Task.sleep(for: .milliseconds(100))
        model.finish()
        XCTAssertGreaterThan(model.elapsed, paused + 0.08)
        let duration = model.elapsed
        model.save("  Write a poem  ")
        XCTAssertEqual(model.phase, .idle)
        let loaded = Stopwatch(directory: dir)
        XCTAssertEqual(loaded.sessions.first?.name, "Write a poem")
        XCTAssertEqual(loaded.sessions.first!.duration, duration, accuracy: 0.001)
        XCTAssertEqual(loaded.phase, .idle)
    }
    @MainActor func testRecoverPausedAndFinished() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let model = Stopwatch(directory: dir)
        model.start(); model.pause()
        let recovered = Stopwatch(directory: dir)
        XCTAssertEqual(recovered.phase, .paused)
        XCTAssertEqual(recovered.elapsed, model.elapsed, accuracy: 0.001)
        recovered.finish()
        let finished = Stopwatch(directory: dir)
        XCTAssertEqual(finished.phase, .finished)
        finished.save(" ")
        XCTAssertEqual(finished.sessions.first?.name, "A little focus time")
    }
    @MainActor func testRunningRecoveryAndDiscard() async throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        let model = Stopwatch(directory: dir)
        model.start()
        try await Task.sleep(for: .milliseconds(100))
        let recovered = Stopwatch(directory: dir)
        XCTAssertEqual(recovered.phase, .running)
        XCTAssertGreaterThan(recovered.elapsed, 0.08)
        recovered.refreshMenu()
        XCTAssertEqual(recovered.menuTime, timeText(recovered.elapsed))
        precondition(recovered.discard())
        XCTAssertEqual(recovered.phase, .idle)
        XCTAssertEqual(Stopwatch(directory: dir).phase, .idle)
        XCTAssertEqual(recovered.sessions.count, 0)
    }
    @MainActor func testHistoryEditingAndPeriods() throws {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: dir) }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        calendar.firstWeekday = 2; calendar.minimumDaysInFirstWeek = 4
        func date(_ day: Int, _ month: Int = 10) -> Date { calendar.date(from: DateComponents(year: 2026, month: month, day: day))! }
        let records = [Session(name: "First", start: date(19), end: date(19).addingTimeInterval(3600), duration: 3600), Session(name: "Second", start: date(20), end: date(20).addingTimeInterval(1800), duration: 1800), Session(name: "Next week", start: date(26), end: date(26), duration: 300), Session(name: "Next month", start: date(1, 11), end: date(1, 11), duration: 60)]
        XCTAssertEqual(HistoryPeriod.day.sessions(in: records, containing: date(19), calendar: calendar).count, 1)
        let week = HistoryPeriod.week.sessions(in: records, containing: date(19), calendar: calendar)
        XCTAssertEqual(week.count, 2)
        XCTAssertEqual(week.reduce(0) { $0 + $1.duration }, 5400, accuracy: 0.001)
        XCTAssertEqual(HistoryPeriod.month.sessions(in: records, containing: date(19), calendar: calendar).count, 3)
        let model = Stopwatch(directory: dir)
        precondition(model.replaceHistory(records))
        precondition(model.renameSession(records[0].id, to: "  Renamed  "))
        XCTAssertEqual(Stopwatch(directory: dir).sessions[0].name, "Renamed")
        precondition(!model.renameSession(records[0].id, to: "  "))
        precondition(model.deleteSession(records[1].id))
        XCTAssertEqual(Stopwatch(directory: dir).sessions.count, 3)
        precondition(model.restoreSession(records[1]))
        XCTAssertEqual(Stopwatch(directory: dir).sessions.count, 4)
        precondition(!model.restoreSession(records[1]))
    }
    func testMonthCalendar() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "America/New_York")!
        calendar.firstWeekday = 2
        for month in 1...12 {
            let date = calendar.date(from: DateComponents(year: 2026, month: month, day: 15))!
            let grid = monthCalendarDates(containing: date, calendar: calendar)
            XCTAssertEqual(grid.count, 42)
            XCTAssertEqual(calendar.component(.weekday, from: grid[0]), 2)
            let actual = grid.filter { calendar.component(.month, from: $0) == month }
            XCTAssertEqual(actual.count, calendar.range(of: .day, in: .month, for: date)!.count)
            for index in 1..<grid.count { XCTAssertEqual(calendar.dateComponents([.day], from: grid[index-1], to: grid[index]).day!, 1) }
        }
        XCTAssertEqual(focusDuration(7200), "2h")
        XCTAssertEqual(focusDuration(4500), "1h 15m")
        XCTAssertEqual(focusDuration(0), "0m")
        XCTAssertEqual(focusDuration(29), "29s")
    }
    func testFormatting() {
        XCTAssertEqual(timeText(3661), "01:01:01")
        XCTAssertEqual(timeText(360000), "100:00:00")
    }
}
