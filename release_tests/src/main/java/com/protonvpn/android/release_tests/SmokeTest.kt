/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.release_tests

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SHORT_TIMEOUT = 1_000L
private const val LONG_TIMEOUT = 20_000L
private const val TEST_PACKAGE = "ch.protonvpn.android"

class SmokeTest {

    private lateinit var device: UiDevice

    @Before
    fun setup() {
        // The pulsating FAB causes test to wait until timeout even if condition is met,
        // disable animations to avoid this.
        disableAnimations()

        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        launchVpnApp(device)
    }

    @Test
    fun testSignIn() {
        signIn("testas3", BuildConfig.TEST_ACCOUNT_PASSWORD)
    }

    private fun signIn(username: String, password: String) {
        device.findObject(By.text("Sign in")).click()
        val usernameInput = device.wait(Until.findObject(protonInput("usernameInput")), SHORT_TIMEOUT)
        val passwordInput = device.findObject(protonInput("passwordInput"))

        assertNotNull(usernameInput)
        assertNotNull(passwordInput)
        usernameInput.text = username
        passwordInput.text = password
        // Use ID for the button because "Sign in" text is not unique (also used in header).
        device.findObject(By.res(TEST_PACKAGE, "signInButton")).click()

        val hasToolbarTitle = device.wait(Until.hasObject(By.text("Proton VPN")), LONG_TIMEOUT)
        val hasVpnStatusText = device.hasObject(By.text("Not connected (Unprotected)"))
        assertTrue(hasToolbarTitle)
        assertTrue(hasVpnStatusText)
    }

    private fun launchVpnApp(device: UiDevice) {
        // Based on https://developer.android.com/training/testing/other-components/ui-automator#access-ui
        val launcherPackage = device.launcherPackageName
        assertNotNull(launcherPackage)
        device.wait(
            Until.hasObject(By.pkg(launcherPackage).depth(0)),
            LONG_TIMEOUT
        )

        // Launch the app
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(TEST_PACKAGE)?.apply {
            // Clear out any previous instances
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        assertNotNull(intent)

        context.startActivity(intent)

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(TEST_PACKAGE).depth(0)), LONG_TIMEOUT);
    }

    private fun disableAnimations() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf(
            "settings put global animator_duration_scale 0",
            "settings put global transition_animation_scale 0",
            "settings put global window_animation_scale 0"
        ).forEach { command ->
            uiAutomation.executeShellCommand(command).run {
                checkError() // throws IOException on error
                close()
            }
        }
    }

    private fun protonInput(resourceId: String): BySelector =
        By.hasAncestor(By.res(TEST_PACKAGE, resourceId)).res(TEST_PACKAGE, "input")
}
