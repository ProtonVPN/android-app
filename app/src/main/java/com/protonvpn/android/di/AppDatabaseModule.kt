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
import androidx.room.Room.databaseBuilder
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdatesDatabase
import com.protonvpn.android.auth.data.VpnUserDatabase
import com.protonvpn.android.db.AppDatabase
import com.protonvpn.android.db.AppDatabase.Companion.buildDatabase
import com.protonvpn.android.profiles.data.VpnProfilesDatabase
import com.protonvpn.android.redesign.recents.data.VpnRecentsDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.auth.data.db.AuthDatabase
import me.proton.core.challenge.data.db.ChallengeDatabase
import me.proton.core.eventmanager.data.db.EventMetadataDatabase
import me.proton.core.featureflag.data.db.FeatureFlagDatabase
import me.proton.core.humanverification.data.db.HumanVerificationDatabase
import me.proton.core.key.data.db.KeySaltDatabase
import me.proton.core.key.data.db.PublicAddressDatabase
import me.proton.core.notification.data.local.db.NotificationDatabase
import me.proton.core.observability.data.db.ObservabilityDatabase
import me.proton.core.payment.data.local.db.PaymentDatabase
import me.proton.core.push.data.local.db.PushDatabase
import me.proton.core.telemetry.data.db.TelemetryDatabase
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.userrecovery.data.db.DeviceRecoveryDatabase
import me.proton.core.usersettings.data.db.OrganizationDatabase
import me.proton.core.usersettings.data.db.UserSettingsDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppDatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        databaseBuilder(context, AppDatabase::class.java, "db").buildDatabase()
}

@Module
@InstallIn(SingletonComponent::class)
object AppDatabaseDaoModule {
    @Provides
    fun providePeriodicUpdatesDao(db: PeriodicUpdatesDatabase) = db.periodicUpdatesDao()
    @Provides
    fun provideVpnUserDao(db: VpnUserDatabase) = db.vpnUserDao()
    @Provides
    fun provideRecentsDao(db: VpnRecentsDatabase) = db.recentsDao()
    @Provides
    fun provideProfilesDao(db: VpnProfilesDatabase) = db.profilesDao()

    @Provides
    fun provideDefaultConnectionDao(db: VpnRecentsDatabase) = db.defaultConnectionDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppDatabaseBindsModule {
    @Binds
    abstract fun provideAccountDatabase(appDatabase: AppDatabase): AccountDatabase

    @Binds
    abstract fun provideAddressDatabase(appDatabase: AppDatabase): AddressDatabase

    @Binds
    abstract fun provideChallengeDatabase(appDatabase: AppDatabase): ChallengeDatabase

    @Binds
    abstract fun provideDeviceRecoveryDatabase(appDatabase: AppDatabase): DeviceRecoveryDatabase

    @Binds
    abstract fun provideEventMetadataDatabase(appDatabase: AppDatabase): EventMetadataDatabase

    @Binds
    abstract fun provideFeatureFlagDatabase(appDatabase: AppDatabase): FeatureFlagDatabase

    @Binds
    abstract fun provideHumanVerificationDatabase(appDatabase: AppDatabase): HumanVerificationDatabase

    @Binds
    abstract fun provideKeySaltDatabase(appDatabase: AppDatabase): KeySaltDatabase

    @Binds
    abstract fun provideNotificationDatabase(appDatabase: AppDatabase): NotificationDatabase

    @Binds
    abstract fun provideObservabilityDatabase(appDatabase: AppDatabase): ObservabilityDatabase

    @Binds
    abstract fun provideOrganizationDatabase(appDatabase: AppDatabase): OrganizationDatabase

    @Binds
    abstract fun providePaymentDatabase(appDatabase: AppDatabase): PaymentDatabase

    @Binds
    abstract fun providePeriodicUpdatesDatabase(appDatabase: AppDatabase): PeriodicUpdatesDatabase

    @Binds
    abstract fun providePushDatabase(appDatabase: AppDatabase): PushDatabase

    @Binds
    abstract fun provideTelemetryDatabase(appDatabase: AppDatabase): TelemetryDatabase

    @Binds
    abstract fun provideUserDatabase(appDatabase: AppDatabase): UserDatabase

    @Binds
    abstract fun provideUserSettingsDatabase(appDatabase: AppDatabase): UserSettingsDatabase

    @Binds
    abstract fun provideVpnProfilesDatabase(appDatabase: AppDatabase): VpnProfilesDatabase

    @Binds
    abstract fun provideVpnRecentsDatabase(appDatabase: AppDatabase): VpnRecentsDatabase

    @Binds
    abstract fun provideVpnUserDatabase(appDatabase: AppDatabase): VpnUserDatabase

    @Binds
    abstract fun provideAuthDatabase(appDatabase: AppDatabase): AuthDatabase

    @Binds
    abstract fun providePublicAddressDatabase(appDatabase: AppDatabase): PublicAddressDatabase
}
