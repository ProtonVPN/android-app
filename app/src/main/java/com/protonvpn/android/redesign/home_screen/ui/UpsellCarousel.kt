/*
 * Copyright (c) 2024. Proton AG
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.LightAndDarkPreview
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.ui.planupgrade.UpgradeAllowLanHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeDevicesHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeNetShieldHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeP2PHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeSecureCoreHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeSplitTunnelingHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeStreamingHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeTorHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradeVpnAcceleratorHighlightsFragment
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ViewUtils.toDp
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultSmallNorm
import kotlin.math.roundToInt
import kotlin.reflect.KClass

private class PageScope(
    val roundedServerCount: Int,
    val countriesCount: Int,
)

private class Page(
    val upgradeDialogFocusPage: KClass<out Fragment>,
    val upgradeSource: UpgradeSource,
    val content: @Composable PageScope.(Modifier) -> Unit,
)

private val Pages = listOf(
    Page(
        UpgradePlusCountriesHighlightsFragment::class,
        UpgradeSource.HOME_CAROUSEL_COUNTRIES,
    ) { modifier -> UpsellCardCountries(roundedServerCount, countriesCount, modifier) },
    Page(
        UpgradeVpnAcceleratorHighlightsFragment::class,
        UpgradeSource.HOME_CAROUSEL_SPEED,
    ) { modifier -> UpsellCardFasterBrowsing(modifier) },
    Page(
        UpgradeStreamingHighlightsFragment::class,
        UpgradeSource.HOME_CAROUSEL_STREAMING,
    ) { modifier -> UpsellCardStreaming(modifier) },
    Page(
        UpgradeNetShieldHighlightsFragment::class,
        UpgradeSource.HOME_CAROUSEL_NETSHIELD,
    ) { modifier -> UpsellCardNetShield(modifier) },
    Page(
        UpgradeSecureCoreHighlightsFragment::class,
        UpgradeSource.HOME_CAROUSEL_SECURE_CORE,
    ) { modifier -> UpsellCardSecureCore(modifier) },
    Page(
        UpgradeP2PHighlightsFragment::class,
        UpgradeSource.HOME_CAROUSEL_P2P,
    ) { modifier -> UpsellCardP2P(modifier) },
    Page(
        UpgradeDevicesHighlightsFragment::class,
        UpgradeSource.HOME_CAROUSEL_MULTIPLE_DEVICES,
    ) { modifier -> UpsellCardDevices(modifier) },
    Page(
        UpgradeTorHighlightsFragment::class,
        UpgradeSource.HOME_CAROUSEL_TOR,
    ) { modifier -> UpsellCardTor(modifier) },
    Page(
        UpgradeSplitTunnelingHighlightsFragment::class,
        UpgradeSource.HOME_CAROUSEL_SPLIT_TUNNELING,
    ) { modifier -> UpsellCardSplitTunneling(modifier) },
    Page(
        UpgradeAllowLanHighlightsFragment::class,
        UpgradeSource.HOME_CAROUSEL_CUSTOMIZATION,
    ) { modifier -> UpsellCardCustomization(modifier) },
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeUpsellCarousel(
    roundedServerCount: Int,
    countriesCount: Int,
    horizontalMargin: Dp,
    onOpenUpgradeScreen: (focusedPage: KClass<out Fragment>, UpgradeSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageScope = remember(roundedServerCount, countriesCount) { PageScope(roundedServerCount, countriesCount) }
    var minHeight by remember { mutableFloatStateOf(0F) }
    HorizontalPager(
        state = rememberPagerState { Pages.size },
        contentPadding = PaddingValues(horizontal = horizontalMargin),
        pageSpacing = 8.dp,
        pageSize = UpsellCarouselPageSize,
        beyondBoundsPageCount = Pages.size,
        verticalAlignment = Alignment.Top,
        modifier = modifier.onGloballyPositioned { minHeight = it.size.height.toDp() },
    ) { pageIndex ->
        val pageSizeModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
        val page = Pages[pageIndex]
        with(page) {
            pageScope.content(
                pageSizeModifier.clickable(onClick = { onOpenUpgradeScreen(upgradeDialogFocusPage, upgradeSource) })
           )
        }
    }
}

@Composable
private fun UpsellCardCountries(
    roundedServerCount: Int,
    countriesCount: Int,
    modifier: Modifier = Modifier
) {
    val countriesText =
        pluralStringResource(id = R.plurals.upgrade_plus_countries, count = countriesCount, countriesCount)
    UpsellCard(
        title = stringResource(R.string.upsell_card_countries_title),
        description = stringResource(
            R.string.upsell_card_countries_description,
            roundedServerCount,
            countriesText
        ),
        imageRes = R.drawable.upsell_card_worldwide,
        modifier = modifier
    )
}

@Composable
private fun UpsellCardFasterBrowsing(
    modifier: Modifier = Modifier
) {
    UpsellCard(
        title = stringResource(R.string.upsell_card_speed_title),
        description = stringResource(R.string.upsell_card_speed_description, Constants.SERVER_SPEED_UP_TO_GBPS),
        imageRes = R.drawable.upsell_card_speed,
        modifier = modifier,
    )
}

@Composable
private fun UpsellCardStreaming(
    modifier: Modifier = Modifier
) {
    UpsellCard(
        title = stringResource(R.string.upsell_card_streaming_title),
        description = stringResource(id = R.string.upsell_card_streaming_description),
        imageRes = R.drawable.upsell_card_streaming,
        modifier = modifier,
    )
}

@Composable
private fun UpsellCardNetShield(
    modifier: Modifier = Modifier
) {
    UpsellCard(
        title = stringResource(R.string.upsell_card_netshield_title),
        description = stringResource(R.string.upsell_card_netshield_description),
        imageRes = R.drawable.upsell_card_netshield,
        modifier = modifier,
    )
}

@Composable
private fun UpsellCardSecureCore(
    modifier: Modifier = Modifier
) {
    UpsellCard(
        title = stringResource(R.string.upsell_card_secure_core_title),
        description = stringResource(R.string.upsell_card_secure_core_description),
        imageRes = R.drawable.upsell_card_secure_core,
        modifier = modifier,
    )
}

@Composable
private fun UpsellCardP2P(
    modifier: Modifier = Modifier
) {
    UpsellCard(
        title = stringResource(R.string.upsell_card_p2p_title),
        description = stringResource(R.string.upsell_card_p2p_description),
        imageRes = R.drawable.upsell_card_p2p,
        modifier = modifier,
    )
}

@Composable
private fun UpsellCardDevices(
    modifier: Modifier = Modifier
) {
    val devices = Constants.MAX_CONNECTIONS_IN_PLUS_PLAN
    UpsellCard(
        title = pluralStringResource(R.plurals.upsell_card_devices_title, count = devices, devices),
        description = stringResource(R.string.upsell_card_devices_description),
        imageRes = R.drawable.upsell_card_secure_core,
        modifier = modifier,
    )
}

@Composable
private fun UpsellCardTor(
    modifier: Modifier = Modifier
) {
    UpsellCard(
        title = stringResource(R.string.upsell_card_tor_title),
        description = stringResource(R.string.upsell_card_tor_description),
        imageRes = R.drawable.upsell_card_tor,
        modifier = modifier,
    )
}

@Composable
private fun UpsellCardSplitTunneling(
    modifier: Modifier = Modifier
) {
    UpsellCard(
        title = stringResource(R.string.upsell_card_split_tunneling_title),
        description = stringResource(R.string.upsell_card_split_tunneling_description),
        imageRes = R.drawable.upsell_card_split_tunneling,
        modifier = modifier,
    )
}

@Composable
private fun UpsellCardCustomization(
    modifier: Modifier = Modifier
) {
    UpsellCard(
        title = stringResource(R.string.upsell_card_customization_title),
        description = stringResource(R.string.upsell_card_customization_description),
        imageRes = R.drawable.upsell_card_customization,
        modifier = modifier,
    )
}

@Composable
private fun UpsellCard(
    title: String,
    description: String,
    @DrawableRes imageRes: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors().copy(containerColor = ProtonTheme.colors.backgroundSecondary),
        shape = ProtonTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                title,
                style = ProtonTheme.typography.body1Medium,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(description, style = ProtonTheme.typography.body1Regular, color = ProtonTheme.colors.textWeak)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private object UpsellCarouselPageSize : PageSize {
    override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int {
        val maxSize = 300.dp
        val pageSize = (availableSpace - 2*pageSpacing - 12.dp.toPx()) / 2
        return pageSize.coerceIn(0f, maxSize.toPx()).roundToInt()
    }
}

@Preview
@Composable
private fun PreviewUpsellCardCountries() {
    LightAndDarkPreview {
        UpsellCardCountries(roundedServerCount = 1600, countriesCount = 63)
    }
}

@Preview
@Composable
private fun PreviewHomeUpsellCarousel() {
    LightAndDarkPreview {
        HomeUpsellCarousel(
            roundedServerCount = 1500,
            countriesCount = 20,
            horizontalMargin = 16.dp,
            onOpenUpgradeScreen = { _, _ -> },
            modifier = Modifier.heightIn(min = 128.dp)
        )
    }
}
