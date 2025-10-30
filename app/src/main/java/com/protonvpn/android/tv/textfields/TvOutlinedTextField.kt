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

package com.protonvpn.android.tv.textfields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import com.protonvpn.android.redesign.base.ui.optional
import com.protonvpn.android.redesign.base.ui.previews.PreviewBooleanProvider
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm

@Composable
internal fun TvOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    assistiveText: String? = null,
    labelText: String? = null,
    placeholderText: String? = null,
    errorText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        ProtonOutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .optional(
                    predicate = { focusRequester != null },
                    modifier = if (focusRequester == null) {
                        Modifier
                    } else {
                        Modifier.focusRequester(focusRequester)
                    }
                ),
            value = value,
            onValueChange = onValueChange,
            labelText = labelText,
            placeholderText = placeholderText,
            isError = isError,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            textStyle = if (isError) {
                ProtonTheme.typography.defaultNorm.copy(color = ProtonTheme.colors.notificationError)
            } else {
                ProtonTheme.typography.defaultNorm
            },
            cursorColor = ProtonTheme.colors.interactionNorm,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )

        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = ProtonTheme.typography.captionRegular,
                color = ProtonTheme.colors.notificationError,
            )
        } else {
            assistiveText?.let { text ->
                Text(
                    text = text,
                    style = ProtonTheme.typography.captionRegular,
                    color = ProtonTheme.colors.textWeak,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewTvOutlinedTextField(
    @PreviewParameter(PreviewBooleanProvider::class) isError: Boolean,
) {
    ProtonVpnPreview(isDark = true) {
        TvOutlinedTextField(
            value = TextFieldValue("Input value"),
            onValueChange = {},
            assistiveText = "Assistive text",
            errorText = "Error text",
            isError = isError,
        )
    }
}
