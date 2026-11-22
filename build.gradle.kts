plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lontum.screenprice"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lontum.screenprice"
        minSdk = 24
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // Fixed key kept in the project: every new APK installs as an UPDATE and your data is kept.
    signingConfigs {
        create("fixed") {
            storeFile = file("screenprice.keystore")
            storePassword = "lontum2026"
            keyAlias = "screenprice"
            keyPassword = "lontum2026"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.google.android.material:material:1.12.0")
}
