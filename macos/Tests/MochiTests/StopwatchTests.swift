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
        try tests.testSaveRecoveryDoesNotDuplicate()
        tests.testMonthCalendar()
        tests.testBlockingPolicy()
        tests.testFormatting()
        tests.testForest()
        try tests.testGooglePKCE()
        try tests.testPomodoroRecoveryAndCaps()
        try await tests.testResumeFinishedExcludesGap()
        print("PASS: timing, recovery, rename persistence, delete/restore, calendar periods and totals, formatting")
    }
    func testBlockingPolicy() {
        func blocks(_ id:String,_ path:String="",all:Bool=false)->Bool {
            FocusAppPolicy.blocks(id:id,path:path,all:all,selected:["com.google.android.studio","browser"],selectedPaths:["/Applications/Android Studio.app"],excluded:["music"],excludedPaths:["/Applications/Allowed.app"],browsers:["browser"])
        }
        precondition(blocks("com.google.android.studio"))
        precondition(blocks("java.helper","/Applications/Android Studio.app/Contents/jbr/bin/java"))
        precondition(!blocks("java.helper","/Applications/Android Studio.app.other/Contents/java"))
        precondition(blocks("browser"));precondition(!blocks("browser",all:true))
        precondition(!blocks("music",all:true));precondition(blocks("editor",all:true))
        precondition(!blocks("editor","/Applications/Allowed.app/Contents/MacOS/editor",all:true))
    }
    @MainActor func testPomodoroRecoveryAndCaps() throws {
        let dir=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at:dir) }
        let model=Stopwatch(directory:dir);model.selectMode(true,minutes:5)
        let now=Date()
        let draft=Stopwatch.Draft(phase:.running,elapsed:1490,start:now.addingTimeInterval(-1490),end:nil,checkpoint:now.addingTimeInterval(-20),sessionId:UUID(),pomodoroEnabled:true,pomodoro:PomodoroState())
        try JSONEncoder().encode(draft).write(to:dir.appendingPathComponent("active-session.json"))
        let capped=Stopwatch(directory:dir);XCTAssertEqual(capped.phase,.paused);XCTAssertEqual(capped.elapsed,1500,accuracy:0.01);XCTAssertEqual(capped.pomodoro.stage,"break")
        precondition(capped.displayTime>288 && capped.displayTime<=290)
        capped.pomodoro.deadline=Date().addingTimeInterval(-1);capped.checkpoint()
        let ready=Stopwatch(directory:dir);XCTAssertEqual(ready.pomodoro.stage,"ready");XCTAssertEqual(ready.phase,.paused);XCTAssertEqual(ready.elapsed,1500,accuracy:0.01)
        ready.start();XCTAssertEqual(ready.phase,.running);XCTAssertEqual(ready.pomodoro.target,3000,accuracy:0.01)
        ready.pause();ready.finish();XCTAssertEqual(ready.phase,.paused)
        ready.cancelPomodoro();XCTAssertEqual(ready.pomodoro.stage,"dead");XCTAssertEqual(ready.sessions.count,1);XCTAssertEqual(ready.sessions[0].duration,1500,accuracy:0.01)
        let dead=Stopwatch(directory:dir);XCTAssertEqual(dead.pomodoro.stage,"dead");dead.start();XCTAssertEqual(dead.pomodoro.stage,"focus");precondition(dead.elapsed<1);XCTAssertEqual(dead.pomodoro.target,1500,accuracy:0.01)
        dead.finish();XCTAssertEqual(dead.phase,.running);dead.cancelPomodoro();XCTAssertEqual(dead.sessions.count,1)
    }
    func testGooglePKCE() throws {
        let flow=try GooglePKCE(); let other=try GooglePKCE()
        precondition(flow.verifier.count>=43 && flow.verifier != other.verifier)
        let items=URLComponents(url:flow.url,resolvingAgainstBaseURL:false)!.queryItems!
        XCTAssertEqual(items.first { $0.name=="code_challenge_method" }?.value,"s256")
        precondition(!flow.url.absoluteString.contains(flow.verifier))
        let base="focanopy://auth/callback?state="+flow.state
        XCTAssertEqual(try flow.code(URL(string:base+"&code=once")!),"once")
        for value in ["other://auth/callback?state="+flow.state+"&code=x", "focanopy://wrong/callback?state="+flow.state+"&code=x", "focanopy://auth/callback?state=wrong&code=x",base+"&state="+flow.state+"&code=x",base+"&code=x&code=y",base+"&error=access_denied"] {
            do { _ = try flow.code(URL(string:value)!); preconditionFailure("Invalid Google callback accepted") } catch {}
        }
        let json=Data(#"{"access_token":"a","refresh_token":"r","user":{"id":"google-user","email":"focus@example.com","user_metadata":{"full_name":"Tree Grower"}}}"#.utf8)
        let login=try cloudDecoder().decode(CloudLogin.self,from:json)
        XCTAssertEqual(login.user.userMetadata?.fullName,"Tree Grower")
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
        XCTAssertEqual(loaded.sessions.first?.deviceLabel, "Mac")
        XCTAssertEqual(loaded.sessions.first?.platform, "macos")
        precondition(loaded.sessions.first?.deviceId != nil)
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
    @MainActor func testSaveRecoveryDoesNotDuplicate() throws {
        let dir=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at:dir) }
        let model=Stopwatch(directory:dir); model.start(); model.finish()
        let draft=try JSONDecoder().decode(Stopwatch.Draft.self,from:Data(contentsOf:dir.appendingPathComponent("active-session.json")))
        let committed=Session(id:draft.sessionId!,name:"Before interruption",start:draft.start,end:draft.end!,duration:draft.elapsed)
        precondition(model.replaceHistory([committed])) // Simulate a crash before draft cleanup.
        let recovered=Stopwatch(directory:dir); recovered.save("Recovered")
        XCTAssertEqual(recovered.sessions.count,1)
        XCTAssertEqual(recovered.sessions[0].id,committed.id)
        XCTAssertEqual(recovered.sessions[0].name,"Recovered")
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
    @MainActor func testResumeFinishedExcludesGap() async throws {
        let dir=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at:dir) }
        try FileManager.default.createDirectory(at:dir,withIntermediateDirectories:true)
        let id=UUID(), old=Date().addingTimeInterval(-3600)
        let draft=Stopwatch.Draft(phase:.finished,elapsed:1200,start:old.addingTimeInterval(-1200),end:old,checkpoint:old,sessionId:id)
        try JSONEncoder().encode(draft).write(to:dir.appendingPathComponent("active-session.json"))
        let timer=Stopwatch(directory:dir);timer.resumeFinished();timer.resumeFinished()
        try await Task.sleep(for:.milliseconds(80));timer.pause()
        precondition(timer.elapsed>=1200 && timer.elapsed<1201,"Finished gap must be excluded")
        timer.finish();timer.save("Continued work");precondition(timer.sessions.count==1 && timer.sessions[0].id==id)
        timer.start();timer.finish();precondition(timer.discard());precondition(timer.elapsed==0 && timer.sessions.count==1)
    }
    func testForest() {
        for (seconds,count):(Double,Int) in [(0,0),(1799,0),(1800,0),(3599,0),(3600,1),(5400,1),(7200,2),(28799,7),(28800,8),(28801,8)] {
            XCTAssertEqual(grownTrees(seconds),count); XCTAssertEqual(isGarden(seconds),seconds>=28800)
        }
        XCTAssertEqual(treeText(1800+1800),"1 tree")
        XCTAssertEqual(forestSummary(28800,daily:Array(repeating:3600,count:8)),"8 trees")
        XCTAssertEqual(forestSummary(28800,daily:[28800]),"8 trees · 1 garden day")
    }
    func testFormatting() {
        let legacy = try! JSONDecoder().decode(Session.self, from: Data("{\"id\":\"55CE8492-6E44-4142-9242-AC3C0D75A6A4\",\"name\":\"Old\",\"start\":0,\"end\":60,\"duration\":60}".utf8))
        XCTAssertEqual(legacy.deviceLabel, "This device")
        XCTAssertEqual(timeText(3661), "01:01:01")
        XCTAssertEqual(timeText(360000), "100:00:00")
    }
}
