import SwiftUI
import AppKit
import Carbon
import UserNotifications
import UniformTypeIdentifiers
import ApplicationServices

final class FloatingPanel: NSPanel {
    override var canBecomeKey: Bool { true }
    override var canBecomeMain: Bool { false }
    override func close() { orderOut(nil) }
}
@MainActor final class AppDelegate: NSObject, NSApplicationDelegate, NSWindowDelegate, UNUserNotificationCenterDelegate {
    let model = Stopwatch(directory: ProcessInfo.processInfo.environment["MOCHI_TEST_DATA_DIRECTORY"].map { URL(fileURLWithPath: $0) })
    var panel: NSPanel!
    var focusWindow:NSWindow?
    var treePreviewWindow:NSWindow?
    var focusShield:NSPanel?
    var blockingShields:[NSPanel]=[]
    var blockingWatchdog:Timer?
    var resolvedAppPaths:[String:String]=[:]
    lazy var browserIDs:Set<String> = Set(NSWorkspace.shared.urlsForApplications(toOpen:URL(string:"https://example.com")!).compactMap { Bundle(url:$0)?.bundleIdentifier })
    var appObserver:NSObjectProtocol?
    var historyWindow: NSWindow?
    var accountWindow: NSWindow?
    let controlsPopover = NSPopover()
    var hotKey: EventHotKeyRef?
    var hotKeyHandler: EventHandlerRef?
    var sessionHotKeys: [UInt32: EventHotKeyRef] = [:]
    var displayTimer: Timer?
    var ticks = 0
    var dialogOpen = false
    var pendingTaskName = ""
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
        UNUserNotificationCenter.current().delegate=self
        if let url=Bundle.main.url(forResource:"AppIcon",withExtension:"icns"),let icon=NSImage(contentsOf:url) { NSApp.applicationIconImage=icon }
        panel = FloatingPanel(contentRect:NSRect(x:0,y:0,width:150,height:100),styleMask:[.borderless,.nonactivatingPanel],backing:.buffered,defer:false)
        panel.level = .floating
        panel.collectionBehavior = [.canJoinAllSpaces,.fullScreenAuxiliary]
        panel.isFloatingPanel = true; panel.hidesOnDeactivate = false
        panel.isOpaque = false; panel.backgroundColor = .clear; panel.hasShadow = false
        panel.isReleasedWhenClosed = false
        panel.contentView = NSHostingView(rootView:PetView(model:model,showOptions:{ [weak self] view in self?.showOptions(from: view) },primary:{ [weak self] in
            guard let self else { return }
            switch self.model.phase {
            case .running: self.pause()
            case .finished: self.finish()
            case .idle, .paused: self.start()
            }
        },finish:{ [weak self] in self?.finish() },resumeFinished:{ [weak self] in self?.start() },reset:{ [weak self] in self?.cancelSession() }))
        restorePosition(); panel.delegate = self
        registerShortcut(); showPet()
        Task { await model.cloud.sync() }
        displayTimer = Timer.scheduledTimer(withTimeInterval:1,repeats:true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }; self.model.advancePomodoro();self.model.refreshMenu(); self.updateSessionShortcuts();self.ticks += 1
                if let notice=self.model.focusNotice {
                    self.model.focusNotice=nil
                    let content=UNMutableNotificationContent();content.title="Focanopy · Flower focus";content.body=notice;content.sound = .default
                    try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier:UUID().uuidString,content:content,trigger:nil))
                    self.showPet()
                }
                if !self.blockingActive { self.clearBlockingShields();self.blockingWatchdog?.invalidate();self.blockingWatchdog=nil }
                else if self.blockingWatchdog == nil {
                    let timer=Timer(timeInterval:0.25,repeats:true) { [weak self] _ in Task { @MainActor in
                        guard let self else { return };if let front=NSWorkspace.shared.frontmostApplication { self.blockIfSelected(front) }
                    } };self.blockingWatchdog=timer;RunLoop.main.add(timer,forMode:.common)
                }
                if let front=NSWorkspace.shared.frontmostApplication { self.blockIfSelected(front) }
                if self.ticks % 30 == 0 { Task { await self.model.cloud.sync() } }
                if self.ticks % 15 == 0 && self.model.phase == .running { self.model.checkpoint() }
            }
        }
        appObserver=NSWorkspace.shared.notificationCenter.addObserver(forName:NSWorkspace.didActivateApplicationNotification,object:nil,queue:.main) { [weak self] note in
            guard let app=note.userInfo?[NSWorkspace.applicationUserInfoKey] as? NSRunningApplication else { return }
            Task { @MainActor in self?.blockIfSelected(app) }
        }
        if let displayTimer { RunLoop.main.add(displayTimer, forMode: .common) }
        NSWorkspace.shared.notificationCenter.addObserver(self,selector:#selector(woke),name:NSWorkspace.didWakeNotification,object:nil)
        NSWorkspace.shared.notificationCenter.addObserver(self,selector:#selector(sleeping),name:NSWorkspace.willSleepNotification,object:nil)
        NotificationCenter.default.addObserver(self,selector:#selector(screensChanged),name:NSApplication.didChangeScreenParametersNotification,object:nil)
    }
    nonisolated func userNotificationCenter(_ center:UNUserNotificationCenter,willPresent notification:UNNotification,withCompletionHandler completionHandler:@escaping (UNNotificationPresentationOptions)->Void) { completionHandler([.banner,.sound]) }
    func showTreePreview() {
        let w=NSWindow(contentRect:NSRect(x:0,y:0,width:576,height:600),styleMask:[.titled,.closable],backing:.buffered,defer:false)
        w.title="Focanopy · Tree lifecycle preview";w.isReleasedWhenClosed=false;w.contentView=NSHostingView(rootView:TreeLifecyclePreview());w.center()
        treePreviewWindow?.close();treePreviewWindow=w;NSApp.activate(ignoringOtherApps:true);w.makeKeyAndOrderFront(nil)
    }
    func showFocusSettings() {
        if focusWindow == nil {
            let w=NSWindow(contentRect:NSRect(x:0,y:0,width:480,height:700),styleMask:[.titled,.closable],backing:.buffered,defer:false)
            w.title="Focanopy · Focus garden";w.isReleasedWhenClosed=false
            w.contentView=NSHostingView(rootView:FocusPreferences(model:model,onPreview:{[weak self] in self?.showTreePreview()}));w.center();focusWindow=w
        }
        NSApp.activate(ignoringOtherApps:true);focusWindow?.makeKeyAndOrderFront(nil)
    }
    var blockingActive:Bool {
        model.phase == .running && (!model.pomodoroEnabled || model.pomodoro.stage=="focus") && UserDefaults.standard.bool(forKey:"focusBlocking")
    }
    func clearBlockingShields() { blockingShields.forEach { $0.orderOut(nil) };blockingShields=[];focusShield?.orderOut(nil) }
    func blockingPaths(_ key:String)->[String] {
        let prefs=UserDefaults.standard
        let resolved=(prefs.stringArray(forKey:key) ?? []).compactMap { id -> String? in
            if let path=resolvedAppPaths[id] { return path.isEmpty ? nil : path }
            let path=NSWorkspace.shared.urlForApplication(withBundleIdentifier:id)?.path ?? ""
            resolvedAppPaths[id]=path;return path.isEmpty ? nil : path
        }
        return (prefs.stringArray(forKey:key+"Paths") ?? [])+resolved
    }
    func blockIfSelected(_ app:NSRunningApplication) {
        model.advancePomodoro()
        guard blockingActive else { clearBlockingShields();return }
        let prefs=UserDefaults.standard
        let id=app.bundleIdentifier ?? ""
        let path=app.executableURL?.path ?? app.bundleURL?.path ?? ""
        let protected=["com.apple.finder","com.apple.dock","com.apple.systempreferences","com.apple.loginwindow","com.apple.SecurityAgent","com.apple.controlcenter","com.apple.notificationcenterui","com.apple.systemuiserver"]
        guard app.processIdentifier != ProcessInfo.processInfo.processIdentifier,app.activationPolicy == .regular,!protected.contains(id) else { clearBlockingShields();return }
        let blocked=FocusAppPolicy.blocks(id:id,path:path,all:prefs.bool(forKey:"focusBlockAll"),selected:prefs.stringArray(forKey:"focusBlockedApps") ?? [],selectedPaths:blockingPaths("focusBlockedApps"),excluded:prefs.stringArray(forKey:"focusExcludedApps") ?? [],excludedPaths:blockingPaths("focusExcludedApps"),browsers:browserIDs)
        guard blocked else { clearBlockingShields();return }
        // AX minimization covers Java/JetBrains windows that may resist application hiding.
        if AXIsProcessTrusted() {
            let target=AXUIElementCreateApplication(app.processIdentifier);var value:CFTypeRef?
            if AXUIElementCopyAttributeValue(target,kAXWindowsAttribute as CFString,&value) == .success,let windows=value as? [AXUIElement] {
                for window in windows { AXUIElementSetAttributeValue(window,kAXMinimizedAttribute as CFString,kCFBooleanTrue) }
            }
        }
        _=app.hide()
        guard blockingShields.isEmpty else { return }
        for screen in NSScreen.screens {
            let shield=NSPanel(contentRect:screen.frame,styleMask:[.borderless,.nonactivatingPanel],backing:.buffered,defer:false)
            shield.level = .screenSaver;shield.collectionBehavior=[.canJoinAllSpaces,.fullScreenAuxiliary];shield.hidesOnDeactivate=false;shield.isReleasedWhenClosed=false
            shield.contentView=NSHostingView(rootView:VStack(spacing:20) {
                Text("🌳 Keep growing").font(.largeTitle.bold())
                Text("This app is blocked while your timer runs.").font(.title3)
                Text("Your work stays open. Switch to an allowed app, or pause below.").foregroundStyle(.secondary)
                Button("Pause timer & allow apps") { self.model.pause();self.clearBlockingShields() }.keyboardShortcut(.escape,modifiers:[])
                Button("Turn app blocking off") { prefs.set(false,forKey:"focusBlocking");self.clearBlockingShields() }
            }.frame(maxWidth:.infinity,maxHeight:.infinity).background(Color(red:0.08,green:0.14,blue:0.10)).preferredColorScheme(.dark))
            blockingShields.append(shield);shield.orderFrontRegardless()
        }
    }
    func showAccount() {
        if accountWindow == nil {
            let w=NSWindow(contentRect:NSRect(x:0,y:0,width:460,height:570),styleMask:[.titled,.closable],backing:.buffered,defer:false)
            w.title="Focanopy · Account"; w.isReleasedWhenClosed=false; w.contentView=NSHostingView(rootView:AccountView(model:model,onClose:{ [weak self] in self?.accountWindow?.orderOut(nil) })); w.center(); accountWindow=w
        }
        NSApp.activate(ignoringOtherApps:true); accountWindow?.makeKeyAndOrderFront(nil)
    }
    func registerShortcut() {
        var eventType = EventTypeSpec(eventClass: OSType(kEventClassKeyboard), eventKind: UInt32(kEventHotKeyPressed))
        let installed = InstallEventHandler(GetApplicationEventTarget(), { _, event, context in
            guard let event, let context else { return OSStatus(eventNotHandledErr) }
            var identifier = EventHotKeyID()
            let result = GetEventParameter(event, EventParamName(kEventParamDirectObject), EventParamType(typeEventHotKeyID), nil, MemoryLayout<EventHotKeyID>.size, nil, &identifier)
            guard result == noErr, identifier.signature == 0x43595054, (1...4).contains(identifier.id) else { return OSStatus(eventNotHandledErr) }
            let delegate = Unmanaged<AppDelegate>.fromOpaque(context).takeUnretainedValue()
            let shortcutID = identifier.id
            Task { @MainActor in
                if shortcutID == 1 { delegate.togglePet() } else { delegate.handleSessionShortcut(shortcutID) }
            }
            return noErr
        }, 1, &eventType, Unmanaged.passUnretained(self).toOpaque(), &hotKeyHandler)
        guard installed == noErr else { model.error = "Couldn’t enable the global shortcut. Show Pet and Hide Pet remain available."; return }
        let result = RegisterEventHotKey(UInt32(kVK_ANSI_R), UInt32(cmdKey), EventHotKeyID(signature: 0x43595054, id: 1), GetApplicationEventTarget(), 0, &hotKey)
        if result != noErr { model.error = "Command-R is unavailable (error \(result)). Another app may already have registered it. Use Show Pet or Hide Pet from the menu bar." }
    }
    func updateSessionShortcuts() {
        guard hotKeyHandler != nil, panel != nil else { return }
        guard panel.isVisible, model.petVisible, !dialogOpen else {
            for reference in sessionHotKeys.values { UnregisterEventHotKey(reference) }
            sessionHotKeys.removeAll()
            return
        }
        if model.pomodoroEnabled,let ref=sessionHotKeys.removeValue(forKey:4) { UnregisterEventHotKey(ref) }
        let shortcuts: [(UInt32, Int, String)] = [(2, kVK_ANSI_S, "S"), (3, kVK_ANSI_P, "P")]+(model.pomodoroEnabled ? []:[(4,kVK_ANSI_F,"F")])
        for (id, key, label) in shortcuts where sessionHotKeys[id] == nil {
            var reference: EventHotKeyRef?
            let result = RegisterEventHotKey(UInt32(key), UInt32(cmdKey), EventHotKeyID(signature: 0x43595054, id: id), GetApplicationEventTarget(), 0, &reference)
            if result == noErr, let reference { sessionHotKeys[id] = reference }
            else { model.error = "Command-\(label) is unavailable (error \(result)). The pet buttons remain available." }
        }
    }
    func handleSessionShortcut(_ id: UInt32) {
        guard panel != nil, panel.isVisible, model.petVisible, !dialogOpen else { return }
        switch id {
        case 2: if model.phase == .idle || model.phase == .paused { start() }
        case 3: if model.phase == .running { pause() }
        case 4: if model.phase != .idle { finish() }
        default: break
        }
    }
    @objc func togglePet() {
        guard panel != nil else { return }
        if panel.isVisible { hidePet() } else { showPet() }
    }
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool { showPet(); return true }
    @objc func woke() { model.refreshMenu() }
    @objc func sleeping() { model.checkpoint() }
    @objc func screensChanged() { clampPosition(); savePosition() }
    func restorePosition() {
        let defaults = UserDefaults.standard
        if defaults.object(forKey:"petX") != nil {
            panel.setFrameOrigin(NSPoint(x:defaults.double(forKey:"petX"),y:defaults.double(forKey:"petY")))
        } else if let screen = NSScreen.main {
            panel.setFrameOrigin(NSPoint(x:screen.visibleFrame.maxX-170,y:screen.visibleFrame.minY+55))
        }
        clampPosition()
    }
    func clampPosition() {
        let frame = panel.frame
        let screen = NSScreen.screens.max { a,b in
            let ia = a.visibleFrame.intersection(frame), ib = b.visibleFrame.intersection(frame)
            return (ia.isNull ? 0 : ia.width*ia.height) < (ib.isNull ? 0 : ib.width*ib.height)
        } ?? NSScreen.main
        guard let bounds = screen?.visibleFrame else { return }
        panel.setFrameOrigin(NSPoint(x:min(max(frame.minX,bounds.minX),bounds.maxX-frame.width),y:min(max(frame.minY,bounds.minY),bounds.maxY-frame.height)))
    }
    func savePosition() { UserDefaults.standard.set(panel.frame.minX,forKey:"petX"); UserDefaults.standard.set(panel.frame.minY,forKey:"petY") }
    func windowDidMove(_ notification: Notification) { if (notification.object as? NSWindow) === panel { savePosition() } }
    @objc func showPet() { guard panel != nil else { return }; clampPosition(); model.petVisible = true; panel.orderFrontRegardless(); updateSessionShortcuts() }
    @objc func hidePet() { controlsPopover.close(); savePosition(); model.petVisible = false; panel.orderOut(nil); updateSessionShortcuts() }
    @objc func closePet() { hidePet() }
    @objc func start() { if model.phase == .finished { model.resumeFinished() } else { model.start() }; showPet() }
    @objc func pause() { model.pause() }
    @objc func resume() { model.start() }
    @objc func finish() { guard !model.pomodoroEnabled || model.phase == .finished else { return };model.finish(); nameSession() }
    @discardableResult func nameSession(revealHistory: Bool = true) -> Bool {
        guard model.phase == .finished, !dialogOpen else { return false }
        dialogOpen = true; updateSessionShortcuts()
        defer { dialogOpen = false; updateSessionShortcuts() }
        NSApp.activate(ignoringOtherApps:true)
        let alert = NSAlert(); alert.icon=Bundle.main.url(forResource:"AppIcon",withExtension:"icns").flatMap { NSImage(contentsOf:$0) } ?? NSApp.applicationIconImage; alert.messageText = "What did you work on?"
        alert.informativeText = "Nice work! \(timeText(model.elapsed)) of focus."
        alert.addButton(withTitle:"Save Session")
        let closeButton = alert.addButton(withTitle:"Close")
        closeButton.keyEquivalent = "\u{1b}"
        let field = NSComboBox(frame: NSRect(x: 0, y: 25, width: 320, height: 28))
        field.addItems(withObjectValues: ["Job Apply", "Upskill", "Interview Prep", "Work"])
        field.stringValue=pendingTaskName
        field.isEditable = true
        field.completes = true
        field.numberOfVisibleItems = 4
        field.placeholderString = "Choose a category or type your own"
        field.setAccessibilityLabel("Session category or custom task name")
        field.toolTip = "Open the dropdown for a category, or type a custom name."
        let accessory = NSView(frame: NSRect(x: 0, y: 0, width: 320, height: 78))
        let label = NSTextField(labelWithString: "Choose a category or enter a custom task name.")
        label.font = .systemFont(ofSize: 11)
        label.textColor = .secondaryLabelColor
        label.frame = NSRect(x: 0, y: 57, width: 320, height: 17)
        let hint = NSTextField(labelWithString: "Close to save later, resume, or reset from the timer controls.")
        hint.font = .systemFont(ofSize: 10)
        hint.textColor = .secondaryLabelColor
        hint.frame = NSRect(x: 0, y: 0, width: 320, height: 17)
        accessory.addSubview(label); accessory.addSubview(field); accessory.addSubview(hint)
        alert.accessoryView = accessory
        alert.window.initialFirstResponder = field
        let result=alert.runModal();pendingTaskName=field.stringValue
        if result == .alertFirstButtonReturn {
            model.save(field.stringValue)
            showError()
            let saved = model.phase == .idle
            if saved { pendingTaskName="" }
            if saved && revealHistory {
                showHistory()
                historyWindow?.contentView = NSHostingView(rootView: HistoryView(model: model, initialDate: model.sessions.first?.start ?? Date()))
            }
            return saved
        }
        return false
    }
    func showError() {
        guard let error = model.error else { return }
        let alert = NSAlert(); alert.messageText = "Session storage needs attention"; alert.informativeText = error; alert.runModal(); model.error = nil
    }
    @objc func cancelSession() {
        guard model.phase != .idle, !dialogOpen else { return }
        dialogOpen = true; updateSessionShortcuts()
        defer { dialogOpen = false; updateSessionShortcuts() }
        let alert = NSAlert(); alert.messageText = model.pomodoroEnabled ? "Cancel this Pomodoro round?" : "Discard this session?"; alert.informativeText = model.pomodoroEnabled ? "An unfinished tree will die. Completed 25-minute rounds stay saved." : "This session won’t be added to history."
        alert.addButton(withTitle:"Keep Session"); alert.addButton(withTitle:"Discard Session")
        NSApp.activate(ignoringOtherApps:true)
        if alert.runModal() == .alertSecondButtonReturn { if model.pomodoroEnabled { model.cancelPomodoro() } else if model.discard() { pendingTaskName="" }; showError() }
    }
    @objc func quit() { NSApp.terminate(nil) }
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard !dialogOpen else { return .terminateCancel }
        guard model.phase != .idle else { return .terminateNow }
        if model.pomodoroEnabled {
            let alert=NSAlert();alert.messageText="Quit Pomodoro?";alert.informativeText="Keep the timer running across relaunch, or cancel the round. An unfinished tree dies when cancelled; completed rounds stay saved."
            alert.addButton(withTitle:"Keep timer & quit");alert.addButton(withTitle:"Cancel round & quit");alert.addButton(withTitle:"Keep working")
            let result=alert.runModal()
            if result == .alertFirstButtonReturn { return model.checkpoint() ? .terminateNow : .terminateCancel }
            if result == .alertSecondButtonReturn { model.cancelPomodoro();return model.checkpoint() ? .terminateNow : .terminateCancel }
            return .terminateCancel
        }
        let alert = NSAlert(); alert.messageText = "End your active session and quit?"
        alert.informativeText = "Save it to history, discard it, or keep working. The timer continues until you choose."
        alert.addButton(withTitle:"Save & Quit"); alert.addButton(withTitle:"Discard & Quit"); alert.addButton(withTitle:"Cancel")
        NSApp.activate(ignoringOtherApps:true)
        dialogOpen = true; updateSessionShortcuts()
        let decision = alert.runModal()
        dialogOpen = false; updateSessionShortcuts()
        switch decision {
        case .alertFirstButtonReturn:
            model.finish(); return nameSession(revealHistory: false) ? .terminateNow : .terminateCancel
        case .alertSecondButtonReturn: return model.discard() ? .terminateNow : .terminateCancel
        default: return .terminateCancel
        }
    }
    func applicationWillTerminate(_ notification: Notification) {
        for reference in sessionHotKeys.values { UnregisterEventHotKey(reference) }
        if let hotKey { UnregisterEventHotKey(hotKey) }
        if let hotKeyHandler { RemoveEventHandler(hotKeyHandler) }
        savePosition(); model.checkpoint()
    }
    @objc func showHistory() {
        if historyWindow == nil {
            let window = NSWindow(contentRect:NSRect(x:0,y:0,width:980,height:800),styleMask:[.titled,.closable,.resizable],backing:.buffered,defer:false)
            window.title = "Focanopy · Sessions"; window.contentView = NSHostingView(rootView:HistoryView(model:model))
            window.isReleasedWhenClosed = false; window.center(); historyWindow = window
        }
        NSApp.activate(ignoringOtherApps:true); historyWindow?.makeKeyAndOrderFront(nil)
    }
    func showOptions(from view: NSView) {
        if controlsPopover.isShown { controlsPopover.close(); return }
        let screenHeight = view.window?.screen?.visibleFrame.height ?? 800
        let height = min(model.phase == .idle ? 520.0 : 620.0, max(180, screenHeight - 100))
        controlsPopover.behavior = .transient
        controlsPopover.animates = false
        controlsPopover.contentSize = NSSize(width: 280, height: height)
        controlsPopover.contentViewController = NSHostingController(rootView: PetOptionsView(
            model: model, height: height,
            perform: { [weak self] action in
                self?.controlsPopover.close()
                DispatchQueue.main.async { action() }
            },
            start: { [weak self] in self?.start() }, pause: { [weak self] in self?.pause() },
            finish: { [weak self] in self?.finish() }, cancel: { [weak self] in self?.cancelSession() },
            hide: { [weak self] in self?.hidePet() }, close: { [weak self] in self?.closePet() },
            history: { [weak self] in self?.showHistory() }, quit: { [weak self] in self?.quit() },
            dismiss: { [weak self] in self?.controlsPopover.close() },focus: { [weak self] in self?.showFocusSettings() }))
        controlsPopover.show(relativeTo: view.bounds, of: view, preferredEdge: .maxY)
        controlsPopover.contentViewController?.view.window?.makeKey()
    }

}
@main struct MochiApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    var body: some Scene {
        MenuBarExtra { MenuContents(model:delegate.model,delegate:delegate) } label: { MenuLabel(model:delegate.model) }.menuBarExtraStyle(.menu)
        .commands {
            CommandMenu("Pet") {
                Button("Show / Hide Pet",action:delegate.togglePet).keyboardShortcut("r",modifiers:.command)
                Divider()
                Button("Start / Resume",action:{ delegate.handleSessionShortcut(2) }).keyboardShortcut("s").disabled(!delegate.model.petVisible)
                Button("Pause",action:{ delegate.handleSessionShortcut(3) }).keyboardShortcut("p").disabled(!delegate.model.petVisible)
                if !delegate.model.pomodoroEnabled { Button("Finish Session…",action:{ delegate.handleSessionShortcut(4) }).keyboardShortcut("f").disabled(!delegate.model.petVisible) }
                Divider()
                Button("Show Pet",action:delegate.showPet).keyboardShortcut("p",modifiers:[.command,.shift])
                Button("Hide Pet",action:delegate.hidePet)
                Button("Close Pet ×",action:delegate.closePet)
                Divider()
                Button("Pomodoro & App Blocking",action:delegate.showFocusSettings)
        Button("View Session History",action:delegate.showHistory)
                Button(action: delegate.showAccount) { Image(systemName: "person.crop.circle") }.help("Google profile").accessibilityLabel("Google profile")
            }
        }
    }
}
struct MenuLabel: View {
    @ObservedObject var model: Stopwatch
    var body: some View {
        Text(model.phase == .idle ? "⌁" : "⏱ " + model.menuTime).monospacedDigit().accessibilityLabel(model.phase == .idle ? "Focanopy stopwatch idle" : "Focanopy stopwatch " + model.menuTime)
    }
}
struct MenuContents: View {
    @ObservedObject var model: Stopwatch
    var delegate: AppDelegate
    var body: some View {
        Text("Focanopy · \(model.menuTime)")
        switch model.phase {
        case .idle: Button(model.pomodoroEnabled ? "Start 25-minute focus" : "Start Stopwatch",action:delegate.start)
        case .running: Button("Pause",action:delegate.pause); if !model.pomodoroEnabled { Button("Finish Session…",action:delegate.finish) }
        case .paused: Button(model.pomodoroEnabled && model.pomodoro.stage != "focus" ? (model.pomodoro.stage=="break" ? "Skip break & start next focus" : "Start next 25-minute focus") : "Resume",action:delegate.resume); if !model.pomodoroEnabled { Button("Finish Session…",action:delegate.finish) }
        case .finished: Button("Name & Save Session…",action:delegate.finish);Button("Resume Timer",action:delegate.start);Button("Reset Timer…",action:delegate.cancelSession)
        }
        if model.phase != .idle && model.phase != .finished { Button("Cancel Session…",action:delegate.cancelSession) }
        Divider()
        Button("Show / Hide Pet (⌘R)",action:delegate.togglePet)
        Button("Show Pet",action:delegate.showPet); Button("Hide Pet",action:delegate.hidePet)
        Button("Close Pet ×",action:delegate.closePet)
        Button("Pomodoro & App Blocking",action:delegate.showFocusSettings)
        Button("View Session History",action:delegate.showHistory)
                Button(action: delegate.showAccount) { Image(systemName: "person.crop.circle") }.help("Google profile").accessibilityLabel("Google profile")
        if let error = model.error { Divider(); Text(error); Button("Dismiss Error") { model.error = nil } }
        Divider(); Button("Quit App",action:delegate.quit).keyboardShortcut("q")
    }
}

