/*
 * Copyright (c) 2021 Proton Technologies AG
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

import com.protonvpn.android.api.VpnHumanVerificationListener
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.core.configuration.EnvironmentConfiguration
import me.proton.core.humanverification.data.DeviceVerificationListenerImpl
import me.proton.core.humanverification.data.DeviceVerificationProviderImpl
import me.proton.core.humanverification.data.HumanVerificationManagerImpl
import me.proton.core.humanverification.data.HumanVerificationProviderImpl
import me.proton.core.humanverification.data.repository.HumanVerificationRepositoryImpl
import me.proton.core.humanverification.data.utils.NetworkRequestOverriderImpl
import me.proton.core.humanverification.domain.HumanVerificationExternalInput
import me.proton.core.humanverification.domain.HumanVerificationExternalInputImpl
import me.proton.core.humanverification.domain.HumanVerificationManager
import me.proton.core.humanverification.domain.HumanVerificationWorkflowHandler
import me.proton.core.humanverification.domain.repository.HumanVerificationRepository
import me.proton.core.humanverification.domain.utils.NetworkRequestOverrider
import me.proton.core.humanverification.presentation.HumanVerificationApiHost
import me.proton.core.humanverification.presentation.utils.HumanVerificationVersion
import me.proton.core.network.domain.deviceverification.DeviceVerificationListener
import me.proton.core.network.domain.deviceverification.DeviceVerificationProvider
import me.proton.core.network.domain.humanverification.HumanVerificationListener
import me.proton.core.network.domain.humanverification.HumanVerificationProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HumanVerificationModule {

    // HV3
    @Provides
    @HumanVerificationApiHost
    fun provideHumanVerificationApiHost(environmentConfiguration: EnvironmentConfiguration): String =
        environmentConfiguration.hv3Url

    @Provides
    fun provideHumanVerificationVersion(): HumanVerificationVersion = HumanVerificationVersion.HV3

    @Module
    @InstallIn(SingletonComponent::class)
    interface Bindings {

        @Binds
        @Singleton
        fun provideHumanVerificationConfiguration(impl: HumanVerificationExternalInputImpl): HumanVerificationExternalInput

        @Binds
        fun provideNetworkRequestOverrider(impl: NetworkRequestOverriderImpl): NetworkRequestOverrider

        @Binds
        @Singleton
        fun provideHumanVerificationListener(impl: VpnHumanVerificationListener): HumanVerificationListener

        @Binds
        @Singleton
        fun provideHumanVerificationProvider(impl: HumanVerificationProviderImpl): HumanVerificationProvider

        @Binds
        @Singleton
        public fun provideDeviceVerificationProvider(impl: DeviceVerificationProviderImpl): DeviceVerificationProvider

        @Binds
        @Singleton
        public fun provideDeviceVerificationListener(impl: DeviceVerificationListenerImpl): DeviceVerificationListener

        @Binds
        @Singleton
        fun provideHumanVerificationRepository(impl: HumanVerificationRepositoryImpl): HumanVerificationRepository

        @Binds
        @Singleton
        fun bindHumanVerificationManager(humanVerificationManagerImpl: HumanVerificationManagerImpl): HumanVerificationManager

        @Binds
        @Singleton
        fun bindHumanVerificationWorkflowHandler(humanVerificationManagerImpl: HumanVerificationManagerImpl): HumanVerificationWorkflowHandler
    }
}
