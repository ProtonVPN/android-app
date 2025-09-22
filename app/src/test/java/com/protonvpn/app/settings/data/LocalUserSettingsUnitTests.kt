/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.app.settings.data

import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class LocalUserSettingsUnitTests {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun `settings tests are up-to-date`() {
        val defaultSettingsJson = """
            {
                "version": 2,
                "startingValuesSaved": false,
                "apiUseDoh": true,
                "defaultProfileId": null,
                "lanConnections": false,
                "lanConnectionsAllowDirect": false,
                "mtuSize": 1375,
                "netShield": "ENABLED_EXTENDED",
                "protocol": {
                    "vpn": "Smart",
                    "transmission": null
                },
                "randomizedNat": true,
                "splitTunneling": {
                    "isEnabled": false,
                    "mode": "INCLUDE_ONLY",
                    "excludedIps": [],
                    "excludedApps": [],
                    "includedIps": [],
                    "includedApps": []
                },
                "telemetry": true,
                "theme": "Dark",
                "tvAutoConnectOnBoot": false,
                "vpnAccelerator": true,
                "ipV6Enabled": true,
                "customDns": {
                    "enabled": false,
                    "rawDnsList": []
                }
            }
        """.trimIndent()
        val encodedSettings = json.encodeToString(LocalUserSettings.serializer(), LocalUserSettings())

        // If this test fails it means something in settings has changed (new field, changed default value).
        // UPDATE ALL TESTS in this file so that they actually test what they are supposed to test.
        assertEquals(defaultSettingsJson, encodedSettings)

        // Useful for updating the JSON string above.
        println("Current defaults: " + json.encodeToString(LocalUserSettings.serializer(), LocalUserSettings()))
    }

    @Test
    fun `deserialize LocalUserSettings from JSON`() {
        // Tests for renamed fields etc.
        // Use non-default values in the test.
        val settingsJson = """
            {
                "version": 2,
                "startingValuesSaved": false,
                "apiUseDoh": false,
                "defaultProfileId": "00000000-0000-0001-0000-000000000002",
                "lanConnections": true, 
                "lanConnectionsAllowDirect": true,
                "mtuSize": 1000,
                "netShield": "DISABLED",
                "protocol": {
                    "vpn": "OpenVPN",
                    "transmission": "TCP"
                },
                "randomizedNat": false,
                "splitTunneling": {
                    "isEnabled": true,
                    "mode": "EXCLUDE_ONLY",
                    "excludedIps": [
                        "1.1.1.1",
                        "1.2.3.4"
                    ],
                    "excludedApps": [],
                    "includedIps": [
                        "2.2.2.2"
                    ],
                    "includedApps": []
                },
                "telemetry": false,
                "theme": "System",
                "tvAutoConnectOnBoot": true,
                "vpnAccelerator": false,
                "ipV6Enabled": false,
                "customDns": {
                    "enabled": true,
                    "rawDnsList": [
                        "9.9.9.9"
                    ]
                }
            }
        """.trimIndent()
        val expectedSettings = LocalUserSettings(
            version = 2,
            startingValuesSaved = false,
            apiUseDoh = false,
            defaultProfileId = UUID(1L, 2L),
            lanConnections = true,
            lanConnectionsAllowDirect = true,
            mtuSize = 1000,
            netShield = NetShieldProtocol.DISABLED,
            protocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
            randomizedNat = false,
            splitTunneling = SplitTunnelingSettings(
                isEnabled = true,
                mode = SplitTunnelingMode.EXCLUDE_ONLY,
                excludedIps = listOf("1.1.1.1", "1.2.3.4"),
                includedIps = listOf("2.2.2.2"),
            ),
            telemetry = false,
            theme = ThemeType.System,
            tvAutoConnectOnBoot = true,
            vpnAccelerator = false,
            ipV6Enabled = false,
            customDns = CustomDnsSettings(
                toggleEnabled = true,
                rawDnsList = listOf("9.9.9.9")
            )
        )

        val decodedSettings = json.decodeFromString<LocalUserSettings>(settingsJson)
        assertEquals(expectedSettings, decodedSettings)
    }
}
