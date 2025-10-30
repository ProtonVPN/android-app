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

package com.protonvpn.android.base.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun VpnSolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isExternalLink: Boolean = false,
    isLoading: Boolean = false,
) {
    ProtonSolidButton(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        loading = isLoading,
    ) {
        VpnButtonContent(
            text = text,
            isExternalLink = isExternalLink,
        )
    }
}

val ButtonDefaults.ProtonContentPaddingCompact: PaddingValues
    get() = PaddingValues(horizontal = 24.dp, vertical = 12.dp)

@Composable
fun VpnSolidCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ProtonSolidButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = ButtonDefaults.ProtonContentPaddingCompact
    ) {
        VpnButtonContent(text, isExternalLink = false)
    }
}

@Composable
fun VpnWeakSolidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isExternalLink: Boolean = false,
) {
    val colors = ButtonDefaults.protonButtonColors(
        false,
        contentColor = ProtonTheme.colors.textNorm,
        backgroundColor = ProtonTheme.colors.interactionWeakNorm
    )
    ProtonSolidButton(onClick = onClick, colors = colors, modifier = modifier.fillMaxWidth(), enabled = enabled) {
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
fun VpnOutlinedNeutralButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExternalLink: Boolean = false,
) {
    ProtonOutlinedNeutralButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
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

// Black/white outlined button is specific to VPN.
@Composable
fun ProtonOutlinedNeutralButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    contained: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    colors: ButtonColors = ButtonDefaults.protonOutlinedNeutralButtonColors(loading),
    border: BorderStroke = ButtonDefaults.protonOutlineNeutralBorder(enabled, loading),
    content: @Composable () -> Unit,
) {
    ProtonOutlinedButton(
        onClick, modifier, enabled, loading, contained, interactionSource, colors, border, content = content
    )
}

@Composable
fun ButtonDefaults.protonOutlinedNeutralButtonColors(
    loading: Boolean = false,
    backgroundColor: Color = ProtonTheme.colors.backgroundNorm,
    contentColor: Color = ProtonTheme.colors.interactionStrongNorm,
    disabledBackgroundColor: Color = if (loading) {
        ProtonTheme.colors.backgroundSecondary
    } else {
        ProtonTheme.colors.backgroundNorm
    },
    disabledContentColor: Color = if (loading) {
        ProtonTheme.colors.interactionStrongNorm
    } else {
        ProtonTheme.colors.textDisabled
    },
): ButtonColors = buttonColors(
    containerColor = backgroundColor,
    contentColor = contentColor,
    disabledContainerColor = disabledBackgroundColor,
    disabledContentColor = disabledContentColor,
)

@Composable
fun ButtonDefaults.protonOutlineNeutralBorder(
    enabled: Boolean = true,
    loading: Boolean = false,
) = BorderStroke(
    1.0.dp,
    when {
        loading -> ProtonTheme.colors.interactionStrongNorm
        !enabled -> ProtonTheme.colors.iconDisabled
        else -> ProtonTheme.colors.interactionStrongNorm
    },
)

@Composable
private fun VpnButtonContent(
    text: String,
    isExternalLink: Boolean
) {
    if (isExternalLink) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(text, modifier = Modifier.align(Alignment.Center))
            Icon(
                painterResource(id = CoreR.drawable.ic_proton_arrow_out_square),
                contentDescription = stringResource(R.string.accessibility_external_link_suffix),
                tint = LocalContentColor.current,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    } else {
        Text(text)
    }
}
