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

package com.protonvpn.app

import android.content.Context
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.FeatureFlags
import com.protonvpn.android.models.config.NetShieldProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.AndroidUtils
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.Constants
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ConnectionParamsTests {

    @MockK lateinit var context: Context
    @MockK lateinit var userData: UserData
    @MockK lateinit var appConfig: AppConfig
    @MockK lateinit var featureFlags: FeatureFlags
    @MockK lateinit var profile: Profile
    @MockK lateinit var server: Server
    @MockK lateinit var connectingDomain: ConnectingDomain

    private lateinit var params: ConnectionParams

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        ProtonApplication.setAppContextForTest(context)

        mockkObject(AndroidUtils)
        every { context.isTV() } returns false

        mockkObject(Constants)
        every { Constants.VPN_USERNAME_PRODUCT_SUFFIX } returns "+pa"
        every { userData.vpnUserName } returns "user"
        every { userData.isSmartReconnectEnabled } returns true
        every { profile.getNetShieldProtocol(any(), any()) } returns NetShieldProtocol.ENABLED_EXTENDED
        every { connectingDomain.label } returns "label"
        every { appConfig.getFeatureFlags() } returns featureFlags
        every { featureFlags.vpnAccelerator } returns false

        params = ConnectionParams(profile, server, connectingDomain, VpnProtocol.Smart)
    }

    @After
    fun tearDown() {
        unmockkObject(Constants)
    }

    @Test
    fun testUsernameSuffixes() {
        Assert.assertEquals(
            setOf("user", "f2", "pa", "b:label"),
            params.getVpnUsername(userData, appConfig).split("+").toSet())
    }

    @Test
    fun testTvSuffix() {
        every { Constants.VPN_USERNAME_PRODUCT_SUFFIX } returns "+pt"
        val result = params.getVpnUsername(userData, appConfig).split("+")

        Assert.assertEquals(
            setOf("user", "f2", "pt", "b:label"),
            result.toSet())
    }

    @Test
    fun testNoLabelSuffix() {
        every { connectingDomain.label } returns null
        Assert.assertEquals(
            setOf("user", "f2", "pa"),
            params.getVpnUsername(userData, appConfig).split("+").toSet())
    }

    @Test
    fun testSplitTcpSuffixSettings() {
        every { featureFlags.vpnAccelerator } returns true
        every { userData.isSmartReconnectEnabled } returns false
        Assert.assertEquals(
            setOf("user", "f2", "pa", "b:label", "nst"),
            params.getVpnUsername(userData, appConfig).split("+").toSet())
    }

    @Test
    fun testSplitTcpSuffixFeatureDisabled() {
        every { featureFlags.vpnAccelerator } returns false
        every { userData.isSmartReconnectEnabled } returns false
        Assert.assertEquals(
            setOf("user", "f2", "pa", "b:label"),
            params.getVpnUsername(userData, appConfig).split("+").toSet())
    }
}
