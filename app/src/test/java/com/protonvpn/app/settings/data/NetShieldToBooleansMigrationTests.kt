/*
 * Copyright (c) 2026. Proton AG
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
import com.protonvpn.android.settings.data.NetShieldToBooleansMigration
import com.protonvpn.android.settings.data.SplitTunnelingMode
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetShieldToBooleansMigrationTests {

    private val migration = NetShieldToBooleansMigration()

    @Test
    fun `GIVEN version below 3 WHEN shouldMigrate THEN returns true`() = runTest {
        val oldSettings = LocalUserSettings(version = 2, legacyNetShield = null)

        val shouldMigrateSettings = migration.shouldMigrate(currentData = oldSettings)

        assertTrue(actual = shouldMigrateSettings)
    }

    @Test
    fun `GIVEN legacyNetShield is still present WHEN shouldMigrate THEN returns true`() = runTest {
        val leftoverSettings = LocalUserSettings(
            version = 3,
            legacyNetShield = NetShieldProtocol.ENABLED_EXTENDED,
        )

        val shouldMigrateSettings = migration.shouldMigrate(currentData = leftoverSettings)

        assertTrue(actual = shouldMigrateSettings)
    }

    @Test
    fun `GIVEN old version and legacyNetShield is present WHEN shouldMigrate THEN returns true`() = runTest {
        val oldSettings = LocalUserSettings(
            version = 2,
            legacyNetShield = NetShieldProtocol.DISABLED,
        )

        val shouldMigrateSettings = migration.shouldMigrate(currentData = oldSettings)

        assertTrue(actual = shouldMigrateSettings)
    }

    @Test
    fun `GIVEN already migrated settings WHEN shouldMigrate THEN returns false`() = runTest {
        val migratedSettings = LocalUserSettings(version = 3, legacyNetShield = null)

        val shouldMigrateSettings = migration.shouldMigrate(currentData = migratedSettings)

        assertFalse(actual = shouldMigrateSettings)
    }

    @Test
    fun `GIVEN very old version WHEN shouldMigrate THEN returns true`() = runTest {
        val veryOldSettings = LocalUserSettings(version = 0, legacyNetShield = null)

        val shouldMigrateSettings = migration.shouldMigrate(currentData = veryOldSettings)

        assertTrue(actual = shouldMigrateSettings)
    }

    @Test
    fun `GIVEN legacyNetShield is DISABLED WHEN migrate THEN both booleans are off`() = runTest {
        val currentSettings = LocalUserSettings(
            version = 2,
            legacyNetShield = NetShieldProtocol.DISABLED,
            // Opposite values on the new fields, to prove migration actually overrides them.
            isNetShieldEnabled = true,
            isAdultContentBlocked = true,
        )

        val migratedSettings = migration.migrate(currentData = currentSettings)

        assertFalse(actual = migratedSettings.isNetShieldEnabled)
        assertFalse(actual = migratedSettings.isAdultContentBlocked)
    }

    @Suppress("DEPRECATION") // Legacy TV enum value still has to be migrated.
    @Test
    fun `GIVEN legacyNetShield is legacy ENABLED WHEN migrate THEN netshield is on and adult content is off`() = runTest {
        val currentSettings = LocalUserSettings(
            version = 2,
            legacyNetShield = NetShieldProtocol.ENABLED,
            isNetShieldEnabled = false,
            isAdultContentBlocked = false,
        )

        val migratedSettings = migration.migrate(currentData = currentSettings)

        assertTrue(actual = migratedSettings.isNetShieldEnabled)
        assertFalse(actual = migratedSettings.isAdultContentBlocked)
    }

    @Test
    fun `GIVEN legacyNetShield is ENABLED_EXTENDED WHEN migrate THEN netshield is on and adult content is off`() = runTest {
        val currentSettings = LocalUserSettings(
            version = 2,
            legacyNetShield = NetShieldProtocol.ENABLED_EXTENDED,
            isNetShieldEnabled = false,
            isAdultContentBlocked = false,
        )

        val migratedSettings = migration.migrate(currentData = currentSettings)

        assertTrue(actual = migratedSettings.isNetShieldEnabled)
        assertFalse(actual = migratedSettings.isAdultContentBlocked)
    }

    @Test
    fun `GIVEN legacyNetShield is ENABLED_EXTENDED_ADULT_CONTENT WHEN migrate THEN both booleans are on`() = runTest {
        val currentSettings = LocalUserSettings(
            version = 2,
            legacyNetShield = NetShieldProtocol.ENABLED_EXTENDED_ADULT_CONTENT,
            isNetShieldEnabled = false,
            isAdultContentBlocked = false,
        )

        val migrated = migration.migrate(currentData = currentSettings)

        assertTrue(actual = migrated.isNetShieldEnabled)
        assertTrue(actual = migrated.isAdultContentBlocked)
    }

    @Test
    fun `GIVEN every NetShieldProtocol value WHEN migrate THEN booleans are mapped correctly`() = runTest {
        listOf(
            Triple(NetShieldProtocol.DISABLED, false, false),
            Triple(NetShieldProtocol.ENABLED, true, false),
            Triple(NetShieldProtocol.ENABLED_EXTENDED, true, false),
            Triple(NetShieldProtocol.ENABLED_EXTENDED_ADULT_CONTENT, true, true),
        ).forEach { (legacyNetShield, expectedIsNetShieldEnabled, expectedIsAdultContentBlocked) ->
            val currentSettings = LocalUserSettings(version = 2, legacyNetShield = legacyNetShield)

            val migratedSettings = migration.migrate(currentData = currentSettings)

            assertEquals(
                expected = expectedIsNetShieldEnabled,
                actual = migratedSettings.isNetShieldEnabled,
                message = "isNetShieldEnabled for $legacyNetShield",
            )
            assertEquals(
                expected = expectedIsAdultContentBlocked,
                actual = migratedSettings.isAdultContentBlocked,
                message = "isAdultContentBlocked for $legacyNetShield",
            )
        }
    }

    @Test
    fun `GIVEN any settings WHEN migrate THEN version is bumped to 3`() = runTest {
        val currentSettings = LocalUserSettings(version = 2)
        val expectedVersion = 3

        val migratedSettings = migration.migrate(currentData = currentSettings)

        assertEquals(expected = expectedVersion, actual = migratedSettings.version)
    }

    @Test
    fun `GIVEN legacyNetShield is present WHEN migrate THEN legacyNetShield is cleared`() = runTest {
        val currentSettings = LocalUserSettings(
            version = 2,
            legacyNetShield = NetShieldProtocol.ENABLED_EXTENDED,
        )

        val migrated = migration.migrate(currentData = currentSettings)

        assertNull(actual = migrated.legacyNetShield)
    }

    @Test
    fun `GIVEN legacyNetShield is null WHEN migrate THEN existing booleans are kept`() = runTest {
        val currentSettings = LocalUserSettings(
            version = 2,
            legacyNetShield = null,
            isNetShieldEnabled = false,
            isAdultContentBlocked = true,
        )
        val expectedVersion = 3

        val migratedSettings = migration.migrate(currentData = currentSettings)

        assertEquals(expected = expectedVersion, actual = migratedSettings.version)
        assertNull(actual = migratedSettings.legacyNetShield)
        assertFalse(actual = migratedSettings.isNetShieldEnabled)
        assertTrue(actual = migratedSettings.isAdultContentBlocked)
    }

    @Test
    fun `GIVEN non-default settings WHEN migrate THEN unrelated settings are preserved`() = runTest {
        val currentSettings = LocalUserSettings(
            version = 2,
            startingValuesSaved = true,
            apiUseDoh = false,
            defaultProfileId = UUID(1L, 2L),
            lanConnections = true,
            lanConnectionsAllowDirect = true,
            mtuSize = 1000,
            legacyNetShield = NetShieldProtocol.ENABLED_EXTENDED_ADULT_CONTENT,
            isNetShieldEnabled = false, // Will be overridden by migration.
            isAdultContentBlocked = false, // Will be overridden by migration.
            protocol = ProtocolSelection(
                vpn = VpnProtocol.WireGuard,
                transmission = TransmissionProtocol.TCP,
            ),
            randomizedNat = false,
            splitTunneling = SplitTunnelingSettings(
                isEnabled = true,
                mode = SplitTunnelingMode.EXCLUDE_ONLY,
                excludedIps = listOf("1.1.1.1"),
                includedIps = listOf("2.2.2.2"),
            ),
            telemetry = false,
            theme = ThemeType.System,
            tvAutoConnectOnBoot = true,
            vpnAccelerator = false,
            ipV6Enabled = false,
            customDns = CustomDnsSettings(
                toggleEnabled = true,
                rawDnsList = listOf("9.9.9.9"),
            ),
        )
        val expectedSetting = currentSettings.copy(
            version = 3,
            legacyNetShield = null,
            isNetShieldEnabled = true,
            isAdultContentBlocked = true,
        )

        val migratedSettings = migration.migrate(currentData = currentSettings)

        assertEquals(expected = expectedSetting, actual = migratedSettings)
    }

    @Test
    fun `GIVEN migrated settings WHEN shouldMigrate THEN returns false`() = runTest {
        val currentSettings = LocalUserSettings(
            version = 2,
            legacyNetShield = NetShieldProtocol.ENABLED_EXTENDED,
        )
        val migratedSettings = migration.migrate(currentData = currentSettings)

        val shouldMigrateSettings = migration.shouldMigrate(currentData = migratedSettings)

        assertFalse(actual = shouldMigrateSettings)
    }

    @Test
    fun `GIVEN already migrated settings WHEN migrate is called again THEN result is unchanged`() = runTest {
        val currentSettings = LocalUserSettings(
            version = 2,
            legacyNetShield = NetShieldProtocol.ENABLED_EXTENDED_ADULT_CONTENT,
        )

        val migratedSettingsOnce = migration.migrate(currentData = currentSettings)
        val migratedSettingsTwice = migration.migrate(currentData = migratedSettingsOnce)

        assertEquals(expected = migratedSettingsOnce, actual = migratedSettingsTwice)
    }

    @Test
    fun `GIVEN migration WHEN cleanUp is called THEN it does not throw`() = runTest {
        migration.cleanUp()
    }

}
