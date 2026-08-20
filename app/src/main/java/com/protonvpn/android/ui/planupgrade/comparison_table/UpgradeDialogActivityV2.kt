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

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.BoxWithVerticalScrollEdgeFade
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarCloseIcon
import com.protonvpn.android.base.ui.VpnTextButton
import com.protonvpn.android.base.ui.copy
import com.protonvpn.android.base.ui.horizontalPaddingForWindowSize
import com.protonvpn.android.base.ui.largeScreenContentPadding
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.base.ui.theme.enableEdgeToEdgeVpn
import com.protonvpn.android.base.ui.upsellGradientEnd
import com.protonvpn.android.base.ui.upsellGradientMid
import com.protonvpn.android.base.ui.upsellGradientStart
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ProtonSnackbar
import com.protonvpn.android.redesign.base.ui.ProtonSnackbarType
import com.protonvpn.android.redesign.base.ui.showSnackbar
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.onboarding.OnboardingTelemetry
import com.protonvpn.android.ui.planupgrade.PaymentPanel
import com.protonvpn.android.ui.planupgrade.PaymentPanelState
import com.protonvpn.android.ui.planupgrade.UpgradeActivityHelper
import com.protonvpn.android.ui.planupgrade.UpgradeDialogLauncherVM
import com.protonvpn.android.ui.planupgrade.UpgradeDialogViewModel
import com.protonvpn.android.ui.planupgrade.comparison_table.UpgradeDialogActivityV2.BenefitsViewState
import com.protonvpn.android.ui.planupgrade.getPaymentErrorString
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.mixDstOver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import javax.inject.Inject

/**
 * Upgrade activity with a plan comparison table.
 */
// Note: remove the V2 from name when deleting the feature flag.
@AndroidEntryPoint
class UpgradeDialogActivityV2 : AppCompatActivity() {

    sealed class BenefitsViewState {
        open val showNotNowButton = false

        object AdvancedCustomization : BenefitsViewState()
        data class Countries(
            val country: CountryId?,
            val freeCountries: Int,
            val plusCountriesRounded: Int,
        ) : BenefitsViewState()
        data class Devices(
            val freeCountries: Int,
            val plusCountriesRounded: Int,
        ) : BenefitsViewState()
        object NetShield : BenefitsViewState()
        data class Onboarding(
            val freeCountries: Int,
            val plusCountriesRounded: Int,
        ) : BenefitsViewState() {
            override val showNotNowButton: Boolean = true
        }
        object P2p : BenefitsViewState()
        object Profiles : BenefitsViewState()
        object SecureCore : BenefitsViewState()
        object Speed : BenefitsViewState()
        data class SplitTunneling(
            val freeCountries: Int,
            val plusCountriesRounded: Int,
        ) : BenefitsViewState()
        object Streaming : BenefitsViewState()
        object StreamingBlocked : BenefitsViewState()
        object Tor : BenefitsViewState()

    }

    private val viewModel by viewModels<UpgradeDialogViewModel>()
    private val upsellBenefitsViewModel by viewModels<UpsellBenefitsViewModel>()

    @Inject lateinit var onboardingTelemetry: dagger.Lazy<OnboardingTelemetry>

    private val upgradeActivityHelper = UpgradeActivityHelper(this, ::afterPaymentSuccess)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeVpn()

