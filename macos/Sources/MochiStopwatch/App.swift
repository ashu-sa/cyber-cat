import SwiftUI
import AppKit
import Carbon

final class FloatingPanel: NSPanel {
    override var canBecomeKey: Bool { true }
    override var canBecomeMain: Bool { false }
    override func close() { orderOut(nil) }
}
@MainActor final class AppDelegate: NSObject, NSApplicationDelegate, NSWindowDelegate {
    let model = Stopwatch(directory: ProcessInfo.processInfo.environment["MOCHI_TEST_DATA_DIRECTORY"].map { URL(fileURLWithPath: $0) })
    var panel: NSPanel!
    var historyWindow: NSWindow?
    let controlsPopover = NSPopover()
    var hotKey: EventHotKeyRef?
    var hotKeyHandler: EventHandlerRef?
    var sessionHotKeys: [UInt32: EventHotKeyRef] = [:]
    var displayTimer: Timer?
    var ticks = 0
    var dialogOpen = false
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.accessory)
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
        },finish:{ [weak self] in self?.finish() }))
        restorePosition(); panel.delegate = self
        registerShortcut(); showPet()
        displayTimer = Timer.scheduledTimer(withTimeInterval:1,repeats:true) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }; self.model.refreshMenu(); self.ticks += 1
                if self.ticks % 15 == 0 && self.model.phase == .running { self.model.checkpoint() }
            }
        }
        if let displayTimer { RunLoop.main.add(displayTimer, forMode: .common) }
        NSWorkspace.shared.notificationCenter.addObserver(self,selector:#selector(woke),name:NSWorkspace.didWakeNotification,object:nil)
        NSWorkspace.shared.notificationCenter.addObserver(self,selector:#selector(sleeping),name:NSWorkspace.willSleepNotification,object:nil)
        NotificationCenter.default.addObserver(self,selector:#selector(screensChanged),name:NSApplication.didChangeScreenParametersNotification,object:nil)
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
        let shortcuts: [(UInt32, Int, String)] = [(2, kVK_ANSI_S, "S"), (3, kVK_ANSI_P, "P"), (4, kVK_ANSI_F, "F")]
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
    @objc func start() { model.start(); showPet() }
    @objc func pause() { model.pause() }
    @objc func resume() { model.start() }
    @objc func finish() { model.finish(); nameSession() }
    @discardableResult func nameSession(revealHistory: Bool = true) -> Bool {
        guard model.phase == .finished, !dialogOpen else { return false }
        dialogOpen = true; updateSessionShortcuts()
        defer { dialogOpen = false; updateSessionShortcuts() }
        NSApp.activate(ignoringOtherApps:true)
        let alert = NSAlert(); alert.messageText = "What did you work on?"
        alert.informativeText = "Session cleared! \(timeText(model.elapsed)) of focus."
        alert.addButton(withTitle:"Save Session")
        let closeButton = alert.addButton(withTitle:"Close")
        closeButton.keyEquivalent = "\u{1b}"
        let field = NSComboBox(frame: NSRect(x: 0, y: 25, width: 320, height: 28))
        field.addItems(withObjectValues: ["Job Apply", "Upskill", "Interview Prep", "Work"])
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
        let hint = NSTextField(labelWithString: "Close keeps this session ready to save later.")
        hint.font = .systemFont(ofSize: 10)
        hint.textColor = .secondaryLabelColor
        hint.frame = NSRect(x: 0, y: 0, width: 320, height: 17)
        accessory.addSubview(label); accessory.addSubview(field); accessory.addSubview(hint)
        alert.accessoryView = accessory
        alert.window.initialFirstResponder = field
        if alert.runModal() == .alertFirstButtonReturn {
            model.save(field.stringValue)
            showError()
            let saved = model.phase == .idle
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
        let alert = NSAlert(); alert.messageText = "Discard this session?"; alert.informativeText = "This session won’t be added to history."
        alert.addButton(withTitle:"Keep Session"); alert.addButton(withTitle:"Discard Session")
        NSApp.activate(ignoringOtherApps:true)
        if alert.runModal() == .alertSecondButtonReturn { _ = model.discard(); showError() }
    }
    @objc func quit() { NSApp.terminate(nil) }
    func applicationShouldTerminate(_ sender: NSApplication) -> NSApplication.TerminateReply {
        guard !dialogOpen else { return .terminateCancel }
        guard model.phase != .idle else { return .terminateNow }
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
            window.title = "Cyber Cat · Sessions"; window.contentView = NSHostingView(rootView:HistoryView(model:model))
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
            dismiss: { [weak self] in self?.controlsPopover.close() }))
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
                Button("Finish Session…",action:{ delegate.handleSessionShortcut(4) }).keyboardShortcut("f").disabled(!delegate.model.petVisible)
                Divider()
                Button("Show Pet",action:delegate.showPet).keyboardShortcut("p",modifiers:[.command,.shift])
                Button("Hide Pet",action:delegate.hidePet)
                Button("Close Pet ×",action:delegate.closePet)
                Divider()
                Button("View Session History",action:delegate.showHistory)
            }
        }
    }
}
struct MenuLabel: View {
    @ObservedObject var model: Stopwatch
    var body: some View {
        Text(model.phase == .idle ? "⌁" : "⏱ " + model.menuTime).monospacedDigit().accessibilityLabel(model.phase == .idle ? "Cyber Cat stopwatch idle" : "Cyber Cat stopwatch " + model.menuTime)
    }
}
struct MenuContents: View {
    @ObservedObject var model: Stopwatch
    var delegate: AppDelegate
    var body: some View {
        Text("Cyber Cat · \(model.menuTime)")
        switch model.phase {
        case .idle: Button("Start Stopwatch",action:delegate.start)
        case .running: Button("Pause",action:delegate.pause); Button("Finish Session…",action:delegate.finish)
        case .paused: Button("Resume",action:delegate.resume); Button("Finish Session…",action:delegate.finish)
        case .finished: Button("Name & Save Session…",action:delegate.finish)
        }
        if model.phase != .idle { Button("Cancel Session…",action:delegate.cancelSession) }
        Divider()
        Button("Show / Hide Pet (⌘R)",action:delegate.togglePet)
        Button("Show Pet",action:delegate.showPet); Button("Hide Pet",action:delegate.hidePet)
        Button("Close Pet ×",action:delegate.closePet)
        Button("View Session History",action:delegate.showHistory)
        if let error = model.error { Divider(); Text(error); Button("Dismiss Error") { model.error = nil } }
        Divider(); Button("Quit App",action:delegate.quit).keyboardShortcut("q")
    }
}
