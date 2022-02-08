/*
 *  Copyright (c) 2021 Proton AG
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

package com.protonvpn.tests.account

import androidx.test.core.app.ActivityScenario
import com.protonvpn.TestSettings
import com.protonvpn.actions.AddAccountRobot
import com.protonvpn.actions.LoginRobot
import com.protonvpn.actions.RealConnectionRobot
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.ui.ProtocolSelection
import com.protonvpn.android.ui.main.MobileMainActivity
import com.protonvpn.android.utils.Storage
import com.protonvpn.data.DefaultData
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testsHelper.ServerManagerHelper
import com.protonvpn.testsHelper.TestSetup
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import javax.inject.Inject

/**
 * [RealConnectionTests] Contains tests related to real VPN connection.
 */
@RunWith(Parameterized::class)
@HiltAndroidTest
class RealConnectionTests(private val protocol: ProtocolSelection) {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var api: ProtonApiRetroFit

    private val loginRobot = LoginRobot()
    private val connectionRobot = RealConnectionRobot()
    private val addAccountRobot = AddAccountRobot()
    private lateinit var userDataHelper: UserDataHelper

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun data(): List<ProtocolSelection> {
            return listOf(
                ProtocolSelection.from(VpnProtocol.IKEv2),
                ProtocolSelection.from(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
                ProtocolSelection.from(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
                ProtocolSelection.from(VpnProtocol.WireGuard),
            )
        }
    }

    @Before
    fun setUp(){
        TestSettings.mockedConnectionUsed = false
        TestSettings.mockedServersUsed = false
        userDataHelper = UserDataHelper()
        userDataHelper.logoutUser()
        hiltRule.inject()
        TestSetup.setCompletedOnboarding()
        ActivityScenario.launch(MobileMainActivity::class.java)
        ServerManagerHelper().serverManager.clearCache()
        userDataHelper.setProtocol(protocol.protocol, protocol.transmission)
    }

    @Test
    //Don't run this test case individually, Junit has a bug https://github.com/android/android-test/issues/960
    fun realConnection() {
        addAccountRobot.selectSignInOption()
        loginRobot.signInAndWaitForCountryInCountryList(TestUser.plusUser, "Austria")
        connectionRobot.connectThroughQuickConnectRealConnection()
            .verify {
                runBlocking { checkIfConnectedAndCorrectIpAddressIsDisplayed(api) }
                checkProtocol(protocol)
            }
        connectionRobot.disconnectFromVPN()
            .verify { checkIfDisconnected() }
    }

    @After
    fun tearDown() {
        userDataHelper.logoutUser()
        Storage.clearAllPreferences()
        TestSettings.mockedConnectionUsed = true
        TestSettings.mockedServersUsed = true
        userDataHelper.setProtocol(DefaultData.DEFAULT_PROTOCOL)
    }
}
