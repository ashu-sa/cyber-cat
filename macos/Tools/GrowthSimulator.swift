import SwiftUI
@main struct GrowthSimulator:App {
    var body:some Scene {
        WindowGroup("Focanopy · Tree lifecycle preview") {TreeLifecyclePreview()}.windowResizability(.contentSize)
    }
}
