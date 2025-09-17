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

package com.protonvpn.android.redesign.vpn.ui

import android.content.Intent
import android.provider.Settings
import android.text.BidiFormatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.netshield.NetShieldActions
import com.protonvpn.android.netshield.NetShieldBottomCustomDns
import com.protonvpn.android.netshield.NetShieldBottomPrivateDns
import com.protonvpn.android.netshield.NetShieldBottomSettings
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.netshield.NetShieldView
import com.protonvpn.android.netshield.NetShieldViewState
import com.protonvpn.android.redesign.base.ui.UpsellBannerContent
import com.protonvpn.android.base.ui.vpnGreen
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.DnsOverride
import kotlinx.coroutines.delay
import me.proton.core.compose.theme.ProtonColors
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultStrongNorm
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.compose.theme.headlineNorm
import kotlin.math.roundToInt
import me.proton.core.presentation.R as CoreR

sealed class VpnStatusViewState {
    data class Connected(
        val isSecureCoreServer: Boolean,
        val banner: StatusBanner?,
    ) : VpnStatusViewState()

    data class WaitingForNetwork(
        val locationText: LocationText? = null
    ) : VpnStatusViewState()

    data class Connecting(
        val locationText: LocationText? = null
    ) : VpnStatusViewState()

    data class Disabled(
        val locationText: LocationText? = null
    ) : VpnStatusViewState()

    data object Loading : VpnStatusViewState()
}

sealed class StatusBanner {
    data class NetShieldBanner(
        val netShieldState: NetShieldViewState,
    ) : StatusBanner()

    data object UpgradePlus : StatusBanner()
    data object UnwantedCountry : StatusBanner()
}

data class LocationText(
    val country: String,
    val ip: String,
)

private val STATUS_ICON_SIZE = 32.dp

fun Modifier.vpnStatusOverlayBackground(
    state: VpnStatusViewState,
): Modifier = composed {
    val targetColor = ProtonTheme.colors.getOverlayGradientTop(state)
    val gradientColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 500), label = "Gradient Animation"
    )

    background(Brush.verticalGradient(colors = listOf(gradientColor, gradientColor.copy(alpha = 0.0F))))
}

private fun ProtonColors.getOverlayGradientTop(state: VpnStatusViewState): Color = when(state) {
    is VpnStatusViewState.Connected -> if (isDark) vpnGreen.copy(alpha = 0.5f) else vpnGreen.copy(alpha = 0.2f)
    is VpnStatusViewState.Connecting, is VpnStatusViewState.WaitingForNetwork -> Color.White.copy(alpha = 0.5f)
    is VpnStatusViewState.Disabled ->
        if (isDark) notificationError.copy(alpha = 0.5f) else notificationError.copy(alpha = 0.24f)
    is VpnStatusViewState.Loading -> Color.Transparent
}

@Composable
fun rememberVpnStateAnimationProgress(
    state: VpnStatusViewState,
): State<Float> {
    val transition = updateTransition(targetState = state, label = "connecting -> connected")
    return transition.animateFloat(
        transitionSpec = {
            if (initialState == VpnStatusViewState.Loading ||
                initialState is VpnStatusViewState.Connecting && targetState is VpnStatusViewState.Connected
            ) {
                tween(durationMillis = 500)
            } else {
                snap()
            }
        },
        label = "progress"
    ) { targetState ->
        when (targetState) {
            is VpnStatusViewState.Connected -> 1f
            else -> 0f
        }
    }
}

@Composable
fun VpnStatusTop(
    state: VpnStatusViewState,
    transitionValue: () -> Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        val contentModifier = Modifier
            .align(Alignment.Center)
            .padding(8.dp)
        when (state) {
            is VpnStatusViewState.Connected ->
                VpnConnectedViewTop(state.isSecureCoreServer, transitionValue, contentModifier)

            is VpnStatusViewState.Connecting, is VpnStatusViewState.WaitingForNetwork ->
                CircularProgressIndicator(
                    color = ProtonTheme.colors.iconNorm,
                    strokeWidth = 2.dp,
                    modifier = contentModifier
                        .size(STATUS_ICON_SIZE)
                        .clearAndSetSemantics {} // It's visual only.
                )

            is VpnStatusViewState.Disabled -> {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_proton_lock_open_filled_2),
                    contentDescription = null,
                    tint = ProtonTheme.colors.notificationError,
                    modifier = contentModifier.size(STATUS_ICON_SIZE)
                )
            }
            is VpnStatusViewState.Loading -> {}
        }
    }
}

