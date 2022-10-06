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

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.telephony.TelephonyManager
import com.google.gson.Gson
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonVPNRetrofit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.GlideImagePrefetcher
import com.protonvpn.android.appconfig.ImagePrefetcher
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.concurrency.DefaultDispatcherProvider
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.tv.login.TvLoginPollDelayMs
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.ui.snackbar.DelegatedSnackManager
import com.protonvpn.android.utils.AndroidSharedPreferencesProvider
import com.protonvpn.android.utils.Constants.PRIMARY_VPN_API_URL
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.SharedPreferencesProvider
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.CertRefreshScheduler
import com.protonvpn.android.vpn.CertRefreshWorkerScheduler
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.ServerPing
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnErrorUIManager
import com.protonvpn.android.vpn.VpnPermissionDelegate
import com.protonvpn.android.vpn.VpnServicePermissionDelegate
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.ikev2.StrongSwanBackend
import com.protonvpn.android.vpn.openvpn.OpenVpnBackend
import com.protonvpn.android.vpn.wireguard.WireguardBackend
import com.protonvpn.android.vpn.wireguard.WireguardContextWrapper
import com.wireguard.android.backend.GoBackend
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.domain.entity.Product
import me.proton.core.humanverification.data.utils.NetworkRequestOverriderImpl
import me.proton.core.humanverification.domain.utils.NetworkRequestOverrider
import me.proton.core.network.data.ApiManagerFactory
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.NetworkManager
import me.proton.core.network.data.NetworkPrefsImpl
import me.proton.core.network.data.ProtonCookieStore
import me.proton.core.network.data.client.ClientIdProviderImpl
import me.proton.core.network.data.client.ExtraHeaderProviderImpl
import me.proton.core.network.data.di.Constants
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkPrefs
import me.proton.core.network.domain.client.ClientIdProvider
import me.proton.core.network.domain.client.ClientVersionValidator
import me.proton.core.network.domain.client.ExtraHeaderProvider
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import me.proton.core.network.domain.humanverification.HumanVerificationProvider
import me.proton.core.network.domain.scopes.MissingScopeListener
import me.proton.core.network.domain.server.ServerTimeListener
import me.proton.core.network.domain.serverconnection.DohAlternativesListener
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.network.domain.session.SessionProvider
import me.proton.core.util.kotlin.DispatcherProvider
import me.proton.core.util.kotlin.takeIfNotBlank
import okhttp3.OkHttpClient
import java.util.Random
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ElapsedRealtimeClock

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WallClock

@Module
@InstallIn(SingletonComponent::class)
object AppModuleProd {

    @Provides
    @Singleton
    fun provideVpnDispatcherProvider(): VpnDispatcherProvider = DefaultDispatcherProvider()

    @Singleton
    @Provides
    fun provideNetworkManager(@ApplicationContext appContext: Context): NetworkManager =
        NetworkManager(appContext)

    @Provides
    fun provideNetworkPrefs(@ApplicationContext context: Context): NetworkPrefs =
        NetworkPrefsImpl(context)

