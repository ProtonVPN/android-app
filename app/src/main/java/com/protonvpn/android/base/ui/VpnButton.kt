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

package com.protonvpn.android.redesign.base.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun VpnSolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExternalLink: Boolean = false,
) {
    ProtonSolidButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        VpnButtonContent(text, isExternalLink)
    }
}

@Composable
fun VpnWeakSolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExternalLink: Boolean = false,
) {
    val colors = ButtonDefaults.protonButtonColors(
        false,
        contentColor = ProtonTheme.colors.textNorm,
        backgroundColor = ProtonTheme.colors.interactionWeakNorm
    )
    ProtonSolidButton(onClick = onClick, colors = colors, modifier = modifier.fillMaxWidth()) {
        VpnButtonContent(text, isExternalLink)
    }
}

@Composable
fun VpnOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExternalLink: Boolean = false,
) {
    ProtonOutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        VpnButtonContent(text, isExternalLink)
    }
}

@Composable
fun VpnTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExternalLink: Boolean = false,
) {
    ProtonTextButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        VpnButtonContent(text, isExternalLink)
    }
}

@Composable
private fun VpnButtonContent(
    text: String,
    isExternalLink: Boolean
) {
    if (isExternalLink) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(text, modifier = Modifier.align(Alignment.Center))
            Icon(
                painterResource(id = R.drawable.ic_proton_arrow_out_square),
                contentDescription = stringResource(R.string.accessibility_external_link_suffix),
                tint = ProtonTheme.colors.iconAccent,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    } else {
        Text(text)
    }
}
