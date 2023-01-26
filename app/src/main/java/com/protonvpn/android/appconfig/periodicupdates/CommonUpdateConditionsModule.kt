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

package com.protonvpn.android.appconfig.periodicupdates

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IsLoggedIn

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IsInForeground

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IsConnected

/**
 * Provides common conditions for periodic updates. They are singletons so even if they are used by multiple update
 * actions they will be shared.
 */
@Module
@InstallIn(SingletonComponent::class)
object CommonUpdateConditionsModule {

    @Provides
    @Singleton
    @IsLoggedIn
    fun isLoggedIn(currentUser: CurrentUser) = currentUser.vpnUserFlow.map { it != null }

    @Provides
    @Singleton
    @IsInForeground
    fun isInForeground(foregroundActivityTracker: ForegroundActivityTracker) =
        foregroundActivityTracker.foregroundActivityFlow.map { it != null }

    @Provides
    @Singleton
    @IsConnected
    fun isConnected(vpnStateMonitor: VpnStateMonitor) = vpnStateMonitor.status.map { it.state == VpnState.Connected }
}
