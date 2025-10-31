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

package com.protonvpn.android.ui.storage

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.userstorage.CurrentUserStoreProvider
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.userstorage.StoreProvider
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collection of individual persistent states of the UI. It is separate for each user.
 * Use for small items like last selected value etc.
 * Consider creating separate data stores or using DB for state of larger features.
 */
@Serializable
data class UiStoredState(
    val hasUsedRecents: Boolean = false,
    val hasSeenProfileAutoOpen: Boolean = false,
    val hasPopulatedDefaultProfiles: Boolean = false,
    val lastAppUpdatePromptAckedVersion: Int? = null,
    val shouldPromoteProfiles: Boolean = false,
    val hasShownProfilesInfo: Boolean = false,
    // if null will be scheduled to be true after 2 days.
    val shouldShowWidgetAdoption: Boolean? = null,
    val searchHistory: List<String> = emptyList(),
) {
    companion object {
        val Default = UiStoredState()
    }
}

@Reusable
class UiStateStorage @Inject constructor(
    provider: UiStateStoreProvider,
    currentUser: CurrentUser,
) {
    private val currentUserStoreProvider = CurrentUserStoreProvider(provider, currentUser)

    val state: Flow<UiStoredState> = currentUserStoreProvider.dataFlowOrDefaultIfNoUser(UiStoredState.Default)

    suspend fun update(transform: (current: UiStoredState) -> UiStoredState) =
        currentUserStoreProvider.updateForCurrentUser(transform)
}

@Singleton
class UiStateStoreProvider @Inject constructor(
    factory: LocalDataStoreFactory
) : StoreProvider<UiStoredState>(
    "ui_stored_state",
    UiStoredState.Default,
    UiStoredState.serializer(),
    factory,
)
