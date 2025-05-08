/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.redesign.base.ui

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.base.ui.TextBulletRow
import com.protonvpn.android.base.ui.VpnOutlinedButton
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.vpn.IpPair
import kotlinx.parcelize.Parcelize
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionNorm
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultSmallNorm
import me.proton.core.compose.theme.subheadlineNorm
import me.proton.core.presentation.utils.currentLocale
import java.text.NumberFormat
import me.proton.core.presentation.R as CoreR

@Stable
class InfoSheetState(
    initialType: InfoType? = null
) {
    private val type = mutableStateOf(initialType)
    val currentType: InfoType? get() = type.value

    fun show(newType: InfoType) { type.value = newType }
    fun dismiss() { type.value = null }

    companion object {
        val Saver : Saver<InfoSheetState, InfoType> = Saver(
            save = { it.currentType },
            restore = { InfoSheetState(it) }
        )
    }
}

@Composable
fun rememberInfoSheetState() = rememberSaveable(saver = InfoSheetState.Saver) { InfoSheetState() }

sealed class InfoType : Parcelable {
    @Parcelize data object SecureCore: InfoType()
    @Parcelize data object VpnSpeed: InfoType()
    @Parcelize data object ServerLoad: InfoType()
    @Parcelize data object Protocol: InfoType()
    @Parcelize data object Tor: InfoType()
    @Parcelize data object P2P: InfoType()
    @Parcelize data object SmartRouting: InfoType()
    @Parcelize data object Streaming: InfoType()
    @Parcelize data object Profiles: InfoType()
    @Parcelize data object IPv6Traffic: InfoType()
    @Parcelize data class IpAddress(val myIP: IpPair, val vpnIP: IpPair): InfoType()
    // Add new types to InfoTypePreviewProvider
}

@Composable
fun InfoSheet(
    infoSheetState: InfoSheetState,
    onOpenUrl: (url: String) -> Unit,
    onGotItClick: (() -> Unit)? = null,
) {
    val currentInfo = infoSheetState.currentType
    if (currentInfo != null) {
        SimpleModalBottomSheet(onDismissRequest = { infoSheetState.dismiss() }) {
            InfoSheetContent(currentInfo, onOpenUrl, onGotItClick = onGotItClick)
        }
    }
}

@Composable
fun InfoSheetContent(
    info: InfoType,
    onOpenUrl: (url: String) -> Unit,
    modifier: Modifier = Modifier,
    onGotItClick: (() -> Unit)? = null,
) {
    val learnMoreAction = info.learnMoreUrl?.let { { onOpenUrl(it) } }
    GenericLearnMore(
        modifier = modifier,
        title = stringResource(id = info.title),
        details = info.details?.let { stringResource(id = it) },
        imageRes = info.imageRes,
        onLearnMoreClick = learnMoreAction,
        subDetailsComposable = { SubDetailsComposable(info) },
        learnMoreLabelRes = info.learnMoreLabel,
        onGotItClick = onGotItClick,
    )
}

private val InfoType.title get() = when (this) {
    InfoType.SecureCore -> R.string.info_dialog_secure_core_title
    InfoType.VpnSpeed -> R.string.connection_details_vpn_speed_question
    InfoType.ServerLoad -> R.string.connection_details_server_load_question
    InfoType.Protocol -> R.string.connection_details_protocol
    InfoType.Tor -> R.string.tor_title
    InfoType.P2P -> R.string.info_dialog_p2p_title
    InfoType.SmartRouting -> R.string.smart_routing_title
    InfoType.Streaming -> R.string.info_dialog_streaming_title
    InfoType.Profiles -> R.string.info_dialog_profiles_title
    InfoType.IPv6Traffic -> R.string.info_dialog_ipv6_traffic_title
    is InfoType.IpAddress -> R.string.info_dialog_ipaddress_title
}

private val InfoType.imageRes get() = when (this) {
    InfoType.SecureCore,
    InfoType.VpnSpeed,
    InfoType.ServerLoad,
    InfoType.Protocol,
    InfoType.Tor,
    InfoType.P2P,
    InfoType.IPv6Traffic,
    is InfoType.IpAddress -> null
    InfoType.SmartRouting -> R.drawable.info_smart_routing
    InfoType.Streaming -> R.drawable.upgrade_streaming
    InfoType.Profiles -> R.drawable.upgrade_profiles
}

