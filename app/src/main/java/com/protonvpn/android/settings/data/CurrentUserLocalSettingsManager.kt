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

import androidx.datastore.core.DataMigration
import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.userstorage.SharedStoreProvider
import com.protonvpn.android.userstorage.StoreProvider
import com.protonvpn.android.utils.Storage
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

    suspend fun updateConnectOnBoot(isEnabled: Boolean) =
        update { current -> current.copy(connectOnBoot = isEnabled) }

    suspend fun updateDefaultProfile(id: UUID?) =
        update { current -> current.copy(defaultProfileId = id) }

    suspend fun updateMtuSize(newSize: Int) =
        update { current -> current.copy(mtuSize = newSize) }

    suspend fun updateNetShield(newNetShieldProtocol: NetShieldProtocol) =
        update { current -> current.copy(netShield = newNetShieldProtocol) }

    suspend fun toggleNetShield() = update { current ->
        val newNetShieldState =
            if (current.netShield == NetShieldProtocol.DISABLED) NetShieldProtocol.ENABLED_EXTENDED else NetShieldProtocol.DISABLED
        current.copy(netShield = newNetShieldState)
    }

    suspend fun updateProtocol(newProtocol: ProtocolSelection) =
        update { current -> current.copy(protocol = newProtocol) }

    suspend fun toggleSafeMode() =
        update { current -> current.copy(safeMode = current.safeMode != true) }

    suspend fun updateSecureCore(isEnabled: Boolean) =
        update { current -> current.copy(secureCore = isEnabled) }

    suspend fun toggleVpnAccelerator() =
        update { current -> current.copy(vpnAccelerator = !current.vpnAccelerator) }

    suspend fun toggleAltRouting() =
        update { current -> current.copy(apiUseDoh = !current.apiUseDoh) }

    suspend fun toggleLanConnections() =
        update { current -> current.copy(lanConnections = !current.lanConnections) }

    suspend fun setRandomizedNat(value: Boolean) =
        update { current -> current.copy(randomizedNat = value) }

    suspend fun toggleSplitTunnelingEnabled() =
        update { current ->
            current.copy(splitTunneling = current.splitTunneling.copy(isEnabled = !current.splitTunneling.isEnabled))
        }

    suspend fun updateExcludedApps(excludedApps: List<String>) =
        update { current -> current.copy(splitTunneling = current.splitTunneling.copy(excludedApps = excludedApps)) }

    suspend fun updateExcludedIps(excludedIps: List<String>) =
        update { current -> current.copy(splitTunneling = current.splitTunneling.copy(excludedIps = excludedIps)) }

    suspend fun updateTelemetry(isEnabled: Boolean) =
        update { current -> current.copy(telemetry = isEnabled) }

    suspend fun update(transform: (current: LocalUserSettings) -> LocalUserSettings) =
        currentUserStoreProvider.updateForCurrentUser(transform)
}

@Singleton
class LocalUserSettingsStoreProvider @Inject constructor(
    factory: LocalDataStoreFactory,
    appFeaturesPrefs: AppFeaturesPrefs? = null
) : StoreProvider<LocalUserSettings>(
    "local_user_settings",
    LocalUserSettings.Default,
    LocalUserSettings.serializer(),
    factory,
    listOf(UserDataMigration(appFeaturesPrefs))
)

private class UserDataMigration(
    private val appFeaturesPrefs: AppFeaturesPrefs?
) : DataMigration<LocalUserSettings> {

    private val oldUserData by lazy { Storage.load(UserData::class.java) }

    override suspend fun cleanUp() {
        Storage.delete(UserData::class.java)
    }

    override suspend fun shouldMigrate(currentData: LocalUserSettings): Boolean {
        val userData = oldUserData
        return userData != null
    }

    override suspend fun migrate(currentData: LocalUserSettings): LocalUserSettings {
        val userData = oldUserData
        return if (userData != null) {
            if (userData.protocol.migratingFromIKEv2())
                appFeaturesPrefs?.showIKEv2Migration = true
            val protocol = userData.protocol.migrate()

            LocalUserSettings(
                apiUseDoh = userData.apiUseDoH,
                connectOnBoot = userData.connectOnBoot,
                defaultProfileId = userData.defaultProfileId,
                lanConnections = userData.bypassLocalTraffic,
                mtuSize = userData.mtuSize,
                netShield = userData.netShieldProtocol ?: LocalUserSettings.Default.netShield,
                protocol = protocol,
                randomizedNat = userData.randomizedNatEnabled,
                safeMode = userData.safeModeEnabled,
                secureCore = userData.secureCoreEnabled,
                splitTunneling = SplitTunnelingSettings(
                    userData.useSplitTunneling,
                    userData.splitTunnelIpAddresses,
                    userData.splitTunnelApps
                ),
                telemetry = userData.telemetryEnabled,
                vpnAccelerator = userData.vpnAcceleratorEnabled,
            )
        } else {
            currentData
        }
    }
}
