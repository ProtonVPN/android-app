/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.android.ui.drawer.bugreport

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.auth.AuthFlowStartHelper
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.utils.FileUtils
import com.protonvpn.android.utils.Storage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import me.proton.core.network.domain.ApiResult
import me.proton.core.presentation.ui.view.ProtonInput
import me.proton.core.user.domain.extension.isCredentialLess
import javax.inject.Inject

@HiltViewModel
class ReportBugActivityViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val dispatcherProvider: VpnDispatcherProvider,
    private val currentUser: CurrentUser,
    private val isTv: IsTvCheck,
    private val authFlowStartHelper: AuthFlowStartHelper,
    private val prepareAndPostBugReport: PrepareAndPostBugReport,
) : ViewModel() {

    interface DynamicInputUI {
        fun getSubmitText(): String?
        fun setInputError(error: String)
    }

    sealed class ViewState {
        object Loading : ViewState()
        data class Categories(val categoryList: List<Category>) : ViewState()
        data class Suggestions(val category: Category) : ViewState()
        data class Report(val category: Category) : ViewState()
        data class Error(val error: ApiResult.Error) : ViewState()
        object SubmittingReport : ViewState()
        object Finish : ViewState()
    }

    enum class UiEvent {
        ShowLoginDialog
    }

    private lateinit var categories: List<Category>

    private val _state = MutableLiveData<ViewState>(ViewState.Loading)
    val state: LiveData<ViewState> = _state

    private val _event = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val event: SharedFlow<UiEvent> = _event

    init {
        viewModelScope.launch {
            categories = loadBugReportForm().categories // Set before state change.
            _state.value = ViewState.Categories(categories)
        }
    }

    suspend fun getUserEmail() = currentUser.user()?.email

    fun getCategories() = categories

    fun navigateToSuggestions(category: Category) {
        if (category.suggestions.isNotEmpty()) {
            _state.value = ViewState.Suggestions(category)
        } else {
            navigateToReport(category)
        }
    }

    fun navigateToReport(category: Category) {
        viewModelScope.launch {
            val user = currentUser.user()
            if (!isTv() && user != null && user.isCredentialLess()) {
                _event.tryEmit(UiEvent.ShowLoginDialog)
            } else {
                _state.value = ViewState.Report(category)
            }
        }
    }

    private fun generateReportDescription(category: Category, dynamicInputMap: Map<InputField, DynamicInputUI>): String {
        val fields = dynamicInputMap.asIterable().joinToString("\n\n") {
            it.key.submitLabel + "\n" + it.value.getSubmitText()
        }
        return "Category: ${category.submitLabel}\n\n$fields"
    }

    private fun hasMissingFields(emailField: ProtonInput, dynamicFields: List<DynamicInputUI>): Boolean {
        var missingFieldsFound = false

        val email = emailField.text
        val emailError = when {
            email.isNullOrEmpty() -> R.string.bugReportErrorEmptyEmail
            !isEmailValid(email) -> R.string.bugReportErrorInvalidEmail
            else -> null
        }
        emailError?.let {
            emailField.setInputError(emailField.context.getString(it))
            missingFieldsFound = true
        }

        dynamicFields.forEach {
            if (it.getSubmitText().isNullOrEmpty()) {
                it.setInputError(emailField.context.getString(R.string.dynamic_report_field_mandatory))
                missingFieldsFound = true
            }
        }

        return missingFieldsFound
    }

    fun startSignInFlow() {
        authFlowStartHelper.startAuthFlow(AuthFlowStartHelper.Type.SignIn)
    }

    fun startCreateAccountFlow() {
        authFlowStartHelper.startAuthFlow(AuthFlowStartHelper.Type.CreateAccount)
    }

    fun prepareAndPostReport(
        emailField: ProtonInput,
        category: Category,
        dynamicInputMap: Map<InputField, DynamicInputUI>,
        attachLog: Boolean
    ) {
        mainScope.launch {
            if (hasMissingFields(emailField, dynamicInputMap.filter { it.key.isMandatory }.values.toList()))
                return@launch
            _state.value = ViewState.SubmittingReport
            val email = emailField.text.toString().trim { it <= ' ' }
            val userGeneratedDescription = generateReportDescription(category, dynamicInputMap)
            val result = prepareAndPostBugReport(email, userGeneratedDescription, attachLog)

            _state.value = result.toViewState()
        }
    }

    private suspend fun loadBugReportForm(): DynamicReportModel = withContext(dispatcherProvider.Io) {
        Storage.load<DynamicReportModel>(
            DynamicReportModel::class.java
        ) { DynamicReportModel(FileUtils.getObjectFromAssets(ListSerializer(Category.serializer()), DEFAULT_BUG_REPORT_FILE)) }
    }

    private fun isEmailValid(email: CharSequence): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun ApiResult<GenericResponse>.toViewState() = when (this) {
        is ApiResult.Success<GenericResponse> -> ViewState.Finish
        is ApiResult.Error -> ViewState.Error(this)
    }

    companion object {
        private const val DEFAULT_BUG_REPORT_FILE = "defaultbugreport.json"
    }
}
