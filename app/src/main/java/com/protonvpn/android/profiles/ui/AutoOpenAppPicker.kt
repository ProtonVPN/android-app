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

package com.protonvpn.android.profiles.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.protonvpn.android.R
import com.protonvpn.android.redesign.base.ui.DIALOG_CONTENT_PADDING
import com.protonvpn.android.redesign.base.ui.SettingsRadioItemSmall
import com.protonvpn.android.ui.settings.LabeledItem
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun AutoOpenAppPickerDialog(
    initialPackageName: String?,
    onAppSelected: (String) -> Unit,
    getAllApps: suspend (Int) -> List<LabeledItem>,
    onDismissRequest: () -> Unit,
) {
    val iconSizePx = with(LocalDensity.current) { 24.dp.roundToPx() }
    var loading by remember { mutableStateOf(true) }
    val allApps = remember { mutableStateListOf<LabeledItem>() }
    LaunchedEffect(iconSizePx) {
        val apps = getAllApps(iconSizePx)
        allApps.clear()
        allApps.addAll(apps)
        loading = false
    }
    BaseItemPickerDialog(
        R.string.create_profile_auto_open_app_input_label,
        onDismissRequest = onDismissRequest
    ) {
        when {
            allApps.isEmpty() -> item {
                Box(modifier = Modifier.fillMaxSize().sizeIn(minHeight = 200.dp)) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center).size(24.dp),
                            color = ProtonTheme.colors.iconAccent,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.create_profile_auto_open_app_no_apps),
                            style = ProtonTheme.typography.body2Regular,
                            color = ProtonTheme.colors.textWeak,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
            else -> {
                items(allApps) { app ->
                    SettingsRadioItemSmall(
                        title = app.label,
                        description = null,
                        selected = app.id == initialPackageName,
                        onSelected = { onAppSelected(app.id) },
                        horizontalContentPadding = DIALOG_CONTENT_PADDING,
                        leadingContent = {
                            AutoOpenAppIcon(iconSizePx, app, modifier = Modifier.padding(end = 8.dp))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AutoOpenAppIcon(iconSizePx: Int, item: LabeledItem, modifier: Modifier = Modifier) {
    val iconModifier = modifier.clip(ProtonTheme.shapes.small)
    if (item.iconDrawable != null) {
        val imageBitmap = with(item.iconDrawable) {
            // The app icon bitmaps can be of different sizes, use the drawable bounds.
            toBitmap(width = iconSizePx, height = iconSizePx).asImageBitmap()
        }
        Image(bitmap = imageBitmap, contentDescription = null, modifier = iconModifier)
    } else {
        Image(painter = painterResource(item.iconRes), contentDescription = null, modifier = iconModifier)
    }
}