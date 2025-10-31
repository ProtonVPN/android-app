/*
 * Copyright (c) 2024 Proton AG
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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.AnnotatedClickableText
import com.protonvpn.android.base.ui.ProtonRadio
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.ui.settings.CustomAppIconData
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openUrl
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun IconSelectionSetting(
    activeIcon: CustomAppIconData,
    onIconChange: (CustomAppIconData) -> Unit,
    onClose: () -> Unit,
) {
    SubSetting(
        title = stringResource(id = R.string.settings_change_icon_title),
        onClose = onClose
    ) {
        IconSelectionScreen(activeIcon, onIconChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconSelectionScreen(
    activeIcon: CustomAppIconData,
    onItemConfirmed: (CustomAppIconData) -> Unit,
) {
    val context = LocalContext.current
    val showRestartDialog = rememberSaveable { mutableStateOf(false) }
    val currentIcon: CustomAppIconData by rememberSaveable { mutableStateOf(activeIcon) }
    var pendingIcon: CustomAppIconData by rememberSaveable { mutableStateOf(activeIcon) }

    val allIcons = enumValues<CustomAppIconData>().toList()
    val brandIconList = allIcons.filter { it.category == CustomAppIconData.IconCategory.ProtonVPN }
    val discreetIconList = allIcons.filter { it.category == CustomAppIconData.IconCategory.Discreet }

    val currentlySelected: (CustomAppIconData) -> Boolean = { it == pendingIcon }
    val onItemSelected: (CustomAppIconData) -> Unit = {
        pendingIcon = it
        showRestartDialog.value = true
    }

    if (showRestartDialog.value) {
        ProtonAlert(
            title = stringResource(id = R.string.settings_change_icon_confirmation_title),
            detailsImage = pendingIcon.getPreviewDrawable(),
            isWideDialog = true,
            text = stringResource(id = R.string.settings_change_icon_confirmation_details),
            textColor = ProtonTheme.colors.textWeak,
            confirmLabel = stringResource(id = R.string.dialog_action_change_icon),
            onConfirm = {
                onItemConfirmed(pendingIcon)
                showRestartDialog.value = false
            },
            dismissLabel = stringResource(id = R.string.dialog_action_cancel),
            onDismissRequest = {
                pendingIcon = currentIcon
                showRestartDialog.value = false
            },
        )
    }

    Spacer(modifier = Modifier.size(12.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
    ) {
        Text(
            text = stringResource(id = R.string.settings_change_icon_header_protonvpn),
            style = ProtonTheme.typography.body2Medium,
        )
        FlowRow {
            brandIconList.forEach {
                AppIconElement(
                    preset = it,
                    isSelected = currentlySelected.invoke(it),
                    onClick = {
                        if (!currentlySelected.invoke(it)) {
                            onItemSelected(it)
                        }
                    },
                    modifier = Modifier.padding(vertical = 18.dp).weight(1f)
                )
            }
        }
        Text(
            text = stringResource(id = R.string.settings_change_icon_header_discreet),
            style = ProtonTheme.typography.body2Medium,
        )
        AnnotatedClickableText(
            style = ProtonTheme.typography.body2Regular,
            annotatedStyle = ProtonTheme.typography.body2Medium,
            color = ProtonTheme.colors.textWeak,
            onAnnotatedClick = { context.openUrl(Constants.CHANGE_ICON_URL) },
            annotatedPart = stringResource(id = R.string.settings_icon_change_learn_more),
            fullText = stringResource(
                id = R.string.settings_icon_change_description,
                stringResource(id = R.string.learn_more)
            ),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        FlowRow {
            discreetIconList.forEach {
                AppIconElement(
                    preset = it,
                    isSelected = currentlySelected.invoke(it),
                    onClick = {
                        if (!currentlySelected.invoke(it)) {
                            onItemSelected(it)
                        }
                    },
                    modifier = Modifier.padding(vertical = 18.dp).weight(1f)
                )
            }
        }
    }
}
private fun CustomAppIconData.getPreviewDrawable(): Int {
    return when (this) {
        CustomAppIconData.DEFAULT -> R.drawable.app_icon_preview_classic
        CustomAppIconData.DARK -> R.drawable.app_icon_preview_dark
        CustomAppIconData.RETRO -> R.drawable.app_icon_preview_retro
        CustomAppIconData.WEATHER -> R.drawable.app_icon_preview_weather
        CustomAppIconData.NOTES -> R.drawable.app_icon_preview_notes
        CustomAppIconData.CALCULATOR -> R.drawable.app_icon_preview_calculator
    }
}
@Composable
fun AppIconElement(
    preset: CustomAppIconData,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true, properties = {}),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(preset = preset, onClick = onClick)
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(id = preset.labelResId),
            textAlign = TextAlign.Center,
            style = ProtonTheme.typography.body2Regular,
            color = ProtonTheme.colors.textWeak
        )
        ProtonRadio(selected = isSelected, onClick = onClick)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    preset: CustomAppIconData,
    onClick: () -> Unit
) {
    val imageModifier = modifier
        .size(64.dp)
        .clip(CircleShape)
        .border(1.dp, ProtonTheme.colors.separatorNorm, CircleShape)
        .clickable(onClick = onClick)
    GlideImage(
        model = preset.iconPreviewResId,
        contentDescription = "",
        modifier = imageModifier,
        contentScale = ContentScale.Crop
    )
}
