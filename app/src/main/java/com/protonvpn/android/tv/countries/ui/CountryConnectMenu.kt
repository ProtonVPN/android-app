/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.tv.countries.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.BoxWithVerticalScrollEdgeFade
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import com.protonvpn.android.base.ui.optional
import com.protonvpn.android.base.ui.theme.backgroundSelected
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.tv.ui.ActiveDotTv
import com.protonvpn.android.tv.ui.PlusBadgeTv
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

private val MenuShape = RoundedCornerShape(16.dp)
private val OptionIconSize = 16.dp
private val SelectKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

// A side drawer with the ways to connect to a country: fastest, random or a specific city/state.
@Composable
fun CountryConnectMenuDialog(
    state: CountryConnectMenuUiState,
    onOptionClick: (CountryConnectMenuOption) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInHorizontally { width -> width / 2 } + fadeIn(),
            ) {
                CountryConnectMenu(state, onOptionClick, Modifier.fillMaxHeight())
            }
        }
    }
}

@Composable
internal fun CountryConnectMenu(
    state: CountryConnectMenuUiState,
    onOptionClick: (CountryConnectMenuOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    // Focus the option the user is connected with, if any.
    val initialFocusIndex = remember { state.options.indexOfFirst { it.connected }.coerceAtLeast(0) }
    val initialFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { initialFocusRequester.requestFocus() }
    // The menu is opened with a long press, the key may still be held down when it appears.
    var acceptsSelectKey by remember { mutableStateOf(false) }

    BoxWithVerticalScrollEdgeFade(
        scrollableState = scrollState,
        topFadeHeight = 32.dp,
        bottomFadeHeight = 32.dp,
        topFadeColor = ProtonTheme.colors.backgroundDeep,
        bottomFadeColor = ProtonTheme.colors.backgroundDeep,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                // Ignore the repeats and release of the key that opened the menu, only a new press selects an option.
                if (!acceptsSelectKey && event.key in SelectKeys) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        acceptsSelectKey = true
                        false
                    } else {
                        true
                    }
                } else {
                    false
                }
            }
            .width(280.dp)
            .shadow(8.dp, MenuShape)
            .background(ProtonTheme.colors.backgroundDeep, MenuShape)
            .padding(20.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.verticalScroll(scrollState)
        ) {
            MenuHeader(
                state.countryName,
                state.flagResource,
                Modifier.fillMaxWidth(),
            )
            val itemModifier = Modifier.fillMaxWidth()
            state.options.forEachIndexed { index, option ->
                CountryConnectMenuItem(
                    option = option,
                    onClick = { onOptionClick(option) },
                    modifier = itemModifier
                        .optional({ index == initialFocusIndex }, Modifier.focusRequester(initialFocusRequester))
                )
            }
        }
    }
}

@Composable
private fun MenuHeader(
    countryName: String,
    @DrawableRes flagResource: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
    ) {
        Image(
            painter = painterResource(flagResource),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 30.dp, height = 20.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Text(
            countryName,
            modifier = Modifier.semantics { heading() },
            style = ProtonTheme.typography.body1Regular,
            color = ProtonTheme.colors.textNorm,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CountryConnectMenuItem(
    option: CountryConnectMenuOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val clickLabel = option.clickLabel()?.let { stringResource(it) }
    val contentAlpha = if (!isFocused && (option.inMaintenance || !option.available)) 0.3f else 1f
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = ProtonTheme.colors.textNorm,
            focusedContainerColor = ProtonTheme.colors.backgroundSelected,
            focusedContentColor = ProtonTheme.colors.textInverted,
            pressedContainerColor = ProtonTheme.colors.backgroundSelected,
            pressedContentColor = ProtonTheme.colors.textInverted,
        ),
        // Only adds the label, the action comes from Surface's own semantics.
        modifier = modifier.semantics { if (clickLabel != null) onClick(label = clickLabel, action = null) },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .alpha(contentAlpha)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                painter = painterResource(option.type.iconRes()),
                contentDescription = null,
                modifier = Modifier.size(OptionIconSize),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    option.type.label(),
                    style = ProtonTheme.typography.body2Regular,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (option.inMaintenance) {
                    Text(
                        stringResource(R.string.tv_country_menu_in_maintenance),
                        style = ProtonTheme.typography.captionRegular,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                option.inMaintenance ->
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_proton_wrench),
                        contentDescription = null,
                        modifier = Modifier.size(OptionIconSize),
                    )
                !option.available ->
                    if (isFocused) {
                        // The badge is taller than the text, let it extend into the padding so that the row keeps
                        // its height when focused.
                        PlusBadgeTv(
                            Modifier
                                .height(OptionIconSize)
                                .wrapContentHeight(unbounded = true)
                        )
                    }
                option.connected ->
                    ActiveDotTv()
            }
        }
    }
}

