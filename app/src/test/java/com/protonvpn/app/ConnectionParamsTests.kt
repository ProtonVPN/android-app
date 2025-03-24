/*
 * Copyright (c) 2021 Proton AG
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
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.utils.AndroidUtils
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConnectionParamsTests {

    @MockK lateinit var context: Context
    @MockK lateinit var server: Server
    @MockK lateinit var connectingDomain: ConnectingDomain

    private val connectIntent: ConnectIntent = ConnectIntent.FastestInCountry(CountryId.fastest, emptySet())

    private lateinit var params: ConnectionParams

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        ProtonApplication.setAppContextForTest(context)

        mockkObject(AndroidUtils)
        Storage.setPreferences(MockSharedPreference())

        mockkObject(Constants)
        every { connectingDomain.label } returns "label"

        params = ConnectionParams(connectIntent, server, connectingDomain, VpnProtocol.Smart)
    }

    @After
    fun tearDown() {
        unmockkObject(Constants)
    }

    @Test
    fun testUuidIsRestoredWhenLoadedFromStorage() {
        Storage.save(params, ConnectionParams::class.java)
        val restoredIntent = ConnectionParams.readIntentFromStore(params.uuid)
        assertNotNull(restoredIntent)

        val notRestored = ConnectionParams.readIntentFromStore(UUID.randomUUID())
        assertNull(notRestored)
    }

    @Test
    fun testUuidIsDifferentForEachInstance() {
        Assert.assertNotEquals(params.uuid, ConnectionParams(connectIntent, server, connectingDomain, VpnProtocol.Smart).uuid)
    }
}
