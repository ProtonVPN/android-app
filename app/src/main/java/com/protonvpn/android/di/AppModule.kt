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
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonVPNRetrofit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.Constants.PRIMARY_VPN_API_URL
import com.protonvpn.android.utils.CoreLogger
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
import me.proton.core.network.data.di.ApiFactory
import me.proton.core.network.data.di.NetworkManager
import me.proton.core.network.data.di.NetworkPrefs
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.NetworkManager
import java.util.Random
import javax.inject.Singleton

@Module
class AppModule {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val random = Random()

    @Singleton
    @Provides
    fun provideServerManager(userData: UserData) =
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
    fun provideAppConfig(api: ProtonApiRetroFit): AppConfig = AppConfig(scope, api)

    @Singleton
    @Provides
    fun provideNetworkManager(): NetworkManager =
        NetworkManager(ProtonApplication.getAppContext())

    @Singleton
    @Provides
    fun provideProtonApiManager(
        networkManager: NetworkManager,
        apiClient: VpnApiClient,
        userData: UserData
    ): ApiManager<ProtonVPNRetrofit> {
        val appContext = ProtonApplication.getAppContext()
        val logger = CoreLogger()
        val apiFactory = ApiFactory(PRIMARY_VPN_API_URL, apiClient, logger, networkManager,
                NetworkPrefs(appContext), scope)
        return apiFactory.ApiManager(userData.getNetworkUserData(), ProtonVPNRetrofit::class)
    }

    @Singleton
    @Provides
    fun provideApiClient(): VpnApiClient = VpnApiClient()

    @Singleton
    @Provides
    fun provideAPI(apiManager: ApiManager<ProtonVPNRetrofit>) = ProtonApiRetroFit(scope, apiManager)

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
        networkManager: NetworkManager,
        apiClient: VpnApiClient
    ) = VpnStateMonitor(userData, api, backendManager, serverListUpdater, trafficMonitor, networkManager, scope).apply {
        apiClient.init(this)
    }

    @Singleton
    @Provides
    fun provideVpnBackendManager(userData: UserData, appConfig: AppConfig): VpnBackendProvider =
        ProtonVpnBackendProvider(
                StrongSwanBackend(random, scope),
                OpenVpnBackend(random, userData, appConfig, System::currentTimeMillis))

    @Singleton
    @Provides
    fun provideTrafficMonitor() = TrafficMonitor(
            ProtonApplication.getAppContext(), scope, SystemClock::elapsedRealtime)

    @Singleton
    @Provides
    fun provideGuestHole(serverManager: ServerManager, vpnMonitor: VpnStateMonitor) =
            GuestHole(serverManager, vpnMonitor)
}
