/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.widget.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceComposable
import androidx.glance.color.DayNightColorProvider
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.unit.FixedColorProvider
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.vpnGreen
import me.proton.core.compose.theme.ProtonColors

val LocalProtonColors: ProvidableCompositionLocal<ProtonGlanceColorProviders> =
    staticCompositionLocalOf { ProtonGlanceBrandedColorProviders }

val LocalProtonResources: ProvidableCompositionLocal<ProtonGlanceResources> =
    staticCompositionLocalOf { ProtonGlanceBrandedResources }

data class ProtonGlanceColorProviders(
    val backgroundNorm: ColorProvider,
    val backgroundSecondary: ColorProvider,
    val interactionNorm: ColorProvider,
    val onInteractionNorm: ColorProvider,
    val interactionSecondary: ColorProvider,
    val onInteractionSecondary: ColorProvider,
    val textNorm: ColorProvider,
    val textSecondary: ColorProvider,
    val protected: ColorProvider,
    val unprotected: ColorProvider,
    val logoIcon: ColorProvider?,
    val logoText: ColorProvider,
)

data class ProtonGlanceResources(
    @DrawableRes val logoIcon: Int,
    @DrawableRes val widgetBackgroundNeedsLogin: Int,
    @DrawableRes val widgetBackgroundDisconnected: Int,
    @DrawableRes val widgetBackgroundConnecting: Int,
    @DrawableRes val widgetBackgroundConnected: Int,
)

private val ProtonGlanceBrandedColorProviders = ProtonGlanceColorProviders(
    backgroundNorm = DayNightColorProvider(
        day = ProtonColors.Light.backgroundNorm,
        night = ProtonColors.Dark.backgroundNorm,
    ),
    backgroundSecondary = DayNightColorProvider(day = Color(0x14000000), night = Color(0x14000000)),
    interactionNorm = FixedColorProvider(ProtonColors.Light.brandNorm),
    onInteractionNorm = FixedColorProvider(Color.White),
    interactionSecondary = DayNightColorProvider(
        day = ProtonColors.Light.interactionWeakNorm,
        night = ProtonColors.Dark.interactionWeakNorm
    ),
    onInteractionSecondary = DayNightColorProvider(
        day = ProtonColors.Light.textNorm,
        night = ProtonColors.Dark.textNorm,
    ),
    textNorm = DayNightColorProvider(day = ProtonColors.Light.textNorm, night = ProtonColors.Dark.textNorm),
    textSecondary = DayNightColorProvider(day = ProtonColors.Light.textWeak, night = ProtonColors.Dark.textWeak),
    protected = DayNightColorProvider(day = ProtonColors.Light.vpnGreen, night = ProtonColors.Dark.vpnGreen),
    unprotected = DayNightColorProvider(
        day = ProtonColors.Light.notificationError,
        night = ProtonColors.Dark.notificationError,
    ),
    logoIcon = null,
    logoText = DayNightColorProvider(
        day = ProtonColors.Light.textNorm,
        night = ProtonColors.Dark.textNorm,
    )
)

private val ProtonGlanceDynamicColorProviders = ProtonGlanceColorProviders(
    backgroundNorm = DynamicThemeColorProviders.background,
    backgroundSecondary = DynamicThemeColorProviders.surfaceVariant,
    interactionNorm = DynamicThemeColorProviders.primary,
    onInteractionNorm = DynamicThemeColorProviders.onPrimary,
    interactionSecondary = DynamicThemeColorProviders.secondary,
    onInteractionSecondary = DynamicThemeColorProviders.onSecondary,
    textNorm = DynamicThemeColorProviders.onSurface,
    textSecondary = DynamicThemeColorProviders.onSurfaceVariant,
    protected = DynamicThemeColorProviders.primary,
    unprotected = DynamicThemeColorProviders.onSurfaceVariant,
    logoIcon = DynamicThemeColorProviders.primary,
    logoText = DynamicThemeColorProviders.primary,
)

private val ProtonGlanceBrandedResources = ProtonGlanceResources(
    logoIcon = R.drawable.ic_vpn_icon_colorful,
    widgetBackgroundNeedsLogin = R.drawable.widget_background_needslogin,
    widgetBackgroundDisconnected = R.drawable.widget_background_disconnected,
    widgetBackgroundConnecting = R.drawable.widget_background_connecting,
    widgetBackgroundConnected = R.drawable.widget_background_connected,
)

private val ProtonGlanceDynamicResources = ProtonGlanceResources(
    logoIcon = R.drawable.ic_vpn_icon_monochrome,
    widgetBackgroundNeedsLogin = R.drawable.widget_background_material,
    widgetBackgroundDisconnected = R.drawable.widget_background_material,
    widgetBackgroundConnecting = R.drawable.widget_background_material,
    widgetBackgroundConnected = R.drawable.widget_background_material,
)

object ProtonGlanceTypography {
    val defaultNorm: TextStyle
        @GlanceComposable @Composable
        get() = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = ProtonGlanceTheme.colors.textNorm)
    val defaultOnInteraction: TextStyle
        @GlanceComposable @Composable
        get() = defaultNorm.copy(color = ProtonGlanceTheme.colors.onInteractionNorm)

    val secondary
        @GlanceComposable @Composable
        get() = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = ProtonGlanceTheme.colors.textSecondary
        )

    val status = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ProtonGlanceTheme(
    isMaterialYou: Boolean = false,
    content: @GlanceComposable @Composable () -> Unit
) {
    val colorProviders = if (isMaterialYou) ProtonGlanceDynamicColorProviders else ProtonGlanceBrandedColorProviders
    val resources = if (isMaterialYou) ProtonGlanceDynamicResources else ProtonGlanceBrandedResources

    CompositionLocalProvider(
        LocalProtonColors provides colorProviders,
        LocalProtonResources provides resources,
        content = content
    )
}

object ProtonGlanceTheme {
    val colors: ProtonGlanceColorProviders
        @GlanceComposable @Composable
        @ReadOnlyComposable
        get() = LocalProtonColors.current
    val resources: ProtonGlanceResources
        @GlanceComposable @Composable
        @ReadOnlyComposable
        get() = LocalProtonResources.current
    val typography: ProtonGlanceTypography = ProtonGlanceTypography
}
