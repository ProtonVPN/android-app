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

package com.protonvpn.android.profiles.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.redesign.base.ui.ProfileIcon
import com.protonvpn.android.redesign.base.ui.optional
import me.proton.core.compose.theme.ProtonTheme

const val MAX_PROFILE_LENGTH = 30

@Composable
fun CreateProfileNameRoute(
    viewModel: CreateEditProfileViewModel,
    onNext: () -> Unit
) {
    val state = viewModel.nameScreenStateFlow.collectAsStateWithLifecycle().value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = ProtonTheme.colors.backgroundNorm)
    ) {
        if (state != null)
            CreateName(state, viewModel::setName, viewModel::setColor, viewModel::setIcon, onNext)
    }
}

@Composable
fun CreateName(
    state: NameScreenState,
    setName: (String) -> Unit,
    setColor: (ProfileColor) -> Unit,
    setIcon: (ProfileIcon) -> Unit,
    onNext: () -> Unit
) {
    var errorRes by rememberSaveable { mutableStateOf<Int?>(null) }

    CreateProfileStep(
        onNext = {
            errorRes = when {
                state.name.isBlank() -> R.string.create_profile_name_error_empty
                state.name.isNameTooLong() -> R.string.create_profile_name_error_too_long
                else -> null
            }
            if (errorRes == null) {
                onNext()
            }
        }
    ) {
        TextField(
            value = state.name,
            placeholder = {
                Text(
                    text = stringResource(id = R.string.create_profile_name_hint),
                    style = ProtonTheme.typography.subheadline,
                    color = ProtonTheme.colors.textHint
                )
            },
            supportingText = {
                errorRes?.let { errorRes ->
                    Text(
                        text = stringResource(id = errorRes),
                        color = ProtonTheme.colors.notificationError,
                        style = ProtonTheme.typography.captionMedium
                    )
                }
                Unit
            },
            textStyle = ProtonTheme.typography.subheadline,
            isError = errorRes != null,
            onValueChange = { name ->
                errorRes = if (name.isNameTooLong())
                    R.string.create_profile_name_error_too_long
                else
                    null
                setName(name)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profileName")
        )
        ColorPicker(
            selectedColor = state.color,
            onColorSelected = setColor,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        IconsView(
            color = state.color,
            selectedIcon = state.icon,
            onIconSelected = setIcon
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconsView(
    modifier: Modifier = Modifier,
    color: ProfileColor,
    selectedIcon: ProfileIcon,
    onIconSelected: (ProfileIcon) -> Unit
) {
    val rows = 2
    val numberOfColumns = (ProfileIcon.entries.size + rows - 1) / rows

    val shape = RoundedCornerShape(10.dp)
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                collectionInfo = CollectionInfo(
                    rowCount = rows,
                    columnCount = numberOfColumns,
                )
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        maxItemsInEachRow = numberOfColumns
    ) {
        ProfileIcon.entries.forEach { icon ->
            val isSelected = icon == selectedIcon
            ProfileIcon(
                modifier = Modifier
                    .optional({ isSelected }, Modifier
                        .border(2.dp, ProtonTheme.colors.shade100, shape)
                        .background(ProtonTheme.colors.backgroundSecondary, shape)
                    )
                    .weight(1f)
                    .clip(shape)
                    .selectable(isSelected) { onIconSelected(icon) }
                    .alpha(if (isSelected) 1f else 0.7f)
                    .padding(8.dp)
                    .height(24.dp),
                color = color,
                extraSize = true,
                icon = icon,
                addContentDescription = true
            )
        }
    }
}

@Composable
private fun ColorPicker(
    modifier: Modifier = Modifier,
    selectedColor: ProfileColor,
    onColorSelected: (ProfileColor) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                collectionInfo = CollectionInfo(
                    rowCount = 1,
                    columnCount = ProfileColor.entries.size,
                )
            },
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ProfileColor.entries.forEach { color ->
            val borderColor by animateColorAsState(
                targetValue = if (color == selectedColor) ProtonTheme.colors.shade100 else Color.Transparent,
                label = "Border color"
            )
            val accessibilityName = stringResource(id = R.string.profile_color_accessibility, color.ordinal + 1)
            val isSelected = color == selectedColor
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = accessibilityName }
                        .selectable(isSelected) { onColorSelected(color) }
                        .border(
                            width = 2.dp,
                            color = borderColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                color = color.toColor(),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewProfileNameCreation() {
    ProtonTheme(isDark = true) {
        CreateName(
            state = NameScreenState(
                name = "Profile name",
                color = ProfileColor.Color1,
                icon = ProfileIcon.Icon1
            ),
            {}, {}, {}, {}
        )
    }
}

private fun String.isNameTooLong() = codePointCount(0, length) > MAX_PROFILE_LENGTH