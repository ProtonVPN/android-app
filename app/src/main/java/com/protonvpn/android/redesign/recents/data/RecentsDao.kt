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

package com.protonvpn.android.redesign.recents.data

import androidx.annotation.VisibleForTesting
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.protonvpn.android.profiles.data.ProfileEntity
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.utils.DebugUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.proton.core.domain.entity.UserId

data class RecentConnectionWithIntent(
    @Embedded val recent: RecentConnectionEntity,

    // Exactly one of below will be != null
    @Relation(parentColumn = "id", entityColumn = "recentId")
    val unnamedRecent: UnnamedRecentIntentEntity?,
    @Relation(parentColumn = "profileId", entityColumn = "profileId")
    val profile: ProfileEntity?
)

@Dao
abstract class RecentsDao {

    fun getRecentsList(userId: UserId, limit: Int = -1): Flow<List<RecentConnection>> =
        getRecentsEntityList(userId, limit).map { recents ->
            recents.map { entity -> entity.toRecentConnection() }
        }

    fun getMostRecentConnection(userId: UserId): Flow<RecentConnection?> = getMostRecentConnectionEntity(userId).map {
        it?.toRecentConnection()
    }

    suspend fun getById(id: Long): RecentConnection? = getSync(id)?.toRecentConnection()

    @Query("SELECT count(id) FROM recents WHERE userId = :userId AND isPinned = 0")
    abstract fun getUnpinnedCount(userId: UserId): Flow<Int>

    @Query(
        "UPDATE recents SET isPinned = 1, lastPinnedTimestamp = :timestamp WHERE id = :id"
    )
    abstract suspend fun pin(id: Long, timestamp: Long)

    // Unpinned items get a fake lastConnectionAttemptTimestamp that is 1ms after the second most recent connection to
    // put them at the top of the recents list (outside the connection card).
    // Unless they are already the most recently connected to.
    @Query(
        """
        UPDATE recents
           SET isPinned = 0,
               lastConnectionAttemptTimestamp = MAX(
                    lastConnectionAttemptTimestamp,
                    1 + IFNULL(
                        (SELECT lastConnectionAttemptTimestamp FROM recents ORDER BY lastConnectionAttemptTimestamp DESC LIMIT -1 OFFSET 1),
                        0
                    )
               )
         WHERE id = :id
        """
    )
    abstract suspend fun unpin(id: Long)

    suspend fun insertOrUpdateForConnection(userId: UserId, connectIntent: ConnectIntent, timestamp: Long) =
        insertOrUpdateForConnection(userId, connectIntent.toData(), timestamp)

    @Query("""
         UPDATE recents
          SET lastConnectionAttemptTimestamp = :timestamp
        WHERE profileId = :profileId
    """)
    protected abstract suspend fun updateProfileRecentTimestamp(profileId: Long, timestamp: Long): Int

    @Query("""
        DELETE FROM recents WHERE id IN (
            SELECT id FROM recents
            WHERE isPinned = 0 AND userId = :userId AND id != COALESCE((
                SELECT recentId FROM defaultConnection WHERE userId = :userId LIMIT 1
            ), -1)
            ORDER BY lastConnectionAttemptTimestamp DESC
            LIMIT -1 OFFSET :max
        )
    """)
    abstract suspend fun deleteExcessUnpinnedRecents(userId: UserId, max: Int)

    @Query("DELETE FROM recents WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM recents WHERE id in (:recentIds)")
    abstract suspend fun delete(recentIds: List<Long>)

    fun getUnnamedServerRecentsForAllUsers(): Flow<List<RecentConnection>> =
        getUnnamedRecentsIntentsByTypeForAllUsers(ConnectIntentType.SPECIFIC_SERVER).map { recents ->
            recents.map { it.toRecentConnection() }
        }

    @Transaction
    protected open suspend fun insertOrUpdateForConnection(
        userId: UserId,
        connectIntentData: ConnectIntentData,
        timestamp: Long
    ) {
        val profileId = connectIntentData.profileId
        if (profileId != null) {
            insertOrUpdateProfileRecentForConnection(userId, profileId, timestamp)
        } else {
            insertOrUpdateUnnamedRecentForConnection(userId, connectIntentData, timestamp)
        }
    }

