// swift-tools-version: 6.0
import PackageDescription
let package = Package(name: "MochiStopwatch", platforms: [.macOS(.v14)], products: [.executable(name: "MochiStopwatch", targets: ["MochiStopwatch"])], targets: [.executableTarget(name: "MochiStopwatch")], swiftLanguageModes: [.v5])