private val InfoType.details get() = when (this) {
    InfoType.SecureCore -> R.string.info_dialog_secure_core_description
    InfoType.VpnSpeed -> R.string.connection_details_vpn_speed_description
    InfoType.ServerLoad -> R.string.connection_details_server_load_description
    InfoType.Protocol -> R.string.connection_details_protocol_description
    InfoType.Tor -> R.string.info_dialog_tor_description
    InfoType.P2P -> null
    InfoType.SmartRouting -> R.string.info_dialog_smart_routing_description
    InfoType.Streaming -> R.string.info_dialog_streaming_description
    InfoType.Profiles -> R.string.info_dialog_profiles_description
    InfoType.IPv6Traffic -> R.string.info_dialog_ipv6_traffic_description
    is InfoType.IpAddress -> null
}

private val InfoType.learnMoreLabel get() = when (this) {
    InfoType.SecureCore,
    InfoType.VpnSpeed,
    InfoType.ServerLoad,
    InfoType.Protocol,
    InfoType.SmartRouting,
    InfoType.Profiles -> R.string.connection_details_learn_more
    InfoType.Tor -> R.string.info_dialog_button_learn_more_tor
    InfoType.Streaming -> R.string.info_dialog_button_learn_more_streaming
    InfoType.P2P -> R.string.info_dialog_button_learn_more_p2p
    InfoType.IPv6Traffic, is InfoType.IpAddress -> R.string.info_dialog_button_learn_more_ipaddress
}

private val InfoType.learnMoreUrl get() = when (this) {
    InfoType.SecureCore -> Constants.SECURE_CORE_INFO_URL
    InfoType.VpnSpeed -> Constants.URL_SPEED_LEARN_MORE
    InfoType.ServerLoad -> Constants.URL_LOAD_LEARN_MORE
    InfoType.Protocol -> Constants.URL_PROTOCOL_LEARN_MORE
    InfoType.Tor -> Constants.URL_TOR_LEARN_MORE
    InfoType.P2P -> Constants.URL_P2P_LEARN_MORE
    InfoType.SmartRouting -> Constants.URL_SMART_ROUTING_LEARN_MORE
    InfoType.Streaming -> Constants.URL_STREAMING_LEARN_MORE
    InfoType.Profiles -> Constants.URL_PROFILES_LEARN_MORE
    InfoType.IPv6Traffic -> Constants.URL_IPV6_ADDRESS_LEARN_MORE
    is InfoType.IpAddress -> Constants.URL_IP_ADDRESS_LEARN_MORE
}

@Composable
private fun SubDetailsComposable(info: InfoType) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp)
    when (info) {
        InfoType.VpnSpeed, InfoType.Tor, InfoType.Protocol, InfoType.SmartRouting, InfoType.Profiles -> {}
        InfoType.ServerLoad -> SubDetailsComposableServerLoad(modifier)
        InfoType.SecureCore -> SubDetailsComposableSecureCore(modifier)
        InfoType.P2P -> SubDetailsComposableP2P(modifier)
        InfoType.Streaming -> SubDetailsStreaming(modifier)
        InfoType.IPv6Traffic -> SubDetailsIPv6Traffic(modifier)
        is InfoType.IpAddress -> SubDetailsIpAddress(myIP = info.myIP, vpnIP = info.vpnIP, modifier)
    }
}

@Composable
private fun SubDetailsIPv6Traffic(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        IPV6SubDetailsList(
            CoreR.drawable.ic_proton_checkmark,
            ProtonTheme.colors.notificationSuccess,
            R.string.info_dialog_ipv6_traffic_benefits_title,
            R.string.info_dialog_ipv6_traffic_benefits1,
            R.string.info_dialog_ipv6_traffic_benefits2,
        )
        IPV6SubDetailsList(
            CoreR.drawable.ic_proton_cross,
            ProtonTheme.colors.notificationError,
            R.string.info_dialog_ipv6_traffic_drawbacks_title,
            R.string.info_dialog_ipv6_traffic_drawbacks1,
            R.string.info_dialog_ipv6_traffic_drawbacks2,
        )
    }
}

@Composable
private fun IPV6SubDetailsList(
    @DrawableRes iconRes: Int,
    iconTint: Color,
    @StringRes titleRes: Int,
    vararg items: Int,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Text(
            stringResource(titleRes),
            style = ProtonTheme.typography.body1Bold,
        )
        items.forEach { item ->
            Row {
                Icon(
                    painter = painterResource(iconRes),
                    tint = iconTint,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(16.dp)
                )
                Text(
                    stringResource(item),
                    style = ProtonTheme.typography.body2Regular,
                )
            }
        }
    }
}

