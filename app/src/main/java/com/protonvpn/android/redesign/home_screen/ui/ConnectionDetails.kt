/*
 * Copyright (c) 2023 Proton Technologies AG
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
package com.protonvpn.android.redesign.home_screen.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.speedBytesToString
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.FlagOrGatewayIndicator
import com.protonvpn.android.redesign.base.ui.InfoSheet
import com.protonvpn.android.redesign.base.ui.InfoType
import com.protonvpn.android.redesign.base.ui.ServerLoadBar
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentLabels
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.redesign.vpn.ui.viaCountry
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.headlineNorm
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import me.proton.core.presentation.R as CoreR

@Composable
fun ConnectionDetailsRoute(
    onClosePanel: () -> Unit,
) {
    val viewModel: ConnectionDetailsViewModel = hiltViewModel()
    val viewState by viewModel.connectionDetailsViewState.collectAsStateWithLifecycle()
    ConnectionDetails(viewState, onClosePanel)
}

@Composable
fun ConnectionDetails(
    viewState: ConnectionDetailsViewModel.ConnectionDetailsViewState,
    onClosePanel: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(ProtonTheme.colors.backgroundDeep)
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        when (viewState) {
            is ConnectionDetailsViewModel.ConnectionDetailsViewState.Connected ->
                ConnectionDetailsConnected(viewState, onClosePanel)
            is ConnectionDetailsViewModel.ConnectionDetailsViewState.Close ->
                LaunchedEffect(Unit) {
                    onClosePanel()
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionDetailsConnected(
    viewState: ConnectionDetailsViewModel.ConnectionDetailsViewState.Connected,
    onClosePanel: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isScrolled = remember { derivedStateOf { scrollState.value > 0 } }
    val topAppBarColor = animateColorAsState(
        targetValue = if (isScrolled.value) ProtonTheme.colors.backgroundSecondary else ProtonTheme.colors.backgroundDeep,
        label = "topAppBarColor"
    )
    val context = LocalContext.current
    val onOpenUrl: (String) -> Unit = { url -> context.openUrl(url) }
    var info by remember { mutableStateOf<InfoType?>(null) }
    TopAppBar(
        title = { },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = topAppBarColor.value
        ),
        navigationIcon = {
            IconButton(onClick = { onClosePanel() }) {
                Icon(
                    painterResource(id = CoreR.drawable.ic_arrow_back),
                    contentDescription = stringResource(id = R.string.accessibility_back)
                )
            }
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .largeScreenContentPadding()
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .heightIn(min = 42.dp)
                .semantics(mergeDescendants = true) {},
        ) {
            val connectIntent = viewState.connectIntentViewState
            FlagOrGatewayIndicator(
                connectIntent.primaryLabel,
                modifier = Modifier.padding(top = 4.dp)
            )
            ConnectIntentLabels(
                primaryLabel = connectIntent.primaryLabel,
                secondaryLabel = connectIntent.secondaryLabel,
                serverFeatures = connectIntent.serverFeatures,
                primaryLabelStyle = ProtonTheme.typography.headlineNorm,
                secondaryLabelVerticalPadding = 4.dp,
                isConnected = false,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            )
        }

        IpView(
            viewState.entryIp, viewState.vpnIp, Modifier.padding(vertical = 16.dp)
        )

        viewState.trafficUpdate?.let { trafficUpdate ->
            HeaderText(
                R.string.connection_details_section_vpn_speed,
                withInfoIcon = true,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        onClick = {
                            info = InfoType.VpnSpeed
                        },
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.accessibility_action_open)
                    )
            )
            ConnectionSpeedRow(
                trafficUpdate.downloadSpeed,
                trafficUpdate.uploadSpeed,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
            )
        }

        HeaderText(R.string.connection_details_subtitle, withInfoIcon = false)
        ConnectionStats(
            sessionTime = getSessionTime(sessionTimeInSeconds = viewState.trafficUpdate?.sessionTimeSeconds),
            exitCountry = viewState.exitCountryId,
            entryCountry = viewState.entryCountryId,
            gatewayName = viewState.serverGatewayName,
            city = viewState.serverCity,
            serverName = viewState.serverDisplayName,
            serverLoad = viewState.serverLoad,
            protocol = viewState.protocolDisplay?.let { stringResource(it) },
            onOpenUrl = onOpenUrl
        )
    }
    InfoSheet(info, onOpenUrl, dismissInfo = { info = null })
}

@Composable
private fun HeaderText(
    @StringRes textId: Int,
    withInfoIcon: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(textId),
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak
        )
        if (withInfoIcon) {
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_info_circle),
                tint = ProtonTheme.colors.iconHint,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun getSessionTime(sessionTimeInSeconds: Int?): String {
    val duration = sessionTimeInSeconds?.seconds ?: Duration.ZERO

    val sb = StringBuilder()

    duration.toComponents { days, hours, minutes, seconds, _ ->
        when {
            days != 0L -> {
                sb
                    .append(pluralStringResource(id = R.plurals.connection_details_days, days.toInt(), days.toInt()))
                    .append(" ")
                if (hours != 0) {
                    sb.append(stringResource(id = R.string.connection_details_hours_shortened, hours))
                }
            }

            hours != 0 -> {
                sb
                    .append(stringResource(id = R.string.connection_details_hours_shortened, hours))
                    .append(" ")
                if (minutes != 0) {
                    sb.append(stringResource(id = R.string.connection_details_minutes_shortened, minutes))
                }
            }

            else -> {
                if (minutes != 0) {
                    sb
                        .append(stringResource(id = R.string.connection_details_minutes_shortened, minutes))
                        .append(" ")
                }
                if (seconds != 0) {
                    sb.append(stringResource(id = R.string.connection_details_seconds_shortened, seconds))
                }
            }
        }
    }

    return sb.toString().trim()
}

@Composable
private fun ConnectionSpeedRow(
    downloadSpeed: Long,
    uploadSpeed: Long,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        SpeedInfo(
            title = stringResource(id = R.string.connection_details_download),
            speedValue = downloadSpeed,
            icon = painterResource(id = CoreR.drawable.ic_proton_arrow_down_line),
            color = ProtonTheme.colors.notificationSuccess,
            modifier = Modifier.weight(1f),
        )
        SpeedInfo(
            title = stringResource(id = R.string.connection_details_upload),
            speedValue = uploadSpeed,
            icon = painterResource(id = CoreR.drawable.ic_proton_arrow_up_line),
            color = ProtonTheme.colors.notificationError,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpeedInfo(
    title: String,
    speedValue: Long,
    icon: Painter,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true, properties = {}),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                painter = icon,
                tint = color,
                contentDescription = null,
            )
            Text(
                text = title,
                style = ProtonTheme.typography.body2Regular,
                color = color,
            )
        }
        val accessibilityValue = speedValue.speedBytesToString(useAbbreviations = false)
        Text(
            text = speedValue.speedBytesToString(),
            style = ProtonTheme.typography.headline,
            modifier = Modifier
                .semantics { contentDescription = accessibilityValue }
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun ConnectionStats(
    sessionTime: String,
    exitCountry: CountryId,
    entryCountry: CountryId?,
    gatewayName: String?,
    city: String?,
    serverName: String,
    serverLoad: Float,
    protocol: String? = "",
    onOpenUrl: (url: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ProtonTheme.colors.backgroundNorm,
        shape = ProtonTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            ConnectionDetailRowWithText(
                labelTitle = stringResource(id = R.string.connection_details_connected_for),
                contentValue = sessionTime
            )
            if (gatewayName != null) {
                VpnDivider()
                ConnectionDetailRowWithText(
                    labelTitle = stringResource(id = R.string.connection_details_gateway),
                    contentValue = gatewayName
                )
            }
            VpnDivider()
            ConnectionDetailRowWithComposable(stringResource(id = R.string.connection_details_country),
                contentComposable = {
                    Column {
                        Text(
                            text = exitCountry.label(),
                            style = ProtonTheme.typography.body1Medium,
                            modifier = Modifier.align(Alignment.End)
                        )
                        entryCountry?.let {
                            Text(
                                text = viaCountry(entryCountry = it),
                                style = ProtonTheme.typography.body2Regular,
                                color = ProtonTheme.colors.textWeak,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                },
                onOpenUrl = onOpenUrl
            )
            city?.let {
                VpnDivider()
                ConnectionDetailRowWithText(
                    labelTitle = stringResource(id = R.string.connection_details_city),
                    contentValue = city
                )
            }
            VpnDivider()
            ConnectionDetailRowWithText(
                labelTitle = stringResource(id = R.string.connection_details_server),
                contentValue = serverName
            )
            VpnDivider()
            ConnectionDetailRowWithComposable(
                labelTitle = stringResource(id = R.string.connection_details_server_load),
                infoType = InfoType.ServerLoad,
                onOpenUrl = onOpenUrl,
                contentComposable = {
                    ServerLoadBar(progress = serverLoad / 100)
                    Text(
                        text = stringResource(id = R.string.serverLoad, serverLoad.roundToInt()),
                        style = ProtonTheme.typography.body1Medium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                })
            VpnDivider()
            ConnectionDetailRowWithTextAndInfo(
                labelTitle = stringResource(id = R.string.connection_details_protocol),
                infoType = InfoType.Protocol,
                contentValue = protocol,
                onOpenUrl = onOpenUrl
            )
        }
    }
}

@Composable
fun ConnectionDetailRowWithComposable(
    labelTitle: String,
    modifier: Modifier = Modifier,
    infoType: InfoType? = null,
    onOpenUrl: (url: String) -> Unit,
    contentComposable: @Composable () -> Unit
) {
    var info by remember { mutableStateOf<InfoType?>(null) }
    Column(modifier = Modifier.semantics(mergeDescendants = true, properties = {})) {
        Row(modifier = modifier
            .fillMaxWidth()
            .let {
                if (infoType != null) it.clickable(onClick = {
                    info = infoType
                }) else it
            }
            .clip(RoundedCornerShape(4.dp))
            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start) {
            Text(
                text = labelTitle,
                style = ProtonTheme.typography.body1Regular,
                color = ProtonTheme.colors.textWeak,
                textAlign = TextAlign.Start
            )
            if (infoType != null) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_info_circle),
                    tint = ProtonTheme.colors.iconHint,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(start = 4.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            contentComposable()
        }
    }
    InfoSheet(info = info, onOpenUrl = onOpenUrl, dismissInfo = { info = null })
}

@Composable
fun ConnectionDetailRowWithText(
    labelTitle: String,
    contentValue: String?,
) {
    ConnectionDetailRowWithTextAndInfo(labelTitle, onOpenUrl = {}, infoType = null, contentValue)
}

@Composable
fun ConnectionDetailRowWithTextAndInfo(
    labelTitle: String,
    onOpenUrl: (url: String) -> Unit,
    infoType: InfoType?,
    contentValue: String?
) {
    ConnectionDetailRowWithComposable(labelTitle, infoType = infoType, onOpenUrl = onOpenUrl) {
        Text(
            text = contentValue ?: "",
            style = ProtonTheme.typography.body1Medium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun IpView(
    currentIp: String,
    exitIp: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ProtonTheme.colors.backgroundNorm,
        shape = ProtonTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 20.dp)
                    .padding(16.dp)
            ) {
                var isIpVisible by remember { mutableStateOf(false) }
                val isCurrentIpEmpty = currentIp.isEmpty()
                val displayedIp = when {
                    isCurrentIpEmpty -> stringResource(id = R.string.connection_details_unknown_ip)
                    isIpVisible -> currentIp
                    else -> currentIp.replace(Regex("[^.]"), "*")
                }

                val visibilityToggle = if (!isCurrentIpEmpty) {
                    val accessibilityDescription =
                        stringResource(id = R.string.accessibility_show_ip)
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { isIpVisible = !isIpVisible }
                        .semantics(mergeDescendants = true) {
                            contentDescription = accessibilityDescription
                        }
                } else {
                    Modifier.semantics(mergeDescendants = true, properties = {})
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = visibilityToggle
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(id = R.string.connection_details_my_ip),
                            style = ProtonTheme.typography.captionWeak,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        if (!isCurrentIpEmpty) {
                            Icon(
                                painter = painterResource(
                                    id = if (isIpVisible) CoreR.drawable.ic_proton_eye_slash else CoreR.drawable.ic_proton_eye,
                                ),
                                tint = ProtonTheme.colors.iconHint,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(start = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = displayedIp,
                        style = ProtonTheme.typography.defaultNorm,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_arrow_forward),
                contentDescription = null
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true, properties = {})
                    .padding(16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.connection_details_vpn_ip),
                        style = ProtonTheme.typography.captionWeak
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = exitIp,
                        textAlign = TextAlign.Center,
                        style = ProtonTheme.typography.defaultNorm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 20.dp),
                        )
                }
            }
        }
    }
}

@Preview
@Composable
fun ConnectionDetailsPreview() {
    LightAndDarkPreview {
        val connectIntentViewState = ConnectIntentViewState(
            primaryLabel = ConnectIntentPrimaryLabel.Country(CountryId.sweden, CountryId.iceland),
            secondaryLabel = ConnectIntentSecondaryLabel.SecureCore(CountryId.sweden, CountryId.iceland),
            serverFeatures = emptySet()
        )
        val viewState = ConnectionDetailsViewModel.ConnectionDetailsViewState.Connected(
            connectIntentViewState = connectIntentViewState,
            entryIp = "192.120.0.1",
            vpnIp = "1.4.3.2",
            exitCountryId = CountryId.sweden,
            entryCountryId = CountryId.iceland,
            trafficUpdate = TrafficUpdate(0L, 0L, 1156L, 2048L, 1_000_000L, 2_000_000, 2413),
            serverGatewayName = null,
            serverCity = "Stockholm",
            serverDisplayName = "SE#1",
            serverLoad = 32F,
            protocolDisplay = ProtocolSelection.SMART.displayName,
        )
        ConnectionDetailsConnected(viewState, {})
    }

}

@Preview
@Composable
fun ConnectionStatsPreview() {
    VpnTheme {
        ConnectionStats(
            sessionTime = getSessionTime(sessionTimeInSeconds = 2500),
            exitCountry = CountryId.sweden,
            entryCountry = CountryId.iceland,
            gatewayName = null,
            city = "Stockholm",
            serverName = "SE#1",
            serverLoad = 32F,
            protocol = "WireGuard",
            onOpenUrl = {}
        )
    }
}

@Preview
@Composable
fun IpViewPreview() {
    VpnTheme {
        IpView("192.120.0.1", "123.1233")
    }
}
