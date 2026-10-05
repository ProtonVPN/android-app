/*
 * Copyright (c) 2026. Proton AG
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.UpgradeDialogLauncherVM.Companion.createIntent
import com.protonvpn.android.ui.planupgrade.comparison_table.UpgradeDialogActivityV2
import com.protonvpn.android.utils.getSerializableExtraCompat
import dagger.Reusable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Reusable
class UpgradeDialogLauncher @Inject constructor() {

    fun launch(
        context: Context,
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
    ) {
        launch<UpgradeDialogActivityV2>(context, upgradeSource, upgradeTrigger)
    }

    fun createIntent(
        context: Context,
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
        country: CountryId? = null,
    ): Intent = createIntent<UpgradeDialogActivityV2>(context, upgradeSource, upgradeTrigger, country)


    companion object {
        inline fun <reified T : Activity> launch(
            context: Context,
            upgradeSource: UpgradeSource,
            upgradeTrigger: UpgradeTrigger,
            country: CountryId? = null
        ) {
            context.startActivity(createIntent<T>(context, upgradeSource, upgradeTrigger, country))
        }
    }
}

@HiltViewModel
class UpgradeDialogLauncherVM @Inject constructor(
    val upgradeDialogLauncher: UpgradeDialogLauncher,
) : ViewModel() {

    fun launch(
        context: Context,
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
    ) {
        upgradeDialogLauncher.launch(context, upgradeSource, upgradeTrigger)
    }

    fun launchCountries(
        context: Context,
        upgradeTrigger: UpgradeTrigger,
        country: CountryId?,
    ) {
        val countryId = country?.takeIf { !it.isFastest }
        UpgradeDialogLauncher.launch<UpgradeDialogActivityV2>(
            context,
            UpgradeSource.COUNTRIES,
            upgradeTrigger,
            countryId
        )
    }

    fun launchOnboarding(context: Context) {
        upgradeDialogLauncher.launch(context, UpgradeSource.ONBOARDING, UpgradeTrigger.ONBOARDING)
    }

    companion object {
        const val UPGRADE_SOURCE_KEY = "Upgrade Type"
        const val UPGRADE_TRIGGER_KEY = "Upgrade Trigger"
        const val COUNTRY_KEY = "Country Code"

        inline fun <reified T : Activity> createIntent(
            context: Context,
            upgradeSource: UpgradeSource,
            upgradeTrigger: UpgradeTrigger,
            country: CountryId? = null
        ) = Intent(context, T::class.java).apply {
            putExtra(UPGRADE_SOURCE_KEY, upgradeSource)
            putExtra(UPGRADE_TRIGGER_KEY, upgradeTrigger)
            if (country != null) putExtra(COUNTRY_KEY, country.countryCode)
        }

        fun getUpgradeSourceInfo(intent: Intent?): Triple<UpgradeSource?, UpgradeTrigger?, CountryId?> =
            Triple(
                intent?.getSerializableExtraCompat<UpgradeSource>(UPGRADE_SOURCE_KEY),
                intent?.getSerializableExtraCompat<UpgradeTrigger>(UPGRADE_TRIGGER_KEY),
                intent?.getStringExtra(COUNTRY_KEY)?.let { CountryId(it) }
            )
    }
}
