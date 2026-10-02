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
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
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
import com.protonvpn.android.ui.planupgrade.getPaymentErrorString
import com.protonvpn.android.utils.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

enum class TvUpsellContent {
    AllCountries,
    CustomDns,
    LanConnections,
    NetShield,
    SplitTunneling,
    SubscriptionExpired,
}

@AndroidEntryPoint
class TvUpsellActivity : BaseTvActivity() {

    private val benefitsViewModel by viewModels<TvUpsellViewModel>()
    private val viewModel by viewModels<UpgradeDialogViewModel>()

    private val upgradeActivityHelper = UpgradeActivityHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (upgradeSource, upgradeTrigger, country) = UpgradeDialogLauncherVM.getUpgradeSourceInfo(intent)
        upgradeActivityHelper.onCreate(viewModel)


        if (upgradeSource == null || upgradeTrigger == null) {
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

        setContent {
            ProtonThemeTv {
                val benefitsViewState = benefitsViewModel.viewStateFlow.collectAsStateWithLifecycle().value
                val paymentPanelState = viewModel.fullPanelState.collectAsStateWithLifecycle().value
                val isNonIapScreenEnabled = benefitsViewModel.isNonIapScreenEnabled.collectAsStateWithLifecycle().value
                val upgradeState = paymentPanelState.upgradeState

                when {
                    // Not known yet which variant to show.
                    isNonIapScreenEnabled == null -> Unit
                    isNonIapScreenEnabled && upgradeState == UpgradeDialogViewModel.State.Initializing -> Unit
                    isNonIapScreenEnabled && upgradeState == UpgradeDialogViewModel.State.UpgradeDisabled ->
                        TvUpsellLayoutInfo(modifier = Modifier.fillMaxSize())
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
            country: CountryId? = null
        ) {
            val intent = createIntent(context, tvUpsellContent, upgradeSource, upgradeTrigger, country)
            context.startActivity(intent)
        }

        fun createIntent(
            context: Context,
            tvUpsellContent: TvUpsellContent,
            upgradeSource: UpgradeSource,
            upgradeTrigger: UpgradeTrigger,
            country: CountryId? = null
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
