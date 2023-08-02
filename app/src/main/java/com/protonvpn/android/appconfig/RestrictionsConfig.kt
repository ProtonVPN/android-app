/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.android.appconfig

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.utils.SyncStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestrictionsConfig @Inject constructor(
    scope: CoroutineScope,
    val appConfig: AppConfig,
    val currentUser: CurrentUser
) {
    val restrictionFlow: Flow<Boolean> = combine(
        currentUser.vpnUserFlow,
        appConfig.appConfigFlow
    ) { vpnUser, appConfig ->
        appConfig.featureFlags.showNewFreePlan && vpnUser?.isFreeUser != false
    }.distinctUntilChanged()

    private val internalRestrictionState = SyncStateFlow(scope, restrictionFlow)

    private fun restrictUser() = internalRestrictionState.value
    fun restrictServerList() = restrictUser()
    fun restrictMap() = restrictUser()
    fun restrictProfile() = restrictUser()
    fun restrictQuickConnect() = restrictUser()
}