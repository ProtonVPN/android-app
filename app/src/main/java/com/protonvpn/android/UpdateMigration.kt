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

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.logging.AppUpdateUpdated
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.vpn.CertificateData
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.utils.Storage
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@Reusable
class UpdateMigration @Inject constructor(
    private val mainScope: CoroutineScope,
    private val appPrefs: AppFeaturesPrefs,
    private val uiStateStorage: UiStateStorage,
) {

    fun handleUpdate() {
        val oldVersionCode = Storage.getInt("VERSION_CODE")
        val newVersionCode = BuildConfig.VERSION_CODE
        Storage.saveInt("VERSION_CODE", newVersionCode)
        if (oldVersionCode != 0 && oldVersionCode != newVersionCode) {
            ProtonLogger.log(AppUpdateUpdated, "new version: " + newVersionCode)
            val strippedOldVersionCode = stripArchitecture(oldVersionCode)
            clearCertificateData(strippedOldVersionCode)
            promoteProfiles(strippedOldVersionCode)
            whatsNewWidget(strippedOldVersionCode)
        }
    }

    @SuppressWarnings("MagicNumber")
    private fun clearCertificateData(oldVersionCode: Int) {
        if (oldVersionCode <= 5_03_50_00) {
            Storage.save(null, CertificateData::class.java)
        }
    }

    @SuppressWarnings("MagicNumber")
    private fun promoteProfiles(oldVersionCode: Int) {
        if (oldVersionCode <= 5_07_80_00) {
            mainScope.launch {
                uiStateStorage.update { it.copy(shouldPromoteProfiles = true) }
            }
        }
        if (oldVersionCode in 5_07_93_00.. 5_08_00_00) {
            // These users have already seen the info dialog as part of shouldPromoteProfiles.
            mainScope.launch {
                uiStateStorage.update { it.copy(hasShownProfilesInfo = true) }
            }
        }
    }

    @SuppressWarnings("MagicNumver")
    private fun whatsNewWidget(oldVersionCode: Int) {
        if (oldVersionCode <= 5_08_85_00) {
            appPrefs.showWhatsNew = true
        }
    }

    @SuppressWarnings("MagicNumber")
    private fun stripArchitecture(versionCode: Int) = versionCode % 100_000_000
}
