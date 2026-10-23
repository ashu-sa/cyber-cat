import SwiftUI
import AppKit

struct GrowthSimulatorView: View {
    @State private var elapsed = 0.0
    @State private var flower=false
    @State private var playing = true
    @State private var speed = 360.0
    @State private var lastTick = ProcessInfo.processInfo.systemUptime
    private let pulse = Timer.publish(every: 0.05, on: .main, in: .common).autoconnect()
    var body: some View {
        VStack(spacing: 20) {
            Text("Watch your focus grow").font(.system(size: 25, weight: .semibold, design: .rounded))
            Text("Preview only · Your timer and history stay untouched")
                .font(.callout).foregroundStyle(.secondary)
            Toggle("Pomodoro flower · 25-minute bloom",isOn:$flower).onChange(of:flower) { _,_ in elapsed=0;lastTick=ProcessInfo.processInfo.systemUptime }
            GrowingTree(phase: playing ? .running : .paused, flower:flower, elapsed: { elapsed })
                .scaleEffect(3.5).frame(width: 420, height: 220)
            Text(String(format: "%02d:%02d:%02d", Int(elapsed)/3600, Int(elapsed)/60%60, Int(elapsed)%60))
                .font(.system(size: 32, weight: .medium, design: .monospaced))
            Text("\(Int(elapsed)/(flower ? 1500:3600)) completed \(flower ? "flowers":"trees") · \(flower ? "25-minute bloom":"1-hour tree")")
                .foregroundStyle(Color.cyber)
            Slider(value: $elapsed, in: 0...(flower ? 4500:10800)).accessibilityLabel("Simulated focus time")
            HStack {
                Text("0"); Spacer(); Text(flower ? "25m":"1h"); Spacer(); Text(flower ? "50m":"2h"); Spacer(); Text(flower ? "75m":"3h")
            }.font(.caption).foregroundStyle(.secondary)
            HStack(spacing: 16) {
                Button { playing.toggle(); lastTick = ProcessInfo.processInfo.systemUptime } label: {
                    Label(playing ? "Pause" : "Play", systemImage: playing ? "pause.fill" : "play.fill")
                }
                Button("Restart", systemImage: "arrow.counterclockwise") { elapsed = 0; lastTick = ProcessInfo.processInfo.systemUptime; playing = true }
                Button("Next tree", systemImage: "forward.end.fill") { let period=flower ? 1500:3600;elapsed = min(Double(period*3), Double(Int(elapsed)/period+1)*Double(period)) }
            }.buttonStyle(.bordered)
            Picker("Speed", selection: $speed) {
                Text("One growth cycle in 30 seconds").tag(120.0)
                Text("One growth cycle in 10 seconds").tag(360.0)
                Text("One growth cycle in 5 seconds").tag(720.0)
            }.pickerStyle(.menu)
        }
        .padding(32).frame(width: 500)
        .foregroundStyle(Color(red: 0.80, green: 0.91, blue: 0.82))
        .background(Color(red: 0.06, green: 0.11, blue: 0.09))
        .preferredColorScheme(.dark)
        .onReceive(pulse) { _ in
            let now = ProcessInfo.processInfo.systemUptime
            if playing { elapsed = min(flower ? 4500:10800, elapsed + max(0, now-lastTick)*speed*(flower ? 1500.0/3600:1)); if elapsed >= (flower ? 4500:10800) { playing = false } }
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
