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

package com.protonvpn.android.ui.planupgrade

import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.tv.IsTvCheck
import dagger.Reusable
import javax.inject.Inject

@Reusable
class IsInAppUpgradeAllowedUseCase @Inject constructor(
    private val currentUser: CurrentUser,
    private val purchaseEnabled: CachedPurchaseEnabled,
    private val isTv: IsTvCheck
) {
    operator fun invoke() = !isTv() && purchaseEnabled() && currentUser.vpnUserCached()?.let { user ->
        user.subscribed == 0 && user.credit == 0
    } ?: false
}