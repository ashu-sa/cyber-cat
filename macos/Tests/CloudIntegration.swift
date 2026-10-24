import Foundation
@main struct CloudIntegration {
    @MainActor static func main() async throws {
        let env=ProcessInfo.processInfo.environment
        let name=env["CYBERCAT_TEST_USERNAME"]!, pin=env["CYBERCAT_TEST_PIN"]!
        let dir=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at:dir,withIntermediateDirectories:true)
        defer { try? FileManager.default.removeItem(at:dir) }
        let service="CyberCat.Sync.QA."+UUID().uuidString
        let c=CloudAccount(directory:dir,enabled:true,vaultService:service)
        while c.busy { try await Task.sleep(for: .milliseconds(50)) }
        await c.authenticate(username:name,pin:pin,signup:false,existing:[])
        precondition(c.owner != nil,c.status)
        await c.sync(); precondition(c.status=="All devices up to date",c.status)
        let loaded=CloudAccount(directory:dir,enabled:true,vaultService:service)
        while loaded.busy { try await Task.sleep(for: .milliseconds(50)) }
        precondition(loaded.owner==c.owner,"Keychain session was not restored")
        precondition(loaded.visible.contains { $0.deviceLabel=="Mac" && $0.duration==2400 },"Missing desktop 40m record")
        let s=Session(name:"Native Swift check",start:Date(),end:Date().addingTimeInterval(2),duration:2,platform:"macos",deviceId:UUID().uuidString,deviceName:"Mac")
        try loaded.replace(loaded.visible+[s]); await loaded.sync()
        precondition(loaded.status=="All devices up to date",loaded.status)
        precondition(loaded.visible.contains { $0.id==s.id },"Native upload failed")
        try loaded.replace(loaded.visible.filter { $0.id != s.id }); await loaded.sync()
        precondition(!loaded.visible.contains { $0.id==s.id },"Native delete failed")
        try loaded.signOut()
        print("PASS: native Swift sign-in, Keychain restore, shared 40m record, upload and tombstone sync")
    }
}
