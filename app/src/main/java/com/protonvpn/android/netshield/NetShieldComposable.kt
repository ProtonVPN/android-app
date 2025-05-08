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
package com.protonvpn.android.netshield

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.SettingsFeatureToggle
import com.protonvpn.android.base.ui.theme.NoTrim
import com.protonvpn.android.base.ui.volumeBytesToString
import com.protonvpn.android.redesign.settings.ui.DnsConflictBanner
import com.protonvpn.android.vpn.DnsOverride
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import me.proton.core.presentation.R as CoreR

@Composable
fun NetShieldView(state: NetShieldViewState, onNavigateToSubsetting: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onNavigateToSubsetting)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier
                .semantics(mergeDescendants = true) {
                    onClick { onNavigateToSubsetting(); true }
                }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = state.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(20.dp)
            )
            Text(
                text = stringResource(R.string.netshield_feature_name),
                style = ProtonTheme.typography.body2Regular,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = stringResource(state.stateRes),
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.textWeak,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .testTag("netshieldState")
            )
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_proton_chevron_right),
                contentDescription = null,
                tint = ProtonTheme.colors.iconWeak,
                modifier = Modifier.size(16.dp)
            )
        }
        if (state is NetShieldViewState.Available) {
            AnimatedVisibility(state.bandwidthShown) {
                BandwidthStatsRow(
                    stats = state.netShieldStats,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun BandwidthStatsRow(
    stats: NetShieldStats,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true, properties = {}),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val adsCount = stats.adsBlocked
        val trackerCount = stats.trackersBlocked
        val dataBytesSaved = stats.savedBytes
        val columnModifier = Modifier
            .weight(1f)
        BandwidthColumn(
            title = pluralStringResource(id = R.plurals.netshield_ads_blocked, count = adsCount.toInt()),
            content = adsCount.toString(),
            modifier = columnModifier.testTag("adsBlocked")
        )
        BandwidthColumn(
            title = pluralStringResource(id = R.plurals.netshield_trackers_stopped, count = trackerCount.toInt()),
            content = trackerCount.toString(),
            modifier = columnModifier.testTag("trackersStopped")
        )
        BandwidthColumn(
            title = stringResource(id = R.string.netshield_data_saved),
            content = dataBytesSaved.volumeBytesToString(),
            contentDescription = dataBytesSaved.volumeBytesToString(useAbbreviations = false),
            modifier = columnModifier.testTag("bandwidthSaved")
        )
    }
}

@Composable
private fun BandwidthColumn(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = content,
            style = ProtonTheme.typography.body1Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics {
                    if (contentDescription != null) this.contentDescription = contentDescription
                }
                .testTag("value")
        )
        Text(
            text = title,
            style = ProtonTheme.typography.captionWeak,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun NetShieldBottomCustomDns(
    onCustomDnsLearnMore: () -> Unit,
    onDisableCustomDns: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DnsConflictBanner(
        titleRes = R.string.custom_dns_conflict_banner_netshield_title,
        descriptionRes = R.string.custom_dns_conflict_banner_netshield_description,
        buttonRes = R.string.custom_dns_conflict_banner_disable_custom_dns_button,
        onLearnMore = onCustomDnsLearnMore,
        onButtonClicked = onDisableCustomDns,
        modifier = modifier,
        backgroundColor = Color.Transparent
    )
}

@Composable
fun NetShieldBottomPrivateDns(
    onPrivateDnsLearnMore: () -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DnsConflictBanner(
        titleRes = R.string.private_dns_conflict_banner_netshield_title,
        descriptionRes = R.string.private_dns_conflict_banner_netshield_description,
        buttonRes = R.string.private_dns_conflict_banner_network_settings_button,
        onLearnMore = onPrivateDnsLearnMore,
        onButtonClicked = onOpenPrivateDnsSettings,
        modifier = modifier,
        backgroundColor = Color.Transparent
    )
}

@Composable
fun NetShieldBottomSettings(
    currentNetShield: NetShieldProtocol,
    onValueChanged: (protocol: NetShieldProtocol) -> Unit,
    onNetShieldLearnMore: () -> Unit
) {
    val switchEnabled = currentNetShield != NetShieldProtocol.DISABLED

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)) {
        SettingsFeatureToggle(
            label = stringResource(id = R.string.settings_netshield_title),
            checked = switchEnabled,
            onCheckedChange = { isEnabled ->
                onValueChanged(if (isEnabled) NetShieldProtocol.ENABLED_EXTENDED else NetShieldProtocol.DISABLED)
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )
        AnnotatedClickableText(
            fullText = stringResource(
                id = R.string.netshield_settings_description_not_html,
                stringResource(id = R.string.learn_more)
            ),
            annotatedPart = stringResource(id = R.string.learn_more),
            onAnnotatedClick = onNetShieldLearnMore,
            onAnnotatedOutsideClick = null,
            modifier = Modifier.padding(vertical = 8.dp),
            style = ProtonTheme.typography.body2Regular,
            annotatedStyle = ProtonTheme.typography.body2Medium,
            color = ProtonTheme.colors.textWeak,
        )

        Surface(
            shape = RoundedCornerShape(size = 8.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, ProtonTheme.colors.textDisabled),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.netshield_what_data_means),
                    style = ProtonTheme.typography.body2Medium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                StatsDescriptionRows()
            }
        }

        Text(
            stringResource(R.string.netshield_setting_warning),
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatsDescriptionRows(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatsDescription(
            iconId = R.drawable.netshield_icon_ads,
            titleId = R.string.netshield_ads_title,
            detailsId = R.string.netshield_ads_details
        )
        StatsDescription(
            iconId = R.drawable.netshield_icon_trackers,
            titleId = R.string.netshield_trackers_title,
            detailsId = R.string.netshield_trackers_details
        )
        StatsDescription(
            iconId = R.drawable.netshield_icon_data,
            titleId = R.string.netshield_data_title,
            detailsId = R.string.netshield_data_details
        )
    }
}

@Composable
private fun StatsDescription(
    @DrawableRes iconId: Int,
    @StringRes titleId: Int,
    @StringRes detailsId: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painterResource(iconId),
            contentDescription = null
        )
        Column {
            Text(
                text = stringResource(id = titleId),
                style = ProtonTheme.typography.body2Medium.copy(lineHeightStyle = LineHeightStyle.NoTrim),
            )
            Text(
                text = stringResource(id = detailsId),
                style = ProtonTheme.typography.body2Regular.copy(lineHeightStyle = LineHeightStyle.NoTrim),
                color = ProtonTheme.colors.textWeak,
            )
        }
    }
}

