/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.profiles.usecases

import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.data.toProfileEntity
import com.protonvpn.android.telemetry.ProfilesTelemetry
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@Reusable
class DeleteProfileFromUi @Inject constructor(
    private val mainScope: CoroutineScope,
    private val profilesDao: ProfilesDao,
    private val telemetry: ProfilesTelemetry,
    private val currentUser: CurrentUser,
) {
    inner class UndoOperation(
        private val deletedProfile: Deferred<Profile?>,
        private val telemetryJob: Job,
    ) {
        operator fun invoke() {
            telemetryJob.cancel()
            mainScope.launch {
                val profile = deletedProfile.await()
                if (profile != null) {
                    profilesDao.upsert(profile.toProfileEntity())
                }
            }
        }
    }

    /**
     * Delete profile and report it to telemetry, with undo support.
     *
     * Undo must be performed no later than maxUndoDurationMs after calling this function, otherwise
     * an invalid "delete profile" event will be reported to telemetry.
     *
     * @returns an undo operation that can be used to revert the deletion.
     */
    operator fun invoke(profileId: Long, maxUndoDurationMs: Long): UndoOperation {
        val deletedProfileDeferred = mainScope.async {
            val deletedProfile = profilesDao.getProfileById(profileId)
            profilesDao.remove(profileId)
            deletedProfile
        }
        val telemetryEventJob = mainScope.launch {
            val userId = currentUser.vpnUser()?.userId
            val deletedProfile = deletedProfileDeferred.await()
            if (userId == null || deletedProfile == null) return@launch
            // Profile count after the profile has been deleted.
            val profileCount = profilesDao.getProfileCount(userId)
            delay(maxUndoDurationMs)
            telemetry.profileDeleted(deletedProfile, profileCount)
        }
        return UndoOperation(deletedProfileDeferred, telemetryEventJob)
    }
}
