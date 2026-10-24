plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "com.cybercat.stopwatch"
    compileSdk = 35
    defaultConfig { applicationId = "com.cybercat.stopwatch"; minSdk = 26; targetSdk = 35; versionCode = 17; versionName = "1.13.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    buildFeatures { buildConfig = true; compose = true }
    buildTypes {
        getByName("debug") { buildConfigField("boolean", "APP_BLOCKING_AVAILABLE", "true") }
        getByName("release") { buildConfigField("boolean", "APP_BLOCKING_AVAILABLE", "true") }
        create("standard") { initWith(getByName("debug")); buildConfigField("boolean", "APP_BLOCKING_AVAILABLE", "false"); signingConfig=signingConfigs.getByName("debug") }
        create("qa") { initWith(getByName("debug")); applicationIdSuffix=".qa"; signingConfig=signingConfigs.getByName("debug") }
        create("standardQa") { initWith(getByName("standard")); applicationIdSuffix=".qa"; signingConfig=signingConfigs.getByName("debug") }
    }
    sourceSets {
        getByName("debug") { java.srcDir("src/appBlocking/java"); res.srcDir("src/appBlocking/res"); manifest.srcFile("src/appBlocking/AndroidManifest.xml") }
        getByName("release") { java.srcDir("src/appBlocking/java"); res.srcDir("src/appBlocking/res"); manifest.srcFile("src/appBlocking/AndroidManifest.xml") }
        getByName("qa") { java.srcDir("src/appBlocking/java"); res.srcDir("src/appBlocking/res"); manifest.srcFile("src/appBlocking/AndroidManifest.xml") }
        getByName("standardQa").manifest.srcFile("src/standard/AndroidManifest.xml")
    }
    testBuildType = if(project.hasProperty("standardQa")) "standardQa" else if(project.hasProperty("pomodoroQa") || project.hasProperty("googleQa") || project.hasProperty("interactionQa") || project.hasProperty("mvpQa") || project.hasProperty("forestQa") || project.hasProperty("cloudQa")) "qa" else "debug"
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.browser:browser:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
}