    @Transaction
    protected open suspend fun insertOrUpdateUnnamedRecentForConnection(
        userId: UserId,
        connectIntentData: ConnectIntentData,
        timestamp: Long
    ) {
        DebugUtils.debugAssert("setting overrides should be defined only for profiles") {
            connectIntentData.settingsOverrides == null
        }
        val updatedRows = updateUnnamedRecentConnectionTimestamp(
            userId,
            connectIntentData.connectIntentType,
            connectIntentData.exitCountry,
            connectIntentData.entryCountry,
            connectIntentData.city,
            connectIntentData.region,
            connectIntentData.serverId,
            connectIntentData.features,
            timestamp
        )
        if (updatedRows == 0) {
            val recentId = insert(
                RecentConnectionEntity(
                    userId = userId,
                    isPinned = false,
                    lastPinnedTimestamp = 0,
                    lastConnectionAttemptTimestamp = timestamp
                )
            )
            insert(
                UnnamedRecentIntentEntity(
                    recentId = recentId,
                    connectIntentData = connectIntentData,
                )
            )
        }
    }

    @Transaction
    protected open suspend fun insertOrUpdateProfileRecentForConnection(
        userId: UserId,
        profileId: Long,
        timestamp: Long
    ) {
        val updatedRows = updateProfileRecentTimestamp(profileId, timestamp)
        if (updatedRows == 0) {
            insertProfileRecentWhenProfileExists(
                userId = userId,
                profileId = profileId,
                timestamp = timestamp
            )
        }
    }

    @Query("""
        INSERT INTO recents (userId, profileId, isPinned, lastPinnedTimestamp, lastConnectionAttemptTimestamp)
        SELECT :userId, :profileId, 0, 0, :timestamp
        WHERE EXISTS (SELECT 1 FROM profiles WHERE profileId = :profileId)
    """)
    protected abstract suspend fun insertProfileRecentWhenProfileExists(userId: UserId, profileId: Long, timestamp: Long): Long

    @VisibleForTesting
    @Transaction
    @Query("""
        SELECT * FROM recents
         WHERE userId = :userId
        ORDER BY isPinned DESC,
                 CASE WHEN isPinned THEN lastPinnedTimestamp
                                    ELSE -lastConnectionAttemptTimestamp
                 END ASC
         LIMIT :limit
        """)
    abstract fun getRecentsEntityList(userId: UserId, limit: Int = -1): Flow<List<RecentConnectionWithIntent>>

    @Transaction
    @Query("SELECT * FROM recents WHERE userId = :userId ORDER BY lastConnectionAttemptTimestamp DESC LIMIT 1")
    protected abstract fun getMostRecentConnectionEntity(userId: UserId): Flow<RecentConnectionWithIntent?>

    @Transaction
    @Query("""
        SELECT * FROM recents
        WHERE (id IN (SELECT recentId FROM unnamedRecentsIntents WHERE connectIntentType = :type))
    """)
    protected abstract fun getUnnamedRecentsIntentsByTypeForAllUsers(
        type: ConnectIntentType
    ): Flow<List<RecentConnectionWithIntent>>

    @Transaction
    @Query("SELECT * FROM recents WHERE id = :id")
    protected abstract suspend fun getSync(id: Long): RecentConnectionWithIntent?

    @Insert
    protected abstract suspend fun insert(recent: RecentConnectionEntity): Long

    @Insert
    protected abstract suspend fun insert(unnamedRecentIntentEntity: UnnamedRecentIntentEntity)

    @Query("""
        UPDATE recents
          SET lastConnectionAttemptTimestamp = :timestamp
        WHERE userId = :userId 
          AND (id IN (SELECT recentId FROM unnamedRecentsIntents
                       WHERE connectIntentType = :connectIntentType
                         AND exitCountry IS :exitCountry
                         AND entryCountry IS :entryCountry
                         AND city IS :city
                         AND region IS :region
                         AND serverId IS :serverId
                         AND features = :features
                     )
              )
        """)
    protected abstract suspend fun updateUnnamedRecentConnectionTimestamp(
        userId: UserId,
        connectIntentType: ConnectIntentType,
        exitCountry: String?,
        entryCountry: String?,
        city: String?,
        region: String?,
        serverId: String?,
        features: Set<ServerFeature>,
        timestamp: Long,
    ): Int
}
