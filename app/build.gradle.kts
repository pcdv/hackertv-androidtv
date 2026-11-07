plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.pcdv.hackertv"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.pcdv.hackertv"
        minSdk = 26
        targetSdk = 34
        // CI passes the tag as -PversionName and the run number as -PversionCode.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "0.1.0"
    }

    // The release key comes from the environment, so local builds work without it and the
    // key never sits in the repository. Without KEYSTORE_FILE the release APK is unsigned.
    signingConfigs {
        System.getenv("KEYSTORE_FILE")?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
