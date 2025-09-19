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

package com.protonvpn.android.tv.dialogs

import android.content.DialogInterface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.protonvpn.android.redesign.base.ui.optional
import com.protonvpn.android.tv.settings.ProtonTvFocusableSurface
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun TvAlertDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    focusedButton: Int,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 32.dp))
            .background(color = ProtonTheme.colors.backgroundSecondary),
        onDismissRequest = onDismiss,
        shape = ProtonTheme.shapes.large,
        backgroundColor = ProtonTheme.colors.backgroundSecondary,
        contentColor = ProtonTheme.colors.backgroundSecondary,
        title = {
            Text(
                text = title,
                color = ProtonTheme.colors.textNorm,
                style = ProtonTheme.typography.headline,
            )
        },
        buttons = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 24.dp),
                contentAlignment = androidx.compose.ui.Alignment.CenterEnd,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    TvDialogButton(
                        modifier = Modifier.optional(
                            predicate = { focusedButton == DialogInterface.BUTTON_NEGATIVE },
                            modifier = Modifier.focusRequester(focusRequester),
                        ),
                        text = dismissText,
                        onClick = onDismiss,
                    )

                    TvDialogButton(
                        modifier = Modifier.optional(
                            predicate = { focusedButton == DialogInterface.BUTTON_POSITIVE },
                            modifier = Modifier.focusRequester(focusRequester),
                        ),
                        text = confirmText,
                        onClick = onConfirm,
                    )
                }
            }
        },
    )
}

@Composable
private fun TvDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProtonTvFocusableSurface(
        modifier = modifier,
        color = { Color.Transparent },
        focusedColor = { ProtonTheme.colors.backgroundNorm },
        shape = CircleShape,
        onClick = onClick,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            text = text,
            color = ProtonTheme.colors.textNorm,
            style = ProtonTheme.typography.body2Medium,
        )
    }
}
