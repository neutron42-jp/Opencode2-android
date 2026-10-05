plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.opencode.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.opencode.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.0.4"
    }

    signingConfigs {
        create("release") {
            val home = System.getProperty("user.home")
            storeFile = file(
                System.getenv("OPENCODE_KEYSTORE_FILE")
                    ?: "$home/.android/opencode-release.keystore"
            )
            storePassword = System.getenv("OPENCODE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("OPENCODE_KEY_ALIAS") ?: "opencode"
            keyPassword = System.getenv("OPENCODE_KEY_PASSWORD")
                ?: System.getenv("OPENCODE_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.biometric:biometric:1.1.0")
}
