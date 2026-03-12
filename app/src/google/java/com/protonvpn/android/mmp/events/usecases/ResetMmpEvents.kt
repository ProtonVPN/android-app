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

import com.protonvpn.android.mmp.events.data.MmpEventsDao
import com.protonvpn.android.mmp.referrer.data.MmpReferrerStorage
import dagger.Reusable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Reusable
class ResetMmpEvents @Inject constructor(
    private val mmpEventsDao: MmpEventsDao,
    private val mmpReferrerStorage: MmpReferrerStorage,
) {

    suspend operator fun invoke() = withContext(context = NonCancellable) {
        mmpEventsDao.deleteAll()

        mmpReferrerStorage.updateMmpReferrer { localMmpReferrer ->
            localMmpReferrer.copy(sessionStartTimestamp = null)
        }
    }

}
