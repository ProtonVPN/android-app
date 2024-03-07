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
import androidx.work.WorkManager
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.api.DohEnabled
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.api.ProtonVPNRetrofit
import com.protonvpn.android.api.VpnApiClient
import com.protonvpn.android.api.VpnApiManager
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.GlideImagePrefetcher
import com.protonvpn.android.appconfig.ImagePrefetcher
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingUpdateScheduler
import com.protonvpn.android.appconfig.globalsettings.GlobalSettingsUpdateWorker
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateWorkerScheduler
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateWorkerSchedulerImpl
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.CurrentUserProvider
import com.protonvpn.android.auth.usecase.DefaultCurrentUserProvider
import com.protonvpn.android.concurrency.DefaultDispatcherProvider
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.models.vpn.ServersStore
import com.protonvpn.android.models.vpn.usecase.SupportsProtocol
import com.protonvpn.android.redesign.countries.ui.CountryListViewModelDataAdapter
import com.protonvpn.android.redesign.countries.ui.CountryListViewModelDataAdapterLegacy
import com.protonvpn.android.telemetry.TelemetryUploadScheduler
import com.protonvpn.android.telemetry.TelemetryUploadWorkerScheduler
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.tv.login.TvLoginPollDelayMs
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.ui.snackbar.DelegatedSnackManager
import com.protonvpn.android.userstorage.DefaultLocalDataStoreFactory
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import com.protonvpn.android.utils.AndroidSharedPreferencesProvider
import com.protonvpn.android.utils.BuildConfigUtils
import com.protonvpn.android.utils.Constants.PRIMARY_VPN_API_URL
import com.protonvpn.android.utils.SharedPreferencesProvider
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.ProtonVpnBackendProvider
import com.protonvpn.android.vpn.VpnBackendProvider
import com.protonvpn.android.vpn.VpnConnectionErrorHandler
import com.protonvpn.android.vpn.VpnPermissionDelegate
import com.protonvpn.android.vpn.VpnServicePermissionDelegate
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.openvpn.OpenVpnBackend
import com.protonvpn.android.vpn.wireguard.WireguardBackend
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import me.proton.core.account.domain.entity.AccountType
import me.proton.core.domain.entity.AppStore
import me.proton.core.domain.entity.Product
import me.proton.core.network.data.ApiProvider
import me.proton.core.network.data.client.ExtraHeaderProviderImpl
import me.proton.core.network.data.di.AlternativeApiPins
import me.proton.core.network.data.di.BaseProtonApiUrl
import me.proton.core.network.data.di.CertificatePins
import me.proton.core.network.data.di.Constants
import me.proton.core.network.data.di.DohProviderUrls
import me.proton.core.network.domain.ApiClient
import me.proton.core.network.domain.ApiManager
import me.proton.core.network.domain.client.ExtraHeaderProvider
import me.proton.core.network.domain.serverconnection.DohAlternativesListener
import me.proton.core.util.kotlin.takeIfNotBlank
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.random.Random

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ElapsedRealtimeClock

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WallClock

@Module
@InstallIn(SingletonComponent::class)
object AppModuleProd {

    @Singleton
    @Provides
    @BaseProtonApiUrl
    fun provideProtonApiUrl(): HttpUrl = PRIMARY_VPN_API_URL.toHttpUrl()

    @Provides
    @DohProviderUrls
    fun provideDohProviderUrls(): Array<String> =
        BuildConfigUtils.sanitizedDohServices()?.toTypedArray()
            ?: Constants.DOH_PROVIDERS_URLS

    @Provides
    @CertificatePins
    fun provideCertificatePins(): Array<String> = when {
        BuildConfig.API_TLS_PINS != null -> BuildConfig.API_TLS_PINS
        BuildConfigUtils.isCertificatePinningFlavor() -> Constants.DEFAULT_SPKI_PINS
        else -> emptyArray()
    }

