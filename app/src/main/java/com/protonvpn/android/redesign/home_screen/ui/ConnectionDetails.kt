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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.patrykandpatrick.vico.compose.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.axis.vertical.rememberEndAxis
import com.patrykandpatrick.vico.compose.chart.CartesianChartHost
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineSpec
import com.patrykandpatrick.vico.compose.chart.layout.fullWidth
import com.patrykandpatrick.vico.compose.chart.rememberCartesianChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.component.shape.shader.color
import com.patrykandpatrick.vico.compose.component.shape.shader.verticalGradient
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.axis.formatter.DecimalFormatAxisValueFormatter
import com.patrykandpatrick.vico.core.axis.vertical.VerticalAxis
import com.patrykandpatrick.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatrick.vico.core.chart.values.AxisValueOverrider
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.model.ExtraStore
import com.patrykandpatrick.vico.core.model.lineSeries
import com.patrykandpatrick.vico.core.scroll.Scroll
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.TopAppBarBackIcon
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.speedBytesToString
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.bus.TrafficUpdate
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.InfoSheet
import com.protonvpn.android.redesign.base.ui.InfoType
import com.protonvpn.android.redesign.base.ui.ServerLoadBar
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.base.ui.rememberInfoSheetState
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentLabels
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.redesign.vpn.ui.viaCountry
import com.protonvpn.android.servers.StreamingService
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.IpPair
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.headlineNorm
import java.math.RoundingMode
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
    val context = LocalContext.current
    val onOpenUrl: (String) -> Unit = { url -> context.openUrl(url) }
    val infoSheetState = rememberInfoSheetState()
    SimpleTopAppBar(
        title = { },
        isScrolledPredicate = { scrollState.value > 0 },
        backgroundColor = ProtonTheme.colors.backgroundDeep,
        navigationIcon = {
            TopAppBarBackIcon(onClosePanel)
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .largeScreenContentPadding()
    ) {
        val connectIntent = viewState.connectIntentViewState
        Row(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .heightIn(min = 42.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = if (connectIntent.secondaryLabel != null) Alignment.Top else Alignment.CenterVertically,
        ) {
            ConnectIntentIcon(
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

        HeaderText(
            R.string.connection_details_section_ip,
            withInfoIcon = true,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    onClick = {
                        infoSheetState.show(InfoType.IpAddress(IpPair(viewState.userIp, null), viewState.vpnIp))
                    },
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.accessibility_action_open)
                )
        )
        IpView(
            viewState.userIp, viewState.vpnIp, Modifier.padding(vertical = 16.dp)
        )

        val trafficHistory = viewState.trafficHistory
        if (trafficHistory.isNotEmpty()) {
            HeaderText(
                R.string.connection_details_section_vpn_speed,
                withInfoIcon = true,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        onClick = {
                            infoSheetState.show(InfoType.VpnSpeed)
                        },
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.accessibility_action_open)
                    )
            )
            ConnectionSpeedRow(
                trafficHistory = trafficHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp)
            )
            SpeedGraph(trafficHistory = trafficHistory)
        }

        HeaderText(R.string.connection_details_subtitle, withInfoIcon = false)
        ConnectionStats(
            sessionTime = getSessionTime(sessionTimeInSeconds = trafficHistory.lastOrNull()?.sessionTimeSeconds),
            exitCountry = viewState.exitCountryId,
            entryCountry = viewState.entryCountryId,
            gatewayName = viewState.serverGatewayName,
            city = viewState.serverCity,
            serverName = viewState.serverDisplayName,
            serverLoad = viewState.serverLoad,
            protocol = viewState.protocolDisplay?.let { stringResource(it) },
            onOpenUrl = onOpenUrl
        )

        if (viewState.serverFeatures.hasAnyFeatures()) {
            HeaderText(R.string.connection_details_features_title, withInfoIcon = false)
            ServerFeatures(
                features = viewState.serverFeatures,
                onInfoOpen = { infoSheetState.show(it) }
            )
        }
    }
    InfoSheet(infoSheetState, onOpenUrl)
}

