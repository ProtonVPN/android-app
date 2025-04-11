/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.settings.data

import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataMigration
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.userstorage.SharedStoreProvider
import com.protonvpn.android.userstorage.StoreProvider
import com.protonvpn.android.vpn.ProtocolSelection
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exposes raw local settings for the current user.
 *
 * Consider using EffectiveCurrentUserSettings for getting settings values.
 * This class is intended for functionality that manages settings themselves, like the settings screens.
 */
@Singleton
class CurrentUserLocalSettingsManager @Inject constructor(
    userSettingsStoreProvider: LocalUserSettingsStoreProvider
) {
    // Switch to CurrentUserStoreProvider when implementing VPNAND-1381.
    //private val currentUserStoreProvider = CurrentUserStoreProvider(userSettingsStoreProvider, currentUser)
    private val currentUserStoreProvider = SharedStoreProvider(userSettingsStoreProvider)

    val rawCurrentUserSettingsFlow = currentUserStoreProvider
        .dataFlowOrDefaultIfNoUser(LocalUserSettings.Default)

    suspend fun getRawUserSettingsStore(vpnUser: VpnUser) = currentUserStoreProvider.getDataStoreForUser(vpnUser)

    suspend fun updateApiUseDoh(isEnabled: Boolean) =
        update { current -> current.copy(apiUseDoh = isEnabled) }

    suspend fun updateDefaultProfile(id: UUID?) =
        update { current -> current.copy(defaultProfileId = id) }

    suspend fun updateMtuSize(newSize: Int) =
        update { current -> current.copy(mtuSize = newSize) }

    suspend fun updateCustomDnsList(newDnsList: List<String>) {
        updateCustomDns { current ->
            current.copy(rawDnsList = newDnsList)
        }
    }

    suspend fun updateCustomDns(transform: (CustomDnsSettings) -> (CustomDnsSettings)) =
        update { current ->
            val updatedCustomDns = transform(current.customDns)
            current.copy(customDns = updatedCustomDns)
        }

    suspend fun toggleCustomDNS() =
        update { current ->
            current.copy(customDns = current.customDns.copy(toggleEnabled = !current.customDns.toggleEnabled))
        }

    suspend fun disableCustomDNS() =
        update { current -> current.copy(customDns = current.customDns.copy(toggleEnabled = false)) }

    suspend fun updateNetShield(newNetShieldProtocol: NetShieldProtocol) =
        update { current -> current.copy(netShield = newNetShieldProtocol) }

    suspend fun toggleNetShield() = update { current ->
        val newNetShieldState =
            if (current.netShield == NetShieldProtocol.DISABLED) NetShieldProtocol.ENABLED_EXTENDED else NetShieldProtocol.DISABLED
        current.copy(netShield = newNetShieldState)
    }

    suspend fun updateProtocol(newProtocol: ProtocolSelection) =
        update { current -> current.copy(protocol = newProtocol) }

    suspend fun toggleSplitTunneling() =
        update { current ->
            current.copy(splitTunneling = current.splitTunneling.copy(isEnabled = !current.splitTunneling.isEnabled))
        }

    suspend fun updateSplitTunnelingMode(mode: SplitTunnelingMode) {
        update { current ->
            current.copy(splitTunneling = current.splitTunneling.copy(mode = mode))
        }
    }

    suspend fun toggleVpnAccelerator() =
        update { current -> current.copy(vpnAccelerator = !current.vpnAccelerator) }

    suspend fun toggleAltRouting() =
        update { current -> current.copy(apiUseDoh = !current.apiUseDoh) }

    suspend fun toggleIPv6() =
        update { current -> current.copy(ipV6Enabled = !current.ipV6Enabled) }

    suspend fun toggleLanConnections() =
        update { current -> current.copy(lanConnections = !current.lanConnections) }

    suspend fun toggleLanAllowDirectConnections() =
        update { current -> current.copy(lanConnectionsAllowDirect = !current.lanConnectionsAllowDirect) }

    suspend fun setRandomizedNat(value: Boolean) =
        update { current -> current.copy(randomizedNat = value) }

    suspend fun updateSplitTunnelSettings(transform: (SplitTunnelingSettings) -> (SplitTunnelingSettings)) =
        update { current -> current.copy(splitTunneling = transform(current.splitTunneling)) }

    suspend fun updateSplitTunnelApps(selectedApps: List<String>, mode: SplitTunnelingMode) =
        update { current ->
            val newSplitTunneling = when (mode) {
                SplitTunnelingMode.INCLUDE_ONLY -> current.splitTunneling.copy(includedApps = selectedApps)
                SplitTunnelingMode.EXCLUDE_ONLY -> current.splitTunneling.copy(excludedApps = selectedApps)
            }
            current.copy(splitTunneling = newSplitTunneling)
        }

    suspend fun updateExcludedIps(selectedIps: List<String>, mode: SplitTunnelingMode) =
        update { current ->
            val newSplitTunneling = when (mode) {
                SplitTunnelingMode.INCLUDE_ONLY -> current.splitTunneling.copy(includedIps = selectedIps)
                SplitTunnelingMode.EXCLUDE_ONLY -> current.splitTunneling.copy(excludedIps = selectedIps)
            }
            current.copy(splitTunneling = newSplitTunneling)
        }

    suspend fun updateTelemetry(isEnabled: Boolean) =
        update { current -> current.copy(telemetry = isEnabled) }

    suspend fun update(transform: (current: LocalUserSettings) -> LocalUserSettings) =
        currentUserStoreProvider.updateForCurrentUser(transform)
}

@Singleton
class LocalUserSettingsStoreProvider @Inject constructor(
    factory: LocalDataStoreFactory,
) : StoreProvider<LocalUserSettings>(
    "local_user_settings",
    LocalUserSettings.Default,
    LocalUserSettings.serializer(),
    factory,
    listOf(SplitTunnelingMigration())
)

@VisibleForTesting
class SplitTunnelingMigration : DataMigration<LocalUserSettings> {

    override suspend fun shouldMigrate(currentData: LocalUserSettings): Boolean = currentData.version < 2

    override suspend fun migrate(currentData: LocalUserSettings): LocalUserSettings =
        currentData.copy(
            version = 2,
            splitTunneling = migrateSplitTunneling(currentData.splitTunneling)
        )

    override suspend fun cleanUp() = Unit

    private fun migrateSplitTunneling(current: SplitTunnelingSettings): SplitTunnelingSettings =
        // Update the mode for existing settings, even when disabled.
        current.copy(mode = migratedMode(excludedApps = current.excludedApps, excludedIps = current.excludedIps))
}

private fun migratedMode(excludedApps: List<String>, excludedIps: List<String>): SplitTunnelingMode =
    if (excludedApps.isNotEmpty() || excludedIps.isNotEmpty()) SplitTunnelingMode.EXCLUDE_ONLY
    else SplitTunnelingMode.INCLUDE_ONLY