@ProtonVpnPreview
@Composable
private fun NetShieldBottomPreview() {
    ProtonVpnPreview {
        NetShieldBottomSettings(
            currentNetShield = NetShieldProtocol.DISABLED,
            onValueChanged = {},
            onNetShieldLearnMore = {}
        )
    }
}

@ProtonVpnPreview
@Composable
private fun NetShieldBottomSheetPreview() {
    ProtonVpnPreview {
        NetShieldBottomSettings(
            currentNetShield = NetShieldProtocol.DISABLED,
            onValueChanged = {},
            onNetShieldLearnMore = {}
        )
    }
}
@ProtonVpnPreview
@Composable
private fun NetShieldOnPreview() {
    ProtonVpnPreview {
        NetShieldView(
            state = NetShieldViewState.Available(
                protocol = NetShieldProtocol.ENABLED_EXTENDED,
                netShieldStats = NetShieldStats(
                    adsBlocked = 3,
                    trackersBlocked = 0,
                    savedBytes = 2000
                )
            ),
            onNavigateToSubsetting = {}
        )
    }
}

@ProtonVpnPreview
@Composable
private fun NetShieldOffPreview() {
    ProtonVpnPreview {
        NetShieldView(
            state = NetShieldViewState.Available(
                protocol = NetShieldProtocol.DISABLED,
                netShieldStats = NetShieldStats(
                    adsBlocked = 3,
                    trackersBlocked = 5,
                )
            ),
            onNavigateToSubsetting = {}
        )
    }
}

@ProtonVpnPreview
@Composable
private fun NetShieldUnavailablePreview() {
    ProtonVpnPreview {
        NetShieldView(
            state = NetShieldViewState.Unavailable(
                protocol = NetShieldProtocol.ENABLED_EXTENDED,
                DnsOverride.CustomDns
            ),
            onNavigateToSubsetting = {}
        )
    }
}
