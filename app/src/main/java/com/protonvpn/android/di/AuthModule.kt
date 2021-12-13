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

import android.content.Context
import com.protonvpn.android.auth.VpnUserCheck
import com.protonvpn.android.auth.usecase.VpnLogin
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.vpn.CertificateRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.auth.data.repository.AuthRepositoryImpl
import me.proton.core.auth.domain.ClientSecret
import me.proton.core.auth.domain.repository.AuthRepository
import me.proton.core.auth.domain.usecase.PostLoginAccountSetup
import me.proton.core.auth.presentation.AuthOrchestrator
import me.proton.core.crypto.android.srp.GOpenPGPSrpCrypto
import me.proton.core.crypto.common.srp.SrpCrypto
import me.proton.core.network.data.ApiProvider
import me.proton.core.user.domain.UserManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(apiProvider: ApiProvider): AuthRepository = AuthRepositoryImpl(apiProvider)

    @Provides
    @Singleton
    fun provideSrpCrypto(): SrpCrypto =
        GOpenPGPSrpCrypto()

    @Provides
    @ClientSecret
    fun provideClientSecret(): String = ""

    @Provides
    @Singleton
    fun provideUserCheck(
        mainScope: CoroutineScope,
        @ApplicationContext context: Context,
        userData: UserData,
        accountManager: AccountManager,
        userManager: UserManager,
        certificateRepository: CertificateRepository,
        vpnLogin: VpnLogin
    ): PostLoginAccountSetup.UserCheck = VpnUserCheck(
        mainScope, userData, context, accountManager, userManager, certificateRepository, vpnLogin)
}

@Module
@InstallIn(ActivityRetainedComponent::class)
object AuthActivityRetainedModule {

    @Provides
    fun provideAuthOrchestrator(): AuthOrchestrator = AuthOrchestrator()
}