        val plusCountries = upsellBenefitsViewModel.getAllCountryCount()
        val (upgradeSource, upgradeTrigger, country) = UpgradeDialogLauncherVM.getUpgradeSourceInfo(intent)
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
            viewModel.reportUpgradeFlowStart(upgradeSource, upgradeTrigger, country)
        }

        lifecycleScope.launch {
            when(val current = content.value) {
                is BenefitsViewState.Countries ->
                    content.value = current.copy(freeCountries = upsellBenefitsViewModel.getFreeCountryCount())
                is BenefitsViewState.Devices ->
                    content.value = current.copy(freeCountries = upsellBenefitsViewModel.getFreeCountryCount())
                is BenefitsViewState.SplitTunneling ->
                    content.value = current.copy(freeCountries = upsellBenefitsViewModel.getFreeCountryCount())

                else -> Unit
            }
        }
        val snackbarHostState = SnackbarHostState()
        viewModel.eventErrorMessage.receiveAsFlow()
            .flowWithLifecycle(lifecycle)
            .onEach { error ->
                val snackbarString = error.getPaymentErrorString(this)
                snackbarHostState.showSnackbar(snackbarString, type = ProtonSnackbarType.ERROR)
            }
            .launchIn(lifecycleScope)
        setContent {
            VpnTheme {
                PlanUpgradeDialog(
                    benefitsViewState = content.value,
                    paymentPanelState = viewModel.fullPanelState.collectAsStateWithLifecycle().value,
                    snackbarHostState = snackbarHostState,
                    onClose = ::finish,
                    modifier = Modifier
                        .fillMaxSize()
                )
            }
        }
    }

    private fun afterPaymentSuccess(successPaymentState: UpgradeDialogViewModel.State.PurchaseSuccess) {
        onboardingTelemetry.get().onOnboardingPaymentSuccess(
            newPlanName = successPaymentState.newPlanName,
            billingCycle = successPaymentState.billingCycle,
        )
    }

    companion object {
        fun isSupported(upgradeSource: UpgradeSource): Boolean =
            getContentType(upgradeSource, null, plusCountries = 0) != null

        private fun getContentType(
            upgradeSource: UpgradeSource,
            country: CountryId?,
            plusCountries: Int,
        ): BenefitsViewState? = when (upgradeSource) {
            UpgradeSource.ADVANCED_CUSTOMIZATION,
            UpgradeSource.ALLOW_LAN,
            UpgradeSource.CUSTOM_DNS,
            UpgradeSource.DEFAULT_CONNECTION,
            UpgradeSource.EXCLUDE_LOCATIONS,
            UpgradeSource.MODERATE_NAT -> BenefitsViewState.AdvancedCustomization

            UpgradeSource.COUNTRIES -> BenefitsViewState.Countries(
                country = country,
                freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
                plusCountriesRounded = plusCountries.roundToTens(),
            )
            UpgradeSource.DEVICES -> BenefitsViewState.Devices(
                freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
                plusCountriesRounded = plusCountries.roundToTens(),
            )
            UpgradeSource.NETSHIELD -> BenefitsViewState.NetShield
            UpgradeSource.ONBOARDING -> BenefitsViewState.Onboarding(
                freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
                plusCountriesRounded = plusCountries.roundToTens(),
            )
            UpgradeSource.P2P -> BenefitsViewState.P2p
            UpgradeSource.PROFILES -> BenefitsViewState.Profiles
            UpgradeSource.SECURE_CORE -> BenefitsViewState.SecureCore
            UpgradeSource.SPLIT_TUNNELING -> BenefitsViewState.SplitTunneling(
                freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
                plusCountriesRounded = plusCountries.roundToTens(),
            )
            UpgradeSource.STREAMING -> BenefitsViewState.Streaming
            UpgradeSource.STREAMING_ACTIVITY -> BenefitsViewState.StreamingBlocked
            UpgradeSource.TOR -> BenefitsViewState.Tor
            UpgradeSource.VPN_ACCELERATOR -> BenefitsViewState.Speed

            else -> null
        }
    }
}

