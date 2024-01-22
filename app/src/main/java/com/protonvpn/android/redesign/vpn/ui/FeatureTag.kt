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

package com.protonvpn.android.redesign.vpn.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.vpn.ServerFeature
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionUnspecified
import me.proton.core.presentation.R as CoreR

@Composable
fun FeatureTag(
    feature: ServerFeature,
    modifier: Modifier = Modifier,
    contentColor: Color = LocalContentColor.current
) {
    Row(modifier = modifier) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(
                painterResource(id = feature.iconRes()),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                stringResource(feature.labelRes()),
                modifier = Modifier.padding(start = 6.dp),
                style = ProtonTheme.typography.captionUnspecified
            )
        }
    }
}

private fun ServerFeature.iconRes() = when (this) {
    ServerFeature.Tor -> CoreR.drawable.ic_proton_brand_tor
    ServerFeature.P2P -> CoreR.drawable.ic_proton_arrow_right_arrow_left
}

private fun ServerFeature.labelRes() = when (this) {
    ServerFeature.Tor -> R.string.server_feature_label_tor
    ServerFeature.P2P -> R.string.server_feature_label_p2p
}

@Preview
@Composable
fun FeatureTagPreview() {
    VpnTheme {
        FeatureTag(ServerFeature.Tor)
    }
}
