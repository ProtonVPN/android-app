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

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.theme.VpnTheme
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionStrongNorm
import me.proton.core.compose.theme.captionUnspecified
import me.proton.core.compose.theme.defaultHint
import me.proton.core.compose.theme.defaultNorm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressWarnings("LongParameterList")
fun ProtonOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = ProtonTheme.typography.defaultNorm,
    labelText: String? = null,
    placeholderText: String? = null,
    assistiveText: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val supportingTextString =
        if (isError && errorText != null) errorText else assistiveText
    val supportingText: @Composable (() -> Unit)? = supportingTextString?.let { text ->
        {
            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            Text(
                text,
                color = if (isError) ProtonTheme.colors.notificationError else ProtonTheme.colors.textWeak,
                style = ProtonTheme.typography.captionUnspecified,
                // The supportive text's padding is hardcoded in TextFieldLayout, several levels deep in
                // OutlinedTextFieldDecorationBox. Use an ugly hack to undo it :-/
                modifier = Modifier.graphicsLayer {
                    translationX = if (isRtl) 16.dp.toPx() else -16.dp.toPx()
                }
            )
        }
    }
    val placeholder: @Composable (() -> Unit)? =
        placeholderText?.let { @Composable { Text(placeholderText, style = ProtonTheme.typography.defaultHint) } }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = ProtonTheme.colors.backgroundSecondary,
        unfocusedContainerColor = ProtonTheme.colors.backgroundSecondary,
        disabledContainerColor = ProtonTheme.colors.backgroundSecondary,
        focusedBorderColor = ProtonTheme.colors.interactionNorm,
        unfocusedBorderColor = Color.Transparent,
    )
    val cursorColor = rememberUpdatedState(
        if (isError) {
            ProtonTheme.colors.notificationError
        } else {
            textStyle.color.takeOrElse { ProtonTheme.colors.textNorm }
        }
    )
    BasicTextField(
        value = value,
        modifier = if (labelText != null) {
            modifier.semantics(mergeDescendants = true) {}
        } else {
            modifier
        },
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        cursorBrush = SolidColor(cursorColor.value),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        decorationBox = @Composable { innerTextField ->
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = TextFieldDefaults.MinWidth)
            ) {
                if (labelText != null) {
                    Text(labelText, style = ProtonTheme.typography.captionStrongNorm)
                    VerticalSpacer()
                }
                // Wrap OutlinedTextFieldDecorationBox in a Box because there's no way to pass size modifier directly.
                Box(Modifier.fillMaxWidth(), propagateMinConstraints = true) {
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = value,
                        innerTextField = innerTextField,
                        enabled = enabled,
                        singleLine = singleLine,
                        visualTransformation = visualTransformation,
                        interactionSource = interactionSource,
                        isError = isError,
                        label = null,
                        placeholder = placeholder,
                        leadingIcon = null,
                        trailingIcon = null,
                        supportingText = supportingText,
                        colors = colors,
                        contentPadding = OutlinedTextFieldDefaults.contentPadding(),
                        container = {
                            OutlinedTextFieldDefaults.ContainerBox(
                                enabled = enabled,
                                isError = isError,
                                interactionSource = interactionSource,
                                colors = colors,
                                shape = OutlinedTextFieldDefaults.shape,
                            )
                        },
                    )
                }
            }
        }
    )
}

@Preview(
    showBackground = true,
    backgroundColor = 0xffffffff
)
@Composable
private fun PreviewProtonOutlinedTextField() {
    var enteredText by remember { mutableStateOf("Input Text") }
    VpnTheme {
        ProtonOutlinedTextField(
            value = enteredText,
            labelText = "Username or e-mail",
            assistiveText = "Assistive Text",
            onValueChange = { enteredText = it },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
