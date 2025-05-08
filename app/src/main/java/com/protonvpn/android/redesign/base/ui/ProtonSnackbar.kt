/*
 * Copyright (c) 2023. Proton AG
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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonVpnPreview
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallStrongUnspecified
import me.proton.core.compose.theme.defaultSmallUnspecified

enum class ProtonSnackbarType {
    SUCCESS, WARNING, ERROR, NORM
}

data class ProtonSnackbarVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val duration: SnackbarDuration,
    val type: ProtonSnackbarType
) : SnackbarVisuals {

    // Dismiss actions are not supported by our snackbars.
    override val withDismissAction: Boolean = false
}

suspend fun SnackbarHostState.showSnackbar(
    message: String,
    type: ProtonSnackbarType,
    actionLabel: String? = null,
    duration: SnackbarDuration =
        if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite
): SnackbarResult =
    showSnackbar(ProtonSnackbarVisuals(message, actionLabel, duration, type))

@Composable
fun ProtonSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    actionOnNewLine: Boolean = false,
    shape: Shape = ProtonTheme.shapes.medium,
    contentColor: Color = ProtonTheme.colors.textInverted,
    actionColor: Color = ProtonTheme.colors.textInverted,
    actionContentColor: Color = actionColor,
    dismissActionContentColor: Color = ProtonTheme.colors.iconInverted
) {
    // Copied from Snackbar(SnackbarData, ...) to override text styles.
    val actionLabel = snackbarData.visuals.actionLabel
    val actionComposable: (@Composable () -> Unit)? = if (actionLabel != null) {
        @Composable {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = actionColor),
                onClick = { snackbarData.performAction() },
                content = { Text(actionLabel, style = ProtonTheme.typography.defaultSmallStrongUnspecified) }
            )
        }
    } else {
        null
    }

    val containerColor = when ((snackbarData.visuals as? ProtonSnackbarVisuals)?.type) {
        ProtonSnackbarType.SUCCESS -> ProtonTheme.colors.notificationSuccess
        ProtonSnackbarType.WARNING -> ProtonTheme.colors.notificationWarning
        ProtonSnackbarType.ERROR -> ProtonTheme.colors.notificationError
        ProtonSnackbarType.NORM -> ProtonTheme.colors.notificationNorm
        else -> SnackbarDefaults.color
    }
    Snackbar(
        modifier = modifier.padding(12.dp),
        action = actionComposable,
        dismissAction = null,
        actionOnNewLine = actionOnNewLine,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        actionContentColor = actionContentColor,
        dismissActionContentColor = dismissActionContentColor,
        content = { Text(snackbarData.visuals.message, style = ProtonTheme.typography.defaultSmallUnspecified) }
    )
}

private val previewSnackbarVisuals = ProtonSnackbarVisuals(
    "This is a snackbar",
    "Action",
    duration = SnackbarDuration.Short,
    type = ProtonSnackbarType.SUCCESS
)

private val previewSnackbarData = object : SnackbarData {
    override val visuals: SnackbarVisuals = previewSnackbarVisuals
    override fun dismiss() = Unit
    override fun performAction() = Unit
}

@ProtonVpnPreview
@Composable
fun ProtonSnackbarAllFieldsPreview() {
    ProtonVpnPreview {
        ProtonSnackbar(
            snackbarData = previewSnackbarData,
        )
    }
}
