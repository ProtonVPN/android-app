include(":detekt-custom-rules")

rootProject.name = "ProtonVPN"

plugins {
    id("me.proton.core.gradle-plugins.include-core-build") version "1.2.0"
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
include(":openvpn")
include(":shared-test-code")


buildCache {
    local {
        if (System.getenv("CI") == "true") {
            directory = file("build/gradle-build-cache")
        }
        removeUnusedEntriesAfterDays = 3
    }
}
