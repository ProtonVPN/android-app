/*
 * Copyright (c) 2025 Proton AG
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

package com.protonvpn.android.tv.upsell

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.promooffers.data.ApiNotificationTypes
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.UpgradeActivityHelper
import com.protonvpn.android.ui.planupgrade.UpgradeDialogLauncherVM
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.comparison_table.UpsellBenefitsViewModel
import com.protonvpn.android.ui.planupgrade.comparison_table.roundToTens
import com.protonvpn.android.ui.planupgrade.getPaymentErrorString
import com.protonvpn.android.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.parcelize.Parcelize
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

sealed interface TvUpsellContent : Parcelable {
    // countryId is null when the upsell is not about a specific country.
    @Parcelize data class Country(val countryId: CountryId?) : TvUpsellContent
    @Parcelize data object CustomDns : TvUpsellContent
    @Parcelize data object LanConnections : TvUpsellContent
    @Parcelize data object NetShield : TvUpsellContent
    @Parcelize data object SplitTunneling : TvUpsellContent
    @Parcelize data object SubscriptionExpired : TvUpsellContent
}

@AndroidEntryPoint
class TvUpsellActivity : BaseTvActivity() {

    private val benefitsViewModel by viewModels<TvUpsellViewModel>()
    private val viewModel by viewModels<UpgradeDialogViewModel>()
    private val upsellBenefitsViewModel by viewModels<UpsellBenefitsViewModel>()

    private val upgradeActivityHelper = UpgradeActivityHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (upgradeSource, upgradeTrigger, country) = UpgradeDialogLauncherVM.getUpgradeSourceInfo(intent)
        upgradeActivityHelper.onCreate(viewModel)

        val upsellContent = benefitsViewModel.upsellContent
        if (upgradeSource == null || upgradeTrigger == null || upsellContent == null) {
            Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        viewModel.loadBuiltinUpsellPlans(
            notificationType = ApiNotificationTypes.TYPE_BUILTIN_UPSELL_PADLOCK,
            supportedPlanNames = listOf(Constants.CURRENT_PLUS_PLAN),
            shouldReportTelemetry = savedInstanceState == null,
            upgradeSource = upgradeSource,
            upgradeTrigger = upgradeTrigger,
            countryId = country,
        )

        viewModel.eventErrorMessage
            .receiveAsFlow()
            .flowWithLifecycle(lifecycle)
            .onEach { error ->
                Toast.makeText(this, error.getPaymentErrorString(this), Toast.LENGTH_LONG).show()
            }
            .launchIn(lifecycleScope)

        viewModel.refreshVpnUserFrequently
            .flowWithLifecycle(lifecycle)
            .launchIn(lifecycleScope)

        viewModel.upgradeState
            .flowWithLifecycle(lifecycle)
            .filterIsInstance<UpgradeDialogViewModel.State.UpgradeDisabled>()
            .onEach {
                if (it.isPayingUser) finish()
            }
            .launchIn(lifecycleScope)

        val plusCountriesRounded = upsellBenefitsViewModel.getAllCountryCount().roundToTens()
        setContent {
            val freeCountries by produceState(Constants.FALLBACK_FREE_COUNTRY_COUNT) {
                value = upsellBenefitsViewModel.getFreeCountryCount()
            }
            ProtonThemeTv {
                val benefitsViewState = benefitsViewModel.viewStateFlow.collectAsStateWithLifecycle().value
                val paymentPanelState = viewModel.fullPanelState.collectAsStateWithLifecycle().value
                val isNonIapScreenEnabled = benefitsViewModel.isNonIapScreenEnabled.collectAsStateWithLifecycle().value
                val upgradeState = paymentPanelState.upgradeState

                when {
                    // Not known yet which variant to show.
                    isNonIapScreenEnabled == null -> Unit
                    isNonIapScreenEnabled && upgradeState == UpgradeDialogViewModel.State.Initializing -> Unit
                    isNonIapScreenEnabled && upgradeState is UpgradeDialogViewModel.State.UpgradeDisabled ->
                        TvUpsellLayoutQrCode(
                            content = upsellContent,
                            freeCountries = freeCountries,
                            plusCountriesRounded = plusCountriesRounded,
                            onClose = { finish() },
                            modifier = Modifier.fillMaxSize()
                        )
                    benefitsViewState != null ->
                        TvUpsellLayoutIap(
                            modifier = Modifier.fillMaxSize(),
                            viewState = benefitsViewState,
                            paymentPanelState = paymentPanelState,
                            onClose = { finish() },
                        )
                }
            }
        }
    }

    companion object {
        fun launch(
            context: Context,
            tvUpsellContent: TvUpsellContent,
            upgradeSource: UpgradeSource,
            upgradeTrigger: UpgradeTrigger,
            country: CountryId? = (tvUpsellContent as? TvUpsellContent.Country)?.countryId
        ) {
            val intent = createIntent(context, tvUpsellContent, upgradeSource, upgradeTrigger, country)
            context.startActivity(intent)
        }

        fun createIntent(
            context: Context,
            tvUpsellContent: TvUpsellContent,
            upgradeSource: UpgradeSource,
            upgradeTrigger: UpgradeTrigger,
            country: CountryId? = (tvUpsellContent as? TvUpsellContent.Country)?.countryId
        ) = UpgradeDialogLauncherVM.createIntent<TvUpsellActivity>(
            context,
            upgradeSource,
            upgradeTrigger,
            country
        ).apply {
            putExtra(TvUpsellViewModel.KEY_PAID_FEATURE, tvUpsellContent)
        }
    }
}
