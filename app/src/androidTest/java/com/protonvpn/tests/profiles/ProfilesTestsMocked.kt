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

package com.protonvpn.tests.profiles

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.protonvpn.android.R
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.robots.mobile.HomeRobot
import com.protonvpn.robots.mobile.ProfilesRobot
import com.protonvpn.interfaces.verify
import com.protonvpn.testRules.CommonRuleChains.mockedLoggedInRule
import com.protonvpn.testsHelper.ServerManagerHelper
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ProfilesTestsMocked {

    @get:Rule
    val rule = mockedLoggedInRule()

    @Before
    fun setup() {
        ServerManagerHelper().backend.stateOnConnect = VpnState.Connected
    }

    @Test
    fun profileAddEditConnectDelete() {
        HomeRobot
            .navigateToProfiles()
            .verifyPredefinedProfiles()

        ProfilesRobot
            .addProfile()
            .setProfileName("My profile").next().next().save()
            .verify { profileExists("My profile") }
            .open("My profile")
            .edit()
            .setProfileName("Edited").next().next().save()
            .verify { profileExists("Edited") }
            .connect("Edited")
            .verify { isConnected() }
            .disconnect()

        HomeRobot
            .navigateToProfiles()
            .open("Edited")
            .delete()
            .verify { profileNotExists("Edited") }

        ProfilesRobot
            .clearPredefined()
            .verify { zeroScreenDisplayed() }
    }

    @Test
    fun createSecureCoreProfileAndConnect() {
        val profileName = "Secure Core"

        HomeRobot.navigateToProfiles()

        ProfilesRobot
            .addProfile()
            .setProfileName(profileName)
            .next()
            .selectProfileType(R.string.create_profile_type_secure_core)
            .selectCountry(R.string.widget_preview_france)
            .next()
            .save()
            .verify { profileExists(profileName) }
            .connect(profileName)
            .verify { isConnected() }
            .disconnect()
    }

    private fun ProfilesRobot.verifyPredefinedProfiles() =
        verify { profileExists(R.string.initial_profile_name_streaming_us) }
        .verify { profileExists(R.string.initial_profile_name_work_school) }
        .verify { profileExists(R.string.initial_profile_name_anti_censorship) }
        .verify { profileExists(R.string.initial_profile_name_gaming) }
        .verify { profileExists(R.string.initial_profile_name_max_security) }

    private fun ProfilesRobot.clearPredefined() =
        open(R.string.initial_profile_name_streaming_us).delete()
        .open(R.string.initial_profile_name_work_school).delete()
        .open(R.string.initial_profile_name_anti_censorship).delete()
        .open(R.string.initial_profile_name_gaming).delete()
        .open(R.string.initial_profile_name_max_security).delete()
}
