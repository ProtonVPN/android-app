/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.release_tests.rules

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.protonvpn.android.release_tests.data.TestConstants
import org.junit.Assert
import org.junit.rules.TestWatcher
import org.junit.runner.Description

open class LaunchVpnAppRule : TestWatcher() {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    override fun starting(description: Description?) {
        super.starting(description)
        disableAnimations()
        launchVpnApp(device)
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

    private fun launchVpnApp(device: UiDevice) {
        // Based on https://developer.android.com/training/testing/other-components/ui-automator#access-ui
        val launcherPackage = device.launcherPackageName
        Assert.assertNotNull(launcherPackage)
        device.wait(
            Until.hasObject(By.pkg(launcherPackage).depth(0)),
            TestConstants.TWENTY_SECOND_TIMEOUT.inWholeMilliseconds
        )

        // Launch the app
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(TestConstants.TEST_PACKAGE)?.apply {
            // Clear out any previous instances
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        Assert.assertNotNull(intent)

        context.startActivity(intent)

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(TestConstants.TEST_PACKAGE).depth(0)), TestConstants.TWENTY_SECOND_TIMEOUT.inWholeMilliseconds);
    }
}