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
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestrictionsConfig(
    scope: CoroutineScope,
    private val restrictionFlowInternal: Flow<Restrictions>,
) {
    @Inject
    constructor(
        scope: CoroutineScope,
        appConfig: AppConfig,
        currentUser: CurrentUser
    ) : this(
        scope,
        createRestrictionFlow(appConfig, currentUser)
    )

    val restrictionFlow = SyncStateFlow(scope, restrictionFlowInternal)

    suspend fun restrictServerList() = restrictionFlowInternal.first().serverList
    suspend fun restrictProfile() = restrictionFlowInternal.first().profile
    suspend fun changeServerConfig() = restrictionFlowInternal.first().changeServerConfig

    @Deprecated("use suspending restrictQuickConnect")
    fun restrictQuickConnectSync() = restrictionFlow.value.quickConnect

    @Deprecated("use suspending restrictMap")
    fun restrictMapSync() = restrictionFlow.value.map

    companion object {
        private fun createRestrictionFlow(appConfig: AppConfig, currentUser: CurrentUser) =
            combine(
                currentUser.vpnUserFlow,
                appConfig.appConfigFlow
            ) { vpnUser, appConfigResponse ->
                val restrictUser = appConfigResponse.featureFlags.showNewFreePlan && vpnUser?.isFreeUser != false
                Restrictions(
                    restrictUser,
                    ChangeServerConfig(
                        shortDelayInSeconds = appConfigResponse.changeServerShortDelayInSeconds,
                        maxAttemptCount = appConfigResponse.changeServerAttemptLimit,
                        longDelayInSeconds = appConfigResponse.changeServerLongDelayInSeconds
                    )
                )
            }.distinctUntilChanged()
    }
}

data class Restrictions(
    val serverList: Boolean,
    val map: Boolean,
    val profile: Boolean,
    val quickConnect: Boolean,
    val vpnAccelerator: Boolean,
    val lan: Boolean,
    val splitTunneling: Boolean,
    val safeMode: Boolean,
    val changeServerConfig: ChangeServerConfig,
) {
    constructor(restrict: Boolean, changeServerConfig: ChangeServerConfig)
        : this(restrict, restrict, restrict, restrict, restrict, restrict, restrict, restrict, changeServerConfig)

    val isRestricted: Boolean =
        serverList || map || profile || quickConnect || vpnAccelerator || lan || splitTunneling || safeMode
}

data class ChangeServerConfig(
    val shortDelayInSeconds: Int,
    val maxAttemptCount: Int,
    val longDelayInSeconds: Int
)
