import Foundation
import SwiftUI
import Security
import LocalAuthentication
import AuthenticationServices
import CryptoKit
import AppKit

private let cloudURL = "https://fwlengqpmrtmpbrhtxis.supabase.co"
private let cloudPublicKey = "sb_publishable_gsnb17q8TXRiHPu7W1vr4g_K7J8Np3H" // Public project key is filled before packaging.
struct CloudMetadata: Codable { var username: String?; var fullName: String? = nil; var name: String? = nil }
struct CloudUser: Codable { var id: String; var userMetadata: CloudMetadata?; var email: String? = nil }
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
private func credential(_ data: Data? = nil, clear: Bool = false, service:String = "CyberCat.Sync", allowInteraction: Bool = false) throws -> Data? {
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
    // Startup must never wait for an invisible Keychain authorization dialog.
    // Preserve the saved credential if access needs user interaction.
    var read=key; read[kSecReturnData as String]=true; read[kSecMatchLimit as String]=kSecMatchLimitOne
    let context = LAContext(); context.interactionNotAllowed = !allowInteraction
    read[kSecUseAuthenticationContext as String] = context
    var result: CFTypeRef?; let status=SecItemCopyMatching(read as CFDictionary,&result)
    if status == errSecItemNotFound { return nil }
    guard status == errSecSuccess else { throw NSError(domain:NSOSStatusErrorDomain,code:Int(status)) }
    return result as? Data
}
// Keep Keychain writes off the UI thread during sign-in and manual sync.
private func saveCredential(_ data: Data, service: String) async throws {
    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
        DispatchQueue.global(qos: .userInitiated).async {
            do { _ = try credential(data, service: service); continuation.resume() }
            catch { continuation.resume(throwing: error) }
        }
    }
}

struct GooglePKCE {
    let verifier: String
    let state: String
    init() throws {
        func random() throws -> String {
            var bytes=[UInt8](repeating:0,count:32)
            guard SecRandomCopyBytes(kSecRandomDefault,bytes.count,&bytes) == errSecSuccess else { throw URLError(.unknown) }
            return Data(bytes).base64EncodedString().replacingOccurrences(of:"+",with:"-").replacingOccurrences(of:"/",with:"_").replacingOccurrences(of:"=",with:"")
        }
        verifier=try random(); state=try random()
    }
    var challenge: String { Data(SHA256.hash(data:Data(verifier.utf8))).base64EncodedString().replacingOccurrences(of:"+",with:"-").replacingOccurrences(of:"/",with:"_").replacingOccurrences(of:"=",with:"") }
    var url: URL {
        var redirect=URLComponents(string:"focanopy://auth/callback")!
        redirect.queryItems=[URLQueryItem(name:"state",value:state)]
        var url=URLComponents(string:cloudURL+"/auth/v1/authorize")!
        url.queryItems=[URLQueryItem(name:"provider",value:"google"),URLQueryItem(name:"redirect_to",value:redirect.url!.absoluteString),URLQueryItem(name:"code_challenge",value:challenge),URLQueryItem(name:"code_challenge_method",value:"s256"),URLQueryItem(name:"scopes",value:"openid email profile"),URLQueryItem(name:"prompt",value:"select_account")]
        return url.url!
    }
    func code(_ url: URL) throws -> String {
        guard let parts=URLComponents(url:url,resolvingAgainstBaseURL:false), parts.scheme=="focanopy", parts.host=="auth", parts.path=="/callback" else { throw URLError(.badServerResponse) }
        let items=parts.queryItems ?? []
        guard items.filter({ $0.name=="state" }).count==1, items.first(where: { $0.name=="state" })?.value==state else { throw URLError(.badServerResponse) }
        guard !items.contains(where: { $0.name=="error" }), items.filter({ $0.name=="code" }).count==1, let code=items.first(where: { $0.name=="code" })?.value, !code.isEmpty, code.count<=2048 else { throw NSError(domain:"Focanopy.Google",code:1,userInfo:[NSLocalizedDescriptionKey:"Google sign-in was not completed. Please try again."]) }
        return code
    }
}

