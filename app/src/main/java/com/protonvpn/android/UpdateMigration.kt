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

package com.protonvpn.android

import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.logging.AppUpdateUpdated
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class UpdateMigration @Inject constructor(
    private val mainScope: CoroutineScope,
    private val isTv: Lazy<IsTvCheck>,
    private val logout: Lazy<Logout>
) {

    fun handleUpdate() {
        val oldVersionCode = Storage.getInt("VERSION_CODE")
        val newVersionCode = BuildConfig.VERSION_CODE
        Storage.saveInt("VERSION_CODE", newVersionCode)
        if (oldVersionCode != 0 && oldVersionCode != newVersionCode) {
            ProtonLogger.log(AppUpdateUpdated, "new version: " + newVersionCode)
            val strippedOldVersionCode = stripArchitecture(oldVersionCode)
            updateAmazonUi(strippedOldVersionCode)
        }
    }

    private fun updateAmazonUi(oldVersionCode: Int) {
        @SuppressWarnings("MagicNumber")
        if (oldVersionCode <= 4_06_30_00 && BuildConfig.FLAVOR == Constants.FLAVOR_AMAZON && !isTv.get().invoke()) {
            mainScope.launch {
                logout.get().invoke()
            }
        }
    }

    @SuppressWarnings("MagicNumber")
    private fun stripArchitecture(versionCode: Int) = versionCode % 100_000_000
}
