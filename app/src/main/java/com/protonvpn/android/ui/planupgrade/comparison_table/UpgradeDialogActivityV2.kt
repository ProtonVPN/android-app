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

package com.protonvpn.android.ui.planupgrade.comparison_table

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.BoxWithVerticalScrollEdgeFade
import com.protonvpn.android.base.ui.BoxWithVerticalScrollEdgeFadeDefaults
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarCloseIcon
import com.protonvpn.android.base.ui.copy
import com.protonvpn.android.base.ui.horizontalPaddingForWindowSize
import com.protonvpn.android.base.ui.largeScreenContentPadding
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.base.ui.theme.enableEdgeToEdgeVpn
import com.protonvpn.android.base.ui.upsellGradientEnd
import com.protonvpn.android.base.ui.upsellGradientStart
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.telemetry.UpgradeAbTest
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTrigger
import com.protonvpn.android.ui.planupgrade.PaymentPanelFragment
import com.protonvpn.android.ui.planupgrade.UpgradeActivityHelper
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel

import com.protonvpn.android.ui.planupgrade.comparison_table.UpgradeDialogActivityV2.ViewState
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.getSerializableExtraCompat
import com.protonvpn.android.utils.mixDstOver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme

/**
 * Upgrade activity with a plan comparison table.
 */
// Note: remove the V2 from name when deleting the feature flag.
@AndroidEntryPoint
class UpgradeDialogActivityV2 : AppCompatActivity() {

    sealed interface ViewState {
        data class Countries(
            val country: CountryId?,
            val freeCountries: Int,
            val plusCountries: Int,
        ) : ViewState
        object NetShield : ViewState
        object Speed : ViewState
        object Streaming : ViewState
    }

    private val viewModel by viewModels<UpgradeDialogViewModel>()
    private val upsellBenefitsViewModel by viewModels<UpsellBenefitsViewModel>()

    private val upgradeActivityHelper = UpgradeActivityHelper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeVpn()

