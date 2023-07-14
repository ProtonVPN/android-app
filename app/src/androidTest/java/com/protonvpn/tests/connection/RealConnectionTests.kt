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

package com.protonvpn.tests.connection

import com.protonvpn.actions.LoginRobot
import com.protonvpn.actions.compose.ConnectionPanelRobot
import com.protonvpn.actions.compose.ConnectionRobot
import com.protonvpn.actions.compose.HomeRobot
import com.protonvpn.actions.compose.interfaces.verify
import com.protonvpn.android.R
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.data.DefaultData
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.CommonRuleChains.realBackendComposeRule
import com.protonvpn.testsHelper.ServerManagerHelper
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import me.proton.core.test.android.robots.auth.AddAccountRobot
import me.proton.test.fusion.ui.compose.wrappers.NodeMatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * [RealConnectionTests] Contains tests related to real VPN connection.
 */
@HiltAndroidTest
class RealConnectionTests {

    @get:Rule
    val rule = realBackendComposeRule()

    @Inject
    lateinit var vpnStateMonitor: VpnStateMonitor

    private val loginRobot = LoginRobot()
    private val addAccountRobot = AddAccountRobot()
    private lateinit var userDataHelper: UserDataHelper

    @Before
    fun setUp() {
        userDataHelper = UserDataHelper()
        userDataHelper.logoutUser()
        ServerManagerHelper().serverManager.clearCache()
    }

    @Test
    fun realConnectionOpenVpnUDP() {
        realConnection(
            ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
            R.string.settingsProtocolNameOpenVpnUdp
        )
    }

    @Test
    fun realConnectionOpenVpnTCP() {
        realConnection(
            ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            R.string.settingsProtocolNameOpenVpnTcp
        )
    }

    @Test
    fun realConnectionWireguard() {
        realConnection(
            ProtocolSelection(VpnProtocol.WireGuard),
            R.string.settingsProtocolNameWireguard
        )
    }

    @Test
    fun realConnectionWireguardTCP() {
        realConnection(
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP),
            R.string.settingsProtocolNameWireguardTCP

        )
    }

    @Test
    fun realConnectionWireguardTLS() {
        realConnection(
            ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS),
            R.string.settingsProtocolNameWireguardTLS
        )
    }

    private fun realConnection(protocol: ProtocolSelection, expectedProtocolName: Int) {
        userDataHelper.setProtocol(protocol.vpn, protocol.transmission)
        addAccountRobot.signIn()
        loginRobot.signIn(TestUser.plusUser)
            HomeRobot.verify { isLoggedIn() }
        ConnectionRobot.quickConnect()
            .allowVpnPermission()
            .verify { isConnected() }
        HomeRobot.openConnectionPanel()
        ConnectionPanelRobot.verify {
            runBlocking { correctIpIsDisplayed(vpnStateMonitor.exitIp.value!!) }
            correctProtocolIsDisplayed(expectedProtocolName)
        }
        ConnectionPanelRobot.goBack()
        ConnectionRobot.disconnect()
            .verify { isDisconnected() }
    }

    @After
    fun tearDown() {
        userDataHelper.logoutUser()
        Storage.clearAllPreferencesSync()
        userDataHelper.setProtocol(DefaultData.DEFAULT_PROTOCOL)
    }
}
