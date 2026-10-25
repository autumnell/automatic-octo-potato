import java.time.LocalDate
import java.time.ZoneOffset

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.mtgscanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mtgscanner"
        minSdk = 26
        targetSdk = 35
        // CI numbers each build from the workflow's run number (it only goes up, so every build installs
        // over the last). Local builds are build 0, version "1.0".
        val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = maxOf(buildNumber, 1)
        versionName = "1.$buildNumber"
        buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
        buildConfigField("String", "BUILD_DATE", "\"${LocalDate.now(ZoneOffset.UTC)}\"")
        // Phones only: leaving out emulator (x86) native libraries keeps the APK smaller.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // A fixed debug key checked into the repo so every build from CI can be
        // installed over the previous one. Fine for a personal app, not for the Play Store.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // A private release key, used only when CI provides one (RELEASE_KEYSTORE_FILE plus the passwords
        // and alias from repo secrets). Without it, release builds keep using the committed key above.
        val releaseStore = System.getenv("RELEASE_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }?.let { file(it) }
        if (releaseStore != null && releaseStore.exists()) {
            create("release") {
                storeFile = releaseStore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // The build CI publishes. Same applicationId (no suffix on either type) and the same key as
        // debug builds, so it installs over an existing debug install and keeps its data (unless CI has a
        // private release key; see signingConfigs). R8's mapping.txt is published with each build so
        // crash reports can be read.
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            // Native libraries (OpenCV is about 20 MB per ABI) are stored compressed: a much smaller APK to
            // download, unpacked once at install.
            useLegacyPackaging = true
            // OpenCV brings its own copy of the C++ runtime; if another library does too, keep one.
            pickFirsts += "**/libc++_shared.so"
        }
    }
    // Keep a release build from failing on lint alone. CI runs lintDebug separately and uploads the report;
    // existing warnings and errors don't fail the build yet.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
        warningsAsErrors = false
        xmlReport = true
        htmlReport = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    // Applies the startup profile (src/main/baseline-prof.txt) on install, so main screens open precompiled.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Camera
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // On-device text recognition (models bundled in the APK, work offline). Latin reads every frame; the
    // Japanese, Chinese and Korean readers only read the name bar when needed.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")

    // OpenCV: finds the card's corners and straightens it. Falls back to the app's own code if it won't load.
    implementation("org.opencv:opencv:4.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Daily price alerts in the background
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Storage
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Networking (Scryfall)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Card images
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // Art model (image embedder) for telling artwork apart. The model file is downloaded on first use.
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // QR codes on box labels, and reading them back to open a box.
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}

// Temporary (probe branch only): print test output in CI.
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
