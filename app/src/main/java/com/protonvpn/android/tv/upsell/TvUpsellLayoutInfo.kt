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

package com.protonvpn.android.tv.upsell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.protonvpn.android.base.ui.ProtonVpnTvPreview
import me.proton.core.presentation.compose.tv.theme.ProtonThemeTv

@Composable
fun TvUpsellLayoutInfo(
    modifier: Modifier = Modifier,
) {
    // TODO: replace with the actual layout.
    Box(modifier = modifier.background(Color.Blue))
}

@ProtonVpnTvPreview
@Composable
private fun TvUpsellLayoutInfoPreview() {
    ProtonThemeTv {
        TvUpsellLayoutInfo(modifier = Modifier.fillMaxSize())
    }
}
