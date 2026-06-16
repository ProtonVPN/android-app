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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.AbTestComparisonTable
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.UpgradeDialogLauncherVM.Companion.createIntent
import com.protonvpn.android.ui.planupgrade.comparison_table.IsUpsellComparisonTableExperimentEnabled
import com.protonvpn.android.ui.planupgrade.comparison_table.UpgradeDialogActivityV2
import com.protonvpn.android.utils.getSerializableExtraCompat
import dagger.Reusable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.reflect.KClass

@Reusable
class UpgradeDialogLauncher @Inject constructor(
    private val currentUser: CurrentUser,
    private val isUpsellComparisonTableExperimentEnabled: dagger.Lazy<IsUpsellComparisonTableExperimentEnabled>,
) {

    suspend inline fun <reified F: FragmentWithUpgradeSource> createCarouselIntent(
        context: Context,
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
    ): Intent =
        if (UpgradeDialogActivityV2.isSupported(upgradeSource) && useV2Dialogs()) {
            UpgradeDialogLauncherVM.createIntent<UpgradeDialogActivityV2>(
                context, upgradeSource, upgradeTrigger
            )
        } else {
            CarouselUpgradeDialogActivity.createIntent<F>(context, upgradeTrigger)
        }

    suspend fun launch(
        context: Context,
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
        legacyLaunch: () -> Unit
    ) {
        if (UpgradeDialogActivityV2.isSupported(upgradeSource) && useV2Dialogs()) {
            launch<UpgradeDialogActivityV2>(context, upgradeSource, upgradeTrigger)
        } else {
            legacyLaunch()
        }
    }

    suspend fun useV2Dialogs(): Boolean {
        if (!isUpsellComparisonTableExperimentEnabled.get().invoke()) return false
        val userId = currentUser.vpnUser()?.userId ?: return false
        return AbTestComparisonTable.fromUserId(userId) == AbTestComparisonTable.COMPARISON_TABLE
    }

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
    val mainScope: CoroutineScope,
    val upgradeDialogLauncher: UpgradeDialogLauncher,
) : ViewModel() {

    inline fun <reified F : FragmentWithUpgradeSource> launchCarousel(
        context: Context,
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
    ) {
        mainScope.launch {
            upgradeDialogLauncher.launch(context, upgradeSource, upgradeTrigger) {
                CarouselUpgradeDialogActivity.launch<F>(context, upgradeTrigger)
            }
        }
    }

    fun launchCarousel(
        context: Context,
        upgradeSource: UpgradeSource,
        upgradeTrigger: UpgradeTrigger,
        focusedFragmentClass: KClass<out Fragment>?,
    ) {
        mainScope.launch {
            upgradeDialogLauncher.launch(context, upgradeSource, upgradeTrigger) {
                CarouselUpgradeDialogActivity.launch(
                    context,
                    upgradeSource,
                    upgradeTrigger,
                    focusedFragmentClass
                )
            }
        }
    }

    fun launchCountries(
        context: Context,
        upgradeTrigger: UpgradeTrigger,
        country: CountryId?,
    ) {
        mainScope.launch {
            val countryId = country?.takeIf { !it.isFastest }
            if (upgradeDialogLauncher.useV2Dialogs()) {
                UpgradeDialogLauncher.launch<UpgradeDialogActivityV2>(
                    context,
                    UpgradeSource.COUNTRIES,
                    upgradeTrigger,
                    countryId
                )
            } else {
                if (countryId != null) {
                    PlusOnlyUpgradeDialogActivity.launch<UpgradeCountryHighlightsFragment>(
                        context,
                        upgradeTrigger,
                        countryId = countryId
                    )
                } else {
                    PlusOnlyUpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(
                        context,
                        upgradeTrigger,
                        null
                    )
                }
            }
        }
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