        val plusCountries = upsellBenefitsViewModel.getAllCountryCount()
        val country: CountryId? = intent?.getStringExtra(COUNTRY_KEY)?.let { CountryId(it) }
        val upgradeSource = intent?.getSerializableExtraCompat<UpgradeSource>(UPGRADE_SOURCE_KEY)
        val upgradeTrigger = intent?.getSerializableExtraCompat<UpgradeTrigger>(UPGRADE_TRIGGER_KEY)
        val initialContent = upgradeSource?.let { getContentType(upgradeSource, country, plusCountries) }
        if (initialContent == null || upgradeTrigger == null) {
            Toast.makeText(this, R.string.something_went_wrong, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val content = mutableStateOf(initialContent)
        viewModel.loadPlans(allowMultiplePlans = false)
        upgradeActivityHelper.onCreate(viewModel)
        if (savedInstanceState == null) {
            viewModel.reportUpgradeFlowStart(upgradeSource, upgradeTrigger, UpgradeAbTest.COMPARISON_TABLE,country)
        }

        lifecycleScope.launch {
            val current = content.value
            if (current is ViewState.Countries) {
                content.value = current.copy(freeCountries = upsellBenefitsViewModel.getFreeCountryCount())
            }
        }
        setContent {
            VpnTheme {
                PlanUpgradeDialog(
                    viewState = content.value,
                    onClose = ::finish,
                    modifier = Modifier
                        .fillMaxSize()
                )
            }
        }
    }

    companion object {
        private const val UPGRADE_SOURCE_KEY = "Upgrade Type"
        private const val UPGRADE_TRIGGER_KEY = "Upgrade Trigger"
        private const val COUNTRY_KEY = "Country Code"

        fun launch(
            context: Context,
            upgradeSource: UpgradeSource,
            upgradeTrigger: UpgradeTrigger,
            country: CountryId? = null
        ) {
            context.startActivity(
                Intent(context, UpgradeDialogActivityV2::class.java).apply {
                    putExtra(UPGRADE_SOURCE_KEY, upgradeSource)
                    putExtra(UPGRADE_TRIGGER_KEY, upgradeTrigger)
                    if (country != null) putExtra(COUNTRY_KEY, country.countryCode)
                }
            )
        }

        fun isSupported(upgradeSource: UpgradeSource): Boolean =
            getContentType(upgradeSource, null, plusCountries = 0) != null

        private fun getContentType(
            upgradeSource: UpgradeSource,
            country: CountryId?,
            plusCountries: Int,
        ): ViewState? = when (upgradeSource) {
            UpgradeSource.COUNTRIES -> ViewState.Countries(
                country = country,
                freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
                plusCountries = plusCountries
            )
            UpgradeSource.VPN_ACCELERATOR -> ViewState.Speed
            UpgradeSource.NETSHIELD -> ViewState.NetShield
            UpgradeSource.STREAMING -> ViewState.Streaming
            else -> null
        }
    }
}

@VisibleForTesting
@Composable
fun PlanUpgradeDialog(
    viewState: UpgradeDialogActivityV2.ViewState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.systemBars,
) {
    val backgroundGradient = Brush.verticalGradient(
        0f to ProtonTheme.colors.upsellGradientStart,
        0.5f to ProtonTheme.colors.upsellGradientEnd,
    )
    Scaffold(
        topBar = {
            SimpleTopAppBar(
                title = {},
                navigationIcon = { TopAppBarCloseIcon(onClose) },
                backgroundColor = Color.Transparent,
            )
        },
        bottomBar = {
            PaymentsPanelFragmentComposable(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            )
        },
        contentWindowInsets = WindowInsets(0,0,0,0),
        modifier = modifier
            .background(backgroundGradient),
        containerColor = Color.Transparent,
    ) { paddingValues ->
        val scrollState = rememberScrollState(initial = 0)
        BoxWithVerticalScrollEdgeFade(
            scrollableState = scrollState,
            topFadeColor = mixDstOver(ProtonTheme.colors.upsellGradientStart,ProtonTheme.colors.backgroundNorm),
            topFadeHeight =
                BoxWithVerticalScrollEdgeFadeDefaults.FadeHeight +
                        windowInsets.asPaddingValues().calculateTopPadding(),
            modifier = Modifier
                // Ignore the top padding, it'll be applied by consuming window insets be the
                // content.
                .padding(paddingValues.copy(top = 0.dp))
        ) {
            val tableModifier = Modifier
                .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
                .verticalScroll(scrollState)
                .largeScreenContentPadding()
                .horizontalPaddingForWindowSize(medium = 56.dp)

            // Note: UpgradeSource to panel type is not an ideal mapping, but it makes it easy to
            // combine old and new dialogs during the experiments.
            when (viewState) {
                ViewState.Streaming ->
                    UpsellStreamingTablePanel(
                        windowInsets = windowInsets,
                        modifier = tableModifier
                    )

                ViewState.NetShield ->
                    UpsellNetShieldTablePanel(
                        windowInsets = windowInsets,
                        modifier = tableModifier
                    )

                ViewState.Speed ->
                    UpsellSpeedTablePanel(
                        windowInsets = windowInsets,
                        modifier = tableModifier
                    )

                is ViewState.Countries ->
                    UpsellCountryTablePanel(
                        country = viewState.country,
                        freeCountries = viewState.freeCountries,
                        plusCountries = viewState.plusCountries,
                        windowInsets = windowInsets,
                        modifier = tableModifier,
                    )
            }
        }
    }
}

@Composable
private fun PaymentsPanelFragmentComposable(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        Box(
            modifier
                .heightIn(min = 180.dp)
                .largeScreenContentPadding()
                .padding(16.dp)
                .border(2.dp, ProtonTheme.colors.shade100, ProtonTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Text("Payments panel placeholder")
        }
    } else {
        AndroidFragment<PaymentPanelFragment>(modifier)
    }
}

@VisibleForTesting
class UpgradeContentProvider : PreviewParameterProvider<ViewState> {
    override val values: Sequence<ViewState> = sequenceOf(
        ViewState.Countries(
            country = CountryId.sweden,
            freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
            plusCountries = Constants.FALLBACK_COUNTRY_COUNT,
        ),
        ViewState.Countries(
            country = null,
            freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
            plusCountries = Constants.FALLBACK_COUNTRY_COUNT,
        ),
        ViewState.NetShield,
        ViewState.Speed,
        ViewState.Streaming
    )

    override fun getDisplayName(index: Int): String {
        return values.toList()[index].javaClass.simpleName
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewPlanUpgradeDialog(
    @PreviewParameter(UpgradeContentProvider::class) viewState: ViewState
) {
    ProtonVpnPreview {
        PlanUpgradeDialog(viewState, {}, Modifier.fillMaxSize())
    }
}