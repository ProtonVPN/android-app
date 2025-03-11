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

package com.protonvpn.tests.connection

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.actions.LoginRobot
import com.protonvpn.actions.compose.ConnectionPanelRobot
import com.protonvpn.actions.compose.ConnectionRobot
import com.protonvpn.actions.compose.HomeRobot
import com.protonvpn.actions.compose.SettingsRobot
import com.protonvpn.actions.compose.interfaces.verify
import com.protonvpn.android.R
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.utils.openVpnSettings
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testRules.CommonRuleChains.realBackendComposeRule
import com.protonvpn.testsHelper.ServerManagerHelper
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import me.proton.core.test.android.robots.auth.AddAccountRobot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class RealConnectionTests {
    @get:Rule
    val rule = realBackendComposeRule()

    @Inject
    lateinit var vpnStateMonitor: VpnStateMonitor

    private val loginRobot = LoginRobot()
    private val addAccountRobot = AddAccountRobot()
    private lateinit var userDataHelper: UserDataHelper
    private lateinit var context: Context

    @Before
    fun setUp() {
        userDataHelper = UserDataHelper()
        userDataHelper.logoutUser()
        ServerManagerHelper().serverManager.clearCache()
        context = InstrumentationRegistry.getInstrumentation().context
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

    @Test
    fun alwaysOnAutomaticallyConnectsUserToVPN() {
        setupAlwaysOn()
        SettingsRobot.enableAlwaysOn()
        // Always ON settings UI is different per device/android versions, so we use back button to go back
        repeat(2) { SettingsRobot.goBack() }
        HomeRobot.navigateToHome()
        ConnectionRobot.verify { isConnected() }
    }

    @Test
    fun alwaysOnScreenSuccessfullyNavigatesBackToClient() {
        setupAlwaysOn()
        SettingsRobot.alwaysOnOpenProtonVpn()
        HomeRobot.verify { isLoggedIn() }
    }

    private fun realConnection(protocol: ProtocolSelection, expectedProtocolName: Int) {
        userDataHelper.setProtocol(protocol.vpn, protocol.transmission)
        addAccountRobot.signIn()
        loginRobot.signIn(TestUser.anyPaidUser)
        HomeRobot.verify { isLoggedIn() }
        ConnectionRobot.quickConnect()
            .allowVpnPermission()
            .verify { isConnected() }
        HomeRobot.openConnectionPanel()
        ConnectionPanelRobot.verify {
            runBlocking { correctIpIsDisplayed(vpnStateMonitor.exitIp.value?.ipV4!!) }
            correctProtocolIsDisplayed(expectedProtocolName)
        }
        ConnectionPanelRobot.goBack()
        ConnectionRobot.disconnect()
            .verify { isDisconnected() }
    }

    private fun setupAlwaysOn(){
        addAccountRobot.signIn()
        loginRobot.signIn(TestUser.anyPaidUser)
        HomeRobot.verify { isLoggedIn() }
        ConnectionRobot.quickConnect()
            .allowVpnPermission()
            .dissmissNotifications()
            .verify { isConnected() }
        ConnectionRobot.disconnect()
            .verify { isDisconnected() }
        context.openVpnSettings()
    }
}