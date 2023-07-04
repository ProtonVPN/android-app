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

package com.protonvpn.android.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.TypeConverters
import com.protonvpn.android.appconfig.periodicupdates.PeriodicCallInfo
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdatesDatabase
import com.protonvpn.android.auth.data.VpnUserDatabase
import com.protonvpn.android.auth.data.VpnUser
import me.proton.core.account.data.db.AccountConverters
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.account.data.entity.AccountMetadataEntity
import me.proton.core.account.data.entity.SessionDetailsEntity
import me.proton.core.account.data.entity.SessionEntity
import me.proton.core.challenge.data.db.ChallengeConverters
import me.proton.core.challenge.data.db.ChallengeDatabase
import me.proton.core.challenge.data.entity.ChallengeFrameEntity
import me.proton.core.crypto.android.keystore.CryptoConverters
import me.proton.core.data.room.db.BaseDatabase
import me.proton.core.data.room.db.CommonConverters
import me.proton.core.featureflag.data.db.FeatureFlagDatabase
import me.proton.core.featureflag.data.entity.FeatureFlagEntity
import me.proton.core.humanverification.data.db.HumanVerificationConverters
import me.proton.core.humanverification.data.db.HumanVerificationDatabase
import me.proton.core.humanverification.data.entity.HumanVerificationEntity
import me.proton.core.key.data.db.KeySaltDatabase
import me.proton.core.key.data.entity.KeySaltEntity
import me.proton.core.notification.data.local.db.NotificationConverters
import me.proton.core.notification.data.local.db.NotificationDatabase
import me.proton.core.notification.data.local.db.NotificationEntity
import me.proton.core.observability.data.db.ObservabilityDatabase
import me.proton.core.observability.data.entity.ObservabilityEventEntity
import me.proton.core.payment.data.local.db.PaymentDatabase
import me.proton.core.payment.data.local.entity.GooglePurchaseEntity
import me.proton.core.push.data.local.db.PushConverters
import me.proton.core.push.data.local.db.PushDatabase
import me.proton.core.push.data.local.db.PushEntity
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserConverters
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.user.data.entity.AddressEntity
import me.proton.core.user.data.entity.AddressKeyEntity
import me.proton.core.user.data.entity.UserEntity
import me.proton.core.user.data.entity.UserKeyEntity
import me.proton.core.usersettings.data.db.OrganizationDatabase
import me.proton.core.usersettings.data.db.UserSettingsConverters
import me.proton.core.usersettings.data.db.UserSettingsDatabase
import me.proton.core.usersettings.data.entity.OrganizationEntity
import me.proton.core.usersettings.data.entity.OrganizationKeysEntity
import me.proton.core.usersettings.data.entity.UserSettingsEntity

@Database(
    entities = [
        // account-data
        AccountEntity::class,
        AccountMetadataEntity::class,
        SessionEntity::class,
        SessionDetailsEntity::class,
        // challenge
        ChallengeFrameEntity::class,
        // user-data
        UserEntity::class,
        UserKeyEntity::class,
        AddressEntity::class,
        AddressKeyEntity::class,
        // feature flags
        FeatureFlagEntity::class,
        // key-data
        KeySaltEntity::class,
        // human-verification
        HumanVerificationEntity::class,
        // user-settings
        UserSettingsEntity::class,
        // organization
        OrganizationEntity::class,
        OrganizationKeysEntity::class,
        // purchase
        GooglePurchaseEntity::class,
        // observability
        ObservabilityEventEntity::class,
        // notification
        NotificationEntity::class,
        // push
        PushEntity::class,
        // vpn
        PeriodicCallInfo::class,
        VpnUser::class
    ],
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 14, to = 15),
    ],
    version = AppDatabase.version,
    exportSchema = true
)
@TypeConverters(
    AccountConverters::class,
    ChallengeConverters::class,
    CommonConverters::class,
    CryptoConverters::class,
    HumanVerificationConverters::class,
    UserConverters::class,
    UserSettingsConverters::class,
    NotificationConverters::class,
    PushConverters::class,
)
abstract class AppDatabase :
    BaseDatabase(),
    AccountDatabase,
    AddressDatabase,
    ChallengeDatabase,
    FeatureFlagDatabase,
    HumanVerificationDatabase,
    KeySaltDatabase,
    OrganizationDatabase,
    PeriodicUpdatesDatabase,
    UserDatabase,
    UserSettingsDatabase,
    PaymentDatabase,
    VpnUserDatabase,
    ObservabilityDatabase,
    NotificationDatabase,
    PushDatabase {

    companion object {
        const val version = 17

        private val migrations = listOf(
            DatabaseMigrations.MIGRATION_1_2,
            DatabaseMigrations.MIGRATION_2_3,
            DatabaseMigrations.MIGRATION_4_5,
            DatabaseMigrations.MIGRATION_5_6,
            DatabaseMigrations.MIGRATION_7_8,
            DatabaseMigrations.MIGRATION_8_9,
            DatabaseMigrations.MIGRATION_9_10,
            DatabaseMigrations.MIGRATION_10_11,
            DatabaseMigrations.MIGRATION_11_12,
            DatabaseMigrations.MIGRATION_12_13,
            DatabaseMigrations.MIGRATION_13_14,
            DatabaseMigrations.MIGRATION_15_16,
            DatabaseMigrations.MIGRATION_16_17,
        )

        fun Builder<AppDatabase>.buildDatabase(): AppDatabase {
            migrations.forEach { addMigrations(it) }
            return build()
        }
    }
}
