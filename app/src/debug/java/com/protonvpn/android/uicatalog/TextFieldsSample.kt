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

package com.protonvpn.android.uicatalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallWeak

class TextFieldsSample : SampleScreen("Text Fields", "text_fields") {

    @Composable
    override fun Content(modifier: Modifier, snackbarHostState: SnackbarHostState) {
        Column(modifier = modifier.padding(16.dp)) {
            SingleLabeledComponent("Minimal text field") {
                var enteredText by remember { mutableStateOf("") }
                ProtonOutlinedTextField(
                    value = enteredText,
                    onValueChange = { enteredText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SingleLabeledComponent("Regular with label and assistive text") {
                var enteredText by remember { mutableStateOf("") }
                ProtonOutlinedTextField(
                    value = enteredText,
                    labelText = "Username or e-mail",
                    assistiveText = "Assistive text",
                    errorText = "Error message",
                    placeholderText = "Write something...",
                    onValueChange = { enteredText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SingleLabeledComponent("Error state with label and error text") {
                var enteredText by remember { mutableStateOf("") }
                ProtonOutlinedTextField(
                    value = enteredText,
                    labelText = "Username or e-mail",
                    assistiveText = "Assistive text",
                    errorText = "Error message",
                    onValueChange = { enteredText = it },
                    isError = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SingleLabeledComponent("Single line") {
                var enteredText by remember { mutableStateOf("") }
                ProtonOutlinedTextField(
                    value = enteredText,
                    onValueChange = { enteredText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            SingleLabeledComponent("Max lines: 3") {
                var enteredText by remember { mutableStateOf("") }
                ProtonOutlinedTextField(
                    value = enteredText,
                    onValueChange = { enteredText = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
            Text(
                "Support for minLines will be added in the next Material 3 version (1.1.0)...",
                style = ProtonTheme.typography.defaultSmallWeak,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

