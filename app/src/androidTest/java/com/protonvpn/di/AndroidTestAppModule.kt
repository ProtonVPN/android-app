/*
 * Copyright (c) 2024. Proton AG
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

import com.protonvpn.android.auth.usecase.CurrentUserProvider
import com.protonvpn.android.auth.usecase.DefaultCurrentUserProvider
import com.protonvpn.android.auth.usecase.SetVpnUser
import com.protonvpn.android.auth.usecase.SetVpnUserImpl
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.servers.UpdateServersWithBinaryStatus
import com.protonvpn.android.servers.UpdateServersWithBinaryStatusImpl
import com.protonvpn.testsHelper.EspressoDispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import me.proton.core.configuration.EnvironmentConfiguration
import me.proton.core.test.quark.Quark
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AndroidTestScopeAppModule {

    @Provides
    @Singleton
    fun provideMainScope(): CoroutineScope = MainScope()

    @Provides
    fun provideQuark(environmentConfiguration: EnvironmentConfiguration): Quark =
        Quark.fromDefaultResources(
            host = environmentConfiguration.host,
            proxyToken = environmentConfiguration.proxyToken
        )

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds
        @Singleton
        fun provideVpnDispatcherProvider(impl: EspressoDispatcherProvider): VpnDispatcherProvider

        @Binds
        @Singleton
        fun provideCurrentUserProvider(impl: DefaultCurrentUserProvider): CurrentUserProvider

        @Binds
        fun provideSetVpnUser(setVpnUser: SetVpnUserImpl): SetVpnUser

        @Binds
        fun provideUpdateServersWithBinaryStatus(impl: UpdateServersWithBinaryStatusImpl): UpdateServersWithBinaryStatus
    }
}
