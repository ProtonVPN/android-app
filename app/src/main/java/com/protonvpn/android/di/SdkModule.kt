/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.di

import android.content.Context
import me.proton.vpn.sdk.api.ProtonVpnConnectionManager
import me.proton.vpn.sdk.api.ProtonVpnSdk
import me.proton.vpn.sdk.api.SdkDependencies
import com.protonvpn.android.vpn.protun.VpnSdkNotificationFactory
import com.protonvpn.android.vpn.protun.VpnSdkLogger
import com.protonvpn.android.vpn.protun.VpnSdkSystemEventHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SdkModule {

    @Provides
    @Singleton
    fun provideSdk(
        @ApplicationContext appContext: Context,
        logger: VpnSdkLogger,
        notificationFactory: VpnSdkNotificationFactory,
        systemEventHandler: VpnSdkSystemEventHandler,
    ): ProtonVpnSdk = ProtonVpnSdk.create(appContext) { _ ->
        SdkDependencies(notificationFactory, logger, systemEventHandler)
    }

    @Provides
    fun connectionManager(sdk: ProtonVpnSdk): ProtonVpnConnectionManager = sdk.connectionManager
}