struct FocusApplication:Identifiable {
    let id:String
    let url:URL
    let name:String
    let icon:NSImage
}
struct FocusPreferences:View {
    @ObservedObject var model:Stopwatch
    var onPreview:()->Void = {}
    @State private var confirmCancel=false
    @State private var installedApps:[FocusApplication]=[]
    @State private var appSearch=""
    @State private var accessGranted=AXIsProcessTrusted()
    @State private var browserIDs:Set<String>=[]
    @AppStorage("focusBlocking") private var blocking=false
    @AppStorage("focusBlockAll") private var blockAll=false
    @State private var excludedIDs:[String] = UserDefaults.standard.stringArray(forKey:"focusExcludedApps") ?? []
    @State private var appIDs: [String] = UserDefaults.standard.stringArray(forKey:"focusBlockedApps") ?? []
    private let forest=Color(red:17/255,green:27/255,blue:23/255)
    private let surface=Color(red:30/255,green:44/255,blue:36/255)
    private var stageTitle:String {
        switch model.pomodoro.stage {case "dead":return "ROUND CANCELLED";case "break":return "REST & RESET";case "ready":return "READY TO GROW";default:return model.phase == .paused ? "WATERING PAUSED":"FLOWER FOCUS"}
    }
    private var headline:String {
        switch model.pomodoro.stage {case "dead":return "A fresh start awaits";case "break":return "A little room to breathe";case "ready":return "Your next flower is waiting";default:return "Grow one little flower"}
    }
    private var countdown:String { let value=Int(max(0,model.displayTime));return String(format:"%02d:%02d",value/60,value%60) }
    private var progress:Double {
        if model.pomodoro.stage=="dead" { return 0 }
        return model.pomodoro.stage=="focus" ? min(1,max(0,1-model.displayTime/1500)):1
    }
    private func card<Content:View>(@ViewBuilder _ content:()->Content)->some View {
        VStack(alignment:.leading,spacing:14,content:content).padding(20).frame(maxWidth:.infinity)
            .background(surface,in:RoundedRectangle(cornerRadius:22)).overlay(RoundedRectangle(cornerRadius:22).stroke(Color.cyber.opacity(0.13),lineWidth:1))
    }
    var body:some View {
        ScrollView { VStack(alignment:.leading,spacing:16) {
            VStack(alignment:.leading,spacing:5) {
                Text("Your focus garden").font(.system(size:26,weight:.semibold,design:.rounded))
                Text("A little time. A little growth.").font(.callout).foregroundStyle(.secondary)
                Button("Fast-forward tree preview",systemImage:"forward.fill",action:onPreview).font(.caption)
            }.padding(.bottom,3)
            card {
                Toggle(isOn:Binding(get:{model.pomodoroEnabled},set:{ enabled in
                    model.selectMode(enabled,minutes:model.pomodoro.breakMinutes)
                    if enabled { UNUserNotificationCenter.current().requestAuthorization(options:[.alert,.sound]) { _,_ in } }
                })) {
                    VStack(alignment:.leading,spacing:4) {
                        Text("Pomodoro effect").font(.headline)
                        Text("One flower · 25 minutes of focus").font(.caption).foregroundStyle(.secondary)
                    }
                }.toggleStyle(.switch).tint(.cyber).disabled(model.phase != .idle && model.pomodoro.stage != "dead")
            }
            if model.pomodoroEnabled {
                card {
                    VStack(spacing:10) {
                        Text(stageTitle).font(.system(size:10,weight:.semibold)).tracking(2).foregroundStyle(model.pomodoro.stage=="dead" ? Color.orange.opacity(0.7):.cyber)
                        Text(headline).font(.system(size:22,weight:.medium,design:.rounded))
                        GrowingTree(phase:["break","ready"].contains(model.pomodoro.stage) ? .finished:model.phase,animated:model.petVisible,flower:true,dead:model.pomodoro.stage=="dead",deathProgress:{model.deathProgress},elapsed:{model.elapsed}).scaleEffect(1.6).frame(height:90)
                        TimelineView(.periodic(from:.now,by:1)) { _ in
                            Text(countdown).font(.system(size:48,weight:.medium,design:.monospaced)).monospacedDigit().foregroundStyle(Color(red:0.93,green:0.94,blue:0.86))
                            Text("Focus time  ·  "+timeText(model.elapsed)).font(.system(size:13,weight:.medium,design:.monospaced)).foregroundStyle(Color.cyber)
                            ProgressView(value:progress).tint(.cyber)
                        }
                        Text(model.pomodoro.stage=="dead" ? "This tree died. Start a fresh round when you’re ready." : model.pomodoro.stage=="break" ? "Flower saved. Stretch, breathe, move a little." : model.pomodoro.stage=="ready" ? "Start when you’re ready. No rush." : "A little water. A little growth.").font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        HStack(spacing:12) {
                            if ["break","ready"].contains(model.pomodoro.stage) {
                                Button(model.pomodoro.stage=="break" ? "Skip break & start next round":"Start next round") { model.start() }.buttonStyle(.borderedProminent).tint(.cyber).foregroundStyle(forest)
                            } else {
                                Button { if model.phase == .running { model.pause() } else { model.start() } } label: {
                                    Image(systemName:model.phase == .running ? "pause.fill":"play.fill").frame(width:46,height:42)
                                }.buttonStyle(.plain).background(Color.cyber,in:RoundedRectangle(cornerRadius:12)).foregroundStyle(forest).help(model.phase == .running ? "Pause":"Start a 25-minute round")
                                if model.phase != .idle && model.pomodoro.stage != "dead" {
                                    Button { confirmCancel=true } label:{Image(systemName:"xmark").frame(width:42,height:42)}.buttonStyle(.plain).background(forest,in:RoundedRectangle(cornerRadius:12)).foregroundStyle(Color.orange.opacity(0.7)).help("Cancel round · tree will die")
                                }
                            }
                        }.padding(.top,5)
                    }.frame(maxWidth:.infinity)
                    if model.phase == .idle {
                        Divider().overlay(Color.cyber.opacity(0.15))
                        Picker("Break after focus",selection:Binding(get:{model.pomodoro.breakMinutes},set:{model.selectMode(true,minutes:$0)})) { Text("5 min").tag(5);Text("10 min").tag(10) }.pickerStyle(.segmented)
                    }
                    Text("Completed rounds save automatically. The next round is always your choice.").font(.caption2).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth:.infinity)
                    if ["break","ready"].contains(model.pomodoro.stage) { Button("End Pomodoro") { model.cancelPomodoro() }.font(.caption).foregroundStyle(.secondary) }
                }
            }
            card {
                Toggle("App blocking",isOn:$blocking).font(.headline).toggleStyle(.switch).tint(.cyber)
                Text("Make a little room for what matters.").font(.caption).foregroundStyle(.secondary)
                DisclosureGroup("Apps & permissions") {
                    VStack(alignment:.leading,spacing:12) {
                        Toggle("Block all except browsers",isOn:$blockAll).toggleStyle(.switch).tint(.cyber)
                        Text("On blocks an app. Switch it off to allow it. Browsers stay allowed in all-app mode.").font(.caption).foregroundStyle(.secondary)
                        HStack {
                            Image(systemName:accessGranted ? "checkmark.shield.fill":"lock.shield").foregroundStyle(Color.cyber)
                            VStack(alignment:.leading,spacing:3) {
                                Text(accessGranted ? "Accessibility enabled":"Stronger window control").font(.caption.bold())
                                Text(accessGranted ? "Window minimization is available":"Optional access for resistant apps").font(.caption2).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Button(accessGranted ? "Manage":"Enable") {
                                if accessGranted { NSWorkspace.shared.open(URL(string:"x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility")!) }
                                else { let options=[kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String:true] as CFDictionary;_=AXIsProcessTrustedWithOptions(options) }
                            }
                        }
                        TextField("Find an app",text:$appSearch).textFieldStyle(.roundedBorder)
                        LazyVStack(spacing:8) {
                            ForEach(installedApps.filter { appSearch.isEmpty || $0.name.localizedCaseInsensitiveContains(appSearch) }) { app in
                                let browserAllowed=blockAll && browserIDs.contains(app.id)
                                HStack(spacing:10) {
                                    Image(nsImage:app.icon).resizable().frame(width:30,height:30).accessibilityHidden(true)
                                    VStack(alignment:.leading,spacing:3) {
                                        Text(app.name).font(.callout).lineLimit(1)
                                        Text(browserAllowed ? "Browser · always allowed":"On blocks this app").font(.caption2).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Toggle(app.name+" blocking",isOn:Binding(get:{!browserAllowed && !excludedIDs.contains(app.id) && (blockAll || appIDs.contains(app.id))},set:{setBlocked(app,$0)}))
                                        .labelsHidden().toggleStyle(.switch).tint(.cyber).disabled(browserAllowed)
                                }.padding(10).background(forest,in:RoundedRectangle(cornerRadius:12))
                            }
                        }
                        Button("Add another app…") {chooseApps();loadApps()}
                        Text("Pauses and breaks allow apps. System essentials stay available.").font(.caption2).foregroundStyle(.secondary)
                    }.padding(.top,12)
                }.font(.callout)
            }
        }.padding(22) }.frame(width:478,height:700).background(forest).foregroundStyle(Color(red:0.93,green:0.94,blue:0.89)).preferredColorScheme(.dark)
            .onAppear {loadApps()}
            .onReceive(NotificationCenter.default.publisher(for:NSApplication.didBecomeActiveNotification)) { _ in accessGranted=AXIsProcessTrusted() }
            .alert("Cancel this round?",isPresented:$confirmCancel) {
                Button("Keep growing",role:.cancel) {}
                Button("Cancel round",role:.destructive) { model.cancelPomodoro() }
            } message:{Text("An unfinished tree will die. Completed 25-minute rounds stay saved.")}
    }
    private func setBlocked(_ app:FocusApplication,_ value:Bool) {
        let prefs=UserDefaults.standard
        if blockAll {
            excludedIDs.removeAll{$0==app.id};if !value {excludedIDs.append(app.id)}
        } else {
            appIDs.removeAll{$0==app.id};if value {appIDs.append(app.id);excludedIDs.removeAll{$0==app.id}}
        }
        prefs.set(appIDs,forKey:"focusBlockedApps");prefs.set(excludedIDs,forKey:"focusExcludedApps")
        for (key,ids) in [("focusBlockedApps",appIDs),("focusExcludedApps",excludedIDs)] {
            let old=prefs.stringArray(forKey:key+"Paths") ?? []
            let kept=old.filter {path in Bundle(url:URL(fileURLWithPath:path))?.bundleIdentifier.map{ids.contains($0)} ?? false}
            let resolved=ids.compactMap{NSWorkspace.shared.urlForApplication(withBundleIdentifier:$0)?.path}
            let paths=kept+resolved+installedApps.filter{ids.contains($0.id)}.map{$0.url.path}
            prefs.set(Array(Set(paths)),forKey:key+"Paths")
        }
    }
    private func loadApps() {
        browserIDs=Set(NSWorkspace.shared.urlsForApplications(toOpen:URL(string:"https://example.com")!).compactMap{Bundle(url:$0)?.bundleIdentifier})
        let protected=[Bundle.main.bundleIdentifier ?? "","com.apple.finder","com.apple.systempreferences","com.apple.dock","com.apple.loginwindow"]
        var found:[String:FocusApplication]=[:]
        let roots=[URL(fileURLWithPath:"/Applications"),URL(fileURLWithPath:"/System/Applications"),FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent("Applications")]
        func add(_ url:URL) {
            guard let bundle=Bundle(url:url),let id=bundle.bundleIdentifier,!protected.contains(id) else {return}
            found[id]=FocusApplication(id:id,url:url,name:(bundle.object(forInfoDictionaryKey:"CFBundleDisplayName") as? String) ?? (bundle.object(forInfoDictionaryKey:"CFBundleName") as? String) ?? url.deletingPathExtension().lastPathComponent,icon:NSWorkspace.shared.icon(forFile:url.path))
        }
        for root in roots {
            for url in (try? FileManager.default.contentsOfDirectory(at:root,includingPropertiesForKeys:nil)) ?? [] {
                if url.pathExtension=="app" {add(url)}
                else {for child in (try? FileManager.default.contentsOfDirectory(at:url,includingPropertiesForKeys:nil)) ?? [] where child.pathExtension=="app" {add(child)}}
            }
        }
        for id in appIDs+excludedIDs {if found[id]==nil,let url=NSWorkspace.shared.urlForApplication(withBundleIdentifier:id){add(url)}}
        installedApps=found.values.sorted{$0.name.localizedStandardCompare($1.name) == .orderedAscending}
    }
    func chooseApps() {
        let panel=NSOpenPanel();panel.title="Choose distracting apps";panel.allowedContentTypes=[.applicationBundle];panel.allowsMultipleSelection=true;panel.canChooseDirectories=false
        if panel.runModal() == .OK {
            let ids=panel.urls.compactMap { Bundle(url:$0)?.bundleIdentifier }.filter { ![Bundle.main.bundleIdentifier ?? "","com.apple.finder","com.apple.systempreferences"].contains($0) }
            let key=blockAll ? "focusExcludedApps" : "focusBlockedApps"
            if blockAll { excludedIDs=Array(Set(excludedIDs+ids)) } else { appIDs=Array(Set(appIDs+ids));excludedIDs.removeAll{ids.contains($0)};UserDefaults.standard.set(excludedIDs,forKey:"focusExcludedApps");let removed=Set(panel.urls.map{$0.path});UserDefaults.standard.set((UserDefaults.standard.stringArray(forKey:"focusExcludedAppsPaths") ?? []).filter{path in Bundle(url:URL(fileURLWithPath:path))?.bundleIdentifier.map{!ids.contains($0)} ?? !removed.contains(path)},forKey:"focusExcludedAppsPaths") }
            UserDefaults.standard.set(blockAll ? excludedIDs:appIDs,forKey:key)
            UserDefaults.standard.set(Array(Set((UserDefaults.standard.stringArray(forKey:key+"Paths") ?? [])+panel.urls.map{$0.path})),forKey:key+"Paths")
        }
    }
}
