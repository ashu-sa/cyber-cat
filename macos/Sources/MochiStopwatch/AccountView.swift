import SwiftUI
struct AccountView: View {
    var onClose: (() -> Void)?
    @ObservedObject var model: Stopwatch
    @ObservedObject var cloud: CloudAccount
    @State private var username=""
    @State private var pin=""
    @State private var upload=false
    @Environment(\.dismiss) private var dismiss
    init(model:Stopwatch,onClose:(() -> Void)? = nil) { self.model=model; self.cloud=model.cloud; self.onClose=onClose }
    var body: some View {
        VStack(alignment:.leading,spacing:16) {
            HStack { Text("Your focus, everywhere").font(.title2.bold()); Spacer(); Button("Close") { cloud.cancelGoogleSignIn(); if let onClose { onClose() } else { dismiss() } } }
            Text(cloud.status).font(.callout).foregroundStyle(.secondary).fixedSize(horizontal:false,vertical:true)
            if cloud.owner != nil {
                Text("Signed in as \(cloud.username)")
                Button("Sync now") { Task { await cloud.sync() } }
                if !cloud.cache.conflicts.isEmpty {
                    Text("Some sessions changed on another device. Choose which edits to keep.")
                    HStack {
                        Button("Keep my changes") { resolve(true) }
                        Button("Use cloud changes") { resolve(false) }
                    }
                }
                Button("Sign out") { do { try cloud.signOut() } catch { cloud.status=error.localizedDescription } }.disabled(model.phase != .idle || cloud.busy)
            } else {
                Button("Continue with Google") {
                    guard model.phase == .idle else { return }
                    let records=upload ? model.sessions : []
                    Task { await cloud.authenticateGoogle(existing:records); if cloud.owner != nil { await cloud.sync() } }
                }.buttonStyle(.borderedProminent).disabled(model.phase != .idle || cloud.busy)
                Text("or use your existing username and PIN").font(.caption).foregroundStyle(.secondary)
                TextField("Username",text:$username).textFieldStyle(.roundedBorder)
                SecureField("4-digit PIN",text:$pin).textFieldStyle(.roundedBorder)
                Toggle("Add this device’s existing sessions to my account",isOn:$upload)
                Text("Use the same Google account on every device. Username/PIN accounts are separate and have no email recovery.").font(.caption).foregroundStyle(.secondary)
                HStack { Button("Sign in") { submit(false) }; Button("Create account") { submit(true) } }.disabled(model.phase != .idle || cloud.busy)
            }
            if model.phase != .idle { Text("Finish your current timer before switching accounts.").font(.caption) }
            if cloud.busy { ProgressView().controlSize(.small) }
            if cloud.googleSignInActive { Button("Cancel Google sign-in") { cloud.cancelGoogleSignIn() } }
        }.padding(24).frame(width:410)
    }
    func submit(_ signup:Bool) {
        let name=username.trimmingCharacters(in:.whitespacesAndNewlines).lowercased()
        guard name.range(of:"^[a-z0-9_]{3,24}$",options:.regularExpression) != nil, pin.range(of:"^[0-9]{4}$",options:.regularExpression) != nil else { cloud.status="Use 3–24 letters, numbers or underscores and a 4-digit PIN."; return }
        let code=pin; let records=upload ? model.sessions : []
        Task { await cloud.authenticate(username:name,pin:code,signup:signup,existing:records); pin=""; if cloud.owner != nil { await cloud.sync() } }
    }
    func resolve(_ keep:Bool) { do { try cloud.resolve(keepLocal:keep); Task { await cloud.sync() } } catch { cloud.status=error.localizedDescription } }
}