@Composable
private fun ServerFeatures(
    features: ConnectionDetailsViewModel.ServerFeatures,
    onInfoOpen: (InfoType) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (features.hasSecureCore) {
            FeatureComposable(
                title = stringResource(id = R.string.connection_feature_secure_core_title),
                details = stringResource(id = R.string.connection_feature_secure_core_description),
                iconId = CoreR.drawable.ic_proton_lock_layers,
                onClick = { onInfoOpen(InfoType.SecureCore) },
            )
        }
        if (features.hasP2P) {
            FeatureComposable(
                title = stringResource(id = R.string.connection_feature_p2p_title),
                details = stringResource(id = R.string.connection_feature_p2p_description),
                iconId = CoreR.drawable.ic_proton_arrow_right_arrow_left,
                onClick = { onInfoOpen(InfoType.P2P) },
            )
        }
        if (features.hasTor) {
            FeatureComposable(
                title = stringResource(id = R.string.connection_feature_tor_title),
                details = stringResource(id = R.string.connection_feature_tor_description),
                iconId = CoreR.drawable.ic_proton_brand_tor,
                onClick = { onInfoOpen(InfoType.Tor) },
            )
        }
        if (features.smartRouting != null) {
            FeatureComposable(
                title = stringResource(id = R.string.connection_feature_smart_routing_title),
                details = stringResource(R.string.connection_feature_smart_routing_description),
                iconId = CoreR.drawable.ic_proton_globe,
                onClick = { onInfoOpen(InfoType.SmartRouting) },
            )
        }
        if (features.streamingServices != null) {
            FeatureComposable(
                title = stringResource(id = R.string.connection_feature_streaming_title),
                details = stringResource(id = R.string.connection_feature_streaming_description),
                iconId = CoreR.drawable.ic_proton_play,
                additionalInformation = {
                    StreamingServicesGrid(
                        streamingServices = features.streamingServices,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    )
                },
                onClick = { onInfoOpen(InfoType.Streaming) },
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalLayoutApi::class)
@Composable
private fun StreamingServicesGrid(
    streamingServices: List<StreamingService>,
    modifier: Modifier
) {
    val numberOfColumns = 4
    val rows = if (streamingServices.isNotEmpty()) {
        (streamingServices.size + numberOfColumns - 1) / numberOfColumns
    } else {
        0
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        maxItemsInEachRow = numberOfColumns
    ) {
        repeat(rows * numberOfColumns) {
            // If service is null the column will still be created to fill space in rows which do not contain enough items
            val service = streamingServices.getOrNull(it)
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                service?.let {
                    GlideImage(
                        model = service.iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .height(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = service.name,
                        style = ProtonTheme.typography.overlineRegular,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureComposable(
    modifier: Modifier = Modifier,
    title: String,
    details: String,
    @DrawableRes iconId: Int,
    additionalInformation: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = ProtonTheme.colors.backgroundNorm,
                shape = ProtonTheme.shapes.medium
            )
            .padding(16.dp)
    ) {
        val (icon, titleView, detailsView, additionalView) = createRefs()

        Icon(
            painter = painterResource(id = iconId),
            tint = ProtonTheme.colors.iconNorm,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .constrainAs(icon) {
                    start.linkTo(parent.start)
                    top.linkTo(parent.top)
                }
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.constrainAs(titleView) {
                start.linkTo(icon.end, margin = 8.dp)
                end.linkTo(parent.end)
                top.linkTo(icon.top)
                width = Dimension.fillToConstraints
            }
        ) {
            Text(
                text = title,
                style = ProtonTheme.typography.body2Medium,
                color = ProtonTheme.colors.textNorm,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = stringResource(id = R.string.connection_details_info_label),
                style = ProtonTheme.typography.body2Medium,
                color = ProtonTheme.colors.textWeak,
                modifier = Modifier.padding(end = 4.dp)
            )

            Icon(
                painter = painterResource(id = CoreR.drawable.ic_info_circle),
                tint = ProtonTheme.colors.iconHint,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp)
            )
        }


        Text(
            text = details,
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak,
            modifier = Modifier.constrainAs(detailsView) {
                start.linkTo(titleView.start)
                end.linkTo(titleView.end)
                top.linkTo(titleView.bottom, margin = 8.dp)
                width = Dimension.fillToConstraints
            }
        )

        additionalInformation?.let {
            Box(
                modifier = Modifier.constrainAs(additionalView) {
                    start.linkTo(parent.start)
                    top.linkTo(detailsView.bottom, margin = 8.dp)
                    end.linkTo(parent.end)
                    width = Dimension.fillToConstraints
                }
            ) {
                it()
            }
        }
    }
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
    modifier: Modifier = Modifier,
    trafficHistory: List<TrafficUpdate>,
) {
    val currentDownloadSpeed = trafficHistory.last().downloadSpeed
    val currentUploadSpeed = trafficHistory.last().uploadSpeed
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        SpeedInfoColumn(
            title = stringResource(id = R.string.connection_details_download),
            speedValue = currentDownloadSpeed,
            icon = painterResource(id = CoreR.drawable.ic_proton_arrow_down_line),
            color = ProtonTheme.colors.notificationSuccess,
            modifier = Modifier.weight(1f),
        )
        SpeedInfoColumn(
            title = stringResource(id = R.string.connection_details_upload),
            speedValue = currentUploadSpeed,
            icon = painterResource(id = CoreR.drawable.ic_proton_arrow_up_line),
            color = ProtonTheme.colors.notificationError,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpeedGraph(
    trafficHistory: List<TrafficUpdate>,
    modifier: Modifier = Modifier
) {
    val uploadSpeedList = trafficHistory.map { it.uploadSpeed }
    val downloadSpeedList = trafficHistory.map { it.downloadSpeed }

    val (speedLabel, conversionFactor) = determineScaleAndLabel(uploadSpeedList + downloadSpeedList)

    val modelProducer = remember { CartesianChartModelProducer.build() }
    val scrollState = rememberVicoScrollState(
        scrollEnabled = false,
        initialScroll = Scroll.Absolute.End,
    )

    LaunchedEffect(uploadSpeedList, downloadSpeedList) {
        modelProducer.runTransaction {
            lineSeries {
                series(uploadSpeedList.map { it / conversionFactor })
                series(downloadSpeedList.map { it / conversionFactor })
            }
        }
    }
    Column(modifier) {
        Text(
            text = stringResource(id = R.string.speed_graph_title, speedLabel),
            style = ProtonTheme.typography.captionMedium,
            color = ProtonTheme.colors.textWeak,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    listOf(
                        rememberLineSpec(
                            shader = DynamicShaders.color(ProtonTheme.colors.notificationError),
                            backgroundShader =
                            DynamicShaders.verticalGradient(
                                with(ProtonTheme.colors) { arrayOf(notificationError.copy(alpha = 0.7f), notificationError.copy(alpha = 0f)) }
                            ),
                        ),
                        rememberLineSpec(
                            shader = DynamicShaders.color(ProtonTheme.colors.notificationSuccess),
                            backgroundShader =
                            DynamicShaders.verticalGradient(
                                with(ProtonTheme.colors) { arrayOf(notificationSuccess.copy(alpha = 0.7f), notificationSuccess.copy(alpha = 0f)) }
                            ),
                        ),
                    ),
                    axisValueOverrider = remember { TimeAxisValueOverrider() }
                ),
                endAxis = rememberEndAxis(
                    itemPlacer = remember { AxisItemPlacer.Vertical.count(count = { 3 }) },
                    valueFormatter = DecimalFormatAxisValueFormatter(pattern = "#", roundingMode = RoundingMode.UP),
                    label = rememberAxisLabelComponent(color = ProtonTheme.colors.textWeak),
                    horizontalLabelPosition = VerticalAxis.HorizontalLabelPosition.Outside
                ),
            ),
            diffAnimationSpec = null,
            scrollState = scrollState,
            horizontalLayout = HorizontalLayout.fullWidth(),
            modelProducer = modelProducer,
        )
    }
}

private class TimeAxisValueOverrider : AxisValueOverrider {
    override fun getMinX(minX: Float, maxX: Float, extraStore: ExtraStore): Float =
        maxX - TrafficMonitor.TRAFFIC_HISTORY_LENGTH_S + 1
}

@Composable
private fun determineScaleAndLabel(speeds: List<Long>): Pair<String, Double> {
    val maxSpeedInBytes = speeds.maxOrNull() ?: 0L
    return when {
        maxSpeedInBytes >= 1_000_000_000_000 -> stringResource(id = R.string.terabytes_per_second) to 1_000_000_000_000.0
        maxSpeedInBytes >= 1_000_000_000 -> stringResource(id = R.string.gigabytes_per_second) to 1_000_000_000.0
        maxSpeedInBytes >= 1_000_000 -> stringResource(id = R.string.megabytes_per_second) to 1_000_000.0
        maxSpeedInBytes >= 1_000 -> stringResource(id = R.string.kilobytes_per_second) to 1_000.0
        else -> stringResource(id = R.string.bytes_per_second) to 1.0
    }
}

@Composable
private fun SpeedInfoColumn(
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
            Text(
                text = title,
                style = ProtonTheme.typography.body2Regular,
                color = color,
            )
            Icon(
                painter = icon,
                tint = color,
                contentDescription = null,
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
    val infoSheetState = rememberInfoSheetState()
    Column(modifier = Modifier.semantics(mergeDescendants = true, properties = {})) {
        Row(modifier = modifier
            .fillMaxWidth()
            .let {
                if (infoType != null) it.clickable(onClick = {
                    infoSheetState.show(infoType)
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
    InfoSheet(infoSheetState, onOpenUrl = onOpenUrl)
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
    exitIp: IpPair,
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
                    val vpnIpTitle =
                        if (exitIp.ipV6 != null) R.string.connection_details_vpn_ip4_6
                        else R.string.connection_details_vpn_ip
                    Text(
                        text = stringResource(id = vpnIpTitle),
                        style = ProtonTheme.typography.captionWeak
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExitIpAddressText(exitIp.ipV4)
                    if (exitIp.ipV6 != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ExitIpAddressText(exitIp.ipV6)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitIpAddressText(
    ip: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = ip,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        style = ProtonTheme.typography.defaultNorm,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ProtonTheme.colors.backgroundSecondary)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Preview
@Composable
fun ConnectionDetailsPreview() {
    ProtonVpnPreview {
        val connectIntentViewState = ConnectIntentViewState(
            primaryLabel = ConnectIntentPrimaryLabel.Country(CountryId.sweden, CountryId.iceland),
            secondaryLabel = ConnectIntentSecondaryLabel.SecureCore(CountryId.sweden, CountryId.iceland),
            serverFeatures = emptySet()
        )
        val viewState = ConnectionDetailsViewModel.ConnectionDetailsViewState.Connected(
            connectIntentViewState = connectIntentViewState,
            userIp = "192.120.0.1",
            vpnIp = IpPair("1.4.3.2", "2001:abcd:1234:5678:0000:0000:0000:0001"),
            exitCountryId = CountryId.sweden,
            entryCountryId = CountryId.iceland,
            trafficHistory = listOf(TrafficUpdate(0L, 0L, 1156L, 2048L, 1_000_000L, 2_000_000, 2413)),
            serverGatewayName = null,
            serverCity = "Stockholm",
            serverDisplayName = "SE#1",
            serverLoad = 32F,
            protocolDisplay = ProtocolSelection.SMART.displayName,
            serverFeatures = ConnectionDetailsViewModel.ServerFeatures()
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
fun ServerFeaturesPreview() {
    ServerFeatures(
        features = ConnectionDetailsViewModel.ServerFeatures(
            hasTor = true,
            hasP2P = true,
            hasSecureCore = true,
            smartRouting = ConnectionDetailsViewModel.SmartRouting(CountryId.switzerland, CountryId.sweden),
            streamingServices = listOf(
                StreamingService("Netflix", ""),
                StreamingService("BBC iPlayer", ""),
                StreamingService("Amazon Prime", ""),
                StreamingService("DisneyPlus", ""),
                StreamingService("ESPN", ""),
                StreamingService("HULU", ""),
                StreamingService("CBS", ""),
            )
        )
    ) {

    }
}

@Preview
@Composable
fun FeaturePreview() {
    Column {
        FeatureComposable(
            title = "P2P",
            details = "Very long text to indicate multiple lines. Very long text to indicate multiple lines. Very long text to indicate multiple lines.",
            iconId = CoreR.drawable.ic_proton_brand_tor,
            onClick = {}
        )

        FeatureComposable(
            title = stringResource(id = R.string.connection_feature_streaming_title),
            details = stringResource(id = R.string.connection_feature_streaming_description),
            iconId = CoreR.drawable.ic_proton_play,
            additionalInformation = {
                Text(
                    text = "Additional composable here",
                    style = ProtonTheme.typography.body2Medium,
                    color = ProtonTheme.colors.textNorm,
                )
            },
            onClick = {},
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Preview
@Composable
fun IpViewPreview() {
    VpnTheme {
        IpView("192.120.0.1", IpPair(ipV4 = "1.4.3.2", ipV6 = "2001:abcd:1234:5678:0000:0000:0000:0001"))
    }
}
