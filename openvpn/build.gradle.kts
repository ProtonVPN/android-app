
/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

plugins {
    id("com.android.library")
    id("checkstyle")
    kotlin("android")
    kotlin("android.extensions")
}

android {
    compileSdkVersion(30)
    ndkVersion = "21.3.6528147"

    defaultConfig {
        minSdkVersion(14)
        targetSdkVersion(30)  //'Q'.toInt()
        versionCode = 175
        versionName = "0.7.21"

        externalNativeBuild {
            cmake {
                //arguments = listOf("-DANDROID_TOOLCHAIN=clang",
                //        "-DANDROID_STL=c++_static")
            }
        }
    }

    testOptions.unitTests.isIncludeAndroidResources = true



    externalNativeBuild {
        cmake {
            path =File("${projectDir}/src/main/cpp/CMakeLists.txt")
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "build/ovpnassets")

        }
/*
        create("ui") {
        }

        create("skeleton") {
        }

        getByName("debug") {
        }

        getByName("release") {
        }*/
    }

/*    signingConfigs {
        create("release") {
            // ~/.gradle/gradle.properties
            val keystoreFile: String? by project
            storeFile = keystoreFile?.let { file(it) }
            val keystorePassword: String? by project
            storePassword = keystorePassword
            val keystoreAliasPassword: String? by project
            keyPassword = keystoreAliasPassword
            val keystoreAlias: String? by project
            keyAlias = keystoreAlias
            isV1SigningEnabled = true
            isV2SigningEnabled = true
        }

    }

    lintOptions {
        enable("BackButton", "EasterEgg", "StopShip", "IconExpectedSize", "GradleDynamicVersion", "NewerVersionAvailable")
        warning("ImpliedQuantity", "MissingQuantity")
        disable("MissingTranslation", "UnsafeNativeCodeLocation")
    }

    buildTypes {
        getByName("release") {
            if (project.hasProperty("icsopenvpnDebugSign")) {
                logger.warn("property icsopenvpnDebugSign set, using debug signing for release")
                signingConfig = android.signingConfigs.getByName("debug")
            } else {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
*/
    flavorDimensions("implementation")

    productFlavors {
        /*
        create("ui") {
            setDimension("implementation")
            buildConfigField("boolean", "openvpn3", "true")
        }
        */
        create("skeleton") {
            setDimension("implementation")
            buildConfigField("boolean", "openvpn3", "false")
        }
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_1_8
        sourceCompatibility = JavaVersion.VERSION_1_8
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }


}

var swigcmd = "swig"
// Workaround for Mac OS X since it otherwise does not find swig and I cannot get
// the Exec task to respect the PATH environment :(
if (File("/usr/local/bin/swig").exists())
    swigcmd = "/usr/local/bin/swig"


fun registerGenTask(variantName: String, variantDirName: String): File {
    val baseDir = File(buildDir, "generated/source/ovpn3swig/${variantDirName}")
    val genDir = File(baseDir, "net/openvpn/ovpn3")

    tasks.register<Exec>("generateOpenVPN3Swig${variantName}")
    {

        doFirst {
            mkdir(genDir)
        }
        commandLine(listOf(swigcmd, "-outdir", genDir, "-outcurrentdir", "-c++", "-java", "-package", "net.openvpn.ovpn3",
            "-Isrc/main/cpp/openvpn3/client", "-Isrc/main/cpp/openvpn3/",
            "-o", "${genDir}/ovpncli_wrap.cxx", "-oh", "${genDir}/ovpncli_wrap.h",
            "src/main/cpp/openvpn3/javacli/ovpncli.i"))

    }
    return baseDir
}

/* android.applicationVariants.all(object : Action<ApplicationVariant> {
    override fun execute(variant: ApplicationVariant) {
        val sourceDir = registerGenTask(variant.name, variant.baseName.replace("-", "/"))
        val task = tasks.named("generateOpenVPN3Swig${variant.name}").get()

        variant.registerJavaGeneratingTask(task, sourceDir)
    }
}) */


dependencies {
    // https://maven.google.com/web/index.html
    // https://developer.android.com/jetpack/androidx/releases/core
    val preferenceVersion = "1.1.1"
    val coreVersion = "1.2.0"
    val materialVersion = "1.1.0"
    val fragment_version = "1.2.4"


    implementation("androidx.annotation:annotation:1.1.0")
    implementation("androidx.core:core:$coreVersion")
/*
    // Is there a nicer way to do this?
    dependencies.add("uiImplementation", "androidx.constraintlayout:constraintlayout:1.1.3")
    dependencies.add("uiImplementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.72")
    dependencies.add("uiImplementation", "androidx.cardview:cardview:1.0.0")
    dependencies.add("uiImplementation", "androidx.recyclerview:recyclerview:1.0.0")
    dependencies.add("uiImplementation", "androidx.appcompat:appcompat:1.1.0")
    dependencies.add("uiImplementation", "com.github.PhilJay:MPAndroidChart:v3.1.0")
    dependencies.add("uiImplementation", "com.squareup.okhttp3:okhttp:3.2.0")
    dependencies.add("uiImplementation", "androidx.core:core:$coreVersion")
    dependencies.add("uiImplementation", "androidx.core:core-ktx:$coreVersion")
    dependencies.add("uiImplementation", "org.jetbrains.anko:anko-commons:0.10.4")
    dependencies.add("uiImplementation", "androidx.fragment:fragment-ktx:$fragment_version")
    dependencies.add("uiImplementation", "androidx.preference:preference:$preferenceVersion")
    dependencies.add("uiImplementation", "androidx.preference:preference-ktx:$preferenceVersion")
    dependencies.add("uiImplementation", "com.google.android.material:material:$materialVersion")
    dependencies.add("uiImplementation", "androidx.webkit:webkit:1.2.0")
*/
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.72")
    testImplementation("junit:junit:4.13")
    testImplementation("org.mockito:mockito-core:3.3.3")
    testImplementation("org.robolectric:robolectric:4.3.1")
    testImplementation("androidx.test:core:1.2.0")
}