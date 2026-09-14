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

package com.protonvpn.android.tv.reports.steps.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.bugreport.ui.BugReportViewModel
import com.protonvpn.android.bugreport.ui.BugReportViewModel.BugReportFormState
import com.protonvpn.android.models.config.bugreport.DropdownField
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.models.config.bugreport.TYPE_DROPDOWN
import com.protonvpn.android.models.config.bugreport.TYPE_MULTILINE
import com.protonvpn.android.models.config.bugreport.TYPE_SINGLELINE
import com.protonvpn.android.tv.buttons.TvSolidButton
import com.protonvpn.android.tv.menus.TvDropdownMenu
import com.protonvpn.android.tv.textfields.TvOutlinedTextField

@Composable
fun TvBugReportForm(
    viewState: BugReportViewModel.ViewState,
    formState: BugReportFormState,
    onSetCurrentStep: (BugReportViewModel.BugReportSteps) -> Unit,
    onFormEmailChanged: (String) -> Unit,
    onFormFieldChanged: (InputField, String) -> Unit,
    onFormSendLogsChanged: (Boolean) -> Unit,
    onSubmitReportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(key1 = Unit) {
        onSetCurrentStep(BugReportViewModel.BugReportSteps.Form)

        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier
                .focusRequester(focusRequester = focusRequester)
                .weight(weight = 1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(space = 24.dp),
        ) {
            item(key = "form_email_key") {
                TvOutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = formState.email.orEmpty(),
                    onValueChange = onFormEmailChanged,
                    labelText = stringResource(id = R.string.report_bug_email_label),
                    placeholderText = stringResource(id = R.string.report_bug_email_hint),
                    errorText = stringResource(id = R.string.bugReportErrorInvalidEmail),
                    isError = !formState.isValidEmail,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }

            items(
                items = viewState.inputFields,
                key = { inputField -> inputField.submitLabel },
            ) { inputField ->
                val labelText = inputField.label
                val placeholderText = inputField.placeholder
                val value = formState.dynamicInputs[inputField.submitLabel]
                val isError = formState.dynamicInputValidations[inputField.submitLabel] == true
                val errorText = stringResource(id = R.string.dynamic_report_field_mandatory)

                when (inputField.type) {
                    TYPE_SINGLELINE -> {
                        TvOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = value.orEmpty(),
                            onValueChange = { newSingleLineValue ->
                                onFormFieldChanged(inputField, newSingleLineValue)
                            },
                            labelText = labelText,
                            placeholderText = placeholderText,
                            errorText = errorText,
                            isError = isError,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                    }

                    TYPE_MULTILINE -> {
                        TvOutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = value.orEmpty(),
                            onValueChange = { newMultiLineValue ->
                                onFormFieldChanged(inputField, newMultiLineValue)
                            },
                            labelText = labelText,
                            placeholderText = placeholderText,
                            errorText = errorText,
                            isError = isError,
                            singleLine = false,
                            minLines = 3,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                    }

                    TYPE_DROPDOWN -> {
                        TvDropdownMenu(
                            modifier = Modifier.fillMaxWidth(),
                            labelText = labelText,
                            placeholderText = placeholderText.orEmpty(),
                            errorText = errorText,
                            isError = isError,
                            options = inputField.dropdownOptions.map(DropdownField::label),
                            selectedOption = value,
                            onSelectOption = { newSelectedOption ->
                                onFormFieldChanged(inputField, newSelectedOption)
                            },
                        )
                    }
                }
            }

            item(key = "form_send_logs_key") {
                TvBugReportFormCheckbox(
                    modifier = Modifier.fillMaxWidth(),
                    isChecked = formState.sendLogs,
                    onCheckedChange = onFormSendLogsChanged,
                )
            }
        }

        TvSolidButton(
            text = stringResource(id = R.string.send_report),
            isLoading = viewState.isLoading,
            onClick = onSubmitReportClick,
        )
    }
}
