import SwiftUI

struct AccountView: View {
    var onClose: (() -> Void)?
    @ObservedObject var model: Stopwatch
    @ObservedObject var cloud: CloudAccount
    @State private var username = ""
    @State private var pin = ""
    @State private var upload = false
    @State private var legacyExpanded = false
    @Environment(\.dismiss) private var dismiss
    private let forest = Color(red: 0.18, green: 0.38, blue: 0.27)
    init(model: Stopwatch, onClose: (() -> Void)? = nil) {
        self.model = model; self.cloud = model.cloud; self.onClose = onClose
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    Label("FOCANOPY", systemImage: "leaf.fill")
                        .font(.caption.weight(.semibold)).tracking(2).foregroundStyle(forest)
                    Text(cloud.owner == nil ? "One account. Every grove." : "Your grove is connected.")
                        .font(.system(size: 25, weight: .semibold, design: .rounded))
                }
                Spacer()
                Button(action: close) { Image(systemName: "xmark").font(.system(size: 12, weight: .semibold)).padding(7) }
                    .buttonStyle(.plain).background(.white.opacity(0.7), in: Circle()).accessibilityLabel("Close account")
            }
            if cloud.owner != nil {
                HStack(spacing: 12) {
                    Image(systemName: "person.crop.circle.fill").font(.system(size: 36)).foregroundStyle(forest)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(cloud.username).font(.headline)
                        Text("Your sessions stay with you across devices.").font(.caption).foregroundStyle(.secondary)
                    }
                }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(.white.opacity(0.8), in: RoundedRectangle(cornerRadius: 16))
                statusView
                Button { Task { await cloud.sync() } } label: {
                    Label(cloud.busy ? "Syncing…" : "Sync now", systemImage: "arrow.triangle.2.circlepath").frame(maxWidth: .infinity).padding(.vertical, 7)
                }.buttonStyle(.borderedProminent).tint(forest).disabled(cloud.busy)
                if !cloud.cache.conflicts.isEmpty {
                    Text("Some sessions changed on another device. Choose which edits to keep.").font(.callout)
                    HStack {
                        Button("Keep my changes") { resolve(true) }
                        Button("Use cloud changes") { resolve(false) }
                    }
                }
                Button("Sign out") { do { try cloud.signOut() } catch { cloud.status = error.localizedDescription } }
                    .buttonStyle(.plain).foregroundStyle(.secondary).disabled(model.phase != .idle || cloud.busy)
            } else {
                Text("Keep your focus sessions and growing trees together on your phone and computer.")
                    .font(.callout).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                Button {
                    guard model.phase == .idle else { return }
                    let records = upload ? model.sessions : []
                    Task { await cloud.authenticateGoogle(existing: records); if cloud.owner != nil { await cloud.sync() } }
                } label: {
                    HStack(spacing: 12) {
                        Text("G").font(.system(size: 20, weight: .bold)).foregroundStyle(Color(red: 0.26, green: 0.52, blue: 0.96)).accessibilityHidden(true)
                        Text(cloud.googleSignInActive ? "Waiting for Google…" : "Continue with Google").font(.system(size: 14, weight: .semibold))
                        Spacer()
                        if cloud.busy { ProgressView().controlSize(.small) } else { Image(systemName: "arrow.up.right").foregroundStyle(.secondary) }
                    }.padding(16).contentShape(Rectangle())
                }.buttonStyle(.plain).background(.white, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(forest.opacity(0.2)))
                    .disabled(model.phase != .idle || cloud.busy)
                Text("Opens securely in your browser. Sign in once on each device.").font(.caption).foregroundStyle(.secondary)
                Toggle(isOn: $upload) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Include sessions from this Mac").font(.callout.weight(.medium))
                        Text("Optional. Your local history stays here if switched off.").font(.caption).foregroundStyle(.secondary)
                    }
                }.toggleStyle(.switch).tint(forest).disabled(cloud.busy)
                Divider().opacity(0.5)
                DisclosureGroup("Use a username and PIN", isExpanded: $legacyExpanded) {
                    VStack(alignment: .leading, spacing: 10) {
                        TextField("Username", text: $username).textFieldStyle(.roundedBorder)
                        SecureField("4-digit PIN", text: $pin).textFieldStyle(.roundedBorder)
                        HStack {
                            Button("Sign in") { submit(false) }
                            Button("Create account") { submit(true) }
                        }.disabled(model.phase != .idle || cloud.busy)
                        Text("Separate from Google accounts. PIN accounts have no email recovery.").font(.caption).foregroundStyle(.secondary)
                    }.padding(.top, 12)
                }.font(.callout).tint(forest)
                if cloud.status != "Local only" { statusView }
                if cloud.googleSignInActive {
                    Button("Cancel Google sign-in") { cloud.cancelGoogleSignIn() }.buttonStyle(.bordered)
                }
            }
            if model.phase != .idle {
                Label("Finish your current timer before switching accounts.", systemImage: "info.circle").font(.caption).foregroundStyle(.secondary)
            }
        }.padding(26).frame(width: 450).background(Color(red: 0.94, green: 0.97, blue: 0.92)).environment(\.colorScheme, .light)
    }
    private var statusView: some View {
        HStack(alignment: .top, spacing: 8) {
            if cloud.busy { ProgressView().controlSize(.small) }
            else { Image(systemName: cloud.cache.conflicts.isEmpty ? "cloud" : "exclamationmark.circle") }
            Text(cloud.status).fixedSize(horizontal: false, vertical: true)
        }.font(.callout).foregroundStyle(.secondary).accessibilityElement(children: .combine)
    }
    private func close() { cloud.cancelGoogleSignIn(); if let onClose { onClose() } else { dismiss() } }
    func submit(_ signup: Bool) {
        let name = username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard name.range(of: "^[a-z0-9_]{3,24}$", options: .regularExpression) != nil, pin.range(of: "^[0-9]{4}$", options: .regularExpression) != nil else { cloud.status = "Use 3–24 letters, numbers or underscores and a 4-digit PIN."; return }
        let code = pin; let records = upload ? model.sessions : []
        Task { await cloud.authenticate(username: name, pin: code, signup: signup, existing: records); pin = ""; if cloud.owner != nil { await cloud.sync() } }
    }
    func resolve(_ keep: Bool) { do { try cloud.resolve(keepLocal: keep); Task { await cloud.sync() } } catch { cloud.status = error.localizedDescription } }
}