@MainActor final class GoogleBrowserSignIn: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var session: ASWebAuthenticationSession?
    private var completion: CheckedContinuation<URL,Error>?
    private var timeout: Task<Void,Never>?
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor { NSApp.keyWindow ?? NSApp.windows.first ?? NSWindow() }
    func open(_ url: URL) async throws -> URL {
        try await withCheckedThrowingContinuation { completion in
            self.completion=completion
            let session=ASWebAuthenticationSession(url:url,callbackURLScheme:"focanopy") { [weak self] url,error in
                Task { @MainActor in
                    if let url { self?.finish(.success(url)) }
                    else { self?.finish(.failure(error ?? CancellationError())) }
                }
            }
            session.presentationContextProvider=self
            self.session=session
            if !session.start() { finish(.failure(URLError(.cannotConnectToHost))); return }
            timeout=Task { [weak self] in
                do { try await Task.sleep(nanoseconds:180_000_000_000) } catch { return }
                self?.cancel()
            }
        }
    }
    private func finish(_ result: Result<URL,Error>) {
        guard let completion else { return }
        self.completion=nil; timeout?.cancel(); timeout=nil; session=nil
        completion.resume(with:result)
    }
    func cancel() { session?.cancel(); finish(.failure(CancellationError())) }
}

@MainActor final class CloudAccount: ObservableObject {
    @Published var login: CloudLogin?
    @Published var status = "Local only"
    @Published var busy = false
    @Published var cache = CloudCache()
    private let persistCredentials: Bool
    private let vaultService: String
    @Published var authenticating=false
    @Published var googleSignInActive=false
    @Published var needsCredentialRestore = false
    private var googleBrowser: GoogleBrowserSignIn?
    private var googleRequestID: UUID?
    private var restoreRequestID: UUID?
    let directory: URL
    var onChange: (() -> Void)?
    var owner: String? { login?.user.id }
    var username: String { login?.user.userMetadata?.username ?? login?.user.userMetadata?.fullName ?? login?.user.userMetadata?.name ?? login?.user.email ?? "Google account" }
    var visible: [Session] { cache.records.filter { !$0.deleted }.map(\.local).sorted { $0.start > $1.start } }
    init(directory: URL, enabled: Bool, vaultService:String = "CyberCat.Sync") {
        self.directory=directory; self.persistCredentials=enabled; self.vaultService=vaultService
        guard enabled else { return }
        let requestID = UUID(); restoreRequestID = requestID; busy = true
        status = "Restoring your saved account…"
        // Legacy Keychain services can stall even with interaction disabled.
        // Never perform the OS credential read on the UI thread.
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let result = Result { try credential(service: vaultService) }
            Task { @MainActor [weak self] in
                guard let self, self.restoreRequestID == requestID else { return }
                self.restoreRequestID = nil; self.busy = false; self.needsCredentialRestore = false
                do {
                    if let data = try result.get() {
                        let restored = try cloudDecoder().decode(CloudLogin.self, from: data)
                        let restoredCache = try self.load(restored.user.id)
                        self.login = restored; self.cache = restoredCache
                        self.status = "Saved offline · waiting to sync"; self.onChange?()
                        await self.sync()
                    } else { self.status = "Local only" }
                } catch { self.needsCredentialRestore = true; self.status = "Your saved login needs Keychain access. Your account and history are preserved." }
            }
        }
        Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(4))
            guard let self, self.restoreRequestID == requestID else { return }
            self.busy = false; self.needsCredentialRestore = true
            self.status = "Keychain isn’t responding. You can keep focusing locally. Your saved account and history are preserved."
        }
    }
    func restoreSavedAccount() async {
        guard !busy else { return }
        restoreRequestID = nil; busy = true
        status = "Restoring saved sign-in from Keychain…"
        defer { busy = false }
        do {
            let service = vaultService
            let data: Data? = try await withCheckedThrowingContinuation { continuation in
                DispatchQueue.global(qos: .userInitiated).async {
                    do { continuation.resume(returning: try credential(service: service, allowInteraction: true)) }
                    catch { continuation.resume(throwing: error) }
                }
            }
            if let data {
                let restored = try cloudDecoder().decode(CloudLogin.self, from: data)
                let restoredCache = try load(restored.user.id)
                login = restored; cache = restoredCache; needsCredentialRestore = false
                status = "Saved sign-in restored"; onChange?()
            } else { needsCredentialRestore = false; status = "Local only" }
        } catch {
            needsCredentialRestore = true
            status = "macOS couldn’t unlock your saved sign-in. Your account is still saved. Allow Focanopy access in the Keychain prompt, then retry Sync."
        }
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
            let details=(try? JSONSerialization.jsonObject(with:data)) as? [String:Any]
            let message=details?["error_description"] as? String ?? details?["msg"] as? String ?? details?["error"] as? String ?? "Unable to sync. Please try again."
            throw NSError(domain:"CyberCat.Cloud",code:1,userInfo:[NSLocalizedDescriptionKey:message])
        }
        return try cloudDecoder().decode(T.self,from:data)
    }
    func authenticate(username:String,pin:String,signup:Bool,existing:[Session]) async {
        guard !busy else { return }; busy=true; authenticating=true; defer { busy=false; authenticating=false }
        do {
            let result:CloudLogin=try await request("/functions/v1/cybercat-account",["action":signup ? "signup":"login","username":username,"pin":pin])
            try await acceptLogin(result,existing:existing)
        } catch { status=error.localizedDescription }
    }
    private func acceptLogin(_ result: CloudLogin,existing: [Session]) async throws {
        let loaded=try load(result.user.id)
        if persistCredentials { try await saveCredential(cloudEncoder().encode(result), service: vaultService) }; login=result; cache=loaded
        if !existing.isEmpty { try replace(visible+existing.filter { s in !visible.contains { $0.id == s.id } }) }
        status="Signed in · waiting to sync"; onChange?()
    }
    func authenticateGoogle(existing: [Session]) async {
        guard !busy else { return }; busy=true; authenticating=true; googleSignInActive=true
        let requestID=UUID(); googleRequestID=requestID
        defer { busy=false; authenticating=false; googleSignInActive=false; googleBrowser=nil; googleRequestID=nil }
        do {
            var settingsRequest=URLRequest(url:URL(string:cloudURL+"/auth/v1/settings")!); settingsRequest.timeoutInterval=15
            settingsRequest.setValue(cloudPublicKey,forHTTPHeaderField:"apikey")
            let (settingsData,settingsResponse)=try await URLSession.shared.data(for:settingsRequest)
            guard let http=settingsResponse as? HTTPURLResponse, (200..<300).contains(http.statusCode) else { throw URLError(.badServerResponse) }
            let settings=(try JSONSerialization.jsonObject(with:settingsData)) as? [String:Any]
            guard (settings?["external"] as? [String:Any])?["google"] as? Bool == true else { throw NSError(domain:"Focanopy.Google",code:2,userInfo:[NSLocalizedDescriptionKey:"Google sign-in needs one-time setup by the app owner. Username and PIN still work."]) }
            guard googleRequestID == requestID else { throw CancellationError() }
            let pkce=try GooglePKCE()
            let browser=GoogleBrowserSignIn(); googleBrowser=browser
            status="Continue with Google in your browser…"
            let callback=try await browser.open(pkce.url)
            guard googleRequestID == requestID else { throw CancellationError() }
            let code=try pkce.code(callback)
            let result:CloudLogin=try await request("/auth/v1/token?grant_type=pkce",["auth_code":code,"code_verifier":pkce.verifier])
            guard googleRequestID == requestID else { throw CancellationError() }
            try await acceptLogin(result,existing:existing)
        } catch is CancellationError { status="Google sign-in cancelled. Your local history is safe." }
        catch let error as ASWebAuthenticationSessionError where error.code == .canceledLogin { status="Google sign-in cancelled. Your local history is safe." }
        catch { status=error.localizedDescription }
    }
    func cancelGoogleSignIn() { googleRequestID=nil; googleBrowser?.cancel() }

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
            if persistCredentials { try await saveCredential(cloudEncoder().encode(refreshed), service: vaultService) }; self.login=refreshed
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