// Matches the click handling in TvHomeViewModel.onConnectMenuOptionClick.
@StringRes
private fun CountryConnectMenuOption.clickLabel(): Int? = when {
    inMaintenance -> null
    !available -> R.string.accessibility_action_upgrade
    connected -> R.string.accessibility_action_disconnect
    // TODO: remove once there is a ConnectIntent for a random server in a country.
    type == CountryConnectMenuOption.Type.Random -> null
    else -> R.string.accessibility_action_connect
}

@DrawableRes
private fun CountryConnectMenuOption.Type.iconRes(): Int = when (this) {
    CountryConnectMenuOption.Type.Fastest -> R.drawable.ic_proton_bolt_filled
    CountryConnectMenuOption.Type.Random -> CoreR.drawable.ic_proton_arrows_swap_right
    is CountryConnectMenuOption.Type.CityState -> CoreR.drawable.ic_proton_map_pin
}

@Composable
private fun CountryConnectMenuOption.Type.label(): String = when (this) {
    CountryConnectMenuOption.Type.Fastest -> stringResource(R.string.tv_country_menu_fastest)
    CountryConnectMenuOption.Type.Random -> stringResource(R.string.tv_country_menu_random)
    is CountryConnectMenuOption.Type.CityState -> name
}

private fun previewMenuState(
    countryId: CountryId = CountryId("gb"),
    countryName: String = "United Kingdom",
    @DrawableRes flagResource: Int = R.drawable.flag_tv_gb,
    cities: List<String> = listOf("Belfast", "Cardiff", "Edinburgh", "Glasgow", "London", "Manchester"),
    transform: (CountryConnectMenuOption) -> CountryConnectMenuOption = { it },
) = CountryConnectMenuUiState(
    countryId = countryId,
    countryName = countryName,
    flagResource = flagResource,
    options = buildList {
        add(CountryConnectMenuOption(CountryConnectMenuOption.Type.Fastest, true, false, false))
        add(CountryConnectMenuOption(CountryConnectMenuOption.Type.Random, true, false, false))
        cities.forEach { city ->
            val type = CountryConnectMenuOption.Type.CityState(CityStateId(city, isState = false), city)
            add(CountryConnectMenuOption(type, available = true, inMaintenance = false, connected = false))
        }
    }.map(transform),
)

@ProtonVpnTvPreview
@Composable
private fun CountryConnectMenuConnectedPreview() {
    ProtonVpnTvPreview {
        CountryConnectMenu(
            previewMenuState { option ->
                option.copy(connected = option.type == CountryConnectMenuOption.Type.Fastest)
            },
            onOptionClick = {},
            modifier = Modifier.padding(24.dp),
        )
    }
}

@ProtonVpnTvPreview
@Composable
private fun CountryConnectMenuMaintenancePreview() {
    ProtonVpnTvPreview {
        CountryConnectMenu(
            previewMenuState(
                countryId = CountryId("au"),
                countryName = "Australia",
                flagResource = R.drawable.flag_tv_au,
                cities = listOf("Adelaide", "Brisbane", "Melbourne", "Perth", "Sydney"),
            ) { option ->
                val type = option.type
                option.copy(
                    inMaintenance = type is CountryConnectMenuOption.Type.CityState && type.name in setOf("Brisbane", "Perth")
                )
            },
            onOptionClick = {},
            modifier = Modifier.padding(24.dp),
        )
    }
}

@ProtonVpnTvPreview
@Composable
private fun CountryConnectMenuFreeUserPreview() {
    ProtonVpnTvPreview {
        CountryConnectMenu(
            previewMenuState(
                countryId = CountryId("us"),
                countryName = "United States",
                flagResource = R.drawable.flag_tv_us,
                cities = listOf("Arizona", "California", "Colorado", "District Of Columbia", "Florida", "Georgia", "Illinois"),
            ) { option ->
                option.copy(available = option.type == CountryConnectMenuOption.Type.Fastest)
            },
            onOptionClick = {},
            modifier = Modifier.padding(24.dp),
        )
    }
}
