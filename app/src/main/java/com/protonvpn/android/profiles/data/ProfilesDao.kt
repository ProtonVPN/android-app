/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.profiles.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.protonvpn.android.netshield.NetShieldProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.proton.core.domain.entity.UserId

@Dao
abstract class ProfilesDao {

    fun getProfiles(userId: UserId) : Flow<List<Profile>> = getEntities(userId).map { entities ->
        entities.map { it.toProfile() }
    }

    suspend fun getProfileById(id: Long) : Profile? = getEntityById(id)?.toProfile()
    fun getProfileByIdFlow(id: Long) : Flow<Profile?> = getEntityByIdFlow(id).map { it?.toProfile() }

    @Query("SELECT * FROM profiles WHERE userId = :userId ORDER BY createdAt")
    protected abstract fun getEntities(userId: UserId): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE profileId = :id")
    protected abstract fun getEntityByIdFlow(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE profileId = :id")
    protected abstract suspend fun getEntityById(id: Long): ProfileEntity?

    @Query("SELECT count(profileId) FROM profiles WHERE userId = :userId")
    abstract fun getCount(userId: UserId): Int

    @Query("UPDATE profiles SET netShield = :netShield WHERE profileId = :profileId")
    abstract suspend fun updateNetShield(profileId: Long, netShield: NetShieldProtocol)

    @Upsert
    abstract suspend fun upsert(profile: ProfileEntity): Long

    @Insert
    abstract suspend fun insert(profiles: List<ProfileEntity>)

    @Query("DELETE FROM profiles WHERE profileId = :id")
    abstract suspend fun remove(id: Long)

    @Transaction
    open suspend fun prepopulate(userId: UserId, profiles: () -> List<ProfileEntity>) {
        if (getCount(userId) == 0)
            insert(profiles())
    }

    @Query("SELECT COUNT(*) FROM profiles WHERE userId = :userId")
    abstract suspend fun getProfileCount(userId: UserId): Int
}
