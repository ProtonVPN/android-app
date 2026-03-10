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

package com.protonvpn.android.restrictonsupsell

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.userstorage.CurrentUserStoreProvider
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.userstorage.StoreProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class RestrictionState(
    val lastEventTimestamp: Long,
    val lastNotificationTimestamp: Long,
    val lastEventConnectionId: String?,
    val lastDialogConnectionId: String?,
) {
    companion object {
        val default = RestrictionState(0, 0, null, null)
    }
}

@Serializable
data class RestrictionsUpsellStoredState(val streaming: RestrictionState) {
    companion object {
        val default = RestrictionsUpsellStoredState(RestrictionState.default)
    }
}

@Singleton
class RestrictionsUpsellStore @Inject constructor(
    provider: dagger.Lazy<RestrictionsUpsellStoreProvider>,
    currentUser: CurrentUser
) {
    private val currentUserStoreProvider by lazy {
        CurrentUserStoreProvider(provider.get(), currentUser)
    }

    private val noUserState =
        RestrictionsUpsellStoredState(RestrictionState(Long.MAX_VALUE, Long.MAX_VALUE, null, null))
    val state: Flow<RestrictionsUpsellStoredState>
        get() = currentUserStoreProvider.dataFlowOrDefaultIfNoUser(noUserState)

    suspend fun update(
        transform: (RestrictionsUpsellStoredState) -> RestrictionsUpsellStoredState
    ) {
        currentUserStoreProvider.updateForCurrentUser(transform)
    }
}

@Singleton
class RestrictionsUpsellStoreProvider @Inject constructor(factory: LocalDataStoreFactory) :
    StoreProvider<RestrictionsUpsellStoredState>(
        "restrictions_upsell_stored_state",
        RestrictionsUpsellStoredState.default,
        RestrictionsUpsellStoredState.serializer(),
        factory,
    )