@Composable
private fun SubDetailsIpAddress(myIP: IpPair, vpnIP: IpPair, modifier: Modifier = Modifier) {
    Column(modifier) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides ProtonTheme.typography.body2Regular
            ) {
                if (myIP.ipV6 == null) {
                    IPRowHideIfNotEmpty(R.string.connection_details_my_ip, myIP.ipV4)
                } else {
                    IPRowHideIfNotEmpty(R.string.connection_details_my_ipv4, myIP.ipV4)
                    IPRowHideIfNotEmpty(R.string.connection_details_my_ipv6, myIP.ipV6)
                }
                if (vpnIP.ipV6 == null) {
                    IPRow(R.string.connection_details_vpn_ip, vpnIP.ipV4)
                } else {
                    IPRow(R.string.connection_details_vpn_ipv4, vpnIP.ipV4)
                    IPRow(R.string.connection_details_vpn_ipv6, vpnIP.ipV6)
                }
            }
        }
        Text(
            text = stringResource(R.string.info_dialog_ipaddress_description),
            style = ProtonTheme.typography.body2Regular,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}

@Composable
private fun IPRowHideIfNotEmpty(@StringRes titleRes: Int, value: String) {
    if (value.isNotBlank()) {
        IPRowHidden(titleRes, value)
    } else {
        IPRow(titleRes, stringResource(R.string.connection_details_unknown_ip))
    }
}

@Composable
private fun IPRow(@StringRes titleRes: Int, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = titleRes),
            color = ProtonTheme.colors.textWeak,
            modifier = Modifier.padding(end = 2.dp)
        )
        Text(
            text = value,
            modifier = Modifier.padding(2.dp) // Padding to keep size consistent with IPRowHidden
        )
    }
}

@Composable
private fun IPRowHidden(@StringRes titleRes: Int, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(id = titleRes),
            color = ProtonTheme.colors.textWeak,
            modifier = Modifier.padding(end = 2.dp)
        )

        var isIpVisible by remember { mutableStateOf(false) }
        val accessibilityDescription = stringResource(id = R.string.accessibility_show_ip)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClickLabel = accessibilityDescription) { isIpVisible = !isIpVisible }
                .semantics(mergeDescendants = true) {}
                .padding(2.dp)
        ) {
            Text(if (isIpVisible) value else "************")
            Icon(
                painter = painterResource(
                    id = if (isIpVisible) CoreR.drawable.ic_proton_eye_slash else CoreR.drawable.ic_proton_eye,
                ),
                tint = ProtonTheme.colors.iconHint,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(16.dp)
            )
        }
    }
}

@Composable
private fun SubDetailsStreaming(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(id = R.string.info_dialog_how_to_stream),
            style = ProtonTheme.typography.body2Medium,
        )

        val steps = listOf(
            stringResource(id = R.string.info_dialog_how_to_stream_step_1),
            stringResource(id = R.string.info_dialog_how_to_stream_step_2),
            stringResource(id = R.string.info_dialog_how_to_stream_step_3)
        )

        val context = LocalContext.current
        val numberFormat = remember { NumberFormat.getInstance(context.resources.configuration.currentLocale()) }

        Column(
            modifier = Modifier.semantics(mergeDescendants = true, properties = {})
        ) {
            steps.forEachIndexed { index, step ->
                Row {
                    Text(
                        text = "${numberFormat.format(index + 1)}. ",
                        style = ProtonTheme.typography.body2Regular,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(
                        text = "$step",
                        style = ProtonTheme.typography.body2Regular,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

            }
        }
    }
}

@Composable
private fun SubDetailsComposableServerLoad(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ServerLoadInfoBar(
            modifier = Modifier.fillMaxWidth()
        )
        ServerLoadLegendItem(
            progress = 0.3F,
            title = stringResource(id = R.string.connection_details_server_load_low_title),
            details = stringResource(id = R.string.connection_details_server_load_low_description),
        )
        ServerLoadLegendItem(
            progress = 0.76F,
            title = stringResource(id = R.string.connection_details_server_load_medium_title),
            details = stringResource(id = R.string.connection_details_server_load_medium_description),
        )
        ServerLoadLegendItem(
            progress = 0.95F,
            title = stringResource(id = R.string.connection_details_server_load_high_title),
            details = stringResource(id = R.string.connection_details_server_load_high_description),
        )
    }
}

@Composable
private fun SubDetailsComposableP2P(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        P2PContentBullet(
            R.drawable.p2p_servers,
            R.string.info_p2p_what_title,
        ) {
            Text(text = stringResource(id = R.string.info_p2p_what_description))
        }
        P2PContentBullet(
            R.drawable.p2p_download,
            R.string.info_p2p_torrenting_title,
        ) {
            Text(text = stringResource(id = R.string.info_p2p_torrenting_description))
        }
        P2PContentBullet(
            R.drawable.p2p_speed,
            R.string.info_p2p_why_title,
        ) {
            TextBulletRow(R.string.info_p2p_why_description_speed)
            TextBulletRow(R.string.info_p2p_why_description_logs)
            TextBulletRow(R.string.info_p2p_why_description_hide)
        }
    }
}

