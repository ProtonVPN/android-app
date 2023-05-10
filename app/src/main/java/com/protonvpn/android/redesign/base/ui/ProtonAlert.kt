/*
 * Copyright (c) 2023 Proton AG
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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonTextButton
import com.protonvpn.android.base.ui.theme.VpnTheme
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallUnspecified
import me.proton.core.compose.theme.defaultUnspecified
import me.proton.core.compose.theme.headlineNorm
import me.proton.core.compose.theme.headlineSmallUnspecified

@SuppressWarnings("LongParameterList")
@Composable
fun ProtonAlert(
    title: String?,
    text: String,
    confirmLabel: String,
    onConfirm: (checkBoxValue: Boolean) -> Unit,
    dismissLabel: String? = null,
    onDismissButton: (checkBoxValue: Boolean) -> Unit = {},
    onDismissRequest: () -> Unit = {},
    checkBox: String? = null,
    checkBoxInitialValue: Boolean = false,
) {
    val checkBoxValue = rememberSaveable { mutableStateOf(checkBoxInitialValue) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ProtonDialogButton(onClick = { onConfirm(checkBoxValue.value) }, text = confirmLabel)
        },
        dismissButton = dismissLabel?.let {
            { ProtonDialogButton(onClick = { onDismissButton(checkBoxValue.value) }, text = it) }
        },
        title = title?.let {
            { Text(text = it, style = ProtonTheme.typography.headlineNorm) }
        },
        text = {
            Column {
                Text(
                    text = text,
                    style = ProtonTheme.typography.defaultUnspecified
                )
                if (checkBox != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    ProtonDialogCheckbox(
                        checkBox,
                        value = checkBoxValue.value,
                        onValueChange = { checkBoxValue.value = it }
                    )
                }
            }
        },
        shape = ShapeDefaults.Small,
        tonalElevation = 0.dp,
        containerColor = ProtonTheme.colors.backgroundSecondary,
    )
}

@Composable
fun ProtonDialogButton(onClick: () -> Unit, text: String) {
    ProtonTextButton(
        onClick = onClick,
        style = ProtonTheme.typography.headlineSmallUnspecified
    ) {
        Text(text = text)
    }
}

@Composable
fun ProtonDialogCheckbox(
    text: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .toggleable(
                value,
                onValueChange = onValueChange,
                role = Role.Checkbox
            )
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = value,
            colors = CheckboxDefaults.colors(uncheckedColor = ProtonTheme.colors.shade60),
            onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {}
        )
        Text(
            text,
            modifier = Modifier.padding(start = 10.dp),
            style = ProtonTheme.typography.defaultSmallUnspecified
        )
    }
}

@Composable
fun PreviewProtonAlert() {
    ProtonAlert(
        title = "Title",
        text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod " +
            "tempor incididunt ut labore et dolore magna aliqua.",
        confirmLabel = "Confirm",
        onConfirm = {},
        dismissLabel = "Dismiss",
        onDismissButton = {},
        checkBox = "Check me",
        checkBoxInitialValue = false
    )
}

@Preview
@Composable
fun PreviewProtonAlertLight() {
    VpnTheme(isDark = false) {
        PreviewProtonAlert()
    }
}

@Preview
@Composable
fun PreviewProtonAlertDark() {
    VpnTheme(isDark = true) {
        PreviewProtonAlert()
    }
}