    @Singleton
    @Provides
    fun provideApiFactory(
        networkManager: NetworkManager,
        apiClient: VpnApiClient,
        clientIdProvider: ClientIdProvider,
        cookieStore: ProtonCookieStore,
        scope: CoroutineScope,
        sessionProvider: SessionProvider,
        sessionListener: SessionListener,
        humanVerificationProvider: HumanVerificationProvider,
        humanVerificationListener: HumanVerificationListener,
        missingScopeListener: MissingScopeListener,
        extraHeaderProvider: ExtraHeaderProvider,
        networkPrefs: NetworkPrefs,
        clientVersionValidator: ClientVersionValidator,
        guestHoleFallbackListener: GuestHole
    ): ApiManagerFactory {
        val serverTimeListener = object : ServerTimeListener {
            // We'd need to implement that when we start using core's crypto module.
            override fun onServerTimeUpdated(epochSeconds: Long) {}
        }
        val developmentFlavors = listOf("dev", "black")
        val isDevelopmentFlavor = developmentFlavors.any { BuildConfig.FLAVOR.startsWith(it) }
        val certificatePins = if (!isDevelopmentFlavor) Constants.DEFAULT_SPKI_PINS else emptyArray()
        val alternativeCertificatePins =
            if (!isDevelopmentFlavor) Constants.ALTERNATIVE_API_SPKI_PINS else emptyList()
        return ApiManagerFactory(
                PRIMARY_VPN_API_URL,
                apiClient,
                clientIdProvider,
                serverTimeListener,
                networkManager,
                networkPrefs,
                sessionProvider,
                sessionListener,
                humanVerificationProvider,
                humanVerificationListener,
                missingScopeListener,
                cookieStore,
                scope,
                certificatePins = certificatePins,
                alternativeApiPins = alternativeCertificatePins,
                extraHeaderProvider = extraHeaderProvider,
                clientVersionValidator = clientVersionValidator,
                dohAlternativesListener = guestHoleFallbackListener
            )
    }

    @Singleton
    @Provides
    fun provideAPI(
        scope: CoroutineScope,
        apiManager: VpnApiManager,
        telephonyManager: TelephonyManager?
    ) = ProtonApiRetroFit(scope, apiManager, telephonyManager)

    @Singleton
    @Provides
    fun provideUserPrefs(): UserData = UserData.load()

    @Singleton
    @Provides
    fun provideVpnBackendManager(
        appConfig: AppConfig,
        wireguardBackend: WireguardBackend,
        openVpnBackend: OpenVpnBackend,
        strongSwanBackend: StrongSwanBackend,
        userData: UserData,
    ): VpnBackendProvider =
        ProtonVpnBackendProvider(
            appConfig,
            strongSwanBackend,
            openVpnBackend,
            wireguardBackend,
            userData,
        )

    @Singleton
    @Provides
    @TvLoginPollDelayMs
    fun provideTvLoginPollDelayMs() = TvLoginViewModel.POLL_DELAY_MS

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {
        @Binds
        fun bindCertificateRefreshScheduler(scheduler: CertRefreshWorkerScheduler): CertRefreshScheduler

        @Binds
        fun bindSharedPrefsProvider(provider: AndroidSharedPreferencesProvider): SharedPreferencesProvider

        @Binds
        fun bindVpnPrepareDelegate(delegate: VpnServicePermissionDelegate): VpnPermissionDelegate
    }
}

