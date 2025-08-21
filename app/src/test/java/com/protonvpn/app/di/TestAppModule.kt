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
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.protonvpn.app.di

import com.protonvpn.android.auth.usecase.CurrentUserProvider
import com.protonvpn.android.auth.usecase.SetVpnUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.servers.UpdateServersWithBinaryStatus
import com.protonvpn.mocks.FakeUpdateServersWithBinaryStatus
import com.protonvpn.test.shared.TestCurrentUserProvider
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.TestSetVpnUser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TestScopeAppModule {

    @Provides
    @Singleton
    fun provideTestDispatcher() : TestDispatcher =
        UnconfinedTestDispatcher(TestCoroutineScheduler()).apply {
            Dispatchers.setMain(this)
        }

    @Provides
    @Singleton
    fun provideTestScope(testDispatcher: TestDispatcher) = TestScope(testDispatcher)

    @Provides
    @Singleton
    fun provideMainScope(testScope: TestScope): CoroutineScope = testScope.backgroundScope

    @Provides
    @Singleton
    fun provideVpnDispatcherProvider(testDispatcher: TestDispatcher): VpnDispatcherProvider = TestDispatcherProvider(testDispatcher)

    @Provides
    fun providerSetVpnUser(testCurrentUserProvider: TestCurrentUserProvider): SetVpnUser =
        TestSetVpnUser(testCurrentUserProvider)

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds
        @Singleton
        fun provideCurrentUserProvider(impl: TestCurrentUserProvider): CurrentUserProvider

        @Binds
        fun bindUpdateServersWithBinaryStatus(fake: FakeUpdateServersWithBinaryStatus): UpdateServersWithBinaryStatus
    }
}