@Composable
private fun P2PContentBullet(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    description: @Composable () -> Unit
) {
    Row {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 12.dp)
                .size(48.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(id = titleRes),
                style = ProtonTheme.typography.body2Medium,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            ProvideTextStyle(value = ProtonTheme.typography.body2Regular) {
                description()
            }
        }
    }
}

@Composable
private fun SubDetailsComposableSecureCore(modifier: Modifier) {
    Row(modifier) {
        val compareModifier = Modifier
            .weight(1f)
            .semantics(mergeDescendants = true) {}
        SecureCoreCompare(
            R.string.info_secure_core_standard_header,
            R.drawable.info_secure_core_standard_connection,
            listOf(
                CoreR.drawable.ic_proton_lock_filled to R.string.info_secure_core_standard_high_privacy,
                CoreR.drawable.ic_proton_bolt to R.string.info_secure_core_standard_lower_latency
            ),
            modifier = compareModifier
        )
        Spacer(modifier = Modifier.width(16.dp))
        SecureCoreCompare(
            R.string.info_secure_core_secure_header,
            R.drawable.info_secure_core_connection,
            listOf(
                CoreR.drawable.ic_proton_locks_filled to R.string.info_secure_core_advanced_privacy,
                CoreR.drawable.ic_proton_hourglass to R.string.info_secure_core_higher_latency
            ),
            modifier = compareModifier
        )
    }
}

@Composable
private fun SecureCoreCompare(
    @StringRes headerRes: Int,
    @DrawableRes imageRes: Int,
    bullets: List<Pair<Int, Int>>,
    modifier: Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SecureCoreContentHeader(
            headerRes,
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
        )
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .rtlMirror()
        )
        SecureCoreContentBulletList(
            modifier = Modifier.padding(vertical = 12.dp),
            bullets = bullets
        )
    }
}

@Composable
private fun SecureCoreContentBulletList(modifier: Modifier, bullets: List<Pair<Int, Int>>) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        bullets.forEach { (iconRes, textRes) ->
            Row {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = ProtonTheme.colors.iconNorm,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp)
                )
                Text(
                    text = stringResource(id = textRes),
                    style = ProtonTheme.typography.captionRegular,
                )
            }
        }
    }
}

@Composable
private fun SecureCoreContentHeader(
    @StringRes textRes: Int,
    modifier: Modifier
) {
    Text(
        text = stringResource(id = textRes),
        style = ProtonTheme.typography.captionMedium,
        color = ProtonTheme.colors.textWeak,
        modifier = modifier,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ServerLoadLegendItem(
    progress: Float,
    title: String,
    details: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true, properties = {})
    ) {
        ServerLoadBar(progress)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp)
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

@Composable
private fun GenericLearnMore(
    title: String,
    details: String?,
    modifier: Modifier = Modifier,
    @StringRes learnMoreLabelRes: Int = R.string.connection_details_learn_more,
    @DrawableRes imageRes: Int? = null,
    subDetailsComposable: (@Composable () -> Unit)? = null,
    onLearnMoreClick: (() -> Unit)? = null,
    onGotItClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = ProtonTheme.typography.subheadlineNorm,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        imageRes?.let {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )
        }
        details?.let {
            Text(
                text = details,
                style = ProtonTheme.typography.defaultSmallNorm,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        subDetailsComposable?.invoke()
        VerticalSpacer(height = 8.dp)
        if (onLearnMoreClick != null) {
            VpnOutlinedButton(
                stringResource(id = learnMoreLabelRes),
                onClick = onLearnMoreClick,
                isExternalLink = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }
        if (onGotItClick != null) {
            VpnSolidButton(
                stringResource(id = R.string.got_it),
                onClick = onGotItClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }
    }
}

@VisibleForTesting
class InfoTypePreviewProvider : PreviewParameterProvider<InfoType> {
    override val values get() = sequenceOf(
        InfoType.SecureCore,
        InfoType.VpnSpeed,
        InfoType.ServerLoad,
        InfoType.Protocol,
        InfoType.Tor,
        InfoType.P2P,
        InfoType.SmartRouting,
        InfoType.Streaming,
        InfoType.Profiles,
        InfoType.IpAddress(
            myIP = IpPair("1.2.3.4", null),
            vpnIP = IpPair("5.6.7.8", ipV6 = "1234:5678:90ab:cdef:1234:5678:90ab:cdef")
        ),
        InfoType.IPv6Traffic,
    )
}

@ProtonVpnPreview
@Composable
private fun Previews(
    @PreviewParameter(InfoTypePreviewProvider::class) info: InfoType
) {
    ProtonVpnPreview {
        InfoSheetContent(info = info, onOpenUrl = {})
    }
}
