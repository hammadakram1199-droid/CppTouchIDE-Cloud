plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.cpptouchide"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cpptouchide"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    // Use 'api' to expose ML Kit classes to the Kotlin compiler
    api("com.google.mlkit:digital-ink-recognition:19.0.0")
    // Explicitly include Kotlin stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
}
