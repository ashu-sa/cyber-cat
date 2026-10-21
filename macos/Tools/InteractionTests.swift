import SwiftUI
import AppKit

@main struct InteractionTests {
    @MainActor static func main() {
        _=NSApplication.shared
        let window=NSWindow(contentRect:NSRect(x:100,y:100,width:150,height:100),styleMask:[.borderless],backing:.buffered,defer:false)
        window.isReleasedWhenClosed=false
        let root=NSView(frame:NSRect(x:0,y:0,width:150,height:100));window.contentView=root
        let button=FloatingActionButton(frame:NSRect(x:40,y:5,width:26,height:26));root.addSubview(button)
        var actions=0;button.clickAction={ actions+=1 }
        func event(_ type:NSEvent.EventType,_ point:NSPoint)->NSEvent {
            NSEvent.mouseEvent(with:type,location:point,modifierFlags:[],timestamp:ProcessInfo.processInfo.systemUptime,windowNumber:window.windowNumber,context:nil,eventNumber:1,clickCount:1,pressure:1)!
        }
        func drag(_ view:NSView) {
            let origin=window.frame.origin
            view.mouseDown(with:event(.leftMouseDown,NSPoint(x:53,y:18)))
            view.mouseDragged(with:event(.leftMouseDragged,NSPoint(x:83,y:38)))
            // The same screen point is now at the original local point after the window moves.
            view.mouseUp(with:event(.leftMouseUp,NSPoint(x:53,y:18)))
            precondition(window.frame.origin==NSPoint(x:origin.x+30,y:origin.y+20))
        }
        drag(button);precondition(actions==0,"Button drag must not activate")
        button.mouseDown(with:event(.leftMouseDown,NSPoint(x:53,y:18)));button.mouseUp(with:event(.leftMouseUp,NSPoint(x:53,y:18)))
        precondition(actions==1,"Normal button click must activate")
        button.isEnabled=false;drag(button);precondition(actions==1)
        let timer=PetHitView(frame:NSRect(x:20,y:40,width:110,height:25));root.addSubview(timer)
        var menus=0;timer.showOptions={ _ in menus+=1 };drag(timer);precondition(menus==0)
        timer.mouseDown(with:event(.leftMouseDown,NSPoint(x:53,y:50)));timer.mouseUp(with:event(.leftMouseUp,NSPoint(x:53,y:50)))
        precondition(menus==1)
        print("PASS: native timer/button/disabled-button dragging moves window, suppresses actions, and normal clicks still activate")
    }
}
