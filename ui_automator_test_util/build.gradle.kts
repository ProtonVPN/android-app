plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val minSdkVersion: Int by rootProject.extra

android {
    namespace = "com.protonvpn.android.ui_automator_test_util"
    compileSdk = 36

    defaultConfig {
        minSdk = minSdkVersion
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

// TODO: we need to introduce version catalog!
val androidx_lifecycle_version: String by rootProject.extra
val core_version: String by rootProject.extra

dependencies {
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$androidx_lifecycle_version")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("me.proton.test:fusion:0.9.62")
    implementation("me.proton.core:test-performance:$core_version")
    implementation("androidx.test:rules:1.6.1")
}