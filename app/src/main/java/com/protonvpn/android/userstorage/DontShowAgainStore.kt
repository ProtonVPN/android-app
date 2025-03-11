/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.userstorage

import com.protonvpn.android.auth.usecase.CurrentUser
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the current user's "don't show again" choices.
 *
 * Can only be used when a user is logged in.
 */
@Singleton
class DontShowAgainStore @Inject constructor(
    currentUser: CurrentUser,
    dontShowAgainStateStoreProvider: DontShowAgainStateStoreProvider
) {
    @Serializable
    enum class Choice {
        ShowDialog, Positive, Negative
    }

    @Serializable
    enum class Type {
        SignOutWhileConnected,
        ProtocolChangeWhenConnected,
        SplitTunnelingChangeWhenConnected,
        LanConnectionsChangeWhenConnected,
        IPv6ChangeWhenConnected,
    }

    private val storeProvider =
        CurrentUserStoreProvider(dontShowAgainStateStoreProvider, currentUser)

    suspend fun getChoice(type: Type): Choice =
        storeProvider.dataFlowOrDefaultIfNoUser(emptyMap()).first().getOrDefault(type, Choice.ShowDialog)

    suspend fun setChoice(type: Type, choice: Choice) {
        storeProvider.updateForCurrentUser { current -> current + (type to choice) }
    }
}

@Singleton
class DontShowAgainStateStoreProvider @Inject constructor(
    factory: LocalDataStoreFactory
) : StoreProvider<Map<DontShowAgainStore.Type, DontShowAgainStore.Choice>>(
    "dont_show_again",
    emptyMap(),
    MapSerializer(DontShowAgainStore.Type.serializer(), DontShowAgainStore.Choice.serializer()),
    factory
)