    @Provides
    @AlternativeApiPins
    fun provideAlternativeApiPins(): List<String> = when {
        BuildConfig.API_ALT_TLS_PINS != null -> BuildConfig.API_ALT_TLS_PINS.toList()
        BuildConfigUtils.isCertificatePinningFlavor() -> Constants.ALTERNATIVE_API_SPKI_PINS
        else -> emptyList()
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
    fun provideVpnBackendManager(
        appConfig: AppConfig,
        wireguardBackend: WireguardBackend,
        openVpnBackend: OpenVpnBackend,
        supportsProtocol: SupportsProtocol,
    ): VpnBackendProvider =
        ProtonVpnBackendProvider(
            appConfig,
            openVpnBackend,
            wireguardBackend,
            supportsProtocol
        )

    @Singleton
    @Provides
    @TvLoginPollDelayMs
    fun provideTvLoginPollDelayMs() = TvLoginViewModel.POLL_DELAY_MS

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds
        fun bindGlobalSettingsUpdateScheduler(
            scheduler: GlobalSettingsUpdateWorker.Scheduler
        ): GlobalSettingUpdateScheduler

        @Binds
        fun bindSharedPrefsProvider(provider: AndroidSharedPreferencesProvider): SharedPreferencesProvider

        @Binds
        fun bindPeriodicUpdateWorkerScheduler(sched: PeriodicUpdateWorkerSchedulerImpl): PeriodicUpdateWorkerScheduler

        @Binds
        @Singleton
        fun provideVpnDispatcherProvider(impl: DefaultDispatcherProvider): VpnDispatcherProvider

        @Binds
        fun bindVpnPrepareDelegate(delegate: VpnServicePermissionDelegate): VpnPermissionDelegate

        @Binds
        fun bindTelemetryUploadScheduler(scheduler: TelemetryUploadWorkerScheduler): TelemetryUploadScheduler

        @Singleton
        @Binds
        fun provideLocalDataStoreFactory(factory: DefaultLocalDataStoreFactory): LocalDataStoreFactory
    }
}

@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val scope = MainScope()

    @Provides
    @Singleton
    fun provideProduct(): Product =
        Product.Vpn

    @Provides
    fun provideAppStore(): AppStore =
        if (BuildConfig.FLAVOR_functionality == "google") AppStore.GooglePlay else AppStore.FDroid

    @Provides
    @Singleton
    fun provideRequiredAccountType(): AccountType =
        AccountType.External

    @Provides
    @Singleton
    fun provideRandom(): Random = Random

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

    @Singleton
    @Provides
    fun provideApiManager(
        vpnApiManager: VpnApiManager
    ): ApiManager<ProtonVPNRetrofit> = vpnApiManager

    @Singleton
    @Provides
    fun provideVpnApiClient(dohEnabled: DohEnabled, isTvCheck: IsTvCheck): VpnApiClient =
        VpnApiClient(scope, dohEnabled, isTvCheck)

    @Singleton
    @Provides
    fun provideApiClient(vpnApiClient: VpnApiClient): ApiClient = vpnApiClient

    @Singleton
    @Provides
    fun provideConnectivityMonitor() = ConnectivityMonitor(scope, ProtonApplication.getAppContext())

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

    @Provides
    @Singleton
    fun provideDelegatedSnackManager() = DelegatedSnackManager(SystemClock::elapsedRealtime)

    @Provides
    @Singleton
    fun provideServerStore(
        @ApplicationContext context: Context,
        dispatcherProvider: VpnDispatcherProvider
    ) = ServersStore.create(
        scope,
        dispatcherProvider,
        File(context.filesDir, ServersStore.STORE_FILENAME)
    )

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {
        @Singleton
        @Binds
        fun provideGuestHoleFallbackListener(guestHole: GuestHole): DohAlternativesListener

        @Singleton
        @Binds
        fun bindImagePrefetcher(glide: GlideImagePrefetcher): ImagePrefetcher

        @Singleton
        @Binds
        fun provideCurrentUserProvider(provider: DefaultCurrentUserProvider): CurrentUserProvider

        @Singleton
        @Binds
        fun provideCountryListViewModelDataAdapter(impl: CountryListViewModelDataAdapterLegacy): CountryListViewModelDataAdapter
    }
}
