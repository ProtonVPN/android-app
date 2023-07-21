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

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.userstorage.CurrentUserStoreProvider
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.userstorage.StoreProvider
import com.protonvpn.android.vpn.ProtocolSelection
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
    currentUser: CurrentUser,
    userSettingsStoreProvider: LocalUserSettingsStoreProvider
) {
    // TODO: migrate from UserData.
    private val currentUserStoreProvider = CurrentUserStoreProvider(userSettingsStoreProvider, currentUser)

    val rawCurrentUserSettingsFlow = currentUserStoreProvider
        .dataFlowOrDefaultIfNoUser(LocalUserSettings.Default)

    suspend fun updateProtocol(newProtocol: ProtocolSelection) =
        update { current -> current.copy(protocol = newProtocol) }

    suspend fun updateSafeMode(isEnabled: Boolean) =
        update { current -> current.copy(safeMode = isEnabled) }

    suspend fun update(transform: (current: LocalUserSettings) -> LocalUserSettings) =
        currentUserStoreProvider.updateForCurrentUser(transform)
}

@Singleton
class LocalUserSettingsStoreProvider @Inject constructor(
    factory: LocalDataStoreFactory
) : StoreProvider<LocalUserSettings>(
    "local_user_settings",
    LocalUserSettings.Default,
    LocalUserSettings.serializer(),
    factory
)
