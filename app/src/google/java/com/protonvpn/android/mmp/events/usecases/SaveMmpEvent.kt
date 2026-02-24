/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.mmp.events.usecases

import com.protonvpn.android.di.WallClock
import com.protonvpn.android.mmp.IsMmpFeatureFlagEnabled
import com.protonvpn.android.mmp.events.MmpEvent
import com.protonvpn.android.mmp.events.MmpEventType
import com.protonvpn.android.mmp.events.data.MmpEventsDao
import com.protonvpn.android.mmp.events.data.toEntity
import com.protonvpn.android.mmp.referrer.data.MmpReferrerStorage
import com.protonvpn.android.mmp.referrer.usecases.GetMmpReferrer
import dagger.Reusable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Reusable
class SaveMmpEvent @Inject constructor(
    private val isMmpEnabled: IsMmpFeatureFlagEnabled,
    private val mmpEventsDao: MmpEventsDao,
    private val getMmpReferrer: GetMmpReferrer,
    private val mmpReferrerStorage: MmpReferrerStorage,
    @param:WallClock private val now: () -> Long,
) {

    suspend operator fun invoke(
        eventType: MmpEventType,
        isSessionRestartRequired: Boolean = false,
    ) = withContext(context = NonCancellable) {
        if (!isMmpEnabled()) return@withContext

        if (isSessionRestartRequired) {
            mmpReferrerStorage.updateMmpReferrer { localMmpReferrer ->
                localMmpReferrer.copy(sessionStartTimestamp = null)
            }
        }

        val mmpEvent = MmpEvent(
            timestamp = now().truncateToDayMillis(),
            sessionStartTimestamp = getMmpReferrer()?.sessionStartTimestamp?.truncateToDayMillis(),
            eventType = eventType,
        )

        mmpEventsDao.insert(entity = mmpEvent.toEntity())
    }

    private fun Long.truncateToDayMillis() = this - (this % ONE_DAY_MILLIS)

    private companion object {

        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1_000

    }

}
