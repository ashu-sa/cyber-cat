plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.cybercat.stopwatch"
    compileSdk = 35
    defaultConfig { applicationId = "com.cybercat.stopwatch"; minSdk = 26; targetSdk = 35; versionCode = 16; versionName = "1.12.1"; testInstrumentationRunner = if(project.hasProperty("standardQa")) "com.cybercat.stopwatch.StandardInstrumentation" else if(project.hasProperty("pomodoroQa")) "com.cybercat.stopwatch.PomodoroInstrumentation" else if(project.hasProperty("googleQa")) "com.cybercat.stopwatch.GoogleInstrumentation" else if(project.hasProperty("interactionQa")) "com.cybercat.stopwatch.InteractionInstrumentation" else if(project.hasProperty("mvpQa")) "com.cybercat.stopwatch.SmokeInstrumentation" else if(project.hasProperty("forestQa")) "com.cybercat.stopwatch.ForestInstrumentation" else if(project.hasProperty("cloudQa")) "com.cybercat.stopwatch.CloudInstrumentation" else "com.cybercat.stopwatch.SmokeInstrumentation" }
    buildFeatures { buildConfig = true }
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
dependencies { testImplementation("junit:junit:4.13.2") }
