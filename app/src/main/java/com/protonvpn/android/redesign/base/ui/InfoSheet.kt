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

@file:OptIn(ExperimentalMaterial3Api::class)

package com.protonvpn.android.redesign.base.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.VpnOutlinedButton
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.utils.Constants
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionNorm
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultSmallNorm
import me.proton.core.compose.theme.subheadlineNorm
import me.proton.core.presentation.R as CoreR

enum class InfoType {
    SecureCore,
    VpnSpeed,
    ServerLoad,
    Protocol,
    Tor,
    P2P,
    SmartRouting,
}

@Composable
fun InfoSheet(
    info: InfoType?,
    onOpenUrl: (url: String) -> Unit,
    dismissInfo: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (info != null) {
        ModalBottomSheet(
            sheetState = bottomSheetState,
            content = {
                InfoSheetContent(info, onOpenUrl)
            },
            windowInsets = WindowInsets.navigationBars,
            onDismissRequest = dismissInfo
        )
    }
}

@Composable
private fun InfoSheetContent(info: InfoType, onOpenUrl: (url: String) -> Unit) {
    GenericLearnMore(
        title = stringResource(id = info.title),
        details = info.details?.let { stringResource(id = it) },
        imageRes = info.imageRes,
        onLearnMoreClick = {
            onOpenUrl(info.learnMoreUrl)
        },
        subDetailsComposable = { SubDetailsComposable(info) },
        learnMoreLabelRes = info.learnMoreLabel
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
}

private val InfoType.imageRes get() = when (this) {
    InfoType.SecureCore,
    InfoType.VpnSpeed,
    InfoType.ServerLoad,
    InfoType.Protocol,
    InfoType.Tor,
    InfoType.P2P -> null
    InfoType.SmartRouting -> R.drawable.info_smart_routing
}

private val InfoType.details get() = when (this) {
    InfoType.SecureCore -> R.string.info_dialog_secure_core_description
    InfoType.VpnSpeed -> R.string.connection_details_vpn_speed_description
    InfoType.ServerLoad -> R.string.connection_details_server_load_description
    InfoType.Protocol -> R.string.connection_details_protocol_description
    InfoType.Tor -> R.string.info_dialog_tor_description
    InfoType.P2P -> null
    InfoType.SmartRouting -> R.string.info_dialog_smart_routing_description
}

private val InfoType.learnMoreLabel get() = when (this) {
    InfoType.SecureCore,
    InfoType.VpnSpeed,
    InfoType.ServerLoad,
    InfoType.Protocol,
    InfoType.SmartRouting -> R.string.connection_details_learn_more
    InfoType.Tor -> R.string.info_dialog_button_learn_more_tor
    InfoType.P2P -> R.string.info_dialog_button_learn_more_p2p
}

private val InfoType.learnMoreUrl get() = when (this) {
    InfoType.SecureCore -> Constants.SECURE_CORE_INFO_URL
    InfoType.VpnSpeed -> Constants.URL_SPEED_LEARN_MORE
    InfoType.ServerLoad -> Constants.URL_LOAD_LEARN_MORE
    InfoType.Protocol -> Constants.URL_PROTOCOL_LEARN_MORE
    InfoType.Tor -> Constants.URL_TOR_LEARN_MORE
    InfoType.P2P -> Constants.URL_P2P_LEARN_MORE
    InfoType.SmartRouting -> Constants.URL_SMART_ROUTING_LEARN_MORE
}

@Composable
private fun SubDetailsComposable(info: InfoType) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp)
    when (info) {
        InfoType.VpnSpeed, InfoType.Tor, InfoType.Protocol, InfoType.SmartRouting -> {}
        InfoType.ServerLoad -> SubDetailsComposableServerLoad(modifier)
        InfoType.SecureCore -> SubDetailsComposableSecureCore(modifier)
        InfoType.P2P -> SubDetailsComposableP2P(modifier)
    }
}

@Composable
private fun SubDetailsComposableServerLoad(modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
            Text(
                text = stringResource(id = R.string.info_p2p_what_description),
                style = ProtonTheme.typography.captionRegular,
            )
        }
        P2PContentBullet(
            R.drawable.p2p_download,
            R.string.info_p2p_torrenting_title,
        ) {
            Text(
                text = stringResource(id = R.string.info_p2p_torrenting_description),
                style = ProtonTheme.typography.captionRegular,
            )
        }
        P2PContentBullet(
            R.drawable.p2p_speed,
            R.string.info_p2p_why_title,
        ) {
            P2PBulletRow(R.string.info_p2p_why_description_speed)
            P2PBulletRow(R.string.info_p2p_why_description_logs)
            P2PBulletRow(R.string.info_p2p_why_description_hide)
        }
    }
}

@Composable
private fun P2PBulletRow(@StringRes textRes: Int) {
    Row(Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = "•",
            style = ProtonTheme.typography.captionRegular,
            modifier = Modifier.padding(horizontal = 5.dp)
        )
        Text(
            text = stringResource(id = textRes),
            style = ProtonTheme.typography.captionRegular,
        )
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
            description()
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
            modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth()
        )
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().rtlMirror()
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
    @StringRes learnMoreLabelRes: Int = R.string.connection_details_learn_more,
    @DrawableRes imageRes: Int? = null,
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
        VpnOutlinedButton(
            stringResource(id = learnMoreLabelRes),
            onClick = onLearnMoreClick,
            isExternalLink = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private class InfoTypePreviewProvider : PreviewParameterProvider<InfoType> {
    override val values get() = InfoType.entries.asSequence()
}

@Preview
@Composable
private fun Previews(
    @PreviewParameter(InfoTypePreviewProvider::class) info: InfoType
) {
    LightAndDarkPreview {
        Surface {
            InfoSheetContent(info = info, onOpenUrl = {})
        }
    }
}
