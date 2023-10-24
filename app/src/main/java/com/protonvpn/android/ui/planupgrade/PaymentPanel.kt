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

package com.protonvpn.android.ui.planupgrade

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonSolidButton
import com.protonvpn.android.base.ui.TextPlaceholder
import com.protonvpn.android.base.ui.theme.VpnTheme
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm

sealed class ViewState(val inProgress: Boolean) {
    object Initializing : ViewState(false)
    object UpgradeDisabled : ViewState(false)
    object LoadingPlans : ViewState(true)
    class PlanReady(
        val planName: String, @StringRes val cycleResId: Int, val formattedPrice: String, inProgress: Boolean
    ) : ViewState(inProgress)
    object FallbackFlowReady : ViewState(false)
    object Error : ViewState(false)
}

@Composable
fun PaymentPanel(
    viewState: ViewState,
    onPayClicked: () -> Unit,
    onStartFallback: () -> Unit,
    onErrorButtonClicked: () -> Unit,
    onCloseButtonClicked: () -> Unit,
) {
    if (viewState == ViewState.Initializing)
        return

    val hasBackground = viewState == ViewState.LoadingPlans || viewState is ViewState.PlanReady
    val colorAnimationSpec = tween<Color>(durationMillis = 300)
    val backgroundColor by animateColorAsState(
        targetValue = if (hasBackground) ProtonTheme.colors.backgroundSecondary else Color.Transparent,
        animationSpec = colorAnimationSpec,
        label = "background color"
    )
    val separatorColor by animateColorAsState(
        targetValue = if (hasBackground) ProtonTheme.colors.separatorNorm else Color.Transparent,
        animationSpec = colorAnimationSpec,
        label = "separator color"
    )
    Surface(
        color = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .animateContentSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (viewState) {
                    is ViewState.Initializing -> {}
                    is ViewState.LoadingPlans -> {
                        TextPlaceholder(width = 120.dp)
                        Spacer(modifier = Modifier.weight(1f))
                        TextPlaceholder()
                    }
                    is ViewState.PlanReady -> {
                        Image(
                            painter = painterResource(id = R.drawable.vpn_plus_badge),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(viewState.planName, modifier = Modifier.weight(1f))
                        Text(
                            getPriceAndCycleString(viewState.formattedPrice, viewState.cycleResId),
                            style = ProtonTheme.typography.captionWeak
                        )
                    }
                    is ViewState.Error,
                    is ViewState.UpgradeDisabled,
                    is ViewState.FallbackFlowReady -> Unit
                }
            }

            val onClick: () -> Unit = when(viewState) {
                ViewState.Initializing, ViewState.LoadingPlans -> { {} }
                is ViewState.PlanReady -> onPayClicked
                ViewState.FallbackFlowReady -> onStartFallback
                ViewState.Error -> onErrorButtonClicked
                ViewState.UpgradeDisabled -> onCloseButtonClicked
            }
            ProtonSolidButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(vertical = 12.dp),
                contained = false,
                loading = viewState.inProgress,
                onClick = onClick
            ) {
                when (viewState) {
                    is ViewState.Initializing,
                    is ViewState.LoadingPlans,
                    is ViewState.PlanReady,
                    is ViewState.FallbackFlowReady ->
                        Text(stringResource(R.string.upgrade))
                    is ViewState.Error ->
                        Text(stringResource(R.string.try_again))
                    is ViewState.UpgradeDisabled ->
                        Text(stringResource(R.string.close))
                }
            }
        }
        Divider(color = separatorColor, thickness = 1.dp)
    }
}

@Composable
private fun getPriceAndCycleString(formattedPrice: String, @StringRes cycleResId: Int): AnnotatedString {
    val text = stringResource(id = R.string.payment_price_with_period, formattedPrice, stringResource(cycleResId))
    val priceIndex = text.indexOf(formattedPrice)
    return AnnotatedString.Builder(text).apply {
        addStyle(ProtonTheme.typography.defaultNorm.toSpanStyle(), priceIndex, priceIndex + formattedPrice.length)
    }.toAnnotatedString()
}

@Preview
@Composable
private fun PreviewPlan() {
    VpnTheme {
        PaymentPanel(
            viewState = ViewState.PlanReady("VPN Plus", R.string.payment_price_per_year, "$123.99", false),
            {}, {}, {}, {}
        )
    }
}

@Preview
@Composable
private fun PreviewLoadingPlans() {
    VpnTheme(isDark = false) {
        PaymentPanel(viewState = ViewState.LoadingPlans, {}, {}, {}, {})
    }
}
