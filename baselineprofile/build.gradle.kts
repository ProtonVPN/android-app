import groovy.json.StringEscapeUtils

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

val testAccountPass = System.getenv("TEST_ACCOUNT_PASS") ?: project.properties["testAccountPassword"] as? String ?: "Pass"

android {
    namespace = "ch.protonvpn.android.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TEST_ACCOUNT_PASSWORD",  "\"${StringEscapeUtils.escapeJava(testAccountPass)}\"")
    }

    targetProjectPath = ":app"

    flavorDimensions += listOf("environment", "functionality", "distribution")
    productFlavors {
        create("production") { dimension = "environment" }
        create("google") { dimension = "functionality" }
        create("playStore") { dimension = "distribution" }
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(project(":ui_automator_test_util"))

    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.espresso:espresso-core:3.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
    }
}