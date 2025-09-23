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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonVpnPreview
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionUnspecified
import me.proton.core.compose.theme.defaultHint
import me.proton.core.compose.theme.defaultNorm

@Composable
@SuppressWarnings("LongParameterList")
fun ProtonOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
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
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    backgroundColor: Color = ProtonTheme.colors.backgroundSecondary,
    textHeightIn: Dp = Dp.Unspecified,
    cursorColor: Color = cursorColor(textStyle, isError).value,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = @Composable { innerTextField ->
        ProtonOutlineDecorationBox(
            value = value.text,
            labelText = labelText,
            placeholderText = placeholderText,
            assistiveText = assistiveText,
            isError = isError,
            errorText = errorText,
            enabled = enabled,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            backgroundColor = backgroundColor,
            textHeightIn = textHeightIn,
            interactionSource = interactionSource,
            innerTextField = innerTextField,
            trailingIcon = trailingIcon,
        )
    }
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
        cursorBrush = SolidColor(value = cursorColor),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        decorationBox = decorationBox
    )
}

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
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    backgroundColor: Color = ProtonTheme.colors.backgroundSecondary,
    textHeightIn: Dp = Dp.Unspecified,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = @Composable { innerTextField ->
        ProtonOutlineDecorationBox(
            value = value,
            labelText = labelText,
            placeholderText = placeholderText,
            assistiveText = assistiveText,
            isError = isError,
            errorText = errorText,
            enabled = enabled,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            backgroundColor = backgroundColor,
            textHeightIn = textHeightIn,
            interactionSource = interactionSource,
            innerTextField = innerTextField,
            trailingIcon = trailingIcon,
        )
    }
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
        cursorBrush = SolidColor(cursorColor(textStyle, isError).value),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        maxLines = maxLines,
        decorationBox = decorationBox
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun ProtonOutlineDecorationBox(
    value: String,
    labelText: String? = null,
    placeholderText: String? = null,
    assistiveText: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    backgroundColor: Color = ProtonTheme.colors.backgroundSecondary,
    textHeightIn: Dp = Dp.Unspecified,
    trailingIcon: @Composable (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    innerTextField: @Composable () -> Unit,
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
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = TextFieldDefaults.MinWidth)
    ) {
        if (labelText != null) {
            Text(
                labelText,
                style = ProtonTheme.typography.captionMedium,
                color = if (isError) ProtonTheme.colors.notificationError else ProtonTheme.colors.textNorm)
            VerticalSpacer()
        }
        val colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = backgroundColor,
            unfocusedContainerColor = backgroundColor,
            disabledContainerColor = backgroundColor,
            focusedBorderColor = ProtonTheme.colors.interactionNorm,
            unfocusedBorderColor = Color.Transparent,
            errorBorderColor = ProtonTheme.colors.notificationError,
        )
        // Wrap OutlinedTextFieldDecorationBox in a Box because there's no way to pass size modifier directly.
        Box(Modifier
            .fillMaxWidth()
            .heightIn(textHeightIn), propagateMinConstraints = true) {
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
                trailingIcon = trailingIcon,
                supportingText = supportingText,
                colors = colors,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = ProtonTheme.shapes.medium,
                    )
                },
            )
        }
    }
}

@Composable
private fun cursorColor(textStyle: TextStyle, isError: Boolean) = rememberUpdatedState(
    if (isError) {
        ProtonTheme.colors.notificationError
    } else {
        textStyle.color.takeOrElse { ProtonTheme.colors.textNorm }
    }
)

@ProtonVpnPreview
@Composable
private fun PreviewProtonOutlinedTextField() {
    var enteredText by remember { mutableStateOf(TextFieldValue("Input Text")) }
    ProtonVpnPreview {
        ProtonOutlinedTextField(
            value = enteredText,
            labelText = "Username or e-mail",
            assistiveText = "Assistive Text",
            onValueChange = { enteredText = it },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
