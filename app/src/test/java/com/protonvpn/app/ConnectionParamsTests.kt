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
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.AndroidUtils
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.mockVpnUser
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
    @MockK lateinit var currentUser: CurrentUser
    @MockK lateinit var vpnUser: VpnUser

    private lateinit var params: ConnectionParams

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        ProtonApplication.setAppContextForTest(context)

        mockkObject(AndroidUtils)
        every { context.isTV() } returns false
        Storage.setPreferences(MockSharedPreference())

        mockkObject(Constants)
        every { Constants.VPN_USERNAME_PRODUCT_SUFFIX } returns "+pa"
        every { vpnUser.name } returns "user"
        currentUser.mockVpnUser { vpnUser }
        every { userData.isVpnAcceleratorEnabled(any()) } returns true
        every { userData.isSafeModeEnabled(any()) } returns null
        every { userData.randomizedNatEnabled } returns true
        every { connectingDomain.label } returns "label"
        every { appConfig.getFeatureFlags() } returns featureFlags

        params = ConnectionParams(profile, server, connectingDomain, VpnProtocol.Smart)
    }

    @After
    fun tearDown() {
        unmockkObject(Constants)
    }

    @Test
    fun testUuidIsRestoredWhenLoadedFromStorage() {
        Storage.save(params, ConnectionParams::class.java)
        val restoredParams = ConnectionParams.readFromStore()

        Assert.assertEquals(params.uuid, restoredParams?.uuid)
    }

    @Test
    fun testUuidIsDifferentForEachInstance() {
        Assert.assertNotEquals(params.uuid, ConnectionParams(profile, server, connectingDomain, VpnProtocol.Smart).uuid)
    }
}
