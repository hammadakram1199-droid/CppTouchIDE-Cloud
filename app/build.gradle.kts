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
        versionCode = 4
        versionName = "4.0"
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
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // Sora Code Editor
    implementation("io.github.Rosemoe.sora-editor:editor:0.23.4")
    
    // ML Kit Handwriting
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
}
