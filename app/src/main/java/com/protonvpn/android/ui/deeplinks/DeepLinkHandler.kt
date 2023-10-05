/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.ui.deeplinks

import android.net.Uri
import com.protonvpn.android.utils.UserPlanManager
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.equalsNoCase
import javax.inject.Inject

private const val PROTONVPN_SCHEME = "protonvpn"

@Reusable
class DeepLinkHandler @Inject constructor(
    private val mainScope: CoroutineScope,
    private val userPlanManager: UserPlanManager,
) {
    fun processDeepLink(uri: Uri) {
        if (uri.scheme?.equalsNoCase(PROTONVPN_SCHEME) == true) {
            when(uri.host?.lowercase()) {
                "refresh-account" -> refreshVpnInfo()
                else -> Unit
            }
        }
    }

    private fun refreshVpnInfo() {
        mainScope.launch {
            userPlanManager.refreshVpnInfo()
        }
    }
}
