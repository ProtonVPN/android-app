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
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.components.NotificationHelper
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.ui.home.LogoutHandler
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.isTV
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.CoreLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.mocks.MockVpnBackend
import com.protonvpn.testsHelper.IdlingResourceHelper
import dagger.Module
import dagger.Provides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import me.proton.core.network.data.ProtonCookieStore
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
        userData: UserData,
        vpnStateMonitor: VpnStateMonitor,
        userPlanManager: UserPlanManager,
    ) = ServerListUpdater(scope, api, serverManager, userData, ProtonApplication.getAppContext().isTV(),
        vpnStateMonitor, userPlanManager)

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
    fun provideVpnApiManager(
        networkManager: NetworkManager,
        apiClient: VpnApiClient,
        userData: UserData
    ): VpnApiManager {
        val appContext = ProtonApplication.getAppContext()
        val logger = CoreLogger()
        val sessionProvider = userData.apiSessionProvider
        val cookieStore = ProtonCookieStore(appContext)
        val apiFactory = ApiFactory(Constants.PRIMARY_VPN_API_URL, apiClient, logger, networkManager,
                NetworkPrefs(appContext), sessionProvider, sessionProvider, cookieStore, scope)

        val resource: IdlingResource =
            IdlingResourceHelper.create("OkHttp", apiFactory.baseOkHttpClient)
        IdlingRegistry.getInstance().register(resource)

        return VpnApiManager(apiFactory, userData.apiSessionProvider)
    }

    @Singleton
    @Provides
    fun provideApiManager(
        vpnApiManager: VpnApiManager
    ): ApiManager<ProtonVPNRetrofit> = vpnApiManager

    @Singleton
    @Provides
    fun provideUserPlanManager(
        api: ProtonApiRetroFit,
        userData: UserData,
        vpnStateMonitor: VpnStateMonitor,
    ): UserPlanManager = UserPlanManager(api, userData, vpnStateMonitor)

    @Singleton
    @Provides
    fun provideApiClient(userData: UserData, vpnStateMonitor: VpnStateMonitor): VpnApiClient =
        VpnApiClient(scope, userData, vpnStateMonitor)

    @Singleton
    @Provides
    fun provideAPI(apiManager: ApiManager<ProtonVPNRetrofit>, userData: UserData): ProtonApiRetroFit =
        MockApi(scope, apiManager, userData)

    @Singleton
    @Provides
    fun provideRecentManager(
        vpnStateMonitor: VpnStateMonitor,
        serverManager: ServerManager,
        logoutHandler: LogoutHandler
    ) = RecentsManager(scope, vpnStateMonitor, serverManager, logoutHandler)

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
    fun provideVpnConnectionErrorHandler(
        api: ProtonApiRetroFit,
        appConfig: AppConfig,
        userData: UserData,
        userPlanManager: UserPlanManager,
        serverManager: ServerManager,
        vpnStateMonitor: VpnStateMonitor,
        serverListUpdater: ServerListUpdater,
        notificationHelper: NotificationHelper,
    ) = VpnConnectionErrorHandler(scope, ProtonApplication.getAppContext(), api, appConfig, userData, userPlanManager,
        serverManager, vpnStateMonitor, serverListUpdater, notificationHelper)

    @Singleton
    @Provides
    fun provideVpnConnectionManager(
        userData: UserData,
        backendManager: VpnBackendProvider,
        networkManager: NetworkManager,
        vpnConnectionErrorHandler: VpnConnectionErrorHandler,
        vpnStateMonitor: VpnStateMonitor,
        notificationHelper: NotificationHelper,
    ): VpnConnectionManager = MockVpnConnectionManager(
        userData,
        backendManager,
        networkManager,
        vpnConnectionErrorHandler,
        vpnStateMonitor,
        notificationHelper,
        scope
    )

    @Singleton
    @Provides
    fun provideVpnStateMonitor() = VpnStateMonitor()

    @Singleton
    @Provides
    fun provideConnectivityMonitor() = ConnectivityMonitor(scope, ProtonApplication.getAppContext())

    @Singleton
    @Provides
    fun provideNotificationHelper(
        vpnStateMonitor: VpnStateMonitor,
        trafficMonitor: TrafficMonitor,
    ) = NotificationHelper(ProtonApplication.getAppContext(), scope, vpnStateMonitor, trafficMonitor)

    @Singleton
    @Provides
    fun provideVpnBackendManager(): VpnBackendProvider = ProtonVpnBackendProvider(
            strongSwan = MockVpnBackend(VpnProtocol.IKEv2),
            openVpn = MockVpnBackend(VpnProtocol.OpenVPN))

    @Singleton
    @Provides
    fun provideMaintenanceTracker(
        appConfig: AppConfig,
        vpnStateMonitor: VpnStateMonitor,
        vpnErrorHandler: VpnConnectionErrorHandler
    ) = MaintenanceTracker(scope, ProtonApplication.getAppContext(), appConfig, vpnStateMonitor, vpnErrorHandler)

    @Singleton
    @Provides
    fun provideTrafficMonitor(
        vpnStateMonitor: VpnStateMonitor,
        connectivityMonitor: ConnectivityMonitor
    ) = TrafficMonitor(
        ProtonApplication.getAppContext(),
        scope,
        SystemClock::elapsedRealtime,
        vpnStateMonitor,
        connectivityMonitor
    )
    
    @Singleton
    @Provides
    fun provideGuestHole(
        serverManager: ServerManager,
        vpnMonitor: VpnStateMonitor,
        connectionManager: VpnConnectionManager
    ) = GuestHole(scope, serverManager, vpnMonitor, connectionManager)

    @Singleton
    @Provides
    fun provideLogoutHandler(
        userData: UserData,
        serverManager: ServerManager,
        vpnApiManager: VpnApiManager,
        vpnStateMonitor: VpnStateMonitor,
        vpnConnectionManager: VpnConnectionManager,
        vpnApiClient: VpnApiClient
    ): LogoutHandler = LogoutHandler(scope, userData, serverManager, vpnApiManager, userData.apiSessionProvider,
        vpnStateMonitor, vpnConnectionManager, vpnApiClient)
}
