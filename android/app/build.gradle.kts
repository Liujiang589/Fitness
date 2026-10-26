import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}
// Android SDK keys are bound to the application package + signing certificate.
// Keep the local configuration outside source control and command-line arguments.
val amapProperties = Properties().apply {
    val local = rootProject.file("amap.local.properties")
    if (local.isFile) local.inputStream().use { load(it) }
}
val amapAndroidKey = (System.getenv("DUOLIAN_AMAP_ANDROID_KEY")
    ?: amapProperties.getProperty("AMAP_ANDROID_KEY", "")).trim()
require(amapAndroidKey.isEmpty() || amapAndroidKey.matches(Regex("[a-fA-F0-9]{32}"))) {
    "AMAP_ANDROID_KEY must be an Android SDK key (32 hexadecimal characters)."
}
android {
    namespace = "app.duolian.local"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.duolian.local"
        minSdk = 26
        targetSdk = 35
        versionCode = 33
        versionName = "1.19"
        manifestPlaceholders["AMAP_ANDROID_KEY"] = amapAndroidKey
        buildConfigField("boolean", "AMAP_CONFIGURED", amapAndroidKey.isNotEmpty().toString())
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://duolian-fitness-download.goodtulip.chatgpt.site/latest.json\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file(".local-signing/duolian-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("release") {
            // Preserve upgrade identity and the AMap certificate binding; optimize the runnable app.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
tasks.register("verifyStreetMapConfiguration") {
    group = "verification"
    description = "Check that an Android SDK key is configured before packaging the street-map release."
    doLast {
        require(amapAndroidKey.isNotEmpty()) {
            "Street map is not activated. Configure android/amap.local.properties using docs/AMAP_SETUP.md."
        }
        logger.lifecycle("Android map key configured; verify package/certificate authorization and street rendering on a device before publication.")
    }
}
dependencies {
    implementation("com.amap.api:3dmap-location-search:11.3.100_loc11.3.101_sea9.8.1")
    // The bundled SDK calls FastMath from its APS request encoder but omits the dependency in its POM.
    implementation("net.jafama:jafama:2.3.2")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

