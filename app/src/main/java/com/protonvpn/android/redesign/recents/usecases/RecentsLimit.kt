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

import com.protonvpn.android.redesign.recents.data.RecentsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_UNPINNED_RECENTS = 6

@Singleton
class RecentsLimit @Inject constructor(
    mainScope: CoroutineScope,
    recentsDao: RecentsDao
) {
    init {
        recentsDao.getUnpinnedCount()
            .onEach { count ->
                // Note: this is doable with a trigger, might also
                if (count > MAX_UNPINNED_RECENTS) {
                    recentsDao.deleteExcessUnpinnedRecents(MAX_UNPINNED_RECENTS)
                }
            }
            .launchIn(mainScope)
    }
}
