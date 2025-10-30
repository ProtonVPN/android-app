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

package com.protonvpn.android.redesign.base.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.presentation.R as CoreR

@Composable
fun ProtonDropdownMenu(
    labelText: String,
    placeholderText: String,
    options: List<String>,
    selectedOption: String?,
    onSelectOption: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
) {
    var isExpanded by remember { mutableStateOf(value = false) }

    var parentWidthPx by remember { mutableStateOf(value = 0) }

    val density = LocalDensity.current

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            parentWidthPx = coordinates.size.width
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Text(
                text = labelText,
                color = if (isError) {
                    ProtonTheme.colors.notificationError
                } else {
                    ProtonTheme.colors.textNorm
                },
                style = ProtonTheme.typography.captionMedium,
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(space = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape = ProtonTheme.shapes.medium)
                        .background(color = ProtonTheme.colors.backgroundSecondary)
                        .optional(
                            predicate = { isError },
                            modifier = Modifier.border(
                                width = 1.dp,
                                color = ProtonTheme.colors.notificationError,
                                shape = ProtonTheme.shapes.medium
                            )
                        )
                        .clickable { isExpanded = true }
                        .padding(all = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
                ) {
                    Text(
                        modifier = Modifier.weight(weight = 1f, fill = true),
                        text = selectedOption ?: placeholderText,
                        color = if (selectedOption == null) {
                            ProtonTheme.colors.textHint
                        } else {
                            ProtonTheme.colors.textNorm
                        },
                        style = ProtonTheme.typography.defaultNorm,
                    )

                    Icon(
                        modifier = Modifier.size(size = 16.dp),
                        painter = painterResource(id = CoreR.drawable.ic_proton_chevron_down),
                        contentDescription = null,
                        tint = ProtonTheme.colors.iconNorm,
                    )
                }

                if (isError && errorText != null) {
                    Text(
                        text = errorText,
                        color = ProtonTheme.colors.notificationError,
                        style = ProtonTheme.typography.captionRegular,
                    )
                }
            }
        }

        DropdownMenu(
            modifier = Modifier.width(width = with(density) { parentWidthPx.toDp() }),
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
            offset = DpOffset(x = 0.dp, y = 1.dp),
            shape = ProtonTheme.shapes.medium,
            containerColor = ProtonTheme.colors.backgroundSecondary,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            style = ProtonTheme.typography.body1Regular,
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = ProtonTheme.colors.textNorm,
                    ),
                    onClick = {
                        isExpanded = false

                        onSelectOption(option)
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun ProtonDropdownMenuPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        ProtonDropdownMenu(
            labelText = "Dropdown label",
            placeholderText = "Dropdown placeholder",
            options = emptyList(),
            selectedOption = null,
            onSelectOption = {},
        )
    }
}

@Preview
@Composable
private fun ProtonDropdownMenuErrorPreview(
    @PreviewParameter(PreviewBooleanProvider::class) isDark: Boolean,
) {
    ProtonVpnPreview(isDark = isDark) {
        ProtonDropdownMenu(
            labelText = "Dropdown label",
            placeholderText = "Dropdown placeholder",
            isError = true,
            errorText = "Dropdown error",
            options = emptyList(),
            selectedOption = null,
            onSelectOption = {},
        )
    }
}
