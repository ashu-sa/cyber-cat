import SwiftUI
import AppKit

@main struct Screenshots {
    @MainActor static func main() throws {
        _ = NSApplication.shared
        let args = CommandLine.arguments
        let out = URL(fileURLWithPath: args.count > 1 ? args[1] : "../docs/screenshots")
        try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let model = Stopwatch(directory: directory)
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        var sessions: [Session] = []
        for (days, hours, name) in [(0, 1.0, "Work"), (1, 2.0, "Upskill"), (3, 1.5, "Interview Prep"), (5, 3.0, "Job Apply")] {
            let start = calendar.date(byAdding: .day, value: -days, to: today)!.addingTimeInterval(36000)
            sessions.append(Session(name: name, start: start, end: start.addingTimeInterval(hours * 3600), duration: hours * 3600))
        }
        _ = model.replaceHistory(sessions)
        func render<V: View>(_ view: V, name: String, width: CGFloat, height: CGFloat) throws {
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: width, height: height), styleMask: [.borderless], backing: .buffered, defer: false)
            let host = NSHostingView(rootView: view.frame(width: width, height: height))
            window.contentView = host
            host.frame = NSRect(x: 0, y: 0, width: width, height: height)
            host.layoutSubtreeIfNeeded()
            window.displayIfNeeded()
            RunLoop.main.run(until: Date().addingTimeInterval(0.3))
            host.layoutSubtreeIfNeeded()
            guard let bitmap = host.bitmapImageRepForCachingDisplay(in: host.bounds) else { throw NSError(domain: "Render", code: 1) }
            host.cacheDisplay(in: host.bounds, to: bitmap)
            let data = bitmap.representation(using: .png, properties: [:])!
            try data.write(to: out.appendingPathComponent(name))
            window.orderOut(nil)
        }
        try render(HistoryView(model: model), name: "macos-journal.png", width: 980, height: 800)
        try render(PetView(model: model, showOptions: { _ in }, primary: {}, finish: {}), name: "macos-pet.png", width: 150, height: 100)
        print("Rendered macOS native screenshots")
    }
}
