import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.LibraryVariant

/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("checkstyle")
}

android {
    buildFeatures {
        aidl = true
        buildConfig = true
    }
    namespace = "de.blinkt.openvpn"
    compileSdk = 35
    //compileSdkPreview = "UpsideDownCake"

    // Also update runcoverity.sh
    ndkVersion = "28.0.12916984-rc2"

    defaultConfig {
        minSdk = 21
/*
        targetSdk = 35
        //targetSdkPreview = "UpsideDownCake"
        versionCode = 210
        versionName = "0.7.55"
*/
        externalNativeBuild {
            cmake {
                //arguments+= "-DCMAKE_VERBOSE_MAKEFILE=1"
                version = "3.18.1"
            }
        }
    }


    //testOptions.unitTests.isIncludeAndroidResources = true

    externalNativeBuild {
        cmake {
            version = "3.18.1"
            path = File("${projectDir}/src/main/cpp/CMakeLists.txt")
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
        }
*/
    }

/*
    signingConfigs {
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
            enableV1Signing = true
            enableV2Signing = true
        }

        create("releaseOvpn2") {
            // ~/.gradle/gradle.properties
            val keystoreO2File: String? by project
            storeFile = keystoreO2File?.let { file(it) }
            val keystoreO2Password: String? by project
            storePassword = keystoreO2Password
            val keystoreO2AliasPassword: String? by project
            keyPassword = keystoreO2AliasPassword
            val keystoreO2Alias: String? by project
            keyAlias = keystoreO2Alias
            enableV1Signing = true
            enableV2Signing = true
        }

    }

    lint {
        enable += setOf("BackButton", "EasterEgg", "StopShip", "IconExpectedSize", "GradleDynamicVersion", "NewerVersionAvailable")
        checkOnly += setOf("ImpliedQuantity", "MissingQuantity")
        disable += setOf("MissingTranslation", "UnsafeNativeCodeLocation")
    }
*/

    flavorDimensions += listOf("implementation", "ovpnimpl")

    productFlavors {
/*
        create("ui") {
            dimension = "implementation"
        }
*/

        create("skeleton") {
            dimension = "implementation"
        }

        create("ovpn23")
        {
            dimension = "ovpnimpl"
            buildConfigField("boolean", "openvpn3", "true")
        }

/*
        create("ovpn2")
        {
            dimension = "ovpnimpl"
            versionNameSuffix = "-o2"
            buildConfigField("boolean", "openvpn3", "false")
        }
 */
    }
/*
    buildTypes {
        getByName("release") {
            if (project.hasProperty("icsopenvpnDebugSign")) {
                logger.warn("property icsopenvpnDebugSign set, using debug signing for release")
                signingConfig = android.signingConfigs.getByName("debug")
            } else {
                productFlavors["ovpn23"].signingConfig = signingConfigs.getByName("release")
                productFlavors["ovpn2"].signingConfig = signingConfigs.getByName("releaseOvpn2")
            }
        }
    }
 */

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }
    buildFeatures {
        aidl = true
    }
    namespace = "de.blinkt.openvpn"

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

/*
    bundle {
        codeTransparency {
            signing {
                val keystoreTPFile: String? by project
                storeFile = keystoreTPFile?.let { file(it) }
                val keystoreTPPassword: String? by project
                storePassword = keystoreTPPassword
                val keystoreTPAliasPassword: String? by project
                keyPassword = keystoreTPAliasPassword
                val keystoreTPAlias: String? by project
                keyAlias = keystoreTPAlias

                if (keystoreTPFile?.isEmpty() ?: true)
                    println("keystoreTPFile not set, disabling transparency signing")
                if (keystoreTPPassword?.isEmpty() ?: true)
                    println("keystoreTPPassword not set, disabling transparency signing")
                if (keystoreTPAliasPassword?.isEmpty() ?: true)
                    println("keystoreTPAliasPassword not set, disabling transparency signing")
                if (keystoreTPAlias?.isEmpty() ?: true)
                    println("keyAlias not set, disabling transparency signing")

            }
        }
    }
 */
}

abstract class SwigTask : DefaultTask() {
    @get:OutputDirectory abstract val genDir: DirectoryProperty
    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun action() {
        val genDir = genDir.asFile.get()
        genDir.mkdirs()

        val swigcmd = getSwigCommand()
        exec.exec {
            commandLine(swigcmd, "-outdir", genDir, "-outcurrentdir", "-c++", "-java", "-package", "net.openvpn.ovpn3",
                "-Isrc/main/cpp/openvpn3/client", "-Isrc/main/cpp/openvpn3/",
                "-DOPENVPN_PLATFORM_ANDROID",
                "-o", "${genDir}/ovpncli_wrap.cxx", "-oh", "${genDir}/ovpncli_wrap.h",
                "src/main/cpp/openvpn3/client/ovpncli.i")
        }
    }

    private fun getSwigCommand(): String {
        var swigcmd = "swig"
        // Workaround for macOS(arm64) and macOS(intel) since it otherwise does not find swig and
        // I cannot get the Exec task to respect the PATH environment :(
        if (File("/opt/homebrew/bin/swig").exists())
            swigcmd = "/opt/homebrew/bin/swig"
        else if (File("/usr/local/bin/swig").exists())
            swigcmd = "/usr/local/bin/swig"
        return swigcmd
    }
}

fun registerGenTask(variantName: String, variantDirName: String): Provider<Directory> {
    val baseDir = layout.buildDirectory.dir("generated/source/ovpn3swig/${variantDirName}")

    tasks.register<SwigTask>("generateOpenVPN3Swig${variantName}") {
        genDir = baseDir.map { it.dir("net/openvpn/ovpn3") }
        inputs.files( "src/main/cpp/openvpn3/client/ovpncli.i")
    }

    return baseDir
}

android.libraryVariants.all(object : Action<LibraryVariant> {
    override fun execute(variant: LibraryVariant) {
        val sourceDir = registerGenTask(variant.name, variant.baseName.replace("-", "/"))
        val task = tasks.named("generateOpenVPN3Swig${variant.name}")

        variant.registerJavaGeneratingTask(task, sourceDir.get().asFile)
    }
})


dependencies {
    // https://maven.google.com/web/index.html
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation("com.github.seancfoley:ipaddress:5.4.0")

/*
    uiImplementation(libs.android.view.material)
    uiImplementation(libs.androidx.activity)
    uiImplementation(libs.androidx.activity.ktx)
    uiImplementation(libs.androidx.appcompat)
    uiImplementation(libs.androidx.cardview)
    uiImplementation(libs.androidx.viewpager2)
    uiImplementation(libs.androidx.constraintlayout)
    uiImplementation(libs.androidx.core.ktx)
    uiImplementation(libs.androidx.fragment.ktx)
    uiImplementation(libs.androidx.lifecycle.runtime.ktx)
    uiImplementation(libs.androidx.lifecycle.viewmodel.ktx)
    uiImplementation(libs.androidx.preference.ktx)
    uiImplementation(libs.androidx.recyclerview)
    uiImplementation(libs.androidx.security.crypto)
    uiImplementation(libs.androidx.webkit)
    uiImplementation(libs.kotlin)
    uiImplementation(libs.mpandroidchart)
    uiImplementation(libs.square.okhttp)
 */

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.robolectric)
}

/*
fun DependencyHandler.uiImplementation(dependencyNotation: Any): Dependency? =
    add("uiImplementation", dependencyNotation)
 */
