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

package com.protonvpn.android.redesign.settings.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonRadio
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.theme.NoTrim
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.theme.label
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun ThemeSettings(
    selectedTheme: ThemeType,
    onSelected: (ThemeType) -> Unit,
    onClose: () -> Unit,
) {
    SubSetting(
        title = stringResource(R.string.settings_theme_title),
        onClose = onClose
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp)
                .height(IntrinsicSize.Max)
        ) {
            listOf(ThemeType.Light, ThemeType.Dark, ThemeType.System).forEach { theme ->
                ThemeRadioItem(
                    titleRes = theme.label(),
                    imageRes = theme.image(),
                    selected = theme == selectedTheme,
                    onSelected = { onSelected(theme) },
                    modifier =  Modifier.weight(1f)
                )
            }
        }
    }
}

@DrawableRes
private fun ThemeType.image(): Int = when(this) {
    ThemeType.System -> R.drawable.theme_auto
    ThemeType.Light -> R.drawable.theme_light
    ThemeType.Dark -> R.drawable.theme_dark
}

@Composable
private fun ThemeRadioItem(
    @StringRes titleRes: Int,
    @DrawableRes imageRes: Int,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(ProtonTheme.shapes.small)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelected
            )
            .fillMaxHeight()
            .padding(vertical = 10.dp, horizontal = 4.dp), // For ripple.
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
        )
        // Space is being added with vertical arrangement so not using Spacers.
        Box(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(titleRes),
                style = ProtonTheme.typography.body2Regular.copy(lineHeightStyle = LineHeightStyle.NoTrim),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        ProtonRadio(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

@Preview(fontScale = 2f)
@Preview(widthDp = 600)
@Composable
private fun ThemeSettingsPreview() {
    ProtonVpnPreview {
        ThemeSettings(selectedTheme = ThemeType.System, {}, {})
    }
}
