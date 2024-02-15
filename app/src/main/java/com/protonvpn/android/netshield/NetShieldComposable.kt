/*
 * Copyright (c) 2023. Proton Technologies AG
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.utils.ConnectionTools
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionNorm
import me.proton.core.compose.theme.captionStrongNorm
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallStrongNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.defaultStrongNorm
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.compose.theme.overlineWeak
import me.proton.core.presentation.R as CoreR

@Preview
@Composable
fun UpgradeNetShieldFree(
    navigateToUpgrade: () -> Unit = {}
) = UpgradePromo(
    R.string.netshield_free_title,
    R.string.netshield_free_description,
    navigateToUpgrade = navigateToUpgrade
)

@Preview
@Composable
fun UpgradeNetShieldBusiness() =
    UpgradePromo(
        titleRes = R.string.netshield_business_title,
        descriptionRes = R.string.netshield_business_description,
        badgeIconRes = R.drawable.vpn_business_badge,
        navigateToUpgrade = null
    )

@Composable
fun UpgradePromo(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    @DrawableRes iconRes: Int = R.drawable.ic_netshield_promo,
    @DrawableRes badgeIconRes: Int = ResourcesCompat.ID_NULL,
    navigateToUpgrade: (() -> Unit)?
) {
    val rowClickModifier = if (navigateToUpgrade != null) {
        Modifier
            .clickable(onClick = navigateToUpgrade)
            .semantics(mergeDescendants = true, properties = {})
    } else {
        Modifier
    }
    Row(
        modifier = rowClickModifier
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier
                .wrapContentSize()
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                style = ProtonTheme.typography.defaultStrongNorm,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(descriptionRes),
                style = ProtonTheme.typography.defaultNorm,
            )
        }

        if (badgeIconRes != ResourcesCompat.ID_NULL) {
            Image(
                painter = painterResource(id = badgeIconRes),
                contentDescription = null
            )
        }
        if (navigateToUpgrade != null) {
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_proton_chevron_right),
                tint = ProtonTheme.colors.iconHint,
                contentDescription = null,
                modifier = Modifier.wrapContentSize()
            )
        }
    }
}

@Composable
fun NetShieldView(state: NetShieldViewState, onNavigateToSubsetting: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onNavigateToSubsetting)
    ) {
        Row(
            modifier = Modifier
                .semantics(mergeDescendants = true, properties = {})
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = state.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 4.dp)
            )
            Text(
                text = stringResource(R.string.netshield_feature_name),
                style = ProtonTheme.typography.captionNorm,
                modifier = Modifier.weight(1f)
                )
            Text(
                text = stringResource(state.titleRes),
                style = ProtonTheme.typography.captionWeak,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Icon(
                painter = painterResource(id = CoreR.drawable.ic_proton_chevron_right),
                contentDescription = null,
                tint = ProtonTheme.colors.iconHint,
                modifier = Modifier.size(24.dp)
            )
        }
        AnimatedVisibility(state.bandwidthShown) {
            BandwidthStatsRow(stats = state.netShieldStats)
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
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true, properties = {})
    ) {
        val adsCount = stats.adsBlocked
        val trackerCount = stats.trackersBlocked
        val dataSaved = stats.savedBytes
        val modifier = Modifier
            .weight(1f)
            .padding(2.dp)
        BandwidthColumn(
            title = pluralStringResource(id = R.plurals.netshield_ads_blocked, count = adsCount.toInt()),
            content = adsCount.toString(),
            modifier = modifier.testTag("adsBlocked")
        )
        BandwidthColumn(
            title = pluralStringResource(id = R.plurals.netshield_trackers_stopped, count = trackerCount.toInt()),
            content = trackerCount.toString(),
            modifier = modifier.testTag("trackersStopped")
        )
        BandwidthColumn(
            title = stringResource(id = R.string.netshield_data_saved),
            content = ConnectionTools.bytesToSize(dataSaved),
            modifier = modifier.testTag("bandwidthSaved")
        )
    }
}

@Composable
private fun BandwidthColumn(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = content,
            style = ProtonTheme.typography.defaultSmallStrongNorm,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("value")
        )
        Text(
            text = title,
            style = ProtonTheme.typography.overlineWeak,
            textAlign = TextAlign.Center,
        )
    }
}
@Composable
fun NetShieldBottomComposable(
    currentNetShield: NetShieldProtocol,
    onValueChanged: (protocol: NetShieldProtocol) -> Unit,
    onNetShieldLearnMore: () -> Unit
) {
    val switchState = remember {
        mutableStateOf(currentNetShield != NetShieldProtocol.DISABLED)
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material.Text(
                text = stringResource(id = R.string.settings_netshield_title),
                style = ProtonTheme.typography.defaultNorm,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = switchState.value, onCheckedChange = {
                switchState.value = it
                onValueChanged(if (it) NetShieldProtocol.ENABLED_EXTENDED else NetShieldProtocol.DISABLED)
            })

        }
        AnnotatedClickableText(
            fullText = stringResource(id = R.string.netshield_settings_description_not_html, stringResource(
                id = R.string.learn_more
            )),
            annotatedPart = stringResource(id = R.string.learn_more),
            onAnnotatedClick = onNetShieldLearnMore,
            onAnnotatedOutsideClick = null,
            modifier = Modifier.padding(vertical = 8.dp)
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
                androidx.compose.material.Text(
                    text = stringResource(id = R.string.netshield_what_data_means),
                    style = ProtonTheme.typography.defaultNorm,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                StatsDescriptionRows(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StatsDescriptionRows(modifier: Modifier) {
    Column(modifier = modifier) {
        StatsDescriptionRow(
            titleId = R.string.netshield_ads_title,
            detailsId = R.string.netshield_ads_details
        )
        StatsDescriptionRow(
            titleId = R.string.netshield_trackers_title,
            detailsId = R.string.netshield_trackers_details
        )
        StatsDescriptionRow(
            titleId = R.string.netshield_data_title,
            detailsId = R.string.netshield_data_details
        )
    }
}

@Composable
private fun StatsDescriptionRow(titleId: Int, detailsId: Int) {
    Row(modifier = Modifier.padding(8.dp)) {
        Box(modifier = Modifier.weight(0.7f, fill = true)) {
            androidx.compose.material.Text(
                text = stringResource(id = titleId),
                style = ProtonTheme.typography.captionStrongNorm
            )
        }
        androidx.compose.material.Text(
            text = stringResource(id = detailsId),
            style = ProtonTheme.typography.captionWeak,
            modifier = Modifier.weight(2f)
        )
    }
}

@Preview
@Composable
private fun NetShieldBottomPreview() {
    NetShieldBottomComposable(
        currentNetShield = NetShieldProtocol.DISABLED,
        onValueChanged = {},
        onNetShieldLearnMore = {}
    )
}

@Preview
@Composable
private fun NetShieldBottomSheetPreview() {
    NetShieldBottomComposable(
        currentNetShield = NetShieldProtocol.DISABLED,
        onValueChanged = {},
        onNetShieldLearnMore = {}
    )
}
@Preview
@Composable
private fun NetShieldOnPreview() {
    NetShieldView(
        state =
            NetShieldViewState(
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

@Preview
@Composable
private fun NetShieldOffPreview() {
    NetShieldView(
        state =
            NetShieldViewState(
                protocol = NetShieldProtocol.DISABLED,
                netShieldStats = NetShieldStats(
                    adsBlocked = 3,
                    trackersBlocked = 5,
                )
            ),
        onNavigateToSubsetting = {}
    )
}
