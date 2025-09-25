/*
 * Copyright (c) 2021 Proton AG
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

import androidx.annotation.VisibleForTesting
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.TypeConverters
import com.protonvpn.android.appconfig.periodicupdates.PeriodicCallInfo
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdatesDatabase
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.data.VpnUserDatabase
import com.protonvpn.android.profiles.data.ProfileEntity
import com.protonvpn.android.profiles.data.VpnProfilesDatabase
import com.protonvpn.android.redesign.recents.data.DefaultConnectionEntity
import com.protonvpn.android.redesign.recents.data.RecentConnectionEntity
import com.protonvpn.android.redesign.recents.data.RecentsTypeConverters
import com.protonvpn.android.redesign.recents.data.UnnamedRecentIntentEntity
import com.protonvpn.android.redesign.recents.data.VpnRecentsDatabase
import me.proton.core.account.data.db.AccountConverters
import me.proton.core.account.data.db.AccountDatabase
import me.proton.core.account.data.entity.AccountEntity
import me.proton.core.account.data.entity.AccountMetadataEntity
import me.proton.core.account.data.entity.SessionDetailsEntity
import me.proton.core.account.data.entity.SessionEntity
import me.proton.core.auth.data.db.AuthConverters
import me.proton.core.auth.data.db.AuthDatabase
import me.proton.core.auth.data.entity.AuthDeviceEntity
import me.proton.core.auth.data.entity.DeviceSecretEntity
import me.proton.core.auth.data.entity.MemberDeviceEntity
import me.proton.core.challenge.data.db.ChallengeConverters
import me.proton.core.challenge.data.db.ChallengeDatabase
import me.proton.core.challenge.data.entity.ChallengeFrameEntity
import me.proton.core.crypto.android.keystore.CryptoConverters
import me.proton.core.data.room.db.BaseDatabase
import me.proton.core.data.room.db.CommonConverters
import me.proton.core.eventmanager.data.db.EventManagerConverters
import me.proton.core.eventmanager.data.db.EventMetadataDatabase
import me.proton.core.eventmanager.data.entity.EventMetadataEntity
import me.proton.core.featureflag.data.db.FeatureFlagDatabase
import me.proton.core.featureflag.data.entity.FeatureFlagEntity
import me.proton.core.humanverification.data.db.HumanVerificationConverters
import me.proton.core.humanverification.data.db.HumanVerificationDatabase
import me.proton.core.humanverification.data.entity.HumanVerificationEntity
import me.proton.core.key.data.db.KeySaltDatabase
import me.proton.core.key.data.db.PublicAddressDatabase
import me.proton.core.key.data.entity.KeySaltEntity
import me.proton.core.key.data.entity.PublicAddressEntity
import me.proton.core.key.data.entity.PublicAddressInfoEntity
import me.proton.core.key.data.entity.PublicAddressKeyDataEntity
import me.proton.core.key.data.entity.PublicAddressKeyEntity
import me.proton.core.notification.data.local.db.NotificationConverters
import me.proton.core.notification.data.local.db.NotificationDatabase
import me.proton.core.notification.data.local.db.NotificationEntity
import me.proton.core.observability.data.db.ObservabilityDatabase
import me.proton.core.observability.data.entity.ObservabilityEventEntity
import me.proton.core.payment.data.local.db.PaymentDatabase
import me.proton.core.payment.data.local.entity.GooglePurchaseEntity
import me.proton.core.payment.data.local.entity.PurchaseEntity
import me.proton.core.push.data.local.db.PushConverters
import me.proton.core.push.data.local.db.PushDatabase
import me.proton.core.push.data.local.db.PushEntity
import me.proton.core.telemetry.data.db.TelemetryDatabase
import me.proton.core.telemetry.data.entity.TelemetryEventEntity
import me.proton.core.user.data.db.AddressDatabase
import me.proton.core.user.data.db.UserConverters
import me.proton.core.user.data.db.UserDatabase
import me.proton.core.user.data.entity.AddressEntity
import me.proton.core.user.data.entity.AddressKeyEntity
import me.proton.core.user.data.entity.UserEntity
import me.proton.core.user.data.entity.UserKeyEntity
import me.proton.core.userrecovery.data.db.DeviceRecoveryDatabase
import me.proton.core.userrecovery.data.entity.RecoveryFileEntity
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
        PublicAddressEntity::class,
        PublicAddressKeyEntity::class,
        PublicAddressInfoEntity::class,
        PublicAddressKeyDataEntity::class,
        // human-verification
        HumanVerificationEntity::class,
        // user-settings
        UserSettingsEntity::class,
        // organization
        OrganizationEntity::class,
        OrganizationKeysEntity::class,
        // purchase
        GooglePurchaseEntity::class,
        PurchaseEntity::class,
        // observability
        ObservabilityEventEntity::class,
        // notification
        NotificationEntity::class,
        // push
        PushEntity::class,
        // telemetry
        TelemetryEventEntity::class,
        // event-manager
        EventMetadataEntity::class,
        // user-recovery
        RecoveryFileEntity::class,
        // auth-data
        DeviceSecretEntity::class,
        AuthDeviceEntity::class,
        MemberDeviceEntity::class,
        // vpn
        PeriodicCallInfo::class,
        ProfileEntity::class,
        RecentConnectionEntity::class,
        UnnamedRecentIntentEntity::class,
        DefaultConnectionEntity::class,
        VpnUser::class
    ],
    autoMigrations = [
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 33, to = 34),
        AutoMigration(from = 37, to = 38),
        AutoMigration(from = 39, to = 40),
        AutoMigration(from = 40, to = 41),
        AutoMigration(from = 42, to = 43),
        AutoMigration(from = 45, to = 46, spec = DatabaseMigrations.AutoMigration45to46::class),
        AutoMigration(from = 49, to = 50),
    ],
    version = AppDatabase.version,
    exportSchema = true
)
@TypeConverters(
    // Core
    AccountConverters::class,
    ChallengeConverters::class,
    CommonConverters::class,
    CryptoConverters::class,
    HumanVerificationConverters::class,
    UserConverters::class,
    UserSettingsConverters::class,
    NotificationConverters::class,
    PushConverters::class,
    EventManagerConverters::class,
    AuthConverters::class,
    // Vpn
    RecentsTypeConverters::class,
)
abstract class AppDatabase :
    BaseDatabase(),
    AccountDatabase,
    AddressDatabase,
    ChallengeDatabase,
    DeviceRecoveryDatabase,
    EventMetadataDatabase,
    FeatureFlagDatabase,
    HumanVerificationDatabase,
    KeySaltDatabase,
    NotificationDatabase,
    ObservabilityDatabase,
    OrganizationDatabase,
    PaymentDatabase,
    PeriodicUpdatesDatabase,
    PublicAddressDatabase,
    PushDatabase,
    TelemetryDatabase,
    UserDatabase,
    UserSettingsDatabase,
    VpnProfilesDatabase,
    VpnRecentsDatabase,
    VpnUserDatabase,
    AuthDatabase {

    companion object {
        const val version = 51

        @VisibleForTesting
        val migrations = listOf(
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
            DatabaseMigrations.MIGRATION_17_18,
            DatabaseMigrations.MIGRATION_18_19,
            DatabaseMigrations.MIGRATION_19_20,
            DatabaseMigrations.MIGRATION_20_21,
            DatabaseMigrations.MIGRATION_21_22,
            DatabaseMigrations.MIGRATION_22_23,
            DatabaseMigrations.MIGRATION_23_24,
            DatabaseMigrations.MIGRATION_25_26,
            DatabaseMigrations.MIGRATION_26_27,
            DatabaseMigrations.MIGRATION_27_28,
            DatabaseMigrations.MIGRATION_28_29,
            DatabaseMigrations.MIGRATION_29_30,
            DatabaseMigrations.MIGRATION_30_31,
            DatabaseMigrations.MIGRATION_31_32,
            DatabaseMigrations.MIGRATION_32_33,
            DatabaseMigrations.MIGRATION_34_35,
            DatabaseMigrations.MIGRATION_35_36,
            DatabaseMigrations.MIGRATION_36_37,
            DatabaseMigrations.MIGRATION_38_39,
            DatabaseMigrations.MIGRATION_41_42,
            DatabaseMigrations.MIGRATION_43_44,
            DatabaseMigrations.MIGRATION_44_45,
            DatabaseMigrations.MIGRATION_46_47,
            DatabaseMigrations.MIGRATION_47_48,
            DatabaseMigrations.MIGRATION_48_49,
            DatabaseMigrations.MIGRATION_50_51,
        )

        fun Builder<AppDatabase>.buildDatabase(): AppDatabase {
            migrations.forEach { addMigrations(it) }
            return build()
        }
    }
}
