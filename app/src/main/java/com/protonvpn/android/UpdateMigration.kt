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
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.logging.AppUpdateUpdated
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.vpn.CertificateData
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.onboarding.OnboardingTelemetry
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.Storage
import dagger.Lazy
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@Reusable
class UpdateMigration @Inject constructor(
    private val mainScope: CoroutineScope,
    private val localUserSettings: Lazy<CurrentUserLocalSettingsManager>,
    private val isTv: Lazy<IsTvCheck>,
    private val logout: Lazy<Logout>,
    private val onboardingTelemetry: Lazy<OnboardingTelemetry>,
    private val appFeaturesPrefs: Lazy<AppFeaturesPrefs>
) {

    fun handleUpdate() {
        val oldVersionCode = Storage.getInt("VERSION_CODE")
        val newVersionCode = BuildConfig.VERSION_CODE
        Storage.saveInt("VERSION_CODE", newVersionCode)
        if (oldVersionCode != 0 && oldVersionCode != newVersionCode) {
            ProtonLogger.log(AppUpdateUpdated, "new version: " + newVersionCode)
            val strippedOldVersionCode = stripArchitecture(oldVersionCode)
            updateAmazonUi(strippedOldVersionCode)
            updateOnboardingTelemetry(strippedOldVersionCode)
            enableWhatsNew(strippedOldVersionCode)
            updateNetShieldValue(strippedOldVersionCode)
            clearCertificateData(strippedOldVersionCode)
        }
    }

    private fun updateNetShieldValue(oldVersionCode: Int) {
        if (oldVersionCode <= 5_00_00_00) {
            mainScope.launch {
                if (localUserSettings.get().rawCurrentUserSettingsFlow.first().netShield == NetShieldProtocol.ENABLED) {
                    localUserSettings.get().updateNetShield(NetShieldProtocol.ENABLED_EXTENDED)
                }
            }
        }
    }

    private fun updateAmazonUi(oldVersionCode: Int) {
        @SuppressWarnings("MagicNumber")
        if (oldVersionCode <= 4_06_30_00 && BuildConfig.FLAVOR_distribution == Constants.DISTRIBUTION_AMAZON && !isTv.get().invoke()) {
            mainScope.launch {
                logout.get().invoke()
            }
        }
    }

    private fun updateOnboardingTelemetry(oldVersionCode: Int) {
        if (oldVersionCode <= 4_09_38_00) {
            onboardingTelemetry.get().onAppUpdate()
        }
    }

    private fun enableWhatsNew(oldVersionCode: Int) {
        if (oldVersionCode <= 5_00_00_00) {
            appFeaturesPrefs.get().showWhatsNew = true
        }
    }

    @SuppressWarnings("MagicNumber")
    private fun clearCertificateData(oldVersionCode: Int) {
        if (oldVersionCode <= 5_03_50_00) {
            Storage.save(null, CertificateData::class.java)
        }
    }

    @SuppressWarnings("MagicNumber")
    private fun stripArchitecture(versionCode: Int) = versionCode % 100_000_000
}
