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

package com.protonvpn.android.mmp.referrer.usecases

import com.protonvpn.android.mmp.IsMmpFeatureFlagEnabled
import com.protonvpn.android.mmp.referrer.MmpReferrer
import com.protonvpn.android.mmp.referrer.data.MmpReferrerStorage
import dagger.Reusable
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Reusable
class GetMmpReferrer @Inject constructor(
    private val isMmpEnabled: IsMmpFeatureFlagEnabled,
    private val mmpReferrerStorage: MmpReferrerStorage,
    private val fetchMmpReferrer: FetchMmpReferrer,
) {

    suspend operator fun invoke(): MmpReferrer? {
        if (!isMmpEnabled()) return null

        return mmpReferrerStorage.getMmpReferrer() ?: withContext(context = NonCancellable) {
            fetchMmpReferrer.getMmpReferrer()?.also { remoteMmpReferrer ->
                mmpReferrerStorage.setMmpReferrer(mmpReferrer = remoteMmpReferrer)
            }
        }
    }

}
