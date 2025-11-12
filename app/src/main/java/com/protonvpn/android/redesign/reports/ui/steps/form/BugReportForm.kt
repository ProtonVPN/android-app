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

package com.protonvpn.android.redesign.reports.ui.steps.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.models.config.bugreport.DropdownField
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.models.config.bugreport.TYPE_DROPDOWN
import com.protonvpn.android.models.config.bugreport.TYPE_MULTILINE
import com.protonvpn.android.models.config.bugreport.TYPE_SINGLELINE
import com.protonvpn.android.redesign.base.ui.ProtonDropdownMenu
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import com.protonvpn.android.redesign.reports.ui.BugReportViewModel
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun BugReportForm(
    viewState: BugReportViewModel.ViewState,
    onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
    onFormEmailChanged: (String) -> Unit,
    onFormFieldChanged: (InputField, String) -> Unit,
    onFormSendLogsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = Unit) {
        onSetCurrentStep(BugReportViewModel.BugReportSteps.Form)

        focusRequester.requestFocus()
    }

    LazyColumn(
        modifier = modifier.testTag(tag = "BugReportForm"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(space = 32.dp),
    ) {
        item(key = "form_email_key") {
            val initialEmailName = viewState.form.initialEmail

            var emailValue by rememberSaveable(
                initialEmailName,
                stateSaver = TextFieldValue.Saver,
            ) {
                mutableStateOf(
                    value = TextFieldValue(
                        text = initialEmailName,
                        selection = TextRange(index = initialEmailName.length),
                    )
                )
            }

            ProtonOutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 8.dp,
                        bottom = 16.dp,
                    )
                    .focusRequester(focusRequester = focusRequester),
                value = emailValue,
                onValueChange = { newEmailValue ->
                    emailValue = newEmailValue

                    onFormEmailChanged(newEmailValue.text)
                },
                labelText = stringResource(id = R.string.report_bug_email_label),
                placeholderText = stringResource(id = R.string.report_bug_email_hint),
                errorText = stringResource(id = R.string.bugReportErrorInvalidEmail),
                isError = !viewState.form.isValidEmail,
                singleLine = true,
                cursorColor = ProtonTheme.colors.interactionNorm,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
        }

        items(
            items = viewState.inputFields,
            key = { inputField -> inputField.submitLabel },
        ) { inputField ->
            val labelText = inputField.label
            val placeholderText = inputField.placeholder
            val isError = viewState.form.getIsErrorForField(field = inputField)
            val errorText = stringResource(id = R.string.dynamic_report_field_mandatory)

            when (inputField.type) {
                TYPE_SINGLELINE -> {
                    var singleLineValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        mutableStateOf(value = TextFieldValue(text = ""))
                    }

                    ProtonOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = singleLineValue,
                        onValueChange = { newSingleLineValue ->
                            singleLineValue = newSingleLineValue

                            onFormFieldChanged(inputField, newSingleLineValue.text)
                        },
                        labelText = labelText,
                        placeholderText = placeholderText,
                        errorText = errorText,
                        isError = isError,
                        singleLine = true,
                        cursorColor = ProtonTheme.colors.interactionNorm,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                }

                TYPE_MULTILINE -> {
                    var multiLineValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        mutableStateOf(value = TextFieldValue(text = ""))
                    }

                    ProtonOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = multiLineValue,
                        onValueChange = { newMultiLineValue ->
                            multiLineValue = newMultiLineValue

                            onFormFieldChanged(inputField, newMultiLineValue.text)
                        },
                        labelText = labelText,
                        placeholderText = placeholderText,
                        errorText = errorText,
                        isError = isError,
                        singleLine = false,
                        minLines = 3,
                        cursorColor = ProtonTheme.colors.interactionNorm,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                }

                TYPE_DROPDOWN -> {
                    var dropdownValue by rememberSaveable {
                        mutableStateOf<String?>(value = null)
                    }

                    ProtonDropdownMenu(
                        modifier = Modifier.fillMaxWidth(),
                        labelText = labelText,
                        placeholderText = placeholderText.orEmpty(),
                        errorText = errorText,
                        isError = isError,
                        options = inputField.dropdownOptions.map(DropdownField::label),
                        selectedOption = dropdownValue,
                        onSelectOption = { newSelectedOption ->
                            dropdownValue = newSelectedOption

                            onFormFieldChanged(inputField, newSelectedOption)
                        },
                    )
                }
            }
        }

        item(key = "form_send_logs_key") {
            var sendLogsValue by rememberSaveable {
                mutableStateOf(value = viewState.form.sendLogs)
            }

            BugReportFormCheckBox(
                modifier = Modifier.fillMaxWidth(),
                isChecked = sendLogsValue,
                onCheckedChange = { isChecked ->
                    sendLogsValue = isChecked

                    onFormSendLogsChanged(isChecked)
                },
            )
        }
    }
}
