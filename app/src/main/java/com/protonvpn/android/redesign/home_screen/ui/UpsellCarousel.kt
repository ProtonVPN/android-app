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
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.optional
import com.protonvpn.android.base.ui.rtlMirror
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.ViewUtils.toDp
import me.proton.core.compose.theme.ProtonTheme
import kotlin.math.roundToInt

private class PageScope(
    val roundedServerCount: Int,
    val countriesCount: Int,
)

private class Page(
    val upgradeSource: UpgradeSource,
    val content: @Composable PageScope.(Modifier) -> Unit,
)

private fun createPages() = buildList {
    add(
        Page(
            UpgradeSource.COUNTRIES,
        ) { modifier -> UpsellCardCountries(roundedServerCount, countriesCount, modifier) }
    )
    add(
        Page(
            UpgradeSource.VPN_ACCELERATOR,
        ) { modifier -> UpsellCardFasterBrowsing(modifier) }
    )
    add(
        Page(
            UpgradeSource.STREAMING,
        ) { modifier -> UpsellCardStreaming(modifier) }
    )
    add(
        Page(
            UpgradeSource.NETSHIELD,
        ) { modifier -> UpsellCardNetShield(modifier) }
    )
    add(
        Page(
            UpgradeSource.SECURE_CORE,
        ) { modifier -> UpsellCardSecureCore(modifier) }
    )
    add(
        Page(
            UpgradeSource.P2P,
        ) { modifier -> UpsellCardP2P(modifier) }
    )
    add(
        Page(
            UpgradeSource.DEVICES,
        ) { modifier -> UpsellCardDevices(modifier) }
    )
    add(
        Page(
            UpgradeSource.TOR,
        ) { modifier -> UpsellCardTor(modifier) }
    )
    add(
        Page(
            UpgradeSource.SPLIT_TUNNELING,
        ) { modifier -> UpsellCardSplitTunneling(modifier) }
    )
    add(
        Page(
            UpgradeSource.PROFILES,
        ) { modifier -> UpsellCardProfiles(modifier) }
    )
    add(
        Page(
            UpgradeSource.ADVANCED_CUSTOMIZATION,
        ) { modifier -> UpsellCardCustomization(modifier) }
    )
}

@Composable
fun HomeUpsellCarousel(
    state: UpsellCarouselState,
    horizontalMargin: Dp,
    onOpenUpgradeScreen: (UpgradeSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageScope = remember(state.roundedServerCount, state.countryCount) {
        PageScope(state.roundedServerCount, state.countryCount)
    }
    var minHeight by remember { mutableFloatStateOf(0F) }
    val pages = remember { createPages() }
    HorizontalPager(
        state = rememberPagerState { pages.size },
        contentPadding = PaddingValues(horizontal = horizontalMargin),
        pageSpacing = 8.dp,
        pageSize = UpsellCarouselPageSize,
        beyondViewportPageCount = pages.size,
        verticalAlignment = Alignment.Top,
        modifier = modifier.onGloballyPositioned { minHeight = it.size.height.toDp() },
    ) { pageIndex ->
        val pageSizeModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
        val page = pages[pageIndex]
        with(page) {
            pageScope.content(
                pageSizeModifier.clickable(onClick = { onOpenUpgradeScreen(upgradeSource) })
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
        requiresImageRltMirror = true,
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
        requiresImageRltMirror = true,
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
        imageRes = R.drawable.upsell_multiple_devices,
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
private fun UpsellCardProfiles(
    modifier: Modifier = Modifier
) {
    UpsellCard(
        title = stringResource(R.string.upsell_card_profiles_title),
        description = stringResource(R.string.upsell_card_profiles_description),
        imageRes = R.drawable.upsell_card_profiles,
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
        requiresImageRltMirror = true,
    )
}

@Composable
private fun UpsellCard(
    title: String,
    description: String,
    @DrawableRes imageRes: Int,
    modifier: Modifier = Modifier,
    requiresImageRltMirror: Boolean = false,
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
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .optional(
                        predicate = { requiresImageRltMirror },
                        modifier = Modifier.rtlMirror(),
                    ),
                painter = painterResource(id = imageRes),
                contentDescription = null,
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
    ProtonVpnPreview {
        UpsellCardCountries(roundedServerCount = 1600, countriesCount = 63)
    }
}

@Preview
@Composable
private fun PreviewHomeUpsellCarousel() {
    ProtonVpnPreview {
        HomeUpsellCarousel(
            UpsellCarouselState(
                roundedServerCount = 1500,
                countryCount = 20,
            ),
            horizontalMargin = 16.dp,
            onOpenUpgradeScreen = { _ -> },
            modifier = Modifier.heightIn(min = 128.dp)
        )
    }
}
