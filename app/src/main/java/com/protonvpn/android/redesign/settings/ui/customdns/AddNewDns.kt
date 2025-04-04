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
package com.protonvpn.android.redesign.settings.ui.customdns

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.VpnSolidCompactButton
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import com.protonvpn.android.redesign.settings.ui.SubSetting

@Composable
fun AddNewDnsScreen(
    error: AddDnsError?,
    onClose: () -> Unit,
    onAddDns: (String) -> Unit,
    onTextChanged: () -> Unit,
) {
    SubSetting(
        title = stringResource(R.string.settings_add_dns_title),
        onClose = onClose,
    ) {
        DnsInputRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            error = error,
            onAddDns = { newDns ->
                onAddDns(newDns)
            },
            onTextChanged = onTextChanged,
        )
    }
}

@Composable
private fun ColumnScope.DnsInputRow(
    modifier: Modifier,
    error: AddDnsError?,
    onAddDns: (String) -> Unit,
    onTextChanged: () -> Unit,
) {
    var currentDns by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
    ) {
        ProtonOutlinedTextField(
            value = currentDns,
            placeholderText = stringResource(id = R.string.settings_add_dns_placeholder),
            onValueChange = {
                currentDns = it
                onTextChanged()
            },
            isError = error != null,
            assistiveText = stringResource(R.string.settings_add_dns_description),
            errorText = error?.errorRes?.let { stringResource(it) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAddDns(currentDns.text) }),
            modifier = Modifier.focusRequester(focusRequester).weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        VpnSolidCompactButton(
            text = stringResource(R.string.settings_add_dns_new_entry_button),
            onClick = {
                onAddDns(currentDns.text)
            },
        )
    }
}

@Preview
@Composable
fun AddNewDnsScreenPreview() {
    AddNewDnsScreen(
        error = null,
        {}, {}, {}
    )
}

@Preview
@Composable
fun AddNewDnsScreenErrorPreview() {
    AddNewDnsScreen(
        error = AddDnsError.InvalidInput,
        {}, {}, {}
    )
}
