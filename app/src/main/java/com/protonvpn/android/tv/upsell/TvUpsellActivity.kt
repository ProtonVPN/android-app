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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.base.ui.upsellBackground
import com.protonvpn.android.base.ui.upsellGradientEnd
import com.protonvpn.android.base.ui.upsellGradientStart
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.CommonUpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.PaymentPanelState
import com.protonvpn.android.ui.planupgrade.PlanModel
import com.protonvpn.android.ui.planupgrade.UpgradeActivityHelper
import com.protonvpn.android.ui.planupgrade.UpgradeDialogLauncher
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.getPaymentErrorString
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.plan.presentation.entity.PlanCycle
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

        viewModel.loadPlans(allowMultiplePlans = false)
        upgradeActivityHelper.onCreate(viewModel)

        if (savedInstanceState == null) {
            val (upgradeSource, upgradeTrigger, country) = UpgradeDialogLauncher.getUpgradeSourceInfo(intent)
            if (upgradeSource == null || upgradeTrigger == null) {
                Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            viewModel.reportUpgradeFlowStart(upgradeSource, upgradeTrigger, country)
        }

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

                if (benefitsViewState != null) {
                    TvUpsellLayout(
                        modifier = Modifier.fillMaxSize(),
                        viewState = benefitsViewState,
                        paymentPanelState = paymentPanelState,
                        onBackClick = { finish() },
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
        ) = UpgradeDialogLauncher.createIntent<TvUpsellActivity>(
            context,
            upgradeSource,
            upgradeTrigger,
            country
        ).apply {
            putExtra(TvUpsellViewModel.KEY_PAID_FEATURE, tvUpsellContent)
        }
    }
}

@Composable
private fun TvUpsellLayout(
    viewState: TvUpsellViewModel.ViewState,
    paymentPanelState: PaymentPanelState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(color = ProtonTheme.colors.upsellBackground)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ProtonTheme.colors.upsellGradientStart,
                        ProtonTheme.colors.upsellGradientEnd,
                        Color.Transparent,
                        Color.Transparent,
                    )
                ),
                alpha = 0.7f,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(fraction = 0.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
        ) {
            Image(
                painter = painterResource(id = viewState.imageResId),
                contentDescription = null,
            )

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = viewState.titleResId),
                textAlign = TextAlign.Center,
                style = ProtonTheme.typography.hero,
            )

            Text(
                modifier = Modifier.fillMaxWidth(),
                text = AnnotatedString.fromHtml(
                    htmlString = stringResource(
                        id = viewState.descriptionResId,
                        *viewState.descriptionArgs(context),
                    )
                ),
                textAlign = TextAlign.Center,
                color = ProtonTheme.colors.textWeak,
                style = ProtonTheme.typography.body1Regular,
            )

            PaymentPanelTv(
                viewState = paymentPanelState,
                onClose = onBackClick,
                modifier = Modifier.width(500.dp)
            )
        }
    }
}

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutPreview() {
    ProtonThemeTv {
        val viewState = TvUpsellViewModel.ViewState(
            imageResId = R.drawable.worldwide_coverage_tv,
            titleResId = R.string.upsell_tv_all_countries_title,
            descriptionResId = R.string.upsell_tv_all_countries_description,
            descriptionArgs = { context ->
                arrayOf(
                    context.resources.getQuantityString(
                        R.plurals.upgrade_plus_servers_new,
                        21123,
                        21123,
                    ),
                    context.resources.getQuantityString(
                        R.plurals.upgrade_plus_countries,
                        120,
                        120,
                    ),
                )
            }
        )
        val cycles = listOf(
            CommonUpgradeDialogViewModel.CycleViewInfo(
                PlanCycle.YEARLY,
                R.string.payment_price_per_year,
                R.string.payment_price_cycle_year_label,
                CommonUpgradeDialogViewModel.PriceInfo(
                    "$120.00",
                    formattedPerMonthPrice = "$10.00",
                    savePercent = -37,
                    hasIntroPrice = true
                )
            ),
            CommonUpgradeDialogViewModel.CycleViewInfo(
                PlanCycle.MONTHLY,
                R.string.payment_price_per_month,
                R.string.payment_price_cycle_month_label,
                CommonUpgradeDialogViewModel.PriceInfo("$15.99", hasIntroPrice = false)
            ),
        )
        val plan = PlanModel("VPN Plus", "vpn2022", cycles)
        val paymentState = PaymentPanelState(
            upgradeState = CommonUpgradeDialogViewModel.State.PurchaseReady(listOf(plan), plan, false),
            selectedCycle = cycles.first().cycle,
            {}, {}, {}, {}
        )
        TvUpsellLayout(viewState, paymentState, {})
    }
}

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutLoadingPreview() {
    ProtonThemeTv {
        val viewState = TvUpsellViewModel.ViewState(
            imageResId = R.drawable.worldwide_coverage_tv,
            titleResId = R.string.upsell_tv_all_countries_title,
            descriptionResId = R.string.upsell_tv_all_countries_description,
            descriptionArgs = { context ->
                arrayOf(
                    context.resources.getQuantityString(
                        R.plurals.upgrade_plus_servers_new,
                        21123,
                        21123,
                    ),
                    context.resources.getQuantityString(
                        R.plurals.upgrade_plus_countries,
                        120,
                        120,
                    ),
                )
            }
        )
        val paymentState = PaymentPanelState(
            upgradeState = CommonUpgradeDialogViewModel.State.LoadingPlans(2, null),
            selectedCycle = null,
            {}, {}, {}, {}
        )
        TvUpsellLayout(viewState, paymentState, {})
    }
}
