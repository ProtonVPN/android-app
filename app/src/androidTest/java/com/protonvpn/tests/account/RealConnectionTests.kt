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
import com.protonvpn.actions.AddAccountRobot
import com.protonvpn.actions.LoginRobot
import com.protonvpn.actions.RealConnectionRobot
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.ui.main.MobileMainActivity
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.data.DefaultData
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.ProtonHiltAndroidRule
import com.protonvpn.testRules.TestSettingsOverrideRule
import com.protonvpn.testsHelper.ServerManagerHelper
import com.protonvpn.testsHelper.TestSetup
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import javax.inject.Inject

/**
 * [RealConnectionTests] Contains tests related to real VPN connection.
 */
@HiltAndroidTest
class RealConnectionTests {

    private val hiltRule = ProtonHiltAndroidRule(this)

    @get:Rule
    val rules = RuleChain
        .outerRule(TestSettingsOverrideRule(false))
        .around(hiltRule)

    @Inject
    lateinit var vpnStateMonitor: VpnStateMonitor

    private val loginRobot = LoginRobot()
    private val connectionRobot = RealConnectionRobot()
    private val addAccountRobot = AddAccountRobot()
    private lateinit var userDataHelper: UserDataHelper

    @Before
    fun setUp() {
        userDataHelper = UserDataHelper()
        userDataHelper.logoutUser()
        hiltRule.inject()
        TestSetup.setCompletedOnboarding()
        ActivityScenario.launch(MobileMainActivity::class.java)
        ServerManagerHelper().serverManager.clearCache()
    }

    @Test
    fun realConnectionOpenVpnUDP() {
        realConnection(ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP))
    }

    @Test
    fun realConnectionOpenVpnTCP() {
        realConnection(ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP))
    }

    @Test
    fun realConnectionWireguard() {
        realConnection(ProtocolSelection(VpnProtocol.WireGuard))
    }

    @Test
    fun realConnectionWireguardTCP() {
        realConnection(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP))
    }

    @Test
    fun realConnectionWireguardTLS() {
        realConnection(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS))
    }

    private fun realConnection(protocol: ProtocolSelection) {
        userDataHelper.setProtocol(protocol.vpn, protocol.transmission)
        addAccountRobot.selectSignInOption()
        loginRobot.signInAndWaitForCountryInCountryList(TestUser.plusUser, "Austria")
        connectionRobot.connectThroughQuickConnectRealConnection()
            .verify {
                runBlocking { checkIfConnectedAndCorrectIpAddressIsDisplayed(vpnStateMonitor.exitIP!!) }
                checkProtocol(protocol)
            }
        connectionRobot.disconnectFromVPN()
            .verify { checkIfDisconnected() }
    }

    @After
    fun tearDown() {
        userDataHelper.logoutUser()
        Storage.clearAllPreferencesSync()
        userDataHelper.setProtocol(DefaultData.DEFAULT_PROTOCOL)
    }
}
