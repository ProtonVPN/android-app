/*
 * Copyright (c) 2022 Proton AG
 * This file is part of Proton AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.android.di

import android.content.Context
import com.protonvpn.android.vpn.VpnDns
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.arch.ErrorMessageContext
import me.proton.core.network.data.ApiManagerFactory
import me.proton.core.network.data.NetworkManager
import me.proton.core.network.data.NetworkPrefs
import me.proton.core.network.data.ProtonCookieStore
import me.proton.core.network.data.client.ClientIdProviderImpl
import me.proton.core.network.data.client.ClientVersionValidatorImpl
import me.proton.core.network.data.cookie.DiskCookieStorage
import me.proton.core.network.data.cookie.MemoryCookieStorage
import me.proton.core.network.data.di.AlternativeApiPins
import me.proton.core.network.data.di.BaseProtonApiUrl
import me.proton.core.network.data.di.CertificatePins
import me.proton.core.network.data.di.DohProviderUrls
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.network.domain.ApiClient
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkPrefs
import me.proton.core.network.domain.client.ClientIdProvider
import me.proton.core.network.domain.client.ClientVersionValidator
import me.proton.core.network.domain.client.ExtraHeaderProvider
import me.proton.core.network.domain.deviceverification.DeviceVerificationListener
import me.proton.core.network.domain.deviceverification.DeviceVerificationProvider
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import me.proton.core.network.domain.humanverification.HumanVerificationProvider
import me.proton.core.network.domain.interceptor.InterceptorInfo
import me.proton.core.network.domain.scopes.MissingScopeListener
import me.proton.core.network.domain.server.ServerClock
import me.proton.core.network.domain.server.ServerTimeListener
import me.proton.core.network.domain.server.ServerTimeManager
import me.proton.core.network.domain.serverconnection.DohAlternativesListener
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.network.domain.session.SessionProvider
import me.proton.core.network.presentation.util.ErrorMessageContextImpl
import me.proton.core.util.kotlin.CoroutineScopeProvider
import okhttp3.Cache
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

private const val OKHTTP_CACHE_SIZE = 10L * 1024L * 1024L // 10 MiB

@Module
@InstallIn(SingletonComponent::class)
public class CoreNetworkModule {

    @Provides
    @Singleton
    @ElementsIntoSet
    public fun provideNothing(): Set<Pair<InterceptorInfo, Interceptor>> = emptySet()

    @Provides
    @Singleton
    @Suppress("LongParameterList")
    internal fun provideApiFactory(
        @ApplicationContext context: Context,
        apiClient: ApiClient,
        clientIdProvider: ClientIdProvider,
        serverTimeListener: ServerTimeListener,
        networkManager: NetworkManager,
        networkPrefs: NetworkPrefs,
        cookieStore: ProtonCookieStore,
        sessionProvider: SessionProvider,
        sessionListener: SessionListener,
        humanVerificationProvider: HumanVerificationProvider,
        humanVerificationListener: HumanVerificationListener,
        deviceVerificationProvider: DeviceVerificationProvider,
        deviceVerificationListener: DeviceVerificationListener,
        missingScopeListener: MissingScopeListener,
        extraHeaderProvider: ExtraHeaderProvider,
        clientVersionValidator: ClientVersionValidator,
        dohAlternativesListener: DohAlternativesListener?,
        @BaseProtonApiUrl apiUrl: HttpUrl,
        @DohProviderUrls dohProviderUrls: Array<String>,
        @CertificatePins certificatePins: Array<String>,
        @AlternativeApiPins alternativeApiPins: List<String>,
        @SharedOkHttpClient okHttpClient: OkHttpClient,
        interceptors: @JvmSuppressWildcards Set<Pair<InterceptorInfo, Interceptor>>,
    ): ApiManagerFactory {
        return ApiManagerFactory(
            apiUrl,
            apiClient,
            clientIdProvider,
            serverTimeListener,
            networkManager,
            networkPrefs,
            sessionProvider,
            sessionListener,
            humanVerificationProvider,
            humanVerificationListener,
            deviceVerificationProvider,
            deviceVerificationListener,
            missingScopeListener,
            cookieStore,
            CoroutineScope(Job() + Dispatchers.Default),
            certificatePins,
            alternativeApiPins,
            cache = {
                Cache(
                    directory = File(context.cacheDir, "http_cache"),
                    maxSize = OKHTTP_CACHE_SIZE
                )
            },
            extraHeaderProvider = extraHeaderProvider,
            clientVersionValidator = clientVersionValidator,
            dohAlternativesListener = dohAlternativesListener,
            dohProviderUrls = dohProviderUrls,
            okHttpClient = okHttpClient,
            interceptors = interceptors,
        )
    }

    @Provides
    @Singleton
    internal fun provideCookieJar(
        @ApplicationContext context: Context,
        scopeProvider: CoroutineScopeProvider
    ): ProtonCookieStore = ProtonCookieStore(
        persistentStorage = DiskCookieStorage(context, ProtonCookieStore.DISK_COOKIE_STORAGE_NAME, scopeProvider),
        sessionStorage = MemoryCookieStorage()
    )

    @Provides
    @Singleton
    internal fun provideNetworkPrefs(@ApplicationContext context: Context) = NetworkPrefs(context)
}

@Module
@InstallIn(SingletonComponent::class)
public interface CoreNetworkBindsModule {
    @Binds
    @Singleton
    public fun provideClientIdProvider(impl: ClientIdProviderImpl): ClientIdProvider

    @Binds
    public fun provideClientVersionValidator(impl: ClientVersionValidatorImpl): ClientVersionValidator

    @Binds
    public fun bindServerTimeListener(manager: ServerTimeManager): ServerTimeListener

    @Binds
    public fun bindErrorMessageContext(impl: ErrorMessageContextImpl): ErrorMessageContext
}

@Module
@InstallIn(SingletonComponent::class)
public class CoreBaseNetworkModule {
    @Provides
    @Singleton
    @SharedOkHttpClient
    internal fun provideOkHttpClient(vpnDns: VpnDns): OkHttpClient =
        OkHttpClient().newBuilder()
            .dns(vpnDns)
            .build()

    @Provides
    @Singleton
    internal fun provideNetworkManager(@ApplicationContext context: Context): NetworkManager = NetworkManager(context)
}

@Module
@InstallIn(SingletonComponent::class)
public class CoreNetworkCryptoModule {
    @Provides
    @Singleton
    internal fun provideServerTimeOffsetManager(context: CryptoContext): ServerTimeManager {
        return ServerTimeManager() {
            context.pgpCrypto.updateTime(it / 1000)
        }
    }

    @Provides
    @Singleton
    public fun provideServerClock(serverTimeManager: ServerTimeManager): ServerClock {
        return ServerClock(serverTimeManager)
    }
}
