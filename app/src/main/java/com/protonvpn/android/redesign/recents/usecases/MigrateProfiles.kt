/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.recents.usecases

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.toLog
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.RecentConnectionEntity
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.runCatchingCheckedExceptions
import dagger.Reusable
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.EnumSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MigrateProfilesOnStart @Inject constructor(
    mainScope: CoroutineScope,
    migrateProfiles: MigrateProfiles,
    profileManager: ProfileManager,
    userSettings: EffectiveCurrentUserSettings
) {
    init {
        if (profileManager.getSavedProfiles().any { !it.isPreBakedProfile }) {
            mainScope.launch {
                migrateProfiles(userSettings.effectiveSettings.first())
            }
        }
    }
}

@Reusable
class MigrateProfiles @Inject constructor(
    private val profileManager: ProfileManager,
    private val serverManager: ServerManager,
    private val recentsDao: RecentsDao,
    private val currentUser: CurrentUser,
) {
    suspend operator fun invoke(settings: LocalUserSettings) {
        // Wait for the first logged in user.
        val vpnUser = currentUser.vpnUserFlow.filterNotNull().first()
        serverManager.ensureLoaded()
        val isGlobalSecureCoreEnabled = settings.secureCore

        val userId = vpnUser.userId
        // Only custom profiles are migrated to pinned recents. The default profile is handled below.
        val customProfiles = profileManager.getSavedProfiles().filterNot { it.isPreBakedProfile }
        customProfiles.forEach {
            ProtonLogger.logCustom(LogCategory.APP_UPDATE, "Profile to migrate: ${it.toLog(settings)}")
        }

        suspend {
            val defaultProfileConnectIntent =
                serverManager.defaultConnection.toConnectIntent(serverManager, isGlobalSecureCoreEnabled)
            val connectIntents = customProfiles.mapNotNullTo(LinkedHashSet()) { profile ->
                val migrated = profile.toConnectIntent(serverManager, isGlobalSecureCoreEnabled)
                if (migrated != null) {
                    ProtonLogger.logCustom(LogCategory.APP_UPDATE, "Profile migrated to: ${migrated.toLog()}")
                } else {
                    ProtonLogger.logCustom(LogCategory.APP_UPDATE, "Can't migrate ${profile.toLog(settings)}")
                }
                migrated
            }
            val count = connectIntents.size
            val recentEntities = connectIntents.mapIndexed { index, connectIntent ->
                // Generate fake timestamps to give recents order. Newest are at the top, default is first.
                val pinnedTimestamp = if (connectIntent == defaultProfileConnectIntent) 0 else count - index
                val connectedTimestamp = index
                RecentConnectionEntity(
                    userId = userId,
                    isPinned = true,
                    lastConnectionAttemptTimestamp = connectedTimestamp.toLong(),
                    lastPinnedTimestamp = pinnedTimestamp.toLong(),
                    connectIntentData = connectIntent.toData()
                )
            }

            ProtonLogger.logCustom(LogCategory.APP_UPDATE, "Migration generated ${recentEntities.size}")
            recentsDao.insert(recentEntities)

            // Put the default profile in recent card (it could be the default fastest profile).
            val connectionCardIntent = defaultProfileConnectIntent ?: ConnectIntent.Default
            val mostRecentFakeTimestamp = recentEntities.size.toLong()
            recentsDao.insertOrUpdateForConnection(userId, connectionCardIntent, mostRecentFakeTimestamp)
            ProtonLogger.logCustom(
                LogCategory.APP_UPDATE,
                "Successfully migrated ${customProfiles.size} profiles to ${recentEntities.size} recents"
            )
        }.runCatchingCheckedExceptions { e ->
            ProtonLogger.logCustom(LogCategory.APP, "Profile migration failed: $e")
            Sentry.captureException(e)
        }

        profileManager.deleteSavedProfiles()
    }
}

// TODO: make it private and switch to using ServerManager2
@Deprecated("Don't use it outside the migration code")
fun Profile.toConnectIntent(
    serverManager: ServerManager,
    isGlobalSecureCoreEnabled: Boolean
): ConnectIntent? {
    val noFeatures = EnumSet.noneOf(ServerFeature::class.java)
    val isEffectivelySecureCore = isSecureCore ?: isGlobalSecureCoreEnabled
    return when {
        isPreBakedProfile -> if (isEffectivelySecureCore) {
            ConnectIntent.SecureCore(CountryId.fastest, CountryId.fastest)
        } else {
            ConnectIntent.FastestInCountry(CountryId.fastest, noFeatures)
        }
        isEffectivelySecureCore && country.isNotBlank()-> {
            val entryCountry = when {
                wrapper.isFastestInCountry || wrapper.isRandomInCountry -> CountryId.fastest
                wrapper.serverId != null ->
                    CountryId(requireNotNull(serverManager.getServerById(wrapper.serverId)).entryCountry)
                else -> CountryId.fastest
            }
            ConnectIntent.SecureCore(CountryId(country), entryCountry)
        }
        wrapper.isFastestInCountry || wrapper.isRandomInCountry ->
            ConnectIntent.FastestInCountry(CountryId(wrapper.country), noFeatures)
        !directServerId.isNullOrBlank() -> {
            val server = serverManager.getServerById(directServerId!!)
            when {
                server == null -> null
                server.gatewayName != null ->
                    ConnectIntent.Gateway(server.gatewayName!!, serverId = server.serverId)
                else ->
                    ConnectIntent.Server(server.serverId, noFeatures)
            }
        }
        else ->  null
    }
}