@VisibleForTesting
@Composable
fun PlanUpgradeDialog(
    benefitsViewState: BenefitsViewState,
    paymentPanelState: PaymentPanelState,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.systemBars,
) {
    val backgroundGradient = Brush.verticalGradient(
        0f to ProtonTheme.colors.upsellGradientStart,
        0.25f to ProtonTheme.colors.upsellGradientMid,
        0.5f to ProtonTheme.colors.upsellGradientEnd,
    )
    Scaffold(
        topBar = {
            if (!benefitsViewState.showNotNowButton) {
                SimpleTopAppBar(
                    title = {},
                    navigationIcon = { TopAppBarCloseIcon(onClose) },
                    backgroundColor = Color.Transparent,
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            ) {
                PaymentPanel(
                    viewState = paymentPanelState,
                    onClose = onClose,
                    modifier = Modifier
                )
                if (benefitsViewState.showNotNowButton) {
                    VerticalSpacer(height = 8.dp)
                    VpnTextButton(
                        text = stringResource(R.string.upgrade_not_now_button),
                        onClick = onClose
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    ProtonSnackbar(snackbarData = snackbarData)
                },
            )
        },
        contentWindowInsets = WindowInsets(0,0,0,0),
        modifier = modifier
            .background(backgroundGradient),
        containerColor = Color.Transparent,
    ) { paddingValues ->
        UpgradeBenefitsPanel(
            benefitsViewState,
            windowInsets = windowInsets,
            modifier = Modifier
                .fillMaxHeight()
                // Ignore the top padding, it'll be applied by consuming window insets be the
                // content.
                .padding(paddingValues.copy(top = 0.dp))
        )
    }
}

@Composable
private fun UpgradeBenefitsPanel(
    benefitsViewState: BenefitsViewState,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState(initial = 0)
    BoxWithVerticalScrollEdgeFade(
        scrollableState = scrollState,
        topFadeColor = mixDstOver(ProtonTheme.colors.upsellGradientStart,ProtonTheme.colors.backgroundNorm),
        topFadeHeight = 72.dp + windowInsets.asPaddingValues().calculateTopPadding(),
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        val tableModifier = Modifier
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Horizontal))
            .verticalScroll(scrollState)
            .largeScreenContentPadding()
            .horizontalPaddingForWindowSize(medium = 56.dp)

        // Note: UpgradeSource to panel type is not an ideal mapping, but it makes it easy to
        // combine old and new dialogs during the experiments.
        when (benefitsViewState) {
            BenefitsViewState.AdvancedCustomization ->
               UpsellAdvancedCustomizationTablePanel(
                    windowInsets = windowInsets,
                    modifier = tableModifier,
                )

            is BenefitsViewState.Countries ->
                UpsellCountryTablePanel(
                    country = benefitsViewState.country,
                    freeCountries = benefitsViewState.freeCountries,
                    plusCountriesRounded = benefitsViewState.plusCountriesRounded,
                    windowInsets = windowInsets,
                    modifier = tableModifier,
                )

            is BenefitsViewState.Devices ->
                UpsellDevicesTablePanel(
                    freeCountries = benefitsViewState.freeCountries,
                    plusCountriesRounded = benefitsViewState.plusCountriesRounded,
                    windowInsets = windowInsets,
                    modifier = tableModifier,
                )

            BenefitsViewState.NetShield ->
                UpsellNetShieldTablePanel(
                    windowInsets = windowInsets,
                    modifier = tableModifier
                )

            is BenefitsViewState.Onboarding ->
                UpsellOnboardingTablePanel(
                    freeCountries = benefitsViewState.freeCountries,
                    plusCountriesRounded = benefitsViewState.plusCountriesRounded,
                    windowInsets = windowInsets,
                    modifier = tableModifier,
                )

            BenefitsViewState.P2p ->
                UpsellP2pTablePanel(
                    windowInsets = windowInsets,
                    modifier = tableModifier,
                )

            BenefitsViewState.Profiles ->
                UpsellProfilesTablePanel(
                    windowInsets = windowInsets,
                    modifier = tableModifier,
                )

            BenefitsViewState.SecureCore ->
                UpsellSecureCoreTablePanel(
                    windowInsets = windowInsets,
                    modifier = tableModifier,
                )

            BenefitsViewState.Speed ->
                UpsellSpeedTablePanel(
                    windowInsets = windowInsets,
                    modifier = tableModifier
                )

            is BenefitsViewState.SplitTunneling ->
                UpsellSplitTunnelingTablePanel(
                    freeCountries = benefitsViewState.freeCountries,
                    plusCountriesRounded = benefitsViewState.plusCountriesRounded,
                    windowInsets = windowInsets,
                    modifier = tableModifier,
                )

            BenefitsViewState.Streaming ->
                UpsellStreamingTablePanel(
                    windowInsets = windowInsets,
                    modifier = tableModifier,
                )

            BenefitsViewState.StreamingBlocked ->
                UpsellStreamingBlockTablePanel(
                    windowInsets = windowInsets,
                    modifier = tableModifier,
                )

            BenefitsViewState.Tor ->
                UpsellTorTablePanel(
                    windowInsets = windowInsets,
                    modifier = tableModifier
                )
        }
    }
}

private fun Int.roundToTens() = (this / 10) * 10

@VisibleForTesting
class UpgradeContentProvider : PreviewParameterProvider<BenefitsViewState> {
    override val values: Sequence<BenefitsViewState> = sequenceOf(
        BenefitsViewState.AdvancedCustomization,
        BenefitsViewState.Countries(
            country = CountryId.sweden,
            freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
            plusCountriesRounded = Constants.FALLBACK_COUNTRY_COUNT.roundToTens(),
        ),
        BenefitsViewState.Countries(
            country = null,
            freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
            plusCountriesRounded = Constants.FALLBACK_COUNTRY_COUNT.roundToTens(),
        ),
        BenefitsViewState.Devices(
            freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
            plusCountriesRounded = Constants.FALLBACK_COUNTRY_COUNT.roundToTens(),
        ),
        BenefitsViewState.NetShield,
        BenefitsViewState.Onboarding(
            freeCountries = Constants.FALLBACK_FREE_COUNTRY_COUNT,
            plusCountriesRounded = Constants.FALLBACK_COUNTRY_COUNT.roundToTens(),
        ),
        BenefitsViewState.P2p,
        BenefitsViewState.Profiles,
        BenefitsViewState.SecureCore,
        BenefitsViewState.Speed,
        BenefitsViewState.Streaming,
        BenefitsViewState.Tor,
    )

    override fun getDisplayName(index: Int): String {
        return values.toList()[index].javaClass.simpleName
    }
}

@ProtonVpnPreview
@Composable
private fun PreviewPlanUpgradeDialog(
    @PreviewParameter(UpgradeContentProvider::class) benefitsViewState: BenefitsViewState
) {
    ProtonVpnPreview {
        val paymentPanelState = PaymentPanelState(
            upgradeState = UpgradeDialogViewModel.State.LoadingPlans(2, null),
            selectedCycle = null,
            {}, {}, {},
        )
        PlanUpgradeDialog(
            benefitsViewState,
            paymentPanelState,
            SnackbarHostState(),
            {},
            Modifier.fillMaxSize()
        )
    }
}
