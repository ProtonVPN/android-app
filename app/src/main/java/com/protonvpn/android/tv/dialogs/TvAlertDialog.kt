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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.optional
import com.protonvpn.android.tv.buttons.TvTextButton
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun TvAlertDialog(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    focusedButton: Int,
    description: String? = null,
    dismissText: String? = null,
    onDismissRequest: () -> Unit,
    onDismiss: () -> Unit = onDismissRequest,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        modifier = Modifier
            .clip(shape = RoundedCornerShape(size = 32.dp))
            .background(color = ProtonTheme.colors.backgroundSecondary),
        onDismissRequest = onDismissRequest,
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
        text = description?.let { text ->
            {
                Text(
                    text = text,
                    color = ProtonTheme.colors.textWeak,
                    style = ProtonTheme.typography.body2Regular,
                )
            }
        },
        buttons = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        end = 24.dp,
                        bottom = 24.dp,
                    )
                    .optional(
                        predicate = { description == null },
                        modifier = Modifier.padding(top = 16.dp),
                    ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissText?.let { text ->
                        TvTextButton(
                            modifier = Modifier.optional(
                                predicate = { focusedButton == DialogInterface.BUTTON_NEGATIVE },
                                modifier = Modifier.focusRequester(focusRequester),
                            ),
                            text = dismissText,
                            onClick = onDismiss,
                        )
                    }

                    TvTextButton(
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

@Preview
@Composable
private fun PreviewTvAlertDialog() {
    ProtonVpnPreview(isDark = true) {
        TvAlertDialog(
            title = "Dialog title",
            description = "Dialog description text",
            focusedButton = DialogInterface.BUTTON_POSITIVE,
            confirmText = "Confirm",
            onConfirm = {},
            dismissText = "Dismiss",
            onDismissRequest = {}
        )
    }
}

@Preview
@Composable
private fun PreviewTvAlertNoDismiss() {
    ProtonVpnPreview(isDark = true) {
        TvAlertDialog(
            title = "Dialog title",
            description = "Dialog description text",
            focusedButton = DialogInterface.BUTTON_POSITIVE,
            confirmText = "Confirm",
            onConfirm = {},
            onDismissRequest = {}
        )
    }
}

@Preview
@Composable
private fun PreviewTvAlertDialogNoDesc() {
    ProtonVpnPreview(isDark = true) {
        TvAlertDialog(
            title = "Dialog title",
            focusedButton = DialogInterface.BUTTON_POSITIVE,
            confirmText = "Confirm",
            onConfirm = {},
            dismissText = "Dismiss",
            onDismissRequest = {}
        )
    }
}

@Preview
@Composable
private fun PreviewTvAlertDialogNoDescNoDismiss() {
    ProtonVpnPreview(isDark = true) {
        TvAlertDialog(
            title = "Dialog title",
            focusedButton = DialogInterface.BUTTON_POSITIVE,
            confirmText = "Confirm",
            onConfirm = {},
            onDismissRequest = {}
        )
    }
}
