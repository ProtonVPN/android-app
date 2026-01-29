/*
 * Copyright (c) 2025. Proton AG
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

package ch.protonvpn.android.baselineprofile

import android.util.Log
import androidx.benchmark.Shell
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.protonvpn.android.ui_automator_test_util.data.TestConstants
import com.protonvpn.android.ui_automator_test_util.robots.HomeRobot
import com.protonvpn.android.ui_automator_test_util.robots.LoginRobot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * This test class benchmarks the speed of app startup.
 * Run this benchmark to verify how effective a Baseline Profile is.
 * It does this by comparing [CompilationMode.None], which represents the app with no Baseline
 * Profiles optimizations, and [CompilationMode.Partial], which uses Baseline Profiles.
 *
 * Run this benchmark to see startup measurements and captured system traces for verifying
 * the effectiveness of your Baseline Profiles. You can run it directly from Android
 * Studio as an instrumentation test, or run all benchmarks for a variant, for example benchmarkRelease,
 * with this Gradle task:
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 * ```
 *
 * You should run the benchmarks on a physical device, not an Android emulator, because the
 * emulator doesn't represent real world performance and shares system resources with its host.
 *
 * For more information, see the [Macrobenchmark documentation](https://d.android.com/macrobenchmark#create-macrobenchmark)
 * and the [instrumentation arguments documentation](https://d.android.com/topic/performance/benchmarking/macrobenchmark-instrumentation-args).
 **/

// To run startup benchmark locally:
// - sign in to Google Play Store
// - set testAccountPassword in gradle.properties
// - build and install production*BenchmarkRelease
// - run StartupBenchmarks
@RunWith(Suite::class)
@Suite.SuiteClasses(
    StartupBenchmarks::class,
    StartupBenchmarksNoBaselineProfile::class,
)
class StartupBenchmarksAll

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarksNoBaselineProfile {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupFreeUser() =
        startupWithUser(rule, TestConstants.USERNAME_FREE, CompilationMode.None())

    @Test
    fun startupPlusUser() =
        startupWithUser(rule, TestConstants.USERNAME_PLUS, CompilationMode.None())
}

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupPlusUser() =
        startupWithUser(rule, TestConstants.USERNAME_PLUS, CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun startupFreeUser() =
        startupWithUser(rule, TestConstants.USERNAME_FREE, CompilationMode.Partial(BaselineProfileMode.Require))

    @Test
    fun startupFreeUserWithBanner() =
        startupWithUser(
            rule,
            TestConstants.USERNAME_FREE,
            CompilationMode.Partial(BaselineProfileMode.Require),
            expectPromoOffer = true,
        )
}

private fun startupWithUser(
    rule: MacrobenchmarkRule,
    userName: String,
    compilationMode: CompilationMode,
    expectPromoOffer: Boolean = false
) {
    // The application id for the running build variant is read from the instrumentation arguments.
    val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: throw Exception("targetAppId not passed as instrumentation runner arg")
    // Clear data to force a new login.
    val output = Shell.executeScriptCaptureStdoutStderr("pm clear $packageName")
    Log.i("StartupBenchmarks", "pm clear $packageName output:\n${output.stdout}\n${output.stderr}")
    rule.measureRepeated(
        packageName = packageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 15,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            if (LoginRobot.isSigninNeeded()) {
                LoginRobot.signIn(userName, BuildConfig.TEST_ACCOUNT_PASSWORD)
                    .waitUntilLoggedIn()
                if (expectPromoOffer) {
                    val hasBanner = device.wait(
                        Until.hasObject(By.res("promoOfferBanner")),
                        30_000
                    )
                    assertTrue(hasBanner)

                    // Reopen the UI to trigger the one-time splash screen.
                    pressHome()
                    device.wait({ false }, 1_000)
                    startActivityAndWait()
                    val hasSplashOffer = device.wait(
                        Until.hasObject(By.text("Claim offer")),
                        10_000
                    )
                    assertTrue(hasSplashOffer)
                    device.findObject(By.desc("Close"))
                }
            }
        },
        measureBlock = {
            pressHome()
            startActivityAndWait()
            HomeRobot.waitUntilConnectionCardReady()
        }
    )
}