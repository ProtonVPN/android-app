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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonTextButton
import com.protonvpn.android.base.ui.ProtonVpnPreview
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallUnspecified
import me.proton.core.compose.theme.headlineNorm
import me.proton.core.compose.theme.headlineSmallUnspecified

private val WIDE_DIALOG_WIDTH = 480.dp

// Same as the private DialogPadding used by AlertDialog.
val DIALOG_CONTENT_PADDING = 24.dp

object ProtonAlertDefaults {
    val tonalElevation = 0.dp
    val containerColor @Composable get() = ProtonTheme.colors.backgroundSecondary
    val shape @Composable get() = AlertDialogDefaults.shape
}

private fun Modifier.wideDialog() : Modifier = this.widthIn(max = WIDE_DIALOG_WIDTH).padding(16.dp)

@SuppressWarnings("LongParameterList")
@Composable
fun ProtonAlert(
    title: String?,
    @DrawableRes detailsImage: Int? = null,
    text: String,
    textColor: Color = ProtonTheme.colors.textNorm,
    confirmLabel: String,
    onConfirm: (checkBoxValue: Boolean) -> Unit,
    dismissLabel: String? = null,
    onDismissButton: (checkBoxValue: Boolean) -> Unit = {},
    onDismissRequest: () -> Unit = {},
    checkBox: String? = null,
    checkBoxInitialValue: Boolean = false,
    isWideDialog: Boolean = false
) {
    val checkBoxValue = rememberSaveable { mutableStateOf(checkBoxInitialValue) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            ProtonDialogButton(onClick = { onConfirm(checkBoxValue.value) }, text = confirmLabel,
                modifier = Modifier.testTag("confirmButton"))
        },
        dismissButton = dismissLabel?.let {
            { ProtonDialogButton(onClick = { onDismissButton(checkBoxValue.value) }, text = it,
                modifier = Modifier.testTag("dismissButton")) }
        },
        title = title?.let {
            { Text(text = it, style = ProtonTheme.typography.headlineNorm) }
        },
        text = {
            Column {
                detailsImage?.let {
                    Image(
                        painter = painterResource(id = it),
                        contentDescription = "",
                        modifier = Modifier
                            .padding(bottom = 16.dp, top = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                Text(
                    text = text,
                    style = ProtonTheme.typography.body2Regular,
                    color = textColor
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
        tonalElevation = ProtonAlertDefaults.tonalElevation,
        containerColor = ProtonAlertDefaults.containerColor,
        shape = ProtonAlertDefaults.shape,
        properties = DialogProperties(
            usePlatformDefaultWidth = !isWideDialog
        ),
        modifier = if (isWideDialog) Modifier.wideDialog() else Modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtonBasicAlert(
    onDismissRequest: () -> Unit,
    isWideDialog: Boolean = false,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = if (isWideDialog) Modifier.wideDialog() else Modifier,
        properties = DialogProperties(usePlatformDefaultWidth = !isWideDialog),
    ) {
        Surface(
            color = ProtonAlertDefaults.containerColor,
            tonalElevation = ProtonAlertDefaults.tonalElevation,
            shape = ProtonAlertDefaults.shape,
        ) {
            Box(modifier = Modifier.padding(vertical = DIALOG_CONTENT_PADDING)) {
                content()
            }
        }
    }
}

@Composable
fun ProtonDialogButton(onClick: () -> Unit, text: String, enabled: Boolean = true, modifier: Modifier = Modifier) {
    ProtonTextButton(
        onClick = onClick,
        style = ProtonTheme.typography.headlineSmallUnspecified,
        modifier = modifier,
        enabled = enabled
    ) {
        Text(text = text)
    }
}

@Composable
fun ProtonDialogCheckbox(
    text: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
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

@ProtonVpnPreview
@Composable
fun PreviewProtonAlert() {
    ProtonVpnPreview {
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
}

@ProtonVpnPreview
@Composable
fun PreviewAlertWithImageDetails() {
    ProtonVpnPreview {
        ProtonAlert(
            title = "Title",
            text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod " +
                "tempor incididunt ut labore et dolore magna aliqua.",
            detailsImage = R.drawable.app_icon_preview_notes,
            confirmLabel = "Confirm",
            onConfirm = {},
            dismissLabel = "Dismiss",
            onDismissButton = {},
            checkBox = "Check me",
            checkBoxInitialValue = false,
        )
    }
}
