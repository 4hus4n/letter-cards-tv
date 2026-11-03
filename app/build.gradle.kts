plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Every GitHub Actions run gets a higher version, so the app can tell when an update is out.
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.clickandcraft.lettercards"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.clickandcraft.lettercards"
        minSdk = 21
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.$runNumber"
        // GitHub repo the app checks for new releases (owner/name)
        buildConfigField("String", "UPDATE_REPO", "\"4hus4n/letter-cards-tv\"")
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        create("release") {
            // Same key every build, so updates install over the old app
            storeFile = file("release.jks")
            storePassword = "lettercards"
            keyAlias = "lettercards"
            keyPassword = "lettercards"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.core:core:1.13.1")
}
