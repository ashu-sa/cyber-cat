import SwiftUI

struct TreeLifecyclePreview:View {
    @State private var position=0.0
    @State private var playing=true
    @State private var speed=1.0
    @State private var lastTick=ProcessInfo.processInfo.systemUptime
    private let pulse=Timer.publish(every:0.05,on:.main,in:.common).autoconnect()
    private var frame:TreePreviewFrame {TreePreviewFrame.at(position)}
    var body:some View {
        VStack(spacing:18) {
            Text("The life of a little tree").font(.system(size:26,weight:.semibold,design:.rounded))
            Text("Fast-forward preview · Your real timer and history stay untouched").font(.caption).foregroundStyle(.secondary)
            GrowingTree(phase:frame.dead ? .paused : (playing && (position<10 || (position>=12 && position<20)) ? .running:.finished),animated:true,flower:true,dead:frame.dead,deathProgress:{frame.death},motionTime:{position},elapsed:{frame.elapsed})
                .scaleEffect(3.8).frame(width:440,height:230)
            Text(frame.title).font(.headline).multilineTextAlignment(.center).foregroundStyle(Color.cyber)
            Text(frame.dead ? "Water stops. Petals fall. The stem droops and dries." : position>=10 && position<12 ? "A completed flower is saved and stays healthy." : "25 minutes of focus shown in 10 seconds.").font(.callout).foregroundStyle(.secondary)
            Slider(value:$position,in:0...27).accessibilityLabel("Tree lifecycle preview")
            HStack {Text("Seed");Spacer();Text("Bloom");Spacer();Text("New tree");Spacer();Text("Wither")}.font(.caption).foregroundStyle(.secondary)
            HStack(spacing:14) {
                Button(playing ? "Pause":"Play",systemImage:playing ? "pause.fill":"play.fill") {if position>=27 {position=0};playing.toggle();lastTick=ProcessInfo.processInfo.systemUptime}
                Button("Replay",systemImage:"arrow.counterclockwise") {position=0;playing=true;lastTick=ProcessInfo.processInfo.systemUptime}
                Picker("Speed",selection:$speed) {Text("½×").tag(0.5);Text("1×").tag(1.0);Text("2×").tag(2.0)}.frame(width:140)
            }.buttonStyle(.bordered)
        }.padding(28).frame(width:520).foregroundStyle(Color(red:0.91,green:0.94,blue:0.87)).background(Color(red:0.067,green:0.106,blue:0.09)).preferredColorScheme(.dark)
        .onReceive(pulse) {_ in
            guard playing else{return}
            let now=ProcessInfo.processInfo.systemUptime;position=min(27,position+max(0,now-lastTick)*speed);lastTick=now
            if position>=27 {playing=false}
        }.onDisappear {playing=false}
    }
}
