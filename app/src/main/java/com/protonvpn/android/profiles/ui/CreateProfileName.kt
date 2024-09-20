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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.protonvpn.android.R
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.redesign.base.ui.ProfileIcon
import me.proton.core.compose.theme.ProtonTheme

const val MAX_PROFILE_LENGTH = 30

@Composable
fun CreateNameRoute(
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
                modifier = Modifier.fillMaxWidth()
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

        ProfileNavigationButtons(onNext = {
            errorRes = when {
                state.name.isBlank() -> R.string.create_profile_name_error_empty
                state.name.isNameTooLong() -> R.string.create_profile_name_error_too_long
                else -> null
            }
            if (errorRes == null) {
                onNext()
            }
        })
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

    FlowRow(
        modifier = modifier.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = numberOfColumns
    ) {
        repeat(rows * numberOfColumns) {
            val icon = ProfileIcon.entries.getOrNull(it)
            val isSelected = icon == selectedIcon

            var columnModifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp)
            val shape = RoundedCornerShape(10.dp)

            if (isSelected) {
                columnModifier = columnModifier
                    .border(
                        width = 2.dp,
                        color = ProtonTheme.colors.shade100,
                        shape = shape
                    )
                    .background(
                        color = ProtonTheme.colors.backgroundSecondary,
                        shape = shape
                    )
            }

            icon?.let {
                columnModifier = columnModifier
                    .clip(shape)
                    .clickable { onIconSelected(it) }
            }

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = columnModifier
            ) {
                icon?.let { icon ->
                    ProfileIcon(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(vertical = 4.dp)
                            .alpha(if (isSelected) 1F else 0.7F),
                        color = color,
                        icon = icon,
                    )
                }
            }
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
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ProfileColor.entries.forEach { color ->
            val borderColor by animateColorAsState(
                targetValue = if (color == selectedColor) ProtonTheme.colors.shade100 else Color.Transparent,
                label = "Border color"
            )
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onColorSelected(color) }
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