@Composable
fun VpnStatusBottom(
    state: VpnStatusViewState,
    transitionValue: () -> Float,
    netShieldActions: NetShieldActions,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (state) {
                is VpnStatusViewState.Connected -> {
                    if (state.banner != null) {
                        VpnConnectedView(state.banner, netShieldActions, transitionValue)
                    } // otherwise don't display anything.
                }

                is VpnStatusViewState.Connecting -> {
                    VpnConnectingView(state)
                }

                is VpnStatusViewState.Disabled -> {
                    VpnDisabledView(state)
                }

                is VpnStatusViewState.WaitingForNetwork -> {
                    VpnWaitingForNetwork()
                }

                VpnStatusViewState.Loading -> {}
            }
        }
    }
}

@Composable
private fun VpnConnectedViewTop(
    isSecureCoreServer: Boolean,
    transitionValue: () -> Float,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer { this.alpha = transitionValue() }
    ) {
        Icon(
            painter = painterResource(
                id = when {
                    isSecureCoreServer -> CoreR.drawable.ic_proton_locks_filled
                    else -> CoreR.drawable.ic_proton_lock_filled
                }
            ),
            tint = ProtonTheme.colors.vpnGreen,
            contentDescription = null,
            modifier = Modifier
                .size(STATUS_ICON_SIZE)
                .layout { measurable, constraints ->
                    // The most basic layout just to define base line for the icon.
                    val iconBaselineRatio = if (isSecureCoreServer) 0.79f else 0.85f
                    val placeable = measurable.measure(constraints)
                    val alignmentLines: Map<AlignmentLine, Int> =
                        mapOf(FirstBaseline to (placeable.height * iconBaselineRatio).roundToInt())
                    layout(width = placeable.width, height = placeable.height, alignmentLines = alignmentLines) {
                        placeable.placeRelative(0, 0)
                    }
                }
                .alignBy(FirstBaseline)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.vpn_status_connected),
            style = ProtonTheme.typography.headlineNorm,
            color = ProtonTheme.colors.vpnGreen,
            modifier = Modifier.alignBy(FirstBaseline)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VpnConnectedView(
    banner: StatusBanner,
    netShieldActions: NetShieldActions,
    transitionValue: () -> Float
) {
    var isModalVisible by remember { mutableStateOf(false) }
    Surface(
        color = ProtonTheme.colors.backgroundSecondary.copy(alpha = 0.86F),
        shape = ProtonTheme.shapes.large,
        border = TranslucentSurfaceBorder,
        modifier = Modifier
            .offset { IntOffset(x = 0, y = (transitionValue() * 16.dp.toPx()).toInt()) }
            .graphicsLayer {
                this.alpha = transitionValue()
            }
            .padding(top = 8.dp)
    ) {
        when (banner) {
            is StatusBanner.NetShieldBanner -> {
                NetShieldView(
                    state = banner.netShieldState,
                    onNavigateToSubsetting = { isModalVisible = !isModalVisible }
                )
            }

            StatusBanner.UnwantedCountry -> {
                UpsellBannerContent(
                    titleRes = R.string.not_wanted_country_title,
                    descriptionRes = R.string.not_wanted_country_description,
                    iconRes = R.drawable.upsell_worldwide_cover_exclamation,
                    modifier = Modifier
                        .clickable(onClick = netShieldActions.onChangeServerPromoUpgrade)
                        .padding(16.dp)
                )
            }

            StatusBanner.UpgradePlus -> {
                UpsellBannerContent(
                    R.string.netshield_free_title,
                    descriptionRes = R.string.netshield_free_description,
                    iconRes = R.drawable.ic_netshield_promo,
                    modifier = Modifier
                        .clickable(onClick = netShieldActions.onUpgradeNetShield)
                        .padding(16.dp),
                )
            }
        }
    }
    if (isModalVisible && banner is StatusBanner.NetShieldBanner) {
        val context = LocalContext.current
        SimpleModalBottomSheet(
            content = {
                when (banner.netShieldState) {
                    is NetShieldViewState.Available -> NetShieldBottomSettings(
                        currentNetShield = banner.netShieldState.protocol,
                        onNetShieldLearnMore = netShieldActions.onNetShieldLearnMore,
                        onValueChanged = netShieldActions.onNetShieldValueChanged
                    )
                    is NetShieldViewState.Unavailable -> when (banner.netShieldState.dnsOverride) {
                        DnsOverride.None -> check(false) { "Should never happen" }
                        DnsOverride.CustomDns -> NetShieldBottomCustomDns(
                            onCustomDnsLearnMore = { context.openUrl(Constants.URL_NETSHIELD_CUSTOM_DNS_LEARN_MORE) },
                            onDisableCustomDns = netShieldActions.onDisableCustomDns,
                        )
                        DnsOverride.SystemPrivateDns -> NetShieldBottomPrivateDns(
                            onPrivateDnsLearnMore = { context.openUrl(Constants.URL_NETSHIELD_PRIVATE_DNS_LEARN_MORE) },
                            onOpenPrivateDnsSettings = { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
                        )
                    }
                }

            },
            onDismissRequest = {
                isModalVisible = !isModalVisible
            })
    }
}

private val TranslucentSurfaceBorder: BorderStroke @Composable get() {
    val colors = with (ProtonTheme.colors) { listOf(shade100.copy(alpha = 0.08f), shade100.copy(alpha = 0.02f)) }
    return BorderStroke(1.dp, Brush.verticalGradient(colors))
}

@Composable
private fun VpnConnectingView(state: VpnStatusViewState.Connecting) {
    Text(
        text = stringResource(R.string.vpn_status_connecting),
        style = ProtonTheme.typography.defaultStrongNorm,
        modifier = Modifier.padding(8.dp)
    )

    state.locationText?.let {
        LocationTextAnimated(locationText = it)
    }
}

@Composable
private fun VpnWaitingForNetwork() {
    Text(
        text = stringResource(R.string.error_vpn_waiting_for_network),
        style = ProtonTheme.typography.defaultStrongNorm,
        modifier = Modifier.padding(8.dp)
    )
}

@Composable
private fun LocationTextAnimated(locationText: LocationText) {
    Surface(
        color = ProtonTheme.colors.backgroundSecondary.copy(alpha = 0.86F),
        border = TranslucentSurfaceBorder,
        shape = ProtonTheme.shapes.medium,
    ) {
        val country = BidiFormatter.getInstance().unicodeWrap(locationText.country)
        AnimateText(
            targetText = stringResource(
                R.string.vpn_status_disabled_location,
                country,
                locationText.ip
            ),
            highlightText = country,
            targetCharacter = '*',
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun VpnDisabledView(state: VpnStatusViewState.Disabled) {
    Text(
        text = stringResource(R.string.vpn_status_disabled),
        style = ProtonTheme.typography.defaultStrongNorm,
        modifier = Modifier.padding(8.dp)
    )
    Surface(
        color = ProtonTheme.colors.backgroundSecondary.copy(alpha = 0.86F),
        border = TranslucentSurfaceBorder,
        shape = ProtonTheme.shapes.medium,
    ) {
        val (text, highlight) = state.locationText?.let {
            val country = BidiFormatter.getInstance().unicodeWrap(it.country)
            stringResource(R.string.vpn_status_disabled_location, country, it.ip) to country
        } ?: run {
            stringResource(R.string.stateFragmentUnknownIp).let { it to it }
        }
        Text(
            text = annotatedCountryHighlight(text = text, highlight = highlight),
            style = ProtonTheme.typography.defaultWeak,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}


@Composable
private fun AnimateText(
    modifier: Modifier = Modifier,
    targetText: String,
    highlightText: String,
    duration: Int = 50,
    targetCharacter: Char = '*',
    preserveCharacters: CharArray = charArrayOf('.', ' ', '-')
) {
    val displayText = remember(targetText) { mutableStateOf(targetText) }
    val fixedWidth = remember(targetText) { mutableStateOf<Int?>(null) }

    LaunchedEffect(targetText) {
        fixedWidth.value?.let {
            val indicesToReplace = targetText.indices
                .filter { !preserveCharacters.contains(targetText[it]) }
                .shuffled()

            indicesToReplace.forEachIndexed { _, replacementIndex ->
                delay(duration.toLong())
                displayText.value = displayText.value.replaceRange(
                    replacementIndex,
                    replacementIndex + 1,
                    targetCharacter.toString()
                )
            }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Layout(
            content = {
                Text(
                    annotatedCountryHighlight(
                        text = targetText,
                        highlight = highlightText,
                        displayText = displayText.value
                    ),
                    style = ProtonTheme.typography.defaultWeak,
                    modifier = Modifier.onGloballyPositioned {
                        if (fixedWidth.value == null) {
                            fixedWidth.value = it.size.width
                        }
                    },
                )
            },
            modifier = modifier,
            measurePolicy = { measurables, constraints ->
                val placeable = measurables.first().measure(constraints)
                val width = fixedWidth.value ?: placeable.width
                val offsetX = (width - placeable.width) / 2
                layout(width, placeable.height) {
                    placeable.placeRelative(offsetX, 0)
                }
            }
        )
    }
}

@Preview
@Composable
private fun PreviewVpnDisabledState() {
    PreviewHelper(
        state = VpnStatusViewState.Disabled(
            LocationText(
                "Europe",
                "192.1.1.1.1"
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

@Preview
@Composable
private fun PreviewVpnConnectingState() {
    PreviewHelper(
        state = VpnStatusViewState.Connecting(
            LocationText(
                "Europe",
                "192.1.1.1.1"
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

@Preview
@Composable
private fun PreviewVpnDisabledStateWithRTLSymbols() {
    PreviewHelper(
        state = VpnStatusViewState.Disabled(
            LocationText(
                "اغلب برامجا",
                "192.1.1.1.1"
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

@Preview
@Composable
private fun PreviewVpnConnectedState() {
    PreviewHelper(
        state = VpnStatusViewState.Connected(
            false, banner = StatusBanner.NetShieldBanner(
                NetShieldViewState.Available(
                    protocol = NetShieldProtocol.ENABLED_EXTENDED,
                    netShieldStats = NetShieldStats(
                        adsBlocked = 3,
                        trackersBlocked = 0,
                        savedBytes = 2000
                    )
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

@Preview
@Composable
private fun PreviewVpnConnectedSecureCoreState() {
    PreviewHelper(
        state = VpnStatusViewState.Connected(
            true, banner = StatusBanner.NetShieldBanner(
                NetShieldViewState.Available(
                    protocol = NetShieldProtocol.ENABLED_EXTENDED,
                    netShieldStats = NetShieldStats(
                        adsBlocked = 3,
                        trackersBlocked = 0,
                        savedBytes = 2000
                    )
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

@Composable
private fun PreviewHelper(state: VpnStatusViewState, modifier: Modifier = Modifier) {
    ProtonVpnPreview {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            VpnStatusTop(state = state, transitionValue = { 1f })
            VpnStatusBottom(state = state, transitionValue = { 1f }, NetShieldActions({}, {}, {}, {}, {}))
        }
    }
}

@Composable
private fun annotatedCountryHighlight(
    text: String,
    highlight: String,
    displayText: String = text,
) = buildAnnotatedString {
    append(displayText)
    val startIndex = text.indexOf(highlight)
    if (startIndex >= 0) {
        addStyle(
            style = SpanStyle(color = ProtonTheme.colors.textNorm),
            start = startIndex,
            end = startIndex + highlight.length
        )
    }
}
