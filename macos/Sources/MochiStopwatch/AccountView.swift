import SwiftUI

struct AccountView: View {
    var onClose: (() -> Void)?
    @ObservedObject var model: Stopwatch
    @ObservedObject var cloud: CloudAccount
    @Environment(\.dismiss) private var dismiss
    @State private var started = false
    private let forest = Color(red: 0.18, green: 0.38, blue: 0.27)
    init(model: Stopwatch, onClose: (() -> Void)? = nil) {
        self.model = model; self.cloud = model.cloud; self.onClose = onClose
    }
    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Label("Focanopy", systemImage: "leaf.fill").font(.headline).foregroundStyle(forest)
                Spacer()
                Button(action: close) { Image(systemName: "xmark").font(.caption.weight(.semibold)) }
                    .buttonStyle(.plain).accessibilityLabel("Close profile")
            }
            Image(systemName: cloud.owner == nil ? "person.crop.circle" : "person.crop.circle.badge.checkmark")
                .font(.system(size: 38, weight: .light)).foregroundStyle(forest)
            Text(cloud.owner == nil ? (cloud.needsCredentialRestore ? "Restoring your profile" : (cloud.googleSignInActive ? "Connecting with Google" : "Sign in with Google")) : cloud.username)
                .font(.system(size: 20, weight: .semibold, design: .rounded))
            if cloud.owner != nil {
                Text("Your grove, on every device.").font(.callout).foregroundStyle(.secondary)
                if cloud.busy { SyncArrows(active: true) }
                else { Label(cloud.status, systemImage: "cloud").font(.caption).foregroundStyle(.secondary) }
                if !cloud.cache.conflicts.isEmpty {
                    Text("Choose which session edits to keep.").font(.caption)
                    HStack {
                        Button("Keep mine") { resolve(true) }
                        Button("Use cloud") { resolve(false) }
                    }
                }
                Button("Sign out") { do { try cloud.signOut() } catch { cloud.status = error.localizedDescription } }
                    .buttonStyle(.plain).foregroundStyle(.secondary).font(.caption)
                    .disabled(model.phase != .idle || cloud.busy)
            } else {
                if cloud.busy {
                    ProgressView().controlSize(.small)
                    Text(cloud.needsCredentialRestore ? "Unlocking your saved sign-in with macOS…" : "Choose your Google account to continue.").font(.caption).foregroundStyle(.secondary)
                } else {
                    Text(model.phase != .idle ? "Finish your timer to connect your account." : cloud.status)
                        .font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    Button(cloud.needsCredentialRestore ? "Restore saved sign-in" : "Continue with Google", action: connect)
                        .buttonStyle(.borderedProminent).tint(forest).disabled(model.phase != .idle)
                }
                Text("No password or registration form.").font(.caption).foregroundStyle(.secondary)
            }
        }.padding(22).frame(width: 310).background(Color(red: 0.94, green: 0.97, blue: 0.92))
            .environment(\.colorScheme, .light)
            .task {
                guard !started else { return }; started = true
                // Wait for the bounded saved-account restore before starting OAuth.
                while cloud.busy && !cloud.googleSignInActive {
                    do { try await Task.sleep(for: .milliseconds(100)) } catch { return }
                }
                guard !Task.isCancelled, cloud.owner == nil, model.phase == .idle else { return }
                connect()
            }
    }
    private func connect() {
        guard !cloud.busy, model.phase == .idle else { return }
        Task {
            if cloud.needsCredentialRestore { await cloud.restoreSavedAccount() }
            else { await cloud.authenticateGoogle(existing: []) }
            if cloud.owner != nil { await cloud.sync() }
        }
    }
    private func close() { cloud.cancelGoogleSignIn(); if let onClose { onClose() } else { dismiss() } }
    private func resolve(_ keep: Bool) { do { try cloud.resolve(keepLocal: keep); Task { await cloud.sync() } } catch { cloud.status = error.localizedDescription } }
}

struct JournalSyncButton: View {
    @ObservedObject var cloud: CloudAccount
    var onSignIn: () -> Void
    var body: some View {
        Button {
            Task {
                if cloud.needsCredentialRestore { await cloud.restoreSavedAccount() }
                if cloud.owner != nil { await cloud.sync() }
                else if !cloud.needsCredentialRestore { onSignIn() }
            }
        } label: {
            Group {
                SyncArrows(active: cloud.busy)
            }.frame(width: 38, height: 38)
                .foregroundStyle(Color.cyber)
                .background(Color.cyber.opacity(0.1), in: Circle())
        }.buttonStyle(.plain).disabled(cloud.busy)
            .accessibilityLabel(cloud.busy ? "Syncing sessions" : "Sync sessions")
            .help(cloud.needsCredentialRestore ? "Restore saved sign-in and sync" : (cloud.owner == nil ? "Connect Google to sync devices" : cloud.status + " · Click to sync"))
    }
}

private struct SyncArrows: View {
    let active: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var turning = false
    var body: some View {
        Image(systemName: "arrow.triangle.2.circlepath")
            .font(.system(size: 18, weight: .medium))
            .rotationEffect(.degrees(turning ? 360 : 0))
            .animation(turning ? .linear(duration: 1.2).repeatForever(autoreverses: false) : nil, value: turning)
            .onAppear { turning = active && !reduceMotion }
            .onChange(of: active) { _, value in turning = value && !reduceMotion }
            .onChange(of: reduceMotion) { _, value in turning = active && !value }
    }
}
