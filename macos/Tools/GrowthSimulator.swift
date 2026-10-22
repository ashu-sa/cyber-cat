import SwiftUI
import AppKit

struct GrowthSimulatorView: View {
    @State private var elapsed = 0.0
    @State private var playing = true
    @State private var speed = 360.0
    @State private var lastTick = ProcessInfo.processInfo.systemUptime
    private let pulse = Timer.publish(every: 0.05, on: .main, in: .common).autoconnect()
    var body: some View {
        VStack(spacing: 20) {
            Text("Watch your focus grow").font(.system(size: 25, weight: .semibold, design: .rounded))
            Text("Preview only · Your timer and history stay untouched")
                .font(.callout).foregroundStyle(.secondary)
            GrowingTree(phase: playing ? .running : .paused, elapsed: { elapsed })
                .scaleEffect(3.5).frame(width: 420, height: 220)
            Text(String(format: "%02d:%02d:%02d", Int(elapsed)/3600, Int(elapsed)/60%60, Int(elapsed)%60))
                .font(.system(size: 32, weight: .medium, design: .monospaced))
            Text("\(Int(elapsed)/3600) completed trees · Next tree \(Int(elapsed.truncatingRemainder(dividingBy: 3600)/36))%")
                .foregroundStyle(Color.cyber)
            Slider(value: $elapsed, in: 0...10800).accessibilityLabel("Simulated focus time")
            HStack {
                Text("0h"); Spacer(); Text("1h"); Spacer(); Text("2h"); Spacer(); Text("3h")
            }.font(.caption).foregroundStyle(.secondary)
            HStack(spacing: 16) {
                Button { playing.toggle(); lastTick = ProcessInfo.processInfo.systemUptime } label: {
                    Label(playing ? "Pause" : "Play", systemImage: playing ? "pause.fill" : "play.fill")
                }
                Button("Restart", systemImage: "arrow.counterclockwise") { elapsed = 0; lastTick = ProcessInfo.processInfo.systemUptime; playing = true }
                Button("Next tree", systemImage: "forward.end.fill") { elapsed = min(10800, Double(Int(elapsed)/3600+1)*3600) }
            }.buttonStyle(.bordered)
            Picker("Speed", selection: $speed) {
                Text("1 hour in 30 seconds").tag(120.0)
                Text("1 hour in 10 seconds").tag(360.0)
                Text("1 hour in 5 seconds").tag(720.0)
            }.pickerStyle(.menu)
        }
        .padding(32).frame(width: 500)
        .foregroundStyle(Color(red: 0.80, green: 0.91, blue: 0.82))
        .background(Color(red: 0.06, green: 0.11, blue: 0.09))
        .preferredColorScheme(.dark)
        .onReceive(pulse) { _ in
            let now = ProcessInfo.processInfo.systemUptime
            if playing { elapsed = min(10800, elapsed + max(0, now-lastTick)*speed); if elapsed >= 10800 { playing = false } }
            lastTick = now
        }
    }
}

@main struct GrowthSimulator: App {
    var body: some Scene {
        WindowGroup("Focanopy · Growth Simulator") { GrowthSimulatorView() }
            .windowResizability(.contentSize)
    }
}
