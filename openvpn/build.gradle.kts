/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

plugins {
    id ("com.android.library")
    id ("checkstyle")
}

apply {
    plugin("kotlin-android")
    plugin("kotlin-android-extensions")
}

/*
checkstyle {
    showViolations(true)
}
*/

repositories {
    jcenter()
    maven (url ="https://jitpack.io")
    google()
}

dependencies {
    implementation ("com.android.support.constraint:constraint-layout:1.1.3")
    implementation ("com.android.support:support-annotations:28.0.0")
    implementation ("com.android.support:cardview-v7:28.0.0")
    implementation ("com.android.support:recyclerview-v7:28.0.0")
    implementation ("com.github.PhilJay:MPAndroidChart:v3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.11")

    testImplementation ("junit:junit:4.12")
    testImplementation ("org.robolectric:robolectric:4.1")
}



val openvpn3SwigFiles = File(buildDir, "generated/source/ovpn3swig/ovpn3")

tasks.register<Exec>("generateOpenVPN3Swig")
{
    var swigcmd = "swig"
    // Workaround for Mac OS X since it otherwise does not find swig and I cannot get
    // the Exec task to respect the PATH environment :(
    if (File("/usr/local/bin/swig").exists())
        swigcmd = "/usr/local/bin/swig"

    doFirst {
        mkdir(openvpn3SwigFiles)
    }
    commandLine(listOf(swigcmd, "-outdir", openvpn3SwigFiles, "-outcurrentdir", "-c++", "-java", "-package", "net.openvpn.ovpn3",
            "-Isrc/main/cpp/openvpn3/client", "-Isrc/main/cpp/openvpn3/",
            "-o", "${openvpn3SwigFiles}/ovpncli_wrap.cxx", "-oh", "${openvpn3SwigFiles}/ovpncli_wrap.h",
            "src/main/cpp/openvpn3/javacli/ovpncli.i"))
}

android {
    compileSdkVersion(28)

    // FIXME: update on CI image
    ndkVersion = "20.0.5594570"

    defaultConfig {
        minSdkVersion(19)
        targetSdkVersion(28)
        versionCode = 161
        versionName = "0.7.8"

        externalNativeBuild {
            cmake {
                //arguments = listOf("-DANDROID_TOOLCHAIN=clang",
                //        "-DANDROID_STL=c++_static")
                //abiFilters "arm64-v8a"
            }
        }
    }

    externalNativeBuild {
        cmake {
            setPath(File("${projectDir}/src/main/cpp/CMakeLists.txt"))
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "build/ovpnassets")
            java.srcDirs("src/ovpn3/java/", openvpn3SwigFiles)
        }

        create("normal") {
            assets.srcDirs("src/main/assets", "build/ovpnassets")
            java.srcDirs("src/ovpn3/java/", openvpn3SwigFiles)
        }

        getByName("debug") {

        }

        getByName("release") {

        }
    }

    signingConfigs {
        create("release") {}
    }

    lintOptions {
        enable("BackButton", "EasterEgg", "StopShip", "IconExpectedSize", "GradleDynamicVersion", "NewerVersionAvailable")
        warning("ImpliedQuantity", "MissingQuantity")
        disable("MissingTranslation", "UnsafeNativeCodeLocation")
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions("implementation")

    productFlavors {
        /*create("noovpn3") {
            setDimension("implementation")
            buildConfigField ("boolean", "openvpn3", "false")
        }*/
        create("normal") {
            setDimension("implementation")
            buildConfigField ("boolean", "openvpn3", "false")
        }

    }


    compileOptions {
        targetCompatibility = JavaVersion.VERSION_1_8
        sourceCompatibility = JavaVersion.VERSION_1_8
    }
}

// ~/.gradle/gradle.properties
if (project.hasProperty("keystoreFile") &&
        project.hasProperty("keystorePassword") &&
        project.hasProperty("keystoreAliasPassword")) {
    android.signingConfigs.getByName("release") {
        storeFile = file(project.properties["keystoreFile"] as String)
        storePassword = project.properties["keystorePassword"] as String
        keyPassword = project.properties["keystoreAliasPassword"] as String
        keyAlias = project.properties["keystoreAlias"] as String
    }
} else {
    android.buildTypes.getByName("release").signingConfig = null
}


/* Hack-o-rama but it works good enough and documentation is surprisingly sparse */

val swigTask = tasks.named("generateOpenVPN3Swig")
val preBuildTask = tasks.getByName("preBuild")
val assembleTask = tasks.getByName("assemble")

println(tasks.names)

assembleTask.dependsOn(swigTask)
preBuildTask.dependsOn(swigTask)

// Ensure native build is run before assets, so assets are ready to be merged into the apk
/*android.applicationVariants.all { variant ->
    variant.mergeAssets.dependsOn(variant.externalNativeBuildTasks)
}*/


//for (t in android.variant(true))
//    println(t)


//tasks.getByName("processResources").dependsOn(tasks.getByName("externalNativeBuildTasks"))


//tasks.getByName("compileJava").dependsOn(tasks.getByName("generateOpenVPN3Swig"))

/*
android.applicationVariants.all { variant ->
    variant.productFlavors.each {
        if (it.dimension == "implementation" &&  it.name != "noovpn3")
            variant.getJavaCompiler().dependsOn(generateOpenVPN3Swig)

    }

}*/