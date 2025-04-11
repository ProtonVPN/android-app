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
import android.content.Intent
import android.os.Parcel
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.ProtocolSelectionData
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.recents.data.toConnectIntent
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.settings.data.CustomDnsSettings
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.EnumSet
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // Use high SDK to get type-safe methods for parcelization and intent extras.
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
    fun `uuid is restored when loaded from storage`() {
        Storage.save(params, ConnectionParams::class.java)
        val restoredIntent = ConnectionParams.readIntentFromStore(params.uuid)
        assertNotNull(restoredIntent)

        val notRestored = ConnectionParams.readIntentFromStore(UUID.randomUUID())
        assertNull(notRestored)
    }

    @Test
    fun `uuid is different for each instance`() {
        assertNotEquals(params.uuid, ConnectionParams(connectIntent, server, connectingDomain, VpnProtocol.Smart).uuid)
    }

    @Test
    fun `serialization to intent extra`() {
        val extraKey = "intent"
        val connectIntent = ConnectIntent.FastestInCountry(
            country = CountryId.sweden,
            features = EnumSet.of(ServerFeature.P2P),
            profileId = 5,
            settingsOverrides = SettingsOverrides(
                protocolData = ProtocolSelectionData(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
                netShield = NetShieldProtocol.ENABLED_EXTENDED,
                randomizedNat = false,
                lanConnections = true,
                lanConnectionsAllowDirect = false,
                customDns = CustomDnsSettings(
                    toggleEnabled = true,
                    rawDnsList = listOf("1.2.3.4", "2.3.4.5")
                )
            )
        )

        // Write intent to parcel to force serialization
        val parcel = Parcel.obtain()
        Intent().apply {
            putExtra(extraKey, connectIntent.toData())
        }.writeToParcel(parcel, 0)

        parcel.setDataPosition(0)
        val deserializedConnectIntent = Intent.CREATOR.createFromParcel(parcel)
            ?.getSerializableExtra(extraKey, ConnectIntentData::class.java)
            ?.toConnectIntent()
        assertEquals(connectIntent, deserializedConnectIntent)
    }
}
