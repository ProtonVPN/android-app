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

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.VpnOutlinedButton
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.FlagOrGatewayIndicator
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentLabels
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.redesign.vpn.ui.viaCountry
import kotlinx.coroutines.flow.StateFlow
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionNorm
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.headlineNorm
import me.proton.core.compose.theme.overlineWeak
import me.proton.core.compose.theme.subheadlineNorm
import org.joda.time.Duration
import org.joda.time.Period
import kotlin.math.roundToInt

@Composable
fun ConnectionDetailsRoute(
    onBackClicked: () -> Unit
) {
    val viewModel: ConnectionDetailsViewModel = hiltViewModel()
    ConnectionDetails(viewModel.connectionDetailsViewState, onBackClicked)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetails(
    connectionFlow: StateFlow<ConnectionDetailsViewModel.ConnectionDetailsViewState>,
    onBackClicked: () -> Unit
) {
    val connectionViewState = connectionFlow.collectAsStateWithLifecycle()
    val viewState = connectionViewState.value

    val scrollState = rememberScrollState()
    val isScrolled = remember { derivedStateOf { scrollState.value > 0 } }
    val topAppBarColor = animateColorAsState(
        targetValue = if (isScrolled.value) ProtonTheme.colors.backgroundSecondary else ProtonTheme.colors.backgroundDeep
    )
    Column(
        modifier = Modifier
            .background(ProtonTheme.colors.backgroundDeep)
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        TopAppBar(
            title = { },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = topAppBarColor.value
            ),
            navigationIcon = {
                IconButton(onClick = { onBackClicked() }) {
                    Icon(
                        Icons.Filled.ArrowBack,
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
                    labelStyle = ProtonTheme.typography.headlineNorm,
                    detailsStyle = ProtonTheme.typography.defaultSmallWeak,
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
                Spacer(Modifier.height(16.dp))
                ConnectionSpeedRow(
                    trafficUpdate.speedToString(sizeInBytes = trafficUpdate.downloadSpeed),
                    trafficUpdate.speedToString(sizeInBytes = trafficUpdate.uploadSpeed)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.connection_details_subtitle),
                style = ProtonTheme.typography.captionWeak,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ConnectionStats(sessionTime = getSessionTime(sessionTimeInSeconds = viewState.trafficUpdate?.sessionTimeSeconds),
                exitCountry = viewState.exitCountryId,
                entryCountry = viewState.entryCountryId,
                city = viewState.serverCity,
                serverName = viewState.serverDisplayName,
                serverLoad = viewState.serverLoad,
                protocol = viewState.protocolDisplay?.let { stringResource(it) })
        }
    }
}

@Composable
private fun getSessionTime(sessionTimeInSeconds: Int?): String {
    val period = sessionTimeInSeconds?.let {
        Duration.standardSeconds(it.toLong()).toPeriod().normalizedStandard()
    } ?: Period.ZERO

    val sb = StringBuilder()

    when {
        period.days != 0 -> {
            sb.append(
                pluralStringResource(
                    id = R.plurals.connection_details_days, period.days, period.days
                )
            ).append(" ")
            if (period.hours != 0) {
                sb.append(
                    stringResource(
                        id = R.string.connection_details_hours_shortened,
                        period.hours
                    )
                )
            }
        }

        period.hours != 0 -> {
            sb.append(
                stringResource(
                    id = R.string.connection_details_hours_shortened,
                    period.hours
                )
            ).append(" ")
            if (period.minutes != 0) {
                sb.append(
                    stringResource(
                        id = R.string.connection_details_minutes_shortened,
                        period.minutes
                    )
                )
            }
        }

        else -> {
            if (period.minutes != 0) {
                sb.append(
                    stringResource(
                        id = R.string.connection_details_minutes_shortened,
                        period.minutes
                    )
                ).append(" ")
            }
            if (period.seconds != 0) {
                sb.append(
                    stringResource(
                        id = R.string.connection_details_seconds_shortened,
                        period.seconds
                    )
                )
            }
        }
    }

    return sb.toString().trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionSpeedRow(
    downloadSpeed: String,
    uploadSpeed: String
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isModalVisible by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = {
                isModalVisible = !isModalVisible
            })

    ) {
        Text(
            text = stringResource(id = R.string.connection_details_section_vpn_speed),
            style = ProtonTheme.typography.captionWeak,
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_info_circle),
            tint = ProtonTheme.colors.iconHint,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .padding(start = 4.dp)
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        SpeedInfoColumn(
            title = stringResource(id = R.string.connection_details_download),
            value = downloadSpeed,
            icon = painterResource(id = R.drawable.ic_proton_arrow_down_line),
            tint = ProtonTheme.colors.notificationSuccess,
            modifier = Modifier.weight(1f)
        )
        SpeedInfoColumn(
            title = stringResource(id = R.string.connection_details_upload),
            value = uploadSpeed,
            icon = painterResource(id = R.drawable.ic_proton_arrow_up_line),
            tint = ProtonTheme.colors.notificationError,
            modifier = Modifier.weight(1f)
        )
    }
    if (isModalVisible) {
        ModalBottomSheet(
            sheetState = bottomSheetState,
            content = {
                GenericLearnMore(
                    title = stringResource(id = R.string.connection_details_vpn_speed_question),
                    details = stringResource(id = R.string.connection_details_vpn_speed_description),
                    onLearnMoreClick = {
                        // TODO open url on VPN speed
                    })
            },
            windowInsets = WindowInsets.navigationBars,
            onDismissRequest = {
                isModalVisible = !isModalVisible
            })
    }
}

@Composable
private fun SpeedInfoColumn(
    title: String,
    value: String,
    icon: Painter,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        ) {
            Text(
                text = title, style = ProtonTheme.typography.overlineWeak
            )
            Icon(
                painter = icon,
                tint = tint,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(start = 4.dp)
            )
        }
        Text(
            text = value, style = ProtonTheme.typography.defaultNorm
        )
    }
}

