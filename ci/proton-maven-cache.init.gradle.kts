/**
 * Prepends the Proton internal Maven cache proxy ($MAVEN_CACHE_PKG_URL) to every
 * repository container so CI builds hit pkgcache instead of Maven Central.
 * No-op when the env var is unset, so local builds are unaffected.
 *
 * Deploy by copying to $GRADLE_USER_HOME/init.d/ (see ci/base.gitlab-ci.yml).
 *
 * Logs every injection with the [proton-maven-cache] prefix; grep the CI log
 * for that tag to confirm the proxy is wired into a given build.
 */
apply<ProtonMavenCacheInitPlugin>()

class ProtonMavenCacheInitPlugin : Plugin<Gradle> {

    override fun apply(gradle: Gradle) {
        val proxyUrl = System.getenv("MAVEN_CACHE_PKG_URL") ?: return
        println("[proton-maven-cache] init applied (MAVEN_CACHE_PKG_URL=$proxyUrl)")

        fun RepositoryHandler.addRepos(scope: String) {
            maven { setUrl(proxyUrl) }
            println("[proton-maven-cache] prepended $proxyUrl to $scope")
            mavenCentral()
            google()
            gradlePluginPortal()
        }

        gradle.beforeSettings {
            val build = settings.rootDir.name
            pluginManagement.repositories.addRepos("pluginManagement($build)")
            dependencyResolutionManagement.repositories.addRepos("dependencyResolutionManagement($build)")
        }

        // settingsEvaluated runs after settings.gradle.kts but before any
        // project is configured, so we can read the mode and use it inside
        // the per-project hook below.
        gradle.settingsEvaluated {
            val failOnProjectRepos = dependencyResolutionManagement.repositoriesMode.get() ==
                    RepositoriesMode.FAIL_ON_PROJECT_REPOS
            gradle.allprojects {
                buildscript.repositories.addRepos("buildscript($displayName)")
                // Catches build scripts that declare their own `repositories { … }`
                // block (e.g. buildSrc/build.gradle.kts), which bypass
                // dependencyResolutionManagement entirely. Skip when the build
                // forbids project repos — those builds are already covered
                // centrally above.
                if (!failOnProjectRepos) {
                    repositories.addRepos("project($displayName)")
                }
            }
        }
    }
}

