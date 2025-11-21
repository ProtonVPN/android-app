include(":detekt-custom-rules")

rootProject.name = "ProtonVPN"

pluginManagement {
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

include(":app")
include(":detekt-gitlab-output-plugin")
include(":observability:domain")
include(":observability:tools")
include(":openvpn")
include(":shared-test-code")

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
include(":release_tests")
include(":baselineprofile")