@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val scope = MainScope()
    private val random = Random()

    @Provides
    @Singleton
    fun provideProduct(): Product =
        Product.Vpn

    @Provides
    @Singleton
    fun provideRequiredAccountType(): AccountType =
        AccountType.Username

    @Provides
    @Singleton
    fun provideRandom(): Random = random

    @Provides
    @Singleton
    fun provideMainScope(): CoroutineScope = scope

    @Provides
    @WallClock
    fun provideWallClock(): () -> Long = System::currentTimeMillis

    @Provides
    @ElapsedRealtimeClock
    fun provideElapsedRealtimeClock(): () -> Long = SystemClock::elapsedRealtime

    @Provides
    @Singleton
    fun provideExtraHeaderProvider(): ExtraHeaderProvider = ExtraHeaderProviderImpl().apply {
        BuildConfig.BLACK_TOKEN?.takeIfNotBlank()?.let {
            addHeaders("X-atlas-secret" to it)
        }
    }

    @Provides
    fun providePackageManager(): PackageManager = ProtonApplication.getAppContext().packageManager

    @Provides
    fun providePowerManager(@ApplicationContext appContext: Context): PowerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Provides
    fun provideBatteryManager(@ApplicationContext appContext: Context): BatteryManager? =
        appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

    @Provides
    fun provideActivityManager(): ActivityManager =
        ProtonApplication.getAppContext()
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    @Provides
    fun provideTelephonyManager(@ApplicationContext appContext: Context) =
        appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    @Singleton
    @Provides
    fun provideVpnApiManager(apiProvider: ApiProvider, currentUser: CurrentUser) =
        VpnApiManager(apiProvider, currentUser)

    @Provides
    @Singleton
    fun provideClientIdProvider(protonCookieStore: ProtonCookieStore): ClientIdProvider =
        ClientIdProviderImpl(PRIMARY_VPN_API_URL, protonCookieStore)

    @Singleton
    @Provides
    fun provideApiProvider(apiFactory: ApiManagerFactory, sessionProvider: SessionProvider): ApiProvider =
        ApiProvider(apiFactory, sessionProvider)

    @Singleton
    @Provides
    fun provideApiManager(
        vpnApiManager: VpnApiManager
    ): ApiManager<ProtonVPNRetrofit> = vpnApiManager

    @Singleton
    @Provides
    fun provideApiClient(userData: UserData, vpnStateMonitor: VpnStateMonitor): VpnApiClient =
        VpnApiClient(scope, userData, vpnStateMonitor)

    @Singleton
    @Provides
    fun provideGson() = Gson()

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
        networkManager: NetworkManager,
        vpnBackendProvider: VpnBackendProvider,
        currentUser: CurrentUser,
        vpnErrorUiManager: VpnErrorUIManager
    ) = VpnConnectionErrorHandler(
        scope,
        api,
        appConfig,
        userData,
        userPlanManager,
        serverManager,
        vpnStateMonitor,
        serverListUpdater,
        networkManager,
        vpnBackendProvider,
        currentUser,
        vpnErrorUiManager
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
    ) = NotificationHelper(
        ProtonApplication.getAppContext(),
        scope,
        vpnStateMonitor,
        trafficMonitor
    )

    @Singleton
    @Provides
    fun provideWireguardBackend(
        userData: UserData,
        networkManager: NetworkManager,
        appConfig: AppConfig,
        certificateRepository: CertificateRepository,
        dispatcherProvider: DispatcherProvider,
        serverPing: ServerPing,
        localAgentUnreachableTracker: LocalAgentUnreachableTracker,
        currentUser: CurrentUser,
        getNetZone: GetNetZone
    ) = WireguardBackend(
        ProtonApplication.getAppContext(),
        GoBackend(WireguardContextWrapper(ProtonApplication.getAppContext())),
        networkManager,
        userData,
        appConfig,
        certificateRepository,
        dispatcherProvider,
        scope,
        serverPing,
        localAgentUnreachableTracker,
        currentUser,
        getNetZone
    )

    @Singleton
    @Provides
    fun provideMaintenanceTracker(
        appConfig: AppConfig,
        vpnStateMonitor: VpnStateMonitor,
        vpnErrorHandler: VpnConnectionErrorHandler
    ) = MaintenanceTracker(
        scope,
        ProtonApplication.getAppContext(),
        appConfig,
        vpnStateMonitor,
        vpnErrorHandler
    )

    @Singleton
    @Provides
    fun provideTrafficMonitor(
        vpnStateMonitor: VpnStateMonitor,
        connectivityMonitor: ConnectivityMonitor,
    ) = TrafficMonitor(
        ProtonApplication.getAppContext(),
        scope,
        SystemClock::elapsedRealtime,
        vpnStateMonitor,
        connectivityMonitor
    )

    @Provides
    @Singleton
    fun provideDelegatedSnackManager() = DelegatedSnackManager(SystemClock::elapsedRealtime)

    @Provides
    fun provideNetworkRequestOverrider(): NetworkRequestOverrider =
        NetworkRequestOverriderImpl(OkHttpClient())

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {
        @Singleton
        @Binds
        fun provideGuestHoleFallbackListener(guestHole: GuestHole): DohAlternativesListener

        @Singleton
        @Binds
        fun bindDispatcherProvider(provider: VpnDispatcherProvider): DispatcherProvider

        @Singleton
        @Binds
        fun bindImagePrefetcher(glide: GlideImagePrefetcher): ImagePrefetcher
    }
}
