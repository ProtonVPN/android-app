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
import com.protonvpn.android.BuildConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.domain.entity.Product
import me.proton.core.key.data.db.KeySaltDatabase
import me.proton.core.key.data.db.PublicAddressDatabase
import me.proton.core.key.data.repository.KeySaltRepositoryImpl
import me.proton.core.key.data.repository.PrivateKeyRepositoryImpl
import me.proton.core.key.data.repository.PublicAddressRepositoryImpl
import me.proton.core.key.domain.repository.KeySaltRepository
import me.proton.core.key.domain.repository.PrivateKeyRepository
import me.proton.core.key.domain.repository.PublicAddressRepository
import me.proton.core.network.data.ApiProvider
import me.proton.core.user.data.UserAddressKeySecretProvider
import me.proton.core.user.data.UserAddressManagerImpl
import me.proton.core.user.data.UserManagerImpl
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.user.data.repository.DomainRepositoryImpl
import me.proton.core.user.data.repository.UserAddressRepositoryImpl
import me.proton.core.user.data.repository.UserRepositoryImpl
import me.proton.core.user.domain.UserAddressManager
import me.proton.core.user.domain.UserManager
import me.proton.core.user.domain.repository.DomainRepository
import me.proton.core.user.domain.repository.PassphraseRepository
import me.proton.core.user.domain.repository.UserAddressRepository
import me.proton.core.user.domain.repository.UserRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UserManagerModule {

    @Provides
    @Singleton
    fun provideUserRepositoryImpl(
        db: UserDatabase,
        provider: ApiProvider,
        cryptoContext: CryptoContext,
        @ApplicationContext context: Context,
    ): UserRepositoryImpl = UserRepositoryImpl(db, provider, context, cryptoContext, Product.Vpn)

    @Provides
    @Singleton
    fun provideUserAddressRepository(
        db: AddressDatabase,
        provider: ApiProvider,
        userRepository: UserRepository,
        userAddressKeySecretProvider: UserAddressKeySecretProvider,
        cryptoContext: CryptoContext
    ): UserAddressRepository =
        UserAddressRepositoryImpl(db, provider, userRepository, userAddressKeySecretProvider, cryptoContext)

    @Provides
    @Singleton
    fun provideUserAddressKeyPassphraseProvider(
        passphraseRepository: PassphraseRepository,
        cryptoContext: CryptoContext
    ): UserAddressKeySecretProvider =
        UserAddressKeySecretProvider(passphraseRepository, cryptoContext)

    @Provides
    @Singleton
    fun provideDomainRepository(
        provider: ApiProvider
    ): DomainRepository = DomainRepositoryImpl(provider)

    @Provides
    @Singleton
    fun provideKeySaltRepository(
        db: KeySaltDatabase,
        provider: ApiProvider
    ): KeySaltRepository = KeySaltRepositoryImpl(db, provider)

    @Provides
    @Singleton
    fun providePrivateKeyRepository(
        provider: ApiProvider
    ): PrivateKeyRepository = PrivateKeyRepositoryImpl(provider)

    @Provides
    @Singleton
    fun providePublicAddressKeyRepository(
        db: PublicAddressDatabase,
        provider: ApiProvider
    ): PublicAddressRepository = PublicAddressRepositoryImpl(db, provider)

    @Provides
    @Singleton
    fun provideUserManager(
        userRepository: UserRepository,
        userAddressRepository: UserAddressRepository,
        passphraseRepository: PassphraseRepository,
        keySaltRepository: KeySaltRepository,
        privateKeyRepository: PrivateKeyRepository,
        userAddressKeySecretProvider: UserAddressKeySecretProvider,
        cryptoContext: CryptoContext
    ): UserManager = UserManagerImpl(
        userRepository,
        userAddressRepository,
        passphraseRepository,
        keySaltRepository,
        privateKeyRepository,
        userAddressKeySecretProvider,
        cryptoContext
    )

    @Provides
    @Singleton
    fun provideUserAddressManager(
        userRepository: UserRepository,
        userAddressRepository: UserAddressRepository,
        privateKeyRepository: PrivateKeyRepository,
        userAddressKeySecretProvider: UserAddressKeySecretProvider,
        cryptoContext: CryptoContext
    ): UserAddressManager = UserAddressManagerImpl(
        userRepository,
        userAddressRepository,
        privateKeyRepository,
        userAddressKeySecretProvider,
        cryptoContext
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UserManagerBindsModule {

    @Binds
    abstract fun provideUserRepository(userRepositoryImpl: UserRepositoryImpl): UserRepository

    @Binds
    abstract fun providePassphraseRepository(userRepositoryImpl: UserRepositoryImpl): PassphraseRepository
}
