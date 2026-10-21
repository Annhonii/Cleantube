plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes -PversionCode=<run number> so every build is newer than the last one.
val buildNumber = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.cleantube.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cleantube.app"
        minSdk = 21
        targetSdk = 35
        versionCode = buildNumber
        versionName = "2.0.$buildNumber"
    }

    signingConfigs {
        // A fixed key checked into the repo: every CI build has the same signature,
        // so a new APK installs over the old one without uninstalling.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        // NewPipeExtractor uses java.time etc., which minSdk 21 doesn't have natively
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/{DEPENDENCIES,INDEX.LIST,LICENSE*,NOTICE*}"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Thumbnails / avatars
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    // Picks the main colour of a video thumbnail (the app colours follow the playing video)
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // Native player (ExoPlayer): plays the NewPipe stream links directly, no YouTube web player involved.
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    // NewPipe engine: the same extractor the NewPipe app uses to get playable / downloadable stream links.
    // If YouTube breaks downloads again, bump this to the newest release tag:
    // https://github.com/TeamNewPipe/NewPipeExtractor/releases
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
