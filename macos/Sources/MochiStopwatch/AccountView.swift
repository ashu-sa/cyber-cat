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
            Text(cloud.owner == nil ? (cloud.googleSignInActive ? "Connecting with Google" : "Sign in with Google") : cloud.username)
                .font(.system(size: 20, weight: .semibold, design: .rounded))
            if cloud.owner != nil {
                Text("Your grove, on every device.").font(.callout).foregroundStyle(.secondary)
                if cloud.busy { ProgressView().controlSize(.small) }
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
                    Text("Choose your Google account to continue.").font(.caption).foregroundStyle(.secondary)
                } else {
                    Text(model.phase != .idle ? "Finish your timer to connect your account." : cloud.status)
                        .font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    Button("Continue with Google", action: connect)
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
        Task { await cloud.authenticateGoogle(existing: []); if cloud.owner != nil { await cloud.sync() } }
    }
    private func close() { cloud.cancelGoogleSignIn(); if let onClose { onClose() } else { dismiss() } }
    private func resolve(_ keep: Bool) { do { try cloud.resolve(keepLocal: keep); Task { await cloud.sync() } } catch { cloud.status = error.localizedDescription } }
}

struct JournalSyncButton: View {
    @ObservedObject var cloud: CloudAccount
    var onSignIn: () -> Void
    var body: some View {
        VStack(alignment: .trailing, spacing: 4) {
            Button {
                if cloud.owner == nil { onSignIn() }
                else { Task { await cloud.sync() } }
            } label: {
                HStack(spacing: 6) {
                    if cloud.busy { ProgressView().controlSize(.small) }
                    else { Image(systemName: "arrow.triangle.2.circlepath") }
                    Text(cloud.busy ? "Syncing…" : "Sync")
                }
            }.buttonStyle(.bordered).disabled(cloud.busy)
                .help(cloud.owner == nil ? "Sign in with Google to sync your devices" : "Upload saved sessions and refresh sessions from your other devices")
            if cloud.owner != nil {
                Text(cloud.status).font(.caption2).foregroundStyle(.secondary)
                    .lineLimit(2).frame(maxWidth: 190, alignment: .trailing)
                    .accessibilityLabel("Sync status: \(cloud.status)")
            }
        }
    }
}
