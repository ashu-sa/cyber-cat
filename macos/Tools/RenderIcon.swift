import AppKit

@main struct RenderIcon {
    static func main() throws {
        let folder=URL(fileURLWithPath:CommandLine.arguments[1]);try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
        let sizes=[("16x16",16),("16x16@2x",32),("32x32",32),("32x32@2x",64),("128x128",128),("128x128@2x",256),("256x256",256),("256x256@2x",512),("512x512",512),("512x512@2x",1024)]
        for (name,side) in sizes {
            let image=NSBitmapImageRep(bitmapDataPlanes:nil,pixelsWide:side,pixelsHigh:side,bitsPerSample:8,samplesPerPixel:4,hasAlpha:true,isPlanar:false,colorSpaceName:.deviceRGB,bytesPerRow:0,bitsPerPixel:0)!
            NSGraphicsContext.saveGraphicsState();NSGraphicsContext.current=NSGraphicsContext(bitmapImageRep:image)
            NSGraphicsContext.current!.cgContext.scaleBy(x:CGFloat(side)/108,y:CGFloat(side)/108)
            NSColor(red:0.067,green:0.106,blue:0.09,alpha:1).setFill();NSBezierPath(roundedRect:NSRect(x:2,y:2,width:104,height:104),xRadius:24,yRadius:24).fill()
            NSColor(red:0.80,green:0.71,blue:0.55,alpha:1).setFill();NSBezierPath(roundedRect:NSRect(x:51,y:27,width:6,height:31),xRadius:2,yRadius:2).fill()
            NSColor(red:0.47,green:0.74,blue:0.57,alpha:1).setFill()
            for rect in [NSRect(x:29,y:44,width:32,height:32),NSRect(x:49,y:43,width:31,height:32),NSRect(x:39,y:54,width:32,height:30)] { NSBezierPath(ovalIn:rect).fill() }
            NSColor(red:0.71,green:0.86,blue:0.68,alpha:1).setFill();NSBezierPath(ovalIn:NSRect(x:43,y:68,width:14,height:9)).fill()
            NSColor(red:0.33,green:0.49,blue:0.36,alpha:1).setFill();NSBezierPath(roundedRect:NSRect(x:39,y:25,width:31,height:4),xRadius:2,yRadius:2).fill()
            NSGraphicsContext.restoreGraphicsState()
            try image.representation(using:.png,properties:[:])!.write(to:folder.appendingPathComponent("icon_\(name).png"))
        }
    }
}
