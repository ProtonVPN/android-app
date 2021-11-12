/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.kotlinActions

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.protonvpn.MockSwitch
import com.protonvpn.actions.AccountRobot
import com.protonvpn.actions.LoginRobot
import com.protonvpn.actions.ConnectionRobot
import com.protonvpn.actions.MapRobot
import com.protonvpn.actions.ProfilesRobot
import com.protonvpn.android.R
import com.protonvpn.base.BaseRobot
import com.protonvpn.base.BaseVerify
import com.protonvpn.matchers.ProtonMatcher.lastChild
import com.protonvpn.testsHelper.ServiceTestHelper
import org.hamcrest.Matchers
import kotlin.test.assertFalse

class HomeRobot : BaseRobot() {

    fun openAccountView(): AccountRobot {
        clickElementByContentDescription<HomeRobot>(R.string.hamburgerMenu)
        clickElementById<HomeRobot>(R.id.layoutUserInfo)
        return AccountRobot()
    }

    fun logout(): LoginRobot {
        clickElementByContentDescription<HomeRobot>(R.string.hamburgerMenu)
        return clickElementByIdAndText(R.id.drawerButtonLogout, R.string.menuActionSignOut)
    }

    fun swipeLeftToOpenMap(): MapRobot {
        swipeLeftOnElementById<MapRobot>(R.id.list)
        return waitUntilDisplayed(R.id.mapView)
    }

    fun setStateOfSecureCoreSwitch(state: Boolean): HomeRobot {
        if (state != ServiceTestHelper().isSecureCoreEnabled)
            clickElementById<HomeRobot>(R.id.switchSecureCore)
        return this;
    }

    fun swipeLeftToOpenProfiles(): ProfilesRobot {
        view.waitForCondition(
            {
                view.withId(R.id.coordinator).swipeLeft()
                Espresso.onView(ViewMatchers.withId(R.id.textCreateProfile))
                    .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
            })
        return ProfilesRobot()
    }

    fun connectThroughQuickConnect(profileName: String): ConnectionRobot {
        longClickByCustomMatcher<HomeRobot>(
            lastChild(
                ViewMatchers.withId(R.id.fabQuickConnect),
                withClassName(Matchers.endsWith("FloatingActionButton"))
            )
        )
        clickElementByText<HomeRobot>(profileName)
        if (!MockSwitch.mockedConnectionUsed) {
            allowVpnToBeUsed()
        }
        return ConnectionRobot()
    }

    fun allowVpnToBeUsed() {
        if (isAllowVpnRequestVisible()) {
            device.clickNotificationByText("OK")
        }
    }

    private fun isAllowVpnRequestVisible(): Boolean {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        return uiDevice.findObject(UiSelector().textContains("Connection request")).exists()
    }

    class Verify : BaseVerify() {
        fun successfullyLoggedIn() = checkIfElementIsDisplayedById(R.id.fabQuickConnect)

        fun dialogUpgradeVisible() {
            checkIfElementIsDisplayedByStringId(R.string.upgrade_secure_core_message)
            checkIfElementByIdContainsText(R.id.buttonShowPlans, R.string.upgrade_see_plans_button)
            checkIfElementByIdContainsText(R.id.buttonClose, R.string.upgrade_not_now_button)
        }

        fun isSecureCoreDisabled() {
            assertFalse(ServiceTestHelper().isSecureCoreEnabled)
        }
    }

    inline fun verify(block: Verify.() -> Unit) = Verify().apply(block)
}
