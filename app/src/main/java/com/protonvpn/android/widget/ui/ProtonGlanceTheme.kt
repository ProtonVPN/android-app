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
import androidx.glance.color.ColorProvider
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.LocalLocale
import com.protonvpn.android.base.ui.LocalStringProvider
import com.protonvpn.android.base.ui.StringProvider
import com.protonvpn.android.base.ui.vpnGreen
import me.proton.core.compose.theme.ProtonColors
import java.util.Locale

val LocalProtonColors: ProvidableCompositionLocal<ProtonGlanceColorProviders> =
    staticCompositionLocalOf { ProtonGlanceBrandedColorProviders }

val LocalProtonResources: ProvidableCompositionLocal<ProtonGlanceResources> =
    staticCompositionLocalOf { ProtonGlanceBrandedResources }

data class ProtonGlanceColorProviders(
    val interactionNorm: ColorProvider,
    val onInteractionNorm: ColorProvider,
    val onInteractionSecondary: ColorProvider,
    val textNorm: ColorProvider,
    val textSecondary: ColorProvider,
    val protected: ColorProvider,
    val unprotected: ColorProvider,
    val logoIcon: ColorProvider?,
    val logoText: ColorProvider,
)

data class ProtonGlanceResources(
    @DrawableRes val buttonBackgroundInteractionNorm: Int,
    @DrawableRes val buttonBackgroundInteractionSecondary: Int,
    @DrawableRes val buttonBackgroundSecondary: Int,
    @DrawableRes val logoIcon: Int,
    @DrawableRes val widgetBackgroundNeedsLogin: Int,
    @DrawableRes val widgetBackgroundLoggedIn: Int,
    @DrawableRes val widgetGradientConnecting: Int?,
    @DrawableRes val widgetGradientConnected: Int?,
    @DrawableRes val widgetGradientDisconnected: Int?,
)

private val ProtonGlanceBrandedColorProviders = ProtonGlanceColorProviders(
    interactionNorm = ColorProvider(ProtonColors.Light.brandNorm),
    onInteractionNorm = ColorProvider(Color.White),
    onInteractionSecondary = ColorProvider(
        day = ProtonColors.Light.textNorm,
        night = ProtonColors.Dark.textNorm,
    ),
    textNorm = ColorProvider(day = ProtonColors.Light.textNorm, night = ProtonColors.Dark.textNorm),
    textSecondary = ColorProvider(day = ProtonColors.Light.textWeak, night = ProtonColors.Dark.textWeak),
    protected = ColorProvider(day = ProtonColors.Light.vpnGreen, night = ProtonColors.Dark.vpnGreen),
    unprotected = ColorProvider(
        day = ProtonColors.Light.notificationError,
        night = ProtonColors.Dark.notificationError,
    ),
    logoIcon = null,
    logoText = ColorProvider(
        day = ProtonColors.Light.textNorm,
        night = ProtonColors.Dark.textNorm,
    )
)

private val ProtonGlanceDynamicColorProviders = ProtonGlanceColorProviders(
    interactionNorm = DynamicThemeColorProviders.primary,
    onInteractionNorm = DynamicThemeColorProviders.onPrimary,
    onInteractionSecondary = DynamicThemeColorProviders.onSecondary,
    textNorm = DynamicThemeColorProviders.onSurface,
    textSecondary = DynamicThemeColorProviders.onSurfaceVariant,
    protected = DynamicThemeColorProviders.primary,
    unprotected = DynamicThemeColorProviders.onSurfaceVariant,
    logoIcon = DynamicThemeColorProviders.primary,
    logoText = DynamicThemeColorProviders.primary,
)

private val ProtonGlanceBrandedResources = ProtonGlanceResources(
    buttonBackgroundInteractionNorm = R.drawable.widget_button_bg_interaction_norm,
    buttonBackgroundInteractionSecondary = R.drawable.widget_button_bg_interaction_secondary,
    buttonBackgroundSecondary = R.drawable.widget_button_bg_secondary,
    logoIcon = R.drawable.ic_vpn_icon_colorful,
    widgetBackgroundNeedsLogin = R.drawable.widget_background_needslogin,
    widgetBackgroundLoggedIn = R.drawable.widget_background_norm,
    widgetGradientConnecting = R.drawable.widget_bg_gradient_connecting,
    widgetGradientConnected = R.drawable.widget_bg_gradient_connected,
    widgetGradientDisconnected = R.drawable.widget_bg_gradient_disconnected,
)

private val ProtonGlanceDynamicResources = ProtonGlanceResources(
    buttonBackgroundInteractionNorm = R.drawable.widget_button_bg_interaction_norm_material,
    buttonBackgroundInteractionSecondary = R.drawable.widget_button_bg_interaction_secondary_material,
    buttonBackgroundSecondary = R.drawable.widget_button_bg_secondary_material,
    logoIcon = R.drawable.ic_vpn_icon_monochrome,
    widgetBackgroundNeedsLogin = R.drawable.widget_background_material,
    widgetBackgroundLoggedIn = R.drawable.widget_background_material,
    widgetGradientConnecting = null,
    widgetGradientConnected = null,
    widgetGradientDisconnected = null,
)

object ProtonGlanceTypography {
    val big: TextStyle
        @GlanceComposable @Composable
        get() = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = ProtonGlanceTheme.colors.textNorm)

    val bigSecondary
        @GlanceComposable @Composable
        get() = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = ProtonGlanceTheme.colors.textSecondary)

    val medium: TextStyle @GlanceComposable @Composable get() = big.copy(fontSize = 15.sp)
    val mediumSecondary @GlanceComposable @Composable get() = bigSecondary.copy(fontSize = 13.sp)

    val small: TextStyle @GlanceComposable @Composable get() = big.copy(fontSize = 14.sp)
    val smallSecondary @GlanceComposable @Composable get() = bigSecondary.copy(fontSize = 12.sp)

    val defaultOnInteraction: TextStyle
        @GlanceComposable @Composable
        get() = medium.copy(color = ProtonGlanceTheme.colors.onInteractionNorm)

    val status = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val statusSmall = status.copy(fontSize = 14.sp)
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
        LocalStringProvider provides StringProvider { id, formatArgs -> glanceStringResource(id, *formatArgs) },
        LocalLocale provides Locale.getDefault(),
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
