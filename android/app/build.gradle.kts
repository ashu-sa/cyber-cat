plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.cybercat.stopwatch"
    compileSdk = 35
    defaultConfig { applicationId = "com.cybercat.stopwatch"; minSdk = 26; targetSdk = 35; versionCode = 14; versionName = "1.11.0"; testInstrumentationRunner = if(project.hasProperty("pomodoroQa")) "com.cybercat.stopwatch.PomodoroInstrumentation" else if(project.hasProperty("googleQa")) "com.cybercat.stopwatch.GoogleInstrumentation" else if(project.hasProperty("interactionQa")) "com.cybercat.stopwatch.InteractionInstrumentation" else if(project.hasProperty("mvpQa")) "com.cybercat.stopwatch.SmokeInstrumentation" else if(project.hasProperty("forestQa")) "com.cybercat.stopwatch.ForestInstrumentation" else if(project.hasProperty("cloudQa")) "com.cybercat.stopwatch.CloudInstrumentation" else "com.cybercat.stopwatch.SmokeInstrumentation" }
    buildTypes { create("qa") { initWith(getByName("debug")); applicationIdSuffix=".qa"; signingConfig=signingConfigs.getByName("debug") } }
    testBuildType = if(project.hasProperty("pomodoroQa") || project.hasProperty("googleQa") || project.hasProperty("interactionQa") || project.hasProperty("mvpQa") || project.hasProperty("forestQa") || project.hasProperty("cloudQa")) "qa" else "debug"
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }
