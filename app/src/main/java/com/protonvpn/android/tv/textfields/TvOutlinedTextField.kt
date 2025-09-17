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
import androidx.compose.ui.unit.dp
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import com.protonvpn.android.redesign.base.ui.optional
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm

@Composable
internal fun TvOutlinedTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    assistiveText: String? = null,
    placeholderText: String? = null,
    errorText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = 16.dp),
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
            placeholderText = placeholderText,
            isError = isError,
            singleLine = singleLine,
            maxLines = maxLines,
            textStyle = ProtonTheme.typography.defaultNorm,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
        )

        if (isError && errorText != null) {
            Text(
                text = errorText,
                style = ProtonTheme.typography.body2Regular,
                color = ProtonTheme.colors.notificationError,
            )
        } else {
            assistiveText?.let { text ->
                Text(
                    text = text,
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
                )
            }
        }
    }
}
