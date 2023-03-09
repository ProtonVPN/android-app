/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
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

import com.protonvpn.android.auth.VpnSessionListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import me.proton.core.accountmanager.data.AccountManagerImpl
import me.proton.core.accountmanager.data.AccountMigratorImpl
import me.proton.core.accountmanager.data.SessionManagerImpl
import me.proton.core.accountmanager.data.SessionProviderImpl
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.SessionManager
import me.proton.core.accountmanager.domain.migrator.AccountMigrator
import me.proton.core.auth.domain.AccountWorkflowHandler
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.network.domain.session.SessionProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface CoreAccountManagerModule {

    @Binds
    @Singleton
    fun bindAccountManager(accountManagerImpl: AccountManagerImpl): AccountManager

    @Binds
    @Singleton
    fun bindAccountWorkflowHandler(accountManagerImpl: AccountManagerImpl): AccountWorkflowHandler

    @Binds
    @Singleton
    fun provideAccountMigrator(impl: AccountMigratorImpl): AccountMigrator

    @Binds
    @Singleton
    fun provideSessionProvider(impl: SessionProviderImpl): SessionProvider

    @Binds
    @Singleton
    fun provideSessionListener(impl: VpnSessionListener): SessionListener

    @Binds
    @Singleton
    fun provideSessionManager(impl: SessionManagerImpl): SessionManager
}
