/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

rootProject.name = "ProtonVPN"

pluginManagement {
    // Override R8/D8 while on AGP 8.9.1: 8.13.19 supports Kotlin 2.3 metadata in dependencies
    // (e.g. kotlinx-serialization) while the project compiles with Kotlin 2.2.
    // Must be inlined here — apply(from) in build-logic does not reach the R8 minify worker.
    // Remove when updating AGP to 8.13.2 or newer.
    buildscript {
        repositories {
            mavenCentral()
            google()
        }
        dependencies {
            classpath("com.android.tools:r8:8.13.19")
        }
    }

    includeBuild("build-logic")

    repositories {
        maven { url = java.net.URI("https://plugins.gradle.org/m2/") }
        mavenCentral()
        google()
    }

    // Override R8 with a newer version that is compatible with Kotlin 2.2.
    // Remove when updating AGP to 8.10 or newer.
    buildscript {
        repositories {
            maven {
                url = uri("https://storage.googleapis.com/r8-releases")
            }
        }
        dependencies {
            classpath("com.android.tools:r8:8.10.21")
        }
    }
}

plugins {
    id("me.proton.core.gradle-plugins.include-core-build") version "1.3.0"
    id("com.gradle.develocity") version "4.1"
}

includeCoreBuild {
    // By default, target :
    // On CI: "https://$gitlab_host/proton/mobile/android/proton-libs.git".
    // else: "https://github.com/ProtonMail/protoncore_android.git".

    // To force using full Core sources from a specific branch.
    // branch.set("fix/my_needed_fix")

    // To force using full Core sources from a specific commit.
    // commit.set("213e46d307195abbaa063dc03ca40e2cda9a5a6b")
}

develocity {
    buildScan {
        publishing.onlyIf { System.getenv("BUILD_SCAN_PUBLISH") == "true" }
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
    }
}

buildCache {
    local {
        isEnabled = !providers.environmentVariable("CI").isPresent
    }
    providers.environmentVariable("BUILD_CACHE_URL").orNull?.let { buildCacheUrl ->
        remote<HttpBuildCache> {
            isPush = providers.environmentVariable("CI").isPresent
            url = uri(buildCacheUrl)
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":baselineprofile")
include(":detekt-custom-rules")
include(":detekt-gitlab-output-plugin")
include(":observability:domain")
include(":observability:tools")
include(":release_tests")
include(":shared-test-code")
include(":ui_automator_test_util")
