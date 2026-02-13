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
    includeBuild("build-logic")

    repositories {
        val mavenCachePkgUrl = System.getenv("MAVEN_CACHE_PKG_URL")
        if (!mavenCachePkgUrl.isNullOrBlank()) {
            maven { url = uri(mavenCachePkgUrl) }
        }
        maven { url = java.net.URI("https://plugins.gradle.org/m2/") }
        mavenCentral()
        google()
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