@Composable
private fun ConnectionStats(
    sessionTime: String,
    exitCountry: CountryId,
    entryCountry: CountryId?,
    city: String?,
    serverName: String,
    serverLoad: Float,
    modifier: Modifier = Modifier,
    protocol: String? = ""
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
            VpnDivider()
            ConnectionDetailRowWithComposable(stringResource(id = R.string.country),
                contentComposable = {
                    Column {
                        Text(
                            text = exitCountry.label(),
                            style = ProtonTheme.typography.defaultSmallNorm,
                            modifier = Modifier.align(Alignment.End)
                        )
                        entryCountry?.let {
                            Text(
                                text = viaCountry(entryCountry = it),
                                style = ProtonTheme.typography.captionWeak,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                })
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
            ConnectionDetailRowWithComposable(labelTitle = stringResource(id = R.string.connection_details_server_load),
                onInfoComposable = {
                    ServerLoadBottomSheet()
                },
                contentComposable = {
                    ServerLoadBar(progress = serverLoad / 100)
                    Text(
                        text = stringResource(id = R.string.serverLoad, serverLoad.roundToInt()),
                        style = ProtonTheme.typography.defaultSmallNorm,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                })
            VpnDivider()
            ConnectionDetailRowWithText(
                labelTitle = stringResource(id = R.string.connection_details_protocol),
                onInfoClick = {
                    // TODO protocol bottom sheet design paths differ, for now add basic version
                    GenericLearnMore(title = stringResource(id = R.string.connection_details_protocol),
                        details = stringResource(id = R.string.connection_details_protocol_description),
                        onLearnMoreClick = {
                            // TODO open url about protocol
                        })
                },
                contentValue = protocol
            )
        }
    }
}

@Composable
fun GenericLearnMore(
    title: String,
    details: String,
    subDetailsComposable: (@Composable () -> Unit)? = null,
    onLearnMoreClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Text(
            text = title,
            style = ProtonTheme.typography.subheadlineNorm,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = details,
            style = ProtonTheme.typography.defaultSmallNorm,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        subDetailsComposable?.invoke()
        VpnOutlinedButton(
            stringResource(id = R.string.connection_details_learn_more),
            onClick = onLearnMoreClick,
            isExternalLink = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ServerLoadBottomSheet() {
    GenericLearnMore(title = stringResource(id = R.string.connection_details_server_load_question),
        details = stringResource(id = R.string.connection_details_server_load_description),
        subDetailsComposable = {
            ServerLoadLegendItem(
                progress = 0.3F,
                title = stringResource(id = R.string.connection_details_server_load_low_title),
                details = stringResource(id = R.string.connection_details_server_load_low_description),
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            ServerLoadLegendItem(
                progress = 0.76F,
                title = stringResource(id = R.string.connection_details_server_load_medium_title),
                details = stringResource(id = R.string.connection_details_server_load_medium_description),
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
            ServerLoadLegendItem(
                progress = 0.95F,
                title = stringResource(id = R.string.connection_details_server_load_high_title),
                details = stringResource(id = R.string.connection_details_server_load_high_description),
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        },
        onLearnMoreClick = {
            // TODO open link to article on server load
        })
}

@Composable
fun ServerLoadBar(progress: Float) {
    val color = when {
        progress <= 0.75F -> ProtonTheme.colors.notificationSuccess
        progress <= 0.9F -> ProtonTheme.colors.notificationWarning
        else -> ProtonTheme.colors.notificationError
    }
    LinearProgressIndicator(
        progress = progress,
        color = color,
        strokeCap = StrokeCap.Round,
        trackColor = ProtonTheme.colors.shade40,
        modifier = Modifier
            .width(36.dp)
    )
}

@Composable
private fun ServerLoadLegendItem(
    progress: Float,
    title: String,
    details: String,
    modifier: Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically, modifier = modifier
    ) {
        ServerLoadBar(progress)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = title, style = ProtonTheme.typography.captionNorm
            )
            Text(
                text = details, style = ProtonTheme.typography.captionWeak
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionDetailRowWithComposable(
    labelTitle: String,
    modifier: Modifier = Modifier,
    onInfoComposable: (@Composable ColumnScope.() -> Unit)? = null,
    contentComposable: @Composable () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isModalVisible by remember { mutableStateOf(false) }
    Column {
        Row(modifier = modifier
            .fillMaxWidth()
            .let {
                if (onInfoComposable != null) it.clickable(onClick = {
                    isModalVisible = !isModalVisible
                }) else it
            }
            .clip(RoundedCornerShape(4.dp))
            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start) {
            Text(
                text = labelTitle,
                style = ProtonTheme.typography.defaultSmallWeak,
                textAlign = TextAlign.Start
            )
            if (onInfoComposable != null) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_info_circle),
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

    if (isModalVisible && onInfoComposable != null) {
        ModalBottomSheet(sheetState = bottomSheetState,
            content = onInfoComposable,
            windowInsets = WindowInsets.navigationBars,
            onDismissRequest = {
                isModalVisible = !isModalVisible
            })
    }
}

@Composable
fun ConnectionDetailRowWithText(
    labelTitle: String,
    onInfoClick: (@Composable ColumnScope.() -> Unit)? = null,
    contentValue: String?
) {
    ConnectionDetailRowWithComposable(labelTitle, onInfoComposable = onInfoClick) {
        Text(
            text = contentValue ?: "",
            style = ProtonTheme.typography.defaultSmallNorm,
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
                    Modifier
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
                            style = ProtonTheme.typography.overlineWeak,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        if (!isCurrentIpEmpty) {
                            Icon(
                                painter = painterResource(
                                    id = if (isIpVisible) R.drawable.ic_proton_eye_slash else R.drawable.ic_proton_eye,
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
                imageVector = Icons.Default.ArrowForward, contentDescription = null
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally

                ) {
                    Text(
                        text = stringResource(id = R.string.connection_details_vpn_ip),
                        style = ProtonTheme.typography.overlineWeak
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
fun ConnectionStatsPreview() {
    VpnTheme {
        ConnectionStats(
            sessionTime = getSessionTime(sessionTimeInSeconds = 2500),
            exitCountry = CountryId.sweden,
            entryCountry = CountryId.iceland,
            city = "Stockholm",
            serverName = "SE#1",
            serverLoad = 32F,
            protocol = "WireGuard"
        )
    }
}

@Preview
@Composable
fun VpnSpeedPreview() {
    VpnTheme {
        ConnectionSpeedRow("10 ", "123.1233")
    }
}

@Preview
@Composable
fun IpViewPreview() {
    VpnTheme {
        IpView("192.120.0.1", "123.1233")
    }
}

@Preview
@Composable
fun BottomSheetLoadDescriptionPreview() {
    LightAndDarkPreview {
        Surface {
            ServerLoadBottomSheet()
        }
    }
}
