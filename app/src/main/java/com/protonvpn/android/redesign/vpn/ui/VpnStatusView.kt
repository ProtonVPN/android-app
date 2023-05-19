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

import android.text.BidiFormatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.netshield.BandwidthStatsRow
import com.protonvpn.android.netshield.NetShieldStats
import com.protonvpn.android.redesign.base.ui.vpnGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultStrongNorm
import me.proton.core.compose.theme.defaultWeak

sealed class VpnStatusViewState {
    data class Connected(
        val isSecureCoreServer: Boolean,
        val netShieldStatsGreyedOut: Boolean,
        val netShieldStats: NetShieldStats
    ) : VpnStatusViewState()

    data class Connecting(
        val locationText: LocationText? = null
    ) : VpnStatusViewState()

    data class Disabled(
        val locationText: LocationText? = null
    ) : VpnStatusViewState()
}

data class LocationText(
    val country: String,
    val ip: String,
)

@Composable
fun VpnStatusView(
    stateFlow: StateFlow<VpnStatusViewState>,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val statusState = stateFlow.collectAsStateWithLifecycle()

    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (val state = statusState.value) {
                is VpnStatusViewState.Connected -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (state.isSecureCoreServer) R.drawable.ic_proton_locks else R.drawable.ic_proton_lock
                            ),
                            tint = ProtonTheme.colors.vpnGreen,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.vpn_status_connected),
                            style = ProtonTheme.typography.defaultStrongNorm,
                            color = ProtonTheme.colors.vpnGreen,
                        )
                    }
                    Surface(
                        color = ProtonTheme.colors.backgroundDeep,
                        shape = ProtonTheme.shapes.medium,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        BandwidthStatsRow(false, state.netShieldStats)
                    }
                }

                is VpnStatusViewState.Connecting -> {
                    CircularProgressIndicator(
                        color = ProtonTheme.colors.iconNorm,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.vpn_status_connecting),
                        style = ProtonTheme.typography.defaultStrongNorm,
                        color = ProtonTheme.colors.textNorm,
                        modifier = Modifier.padding(8.dp)
                    )
                    Surface(
                        color = ProtonTheme.colors.backgroundDeep,
                        shape = ProtonTheme.shapes.medium
                    ) {
                        AnimateText(
                            targetText = state.locationText?.run {
                                stringResource(R.string.vpn_status_disabled_location, BidiFormatter.getInstance().unicodeWrap(country), ip)
                            } ?: "",
                            targetCharacter = '*',
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        )
                    }
                }

                is VpnStatusViewState.Disabled -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_proton_lock_open),
                        contentDescription = null,
                        tint = ProtonTheme.colors.notificationError,
                        modifier = Modifier.padding(8.dp)
                    )
                    Text(
                        text = stringResource(R.string.vpn_status_disabled),
                        style = ProtonTheme.typography.defaultStrongNorm,
                        modifier = Modifier.padding(8.dp)
                    )
                    Surface(
                        color = ProtonTheme.colors.backgroundDeep,
                        shape = ProtonTheme.shapes.medium,
                    ) {
                        Text(
                            text = state.locationText?.run {
                                stringResource(R.string.vpn_status_disabled_location, BidiFormatter.getInstance().unicodeWrap(country), ip)
                            } ?: stringResource(R.string.stateFragmentUnknownIp),
                            style = ProtonTheme.typography.defaultWeak,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimateText(
    modifier: Modifier = Modifier,
    targetText: String,
    duration: Int = 50,
    targetCharacter: Char = '*',
    preserveCharacters: CharArray = charArrayOf('.', ' ', '-')
) {
    val displayText = remember { mutableStateOf(targetText) }
    val fixedWidth = remember { mutableStateOf<Int?>(null) }

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
                    displayText.value,
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
    VpnStatusView(
        stateFlow = MutableStateFlow(VpnStatusViewState.Disabled(LocationText("Europe", "192.1.1.1.1"))),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}

@Preview
@Composable
private fun PreviewVpnConnectingState() {
    VpnStatusView(
        stateFlow = MutableStateFlow(
            VpnStatusViewState.Connecting(
                (LocationText("Europe", "192.1.1.1.1"))
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
    VpnStatusView(
        stateFlow = MutableStateFlow(
            VpnStatusViewState.Connected(
                isSecureCoreServer = false,
                netShieldStatsGreyedOut = false,
                NetShieldStats(1, 0, 5234)
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
    VpnStatusView(
        stateFlow = MutableStateFlow(
            VpnStatusViewState.Connected(
                isSecureCoreServer = true,
                netShieldStatsGreyedOut = false,
                netShieldStats = NetShieldStats(1, 0, 5234)
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}
