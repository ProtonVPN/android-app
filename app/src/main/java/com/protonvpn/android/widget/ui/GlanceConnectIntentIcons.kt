/*
 * Copyright (c) 2025 Proton AG
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

import android.content.Context
import android.text.TextUtils
import android.util.LayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.ContentScale
import androidx.glance.layout.size
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.LocalLocale
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ConnectIntentIconSize
import com.protonvpn.android.redesign.base.ui.ConnectIntentIconState
import com.protonvpn.android.redesign.base.ui.paintConnectIntentIcon
import com.protonvpn.android.redesign.base.ui.toIconState
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.utils.CountryTools

// Note: rendering to a MEDIUM (30x30) size bitmap while it'll be shown in a 24x24dp ImageView, so GLANCE_ICON_DENSITY
// is a bit smaller than actual.
private const val GLANCE_ICON_DENSITY = 2f

@Composable
fun ConnectIntentGlanceIcon(
    intentLabel: ConnectIntentPrimaryLabel,
    modifier: GlanceModifier = GlanceModifier.size(24.dp)
) {
    val context = LocalContext.current
    val isRtl = TextUtils.getLayoutDirectionFromLocale(LocalLocale.current) == LayoutDirection.RTL
    val iconBitmap = paintConnectIntentIcon(
        context,
        isRtl,
        GLANCE_ICON_DENSITY,
        intentLabel.toIcon(context),
        ConnectIntentIconSize.MEDIUM
    )
    Image(
        ImageProvider(iconBitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
    )
}

private fun ConnectIntentPrimaryLabel.toIcon(context: Context): ConnectIntentIconState {
    return toIconState {
        if (isFastest) R.drawable.widget_flag_fastest
        else CountryTools.getFlagResource(context, countryCode)
    }
}
