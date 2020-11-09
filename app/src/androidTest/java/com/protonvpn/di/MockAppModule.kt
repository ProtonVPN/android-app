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
package com.protonvpn.di

import android.os.SystemClock
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import com.google.gson.Gson
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonVPNRetrofit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.ui.home.AuthManager
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.CoreLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.mocks.MockVpnBackend
import com.protonvpn.testsHelper.IdlingResourceHelper
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import me.proton.core.network.data.di.ApiFactory
import me.proton.core.network.data.di.NetworkPrefs
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.NetworkManager
import javax.inject.Singleton

@Module
class MockAppModule {

    private val scope = CoroutineScope(Main)

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
    fun provideNetworkManager(): NetworkManager = MockNetworkManager()

    @Singleton
    @Provides
    fun provideProtonApiManager(
        networkManager: NetworkManager,
        apiClient: VpnApiClient,
        userData: UserData
    ): ApiManager<ProtonVPNRetrofit> {
        val appContext = ProtonApplication.getAppContext()
        val logger = CoreLogger()
        val apiFactory = ApiFactory(Constants.PRIMARY_VPN_API_URL, apiClient, logger, networkManager,
                NetworkPrefs(appContext), scope)
        val resource: IdlingResource =
                IdlingResourceHelper.create("OkHttp", apiFactory.baseOkHttpClient)
        IdlingRegistry.getInstance().register(resource)

        return apiFactory.ApiManager(userData.getNetworkUserData(), ProtonVPNRetrofit::class)
    }

    @Singleton
    @Provides
    fun provideApiClient(userData: UserData): VpnApiClient = VpnApiClient(userData)

    @Singleton
    @Provides
    fun provideAPI(apiManager: ApiManager<ProtonVPNRetrofit>): ProtonApiRetroFit = MockApi(scope, apiManager)

    @Singleton
    @Provides
    fun provideGson() = Gson()

    @Singleton
    @Provides
    fun provideUserPrefs(): UserData = Storage.load(UserData::class.java, UserData().apply {
        useSmartProtocol = false
    })

    @Singleton
    @Provides
    fun provideVpnStateMonitor(
        userData: UserData,
        api: ProtonApiRetroFit,
        backendManager: VpnBackendProvider,
        serverListUpdater: ServerListUpdater,
        trafficMonitor: TrafficMonitor,
        networkManager: NetworkManager,
        apiClient: VpnApiClient,
        maintenanceTracker: MaintenanceTracker
    ): VpnStateMonitor = MockVpnStateMonitor(userData, api, backendManager, serverListUpdater,
                trafficMonitor, networkManager, maintenanceTracker, scope).apply {
        apiClient.init(this)
    }

    @Singleton
    @Provides
    fun provideVpnBackendManager(): VpnBackendProvider = ProtonVpnBackendProvider(
            strongSwan = MockVpnBackend(VpnProtocol.IKEv2),
            openVpn = MockVpnBackend(VpnProtocol.OpenVPN))

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
    fun provideAuthManager(
        userData: UserData,
        serverManager: ServerManager,
        api: ProtonApiRetroFit,
        vpnStateMonitor: VpnStateMonitor,
        vpnApiClient: VpnApiClient
    ): AuthManager = AuthManager(
            scope, userData, serverManager, api, vpnStateMonitor, vpnApiClient, userData.networkUserData)
}
