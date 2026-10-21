import Foundation
import SwiftUI
import Security

private let cloudURL = "https://fwlengqpmrtmpbrhtxis.supabase.co"
private let cloudPublicKey = "sb_publishable_gsnb17q8TXRiHPu7W1vr4g_K7J8Np3H" // Public project key is filled before packaging.
struct CloudMetadata: Codable { var username: String? }
struct CloudUser: Codable { var id: String; var userMetadata: CloudMetadata? }
struct CloudLogin: Codable { var accessToken: String; var refreshToken: String; var user: CloudUser }
struct CloudRecord: Codable {
    var id: String; var name: String; var startMs: Int64; var endMs: Int64; var durationMs: Int64
    var platform: String; var deviceId: String; var deviceName: String
    var revision: Int64 = 0; var deleted = false
    var dirty: Bool?; var change: String?
    init(_ s: Session) {
        id=s.id.uuidString.lowercased(); name=s.name
        startMs=Int64((s.start.timeIntervalSince1970*1000).rounded()); endMs=Int64((s.end.timeIntervalSince1970*1000).rounded()); durationMs=Int64((s.duration*1000).rounded())
        platform=s.platform ?? "unknown"; deviceId=s.deviceId ?? UUID().uuidString.lowercased(); deviceName=s.deviceName ?? "This device"
    }
    var local: Session { Session(id: UUID(uuidString:id)!, name:name, start:Date(timeIntervalSince1970:Double(startMs)/1000), end:Date(timeIntervalSince1970:Double(endMs)/1000), duration:Double(durationMs)/1000, platform:platform, deviceId:deviceId, deviceName:deviceName) }
}
struct CloudCache: Codable { var records: [CloudRecord] = []; var conflicts: [String:CloudRecord] = [:] }
struct CloudReply: Codable { var records: [CloudRecord]; var conflicts: [String] }
func cloudEncoder() -> JSONEncoder { let e=JSONEncoder(); e.keyEncodingStrategy = .convertToSnakeCase; return e }
func cloudDecoder() -> JSONDecoder { let d=JSONDecoder(); d.keyDecodingStrategy = .convertFromSnakeCase; return d }
private func credential(_ data: Data? = nil, clear: Bool = false, service:String = "CyberCat.Sync") throws -> Data? {
    let key: [String:Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:service,kSecAttrAccount as String:"session"]
    if clear { let s=SecItemDelete(key as CFDictionary); if s != errSecSuccess && s != errSecItemNotFound { throw NSError(domain:NSOSStatusErrorDomain,code:Int(s)) }; return nil }
    if let data {
        let update=SecItemUpdate(key as CFDictionary,[kSecValueData as String:data] as CFDictionary)
        if update == errSecItemNotFound {
            var added=key; added[kSecValueData as String]=data; added[kSecAttrAccessible as String]=kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            let result=SecItemAdd(added as CFDictionary,nil)
            if result != errSecSuccess { throw NSError(domain:NSOSStatusErrorDomain,code:Int(result)) }
        } else if update != errSecSuccess { throw NSError(domain:NSOSStatusErrorDomain,code:Int(update)) }
        return nil
    }
    var read=key; read[kSecReturnData as String]=true; read[kSecMatchLimit as String]=kSecMatchLimitOne
    var result: CFTypeRef?; let status=SecItemCopyMatching(read as CFDictionary,&result)
    if status == errSecItemNotFound { return nil }
    guard status == errSecSuccess else { throw NSError(domain:NSOSStatusErrorDomain,code:Int(status)) }
    return result as? Data
}
@MainActor final class CloudAccount: ObservableObject {
    @Published var login: CloudLogin?
    @Published var status = "Local only"
    @Published var busy = false
    @Published var cache = CloudCache()
    private let persistCredentials: Bool
    private let vaultService: String
    @Published var authenticating=false
    let directory: URL
    var onChange: (() -> Void)?
    var owner: String? { login?.user.id }
    var username: String { login?.user.userMetadata?.username ?? "" }
    var visible: [Session] { cache.records.filter { !$0.deleted }.map(\.local).sorted { $0.start > $1.start } }
    init(directory: URL, enabled: Bool, vaultService:String = "CyberCat.Sync") {
        self.directory=directory; self.persistCredentials=enabled; self.vaultService=vaultService
        guard enabled else { return }
        do {
            if let data=try credential(service:vaultService) { login=try cloudDecoder().decode(CloudLogin.self,from:data); cache=try load(owner!); status="Saved offline · waiting to sync" }
        } catch { login=nil; status="Couldn’t open secure account storage. Your local history is safe." }
    }
    private func file(_ owner: String) -> URL { directory.appendingPathComponent("cloud-\(owner).json") }
    private func load(_ owner: String) throws -> CloudCache { let f=file(owner); return FileManager.default.fileExists(atPath:f.path) ? try JSONDecoder().decode(CloudCache.self,from:Data(contentsOf:f)) : CloudCache() }
    private func write(_ updated: CloudCache) throws {
        guard let owner else { return }
        try JSONEncoder().encode(updated).write(to:file(owner),options:.atomic); cache=updated; onChange?()
    }
    func replace(_ sessions: [Session]) throws {
        var updated=cache; let wanted=Dictionary(uniqueKeysWithValues:sessions.map { ($0.id.uuidString.lowercased(),$0) })
        var known=Set<String>()
        for i in updated.records.indices {
            let old=updated.records[i]; known.insert(old.id)
            let name=wanted[old.id]?.name ?? old.name; let deleted=wanted[old.id] == nil
            if name != old.name || deleted != old.deleted {
                updated.records[i].name=name; updated.records[i].deleted=deleted; updated.records[i].dirty=true; updated.records[i].change=UUID().uuidString
            }
        }
        for (id,session) in wanted where !known.contains(id) { var r=CloudRecord(session); r.dirty=true; r.change=UUID().uuidString; updated.records.append(r) }
        try write(updated)
    }
    private func request<T:Decodable>(_ path:String, _ body:[String:Any], token:String? = nil) async throws -> T {
        var req=URLRequest(url:URL(string:cloudURL+path)!); req.httpMethod="POST"; req.timeoutInterval=25
        req.setValue("application/json",forHTTPHeaderField:"Content-Type"); req.setValue(cloudPublicKey,forHTTPHeaderField:"apikey")
        if let token { req.setValue("Bearer "+token,forHTTPHeaderField:"Authorization") }
        req.httpBody=try JSONSerialization.data(withJSONObject:body)
        let (data,response)=try await URLSession.shared.data(for:req)
        guard let http=response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            let message=(try? JSONSerialization.jsonObject(with:data) as? [String:Any])?["error"] as? String ?? "Unable to sync. Please try again."
            throw NSError(domain:"CyberCat.Cloud",code:1,userInfo:[NSLocalizedDescriptionKey:message])
        }
        return try cloudDecoder().decode(T.self,from:data)
    }
    func authenticate(username:String,pin:String,signup:Bool,existing:[Session]) async {
        guard !busy else { return }; busy=true; authenticating=true; defer { busy=false; authenticating=false }
        do {
            let result:CloudLogin=try await request("/functions/v1/cybercat-account",["action":signup ? "signup":"login","username":username,"pin":pin])
            let loaded=try load(result.user.id)
            if persistCredentials { _ = try credential(cloudEncoder().encode(result),service:vaultService) }; login=result; cache=loaded
            if !existing.isEmpty { try replace(visible+existing.filter { s in !visible.contains { $0.id == s.id } }) }
            status="Signed in · waiting to sync"; onChange?()
        } catch { status=error.localizedDescription }
    }
    func signOut() throws {
        guard !busy else { return }; if persistCredentials { _ = try credential(clear:true,service:vaultService) }; login=nil; cache=CloudCache(); status="Local only"; onChange?()
    }
    func resolve(keepLocal:Bool) throws {
        var updated=cache
        for i in updated.records.indices {
            if let remote=updated.conflicts[updated.records[i].id] {
                if keepLocal { updated.records[i].revision=remote.revision; updated.records[i].change=UUID().uuidString }
                else { updated.records[i]=remote }
            }
        }
        updated.conflicts=[:]; try write(updated)
    }
    func sync() async {
        guard !busy, let login else { return }; busy=true; defer { busy=false }
        let owner=login.user.id
        let sent=Array(cache.records.filter { $0.dirty == true && cache.conflicts[$0.id] == nil }.prefix(100))
        do {
            let refreshed:CloudLogin=try await request("/functions/v1/cybercat-account",["action":"refresh","refresh_token":login.refreshToken])
            guard self.owner == owner else { return }
            if persistCredentials { _ = try credential(cloudEncoder().encode(refreshed),service:vaultService) }; self.login=refreshed
            let payload=try JSONSerialization.jsonObject(with:cloudEncoder().encode(sent))
            let reply:CloudReply=try await request("/rest/v1/rpc/sync_focus_sessions",["changes":payload],token:refreshed.accessToken)
            guard self.owner == owner else { return }
            var updated=CloudCache(records:reply.records)
            var merged=Dictionary(uniqueKeysWithValues:reply.records.map { ($0.id,$0) })
            for local in cache.records where local.dirty == true {
                if reply.conflicts.contains(local.id), let remote=merged[local.id] { updated.conflicts[local.id]=remote; merged[local.id]=local }
                else if cache.conflicts[local.id] != nil { updated.conflicts[local.id]=cache.conflicts[local.id]; merged[local.id]=local }
                else if !sent.contains(where: { $0.id == local.id && $0.change == local.change }) {
                    var pending=local; pending.revision=merged[local.id]?.revision ?? local.revision; merged[local.id]=pending
                }
            }
            updated.records=Array(merged.values); try write(updated)
            status = !updated.conflicts.isEmpty ? "Changes need review" : (updated.records.contains { $0.dirty == true } ? "Saved offline · syncing remaining changes" : "All devices up to date")
        } catch { status="Saved offline · "+error.localizedDescription }
    }
}
