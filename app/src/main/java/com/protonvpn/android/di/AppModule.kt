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
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonVPNRetrofit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.ui.home.LogoutHandler
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.Constants.PRIMARY_VPN_API_URL
import com.protonvpn.android.utils.CoreLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.OpenVpnBackend
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.StrongSwanBackend
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import me.proton.core.network.data.ProtonCookieStore
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
    ) = ServerListUpdater(scope, api, serverManager, userData, ProtonApplication.getAppContext().isTV())

    @Singleton
    @Provides
    fun provideAppConfig(api: ProtonApiRetroFit, userData: UserData): AppConfig = AppConfig(scope, api, userData)

    @Singleton
    @Provides
    fun provideApiNotificationManager(appConfig: AppConfig): ApiNotificationManager =
        ApiNotificationManager(scope, System::currentTimeMillis, appConfig)

    @Singleton
    @Provides
    fun provideNetworkManager(): NetworkManager =
        NetworkManager(ProtonApplication.getAppContext())

    @Singleton
    @Provides
    fun provideVpnApiManager(
        networkManager: NetworkManager,
        apiClient: VpnApiClient,
        userData: UserData
    ): VpnApiManager {
        val appContext = ProtonApplication.getAppContext()
        val logger = CoreLogger()
        val sessionProvider = userData.apiSessionProvider
        val cookieStore = ProtonCookieStore(appContext)
        val apiFactory = if (BuildConfig.DEBUG) {
            ApiFactory(PRIMARY_VPN_API_URL, apiClient, logger, networkManager,
                NetworkPrefs(appContext), sessionProvider, sessionProvider, cookieStore, scope,
                certificatePins = emptyArray(), alternativeApiPins = emptyList())
        } else {
            ApiFactory(PRIMARY_VPN_API_URL, apiClient, logger, networkManager,
                NetworkPrefs(appContext), sessionProvider, sessionProvider, cookieStore, scope)
        }
        return VpnApiManager(apiFactory, userData.apiSessionProvider)
    }

    @Singleton
    @Provides
    fun provideApiManager(
        vpnApiManager: VpnApiManager
    ): ApiManager<ProtonVPNRetrofit> = vpnApiManager

    @Singleton
    @Provides
    fun provideApiClient(userData: UserData): VpnApiClient = VpnApiClient(scope, userData)

    @Singleton
    @Provides
    fun provideAPI(apiManager: ApiManager<ProtonVPNRetrofit>) = ProtonApiRetroFit(scope, apiManager)

    @Singleton
    @Provides
    fun provideRecentManager(
        vpnStateMonitor: VpnStateMonitor,
        serverManager: ServerManager,
        logoutHandler: LogoutHandler
    ) = RecentsManager(
        vpnStateMonitor,
        serverManager,
        logoutHandler
    )

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
        maintenanceTracker: MaintenanceTracker,
        apiClient: VpnApiClient
    ) = VpnStateMonitor(
        ProtonApplication.getAppContext(),
        userData,
        api,
        backendManager,
        serverListUpdater,
        trafficMonitor,
        networkManager,
        maintenanceTracker,
        scope
    ).apply {
        apiClient.init(this)
    }

    @Singleton
    @Provides
    fun provideVpnBackendManager(
        userData: UserData,
        networkManager: NetworkManager,
        appConfig: AppConfig
    ): VpnBackendProvider =
        ProtonVpnBackendProvider(
                StrongSwanBackend(random, networkManager, scope),
                OpenVpnBackend(random, userData, appConfig, System::currentTimeMillis))

    @Singleton
    @Provides
    fun provideReconnectManager(
        serverManager: ServerManager,
        api: ProtonApiRetroFit,
        serverListUpdater: ServerListUpdater,
        appConfig: AppConfig
    ) = MaintenanceTracker(scope, api, serverManager, serverListUpdater, appConfig)

    @Singleton
    @Provides
    fun provideTrafficMonitor() =
        TrafficMonitor(ProtonApplication.getAppContext(), scope, SystemClock::elapsedRealtime)

    @Singleton
    @Provides
    fun provideGuestHole(serverManager: ServerManager, vpnMonitor: VpnStateMonitor) =
            GuestHole(serverManager, vpnMonitor)

    @Singleton
    @Provides
    fun provideLogoutHandler(
        userData: UserData,
        serverManager: ServerManager,
        vpnApiManager: VpnApiManager,
        vpnStateMonitor: VpnStateMonitor,
        vpnApiClient: VpnApiClient
    ): LogoutHandler = LogoutHandler(
            scope, userData, serverManager, vpnApiManager, userData.apiSessionProvider, vpnStateMonitor, vpnApiClient)
}
