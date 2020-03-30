/*
 * Copyright (c) 2018 Proton Technologies AG
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

import android.os.SystemClock
import com.google.gson.Gson
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.AlternativeApiManagerProd
import com.protonvpn.android.api.ProtonApiManager
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonPrimaryApiBackend
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.Constants.PRIMARY_VPN_API_URL
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.OpenVpnBackend
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.StrongSwanBackend
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.Random
import javax.inject.Singleton

@Module
class AppModule {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val random = Random()

    @Singleton
    @Provides
    fun provideServerManager(userData: UserData?) =
            ServerManager(ProtonApplication.getAppContext(), userData)

    @Singleton
    @Provides
    fun provideServerListUpdater(
        api: ProtonApiRetroFit,
        serverManager: ServerManager,
        userData: UserData
    ) = ServerListUpdater(scope, api, serverManager, userData)

    @Singleton
    @Provides
    fun provideProtonApiManager(userData: UserData): ProtonApiManager {
        val altApiManager = AlternativeApiManagerProd(userData, System::currentTimeMillis)
        val primaryApiBackend = ProtonPrimaryApiBackend(PRIMARY_VPN_API_URL)
        return ProtonApiManager(ProtonApplication.getAppContext(), userData, altApiManager,
                primaryApiBackend, random)
    }

    @Singleton
    @Provides
    fun provideAPI(apiManager: ProtonApiManager) = ProtonApiRetroFit(scope, apiManager)

    @Singleton
    @Provides
    fun provideGson() = Gson()

    @Singleton
    @Provides
    fun provideUserPrefs(): UserData = Storage.load(UserData::class.java, UserData())

    @Singleton
    @Provides
    fun provideVpnStateMonitor(
        userData: UserData,
        api: ProtonApiRetroFit,
        backendManager: VpnBackendProvider,
        serverListUpdater: ServerListUpdater,
        trafficMonitor: TrafficMonitor,
        protonApiManager: ProtonApiManager
    ) = VpnStateMonitor(userData, api, backendManager, serverListUpdater, trafficMonitor,
            protonApiManager, scope)

    @Singleton
    @Provides
    fun provideVpnBackendManager(userData: UserData): VpnBackendProvider =
        ProtonVpnBackendProvider(
                StrongSwanBackend(random, scope),
                OpenVpnBackend(random, userData, System::currentTimeMillis))

    @Singleton
    @Provides
    fun provideTrafficMonitor() = TrafficMonitor(
            ProtonApplication.getAppContext(), scope, SystemClock::elapsedRealtime)
}
