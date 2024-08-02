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

package com.protonvpn.android.servers

import com.protonvpn.android.auth.usecase.CurrentUser
import dagger.Reusable
import me.proton.core.featureflag.domain.ExperimentalProtonFeatureFlag
import me.proton.core.featureflag.domain.FeatureFlagManager
import me.proton.core.featureflag.domain.entity.FeatureId
import javax.inject.Inject

// VPNAND-1865: remove
@OptIn(ExperimentalProtonFeatureFlag::class)
@Reusable
class IsImmutableServerListEnabled @Inject constructor(
    private val currentUser: CurrentUser,
    private val featureFlagManager: FeatureFlagManager
) {

    suspend operator fun invoke(): Boolean =
        featureFlagManager.getValue(currentUser.user()?.userId, FeatureId(FEATURE_ID))

    companion object {
        private const val FEATURE_ID = "AndroidImmutableServerList"
    }
}
