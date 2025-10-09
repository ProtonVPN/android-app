/*
 * Copyright (c) 2025 Proton AG
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
package com.protonvpn.android.vpn.usecases

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.redesign.recents.data.RecentsDao
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.Reusable
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TRANSIENT_LIFE_MS = 60 * 60_000L
private const val MAX_TRANSIENTS = 3
private const val MAX_RECENTS = 15
private const val MAX_MUST_HAVES = 35

fun interface GetTruncationMustHaveIDs {
    suspend operator fun invoke(): Set<String> =
        invoke(MAX_RECENTS, MAX_MUST_HAVES)

    suspend operator fun invoke(
        maxRecents: Int,
        maxMustHaves: Int,
    ): Set<String>
}

/**
 * Track IDs of servers that have been chosen by the user but not yet connected to.
 * This could be a server selected while editing a profile or result of search.
 */
@Singleton
class TransientMustHaves @Inject constructor(
    @WallClock private val now: () -> Long
) {
    private data class Entry(val id: String, val timestamp: Long)

    private val serverIds = ArrayList<Entry>()

    fun add(serverId: String) {
        with(serverIds) {
            if (size == MAX_TRANSIENTS)
                removeAt(size - 1)
            add(0, Entry(serverId, now()))
        }
    }

    fun getAll(): List<String> {
        val timeThresholdMs = now() - TRANSIENT_LIFE_MS
        return serverIds.filter { it.timestamp > timeThresholdMs }.map { it.id }
    }
}

@Reusable
class GetTruncationMustHaveIDsImpl @Inject constructor(
    private val currentUser: CurrentUser,
    private val profilesDao: ProfilesDao,
    private val recentsDao: RecentsDao,
    private val recentsManager: RecentsManager,
    private val transientMustHaves: TransientMustHaves,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
) : GetTruncationMustHaveIDs {

    override suspend fun invoke(
        maxRecents: Int,
        maxMustHaves: Int,
    ): Set<String> {
        val userId = currentUser.vpnUser()?.userId
        val regularRecentIDs = userId?.let {
            recentsDao.getRecentsList(userId).first().mapNotNull { it.connectIntent.serverId }
        } ?: emptyList()
        val tvRecentIDs = recentsManager.getAllRecentServers().map { it.serverId }
        val profilesServerIDs = userId?.let {
            profilesDao.getProfilesOrderedByConnectionRecency(userId).first().mapNotNull { it.connectIntent.serverId }
        } ?: emptyList()
        return getMustHaveIDs(
            currentServerID = vpnStatusProviderUI.connectingToServer?.serverId,
            recentsServerIDs = regularRecentIDs + tvRecentIDs,
            profilesServerIDs = profilesServerIDs,
            transientIDs = transientMustHaves.getAll(),
            maxRecents = maxRecents,
            maxMustHaves = maxMustHaves,
        )
    }

    companion object {
        @VisibleForTesting
        fun getMustHaveIDs(
            currentServerID: String?,
            recentsServerIDs: List<String>,
            profilesServerIDs: List<String>,
            transientIDs: List<String>,
            maxRecents: Int,
            maxMustHaves: Int,
        ) : Set<String> = LinkedHashSet<String>().apply {
            currentServerID?.let { add(it) }
            val (recentIDs, recentIDsRest) = LinkedHashSet(
                recentsServerIDs.filter { it != currentServerID && !transientIDs.contains(it) }
            ).let {
                it.take(maxRecents) to it.drop(maxRecents)
            }
            addAll(transientIDs)
            addAll(recentIDs)
            addAll(profilesServerIDs)
            // If there are still slots left, add remaining recent servers
            addAll(recentIDsRest)
        }.take(maxMustHaves).toSet()
    }
}

private val ConnectIntent.serverId get() = (this as? ConnectIntent.Server)?.serverId
