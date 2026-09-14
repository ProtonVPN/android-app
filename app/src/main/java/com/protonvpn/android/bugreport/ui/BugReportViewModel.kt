package com.protonvpn.android.bugreport.ui

import android.app.Activity
import android.os.Parcelable
import android.util.Patterns
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.bugreport.BugReportConfigStore
import com.protonvpn.android.bugreport.PrepareAndPostBugReport
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.models.config.bugreport.Suggestion
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.update.AppUpdateBannerState
import com.protonvpn.android.update.AppUpdateBannerStateFlow
import com.protonvpn.android.update.AppUpdateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject

@HiltViewModel
class BugReportViewModel @Inject constructor(
    private val appUpdateManager: AppUpdateManager,
    private val currentUser: CurrentUser,
    private val bugReportConfigStore: BugReportConfigStore,
    private val mainScope: CoroutineScope,
    private val prepareAndPostBugReport: PrepareAndPostBugReport,
    private val savedStateHandle: SavedStateHandle,
    appUpdateBannerStateFlow: AppUpdateBannerStateFlow,
) : ViewModel() {

    enum class BugReportSteps(val stepIndex: Int) {
        Menu(stepIndex = 1),
        Suggestions(stepIndex = 2),
        Form(stepIndex = 3),
    }

    data class ViewState(
        val currentStep: BugReportSteps,
        val categories: List<Category>,
        val selectedCategory: Category?,
        val appUpdateBannerState: AppUpdateBannerState,
        val isLoading: Boolean,
    ) {

        val initialStep: BugReportSteps = BugReportSteps.Menu

        val canCancelBugReport: Boolean = currentStep == BugReportSteps.Suggestions

        val canMoveToPreviousStep: Boolean = currentStep != initialStep

        val stepsCount: Int = BugReportSteps.entries.size

        val subtitle: String? = selectedCategory?.label

        val suggestions: List<Suggestion> = selectedCategory?.suggestions.orEmpty()

        val inputFields: List<InputField> = selectedCategory?.inputFields.orEmpty()

    }

    @Parcelize
    data class BugReportFormState(
        val email: String? = null,
        val isValidEmail: Boolean = true,
        val dynamicInputs: Map<String, String> = emptyMap(),
        val dynamicInputValidations: Map<String, Boolean> = emptyMap(),
        val sendLogs: Boolean = true,
    ) : Parcelable

    var formState by mutableStateOf(
        value = savedStateHandle.get<BugReportFormState>(FORM_KEY) ?: BugReportFormState()
    )
        private set

    enum class BugReportNetworkError(@param:StringRes val resId: Int) {
        NoInternet(resId = R.string.dynamic_report_completion_error_description_no_internet),
        Timeout(resId = R.string.dynamic_report_completion_error_description_timeout),
        Unknown(resId = R.string.dynamic_report_completion_error_description_generic),
    }

    sealed interface Event {

        data class OnBugReportSubmitError(val networkError: BugReportNetworkError) : Event

        data object OnBugReportSubmitSuccess : Event

    }

    private val categoriesFlow: Flow<List<Category>> = flow {
        val reportModel = bugReportConfigStore.load()
        emit(value = reportModel.categories)
    }

    private val currentStepFlow = savedStateHandle.getStateFlow<BugReportSteps>(
        key = CURRENT_STEP_KEY,
        initialValue = BugReportSteps.Menu,
    )

    private val selectedCategoryFlow = savedStateHandle.getStateFlow<Category?>(
        key = SELECTED_CATEGORY_KEY,
        initialValue = null,
    )

    private val isLoadingFlow = MutableStateFlow(value = false)

    private val eventChannel = Channel<Event>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val eventChannelReceiver: ReceiveChannel<Event> = eventChannel

    val viewStateFlow: StateFlow<ViewState?> = combine(
        currentStepFlow.onEach(::handleStepChange),
        categoriesFlow,
        selectedCategoryFlow,
        appUpdateBannerStateFlow,
        isLoadingFlow,
        ::ViewState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    private suspend fun handleStepChange(newStep: BugReportSteps) {
        when (newStep) {
            BugReportSteps.Menu -> {
                resetCategory()

                resetForm()
            }

            BugReportSteps.Suggestions -> {
                resetForm()
            }

            BugReportSteps.Form -> {
                initFormEmail()
            }
        }
    }

    private suspend fun initFormEmail() {
        if (formState.email == null) {
            currentUser.user()?.email?.let { userEmail ->
                val newFormState = formState.copy(email = userEmail)

                updateFormState(newFormState = newFormState)
            }
        }
    }

    private fun resetForm() {
        updateFormState(newFormState = BugReportFormState())
    }

    private fun resetCategory() {
        savedStateHandle[SELECTED_CATEGORY_KEY] = null
    }

    suspend fun onAppUpdateStart(activity: Activity) {
        appUpdateManager.launchUpdateFlow(activity)
    }

    fun onSelectCategory(category: Category) {
        savedStateHandle[SELECTED_CATEGORY_KEY] = category
    }

    fun onUpdateCurrentStep(newCurrentStep: BugReportSteps) {
        savedStateHandle[CURRENT_STEP_KEY] = newCurrentStep
    }

    fun onFormEmailChanged(newEmail: String) {
        val newFormState = formState.copy(
            email = newEmail,
            isValidEmail = true,
        )

        updateFormState(newFormState = newFormState)
    }

    fun onFormFieldChanged(field: InputField, newValue: String) {
        val newFormState = formState.copy(
            dynamicInputs = formState.dynamicInputs + (field.submitLabel to newValue),
            dynamicInputValidations = formState.dynamicInputValidations + (field.submitLabel to false),
        )

        updateFormState(newFormState = newFormState)
    }

    fun onFormSendLogsChanged(newSendLogs: Boolean) {
        val newFormState = formState.copy(sendLogs = newSendLogs)

        updateFormState(newFormState = newFormState)
    }

    fun onSubmitReport() {
        val viewState = viewStateFlow.value ?: return

        if (viewState.selectedCategory == null) return

        if (!validateBugReportForm(inputFields = viewState.inputFields)) return

        mainScope.launch {
            isLoadingFlow.update { true }

            val networkError = prepareAndPostBugReport(
                email = formState.email.orEmpty(),
                attachLog = formState.sendLogs,
                userGeneratedDescription = generateBugReportDescription(
                    category = viewState.selectedCategory
                ),
            ).getNetworkError()

            val event = if (networkError == null) {
                Event.OnBugReportSubmitSuccess
            } else {
                Event.OnBugReportSubmitError(networkError = networkError)
            }

            eventChannel.send(element = event)

            isLoadingFlow.update { false }
        }
    }

    private fun ApiResult<GenericResponse>.getNetworkError(): BugReportNetworkError? = when (this) {
        is ApiResult.Error.Timeout -> BugReportNetworkError.Timeout
        is ApiResult.Error.NoInternet,
        is ApiResult.Error.Connection -> BugReportNetworkError.NoInternet
        is ApiResult.Error.Http,
        is ApiResult.Error.Parse -> BugReportNetworkError.Unknown

        is ApiResult.Success<GenericResponse> -> null
    }

    private fun validateBugReportForm(inputFields: List<InputField>): Boolean {
        val isValidEmail = Patterns.EMAIL_ADDRESS.matcher(formState.email.orEmpty()).matches()

        var isValidForm = isValidEmail

        val dynamicInputValidations = formState.dynamicInputValidations.toMutableMap()

        inputFields.forEach { inputField ->
            if(inputField.isMandatory && formState.dynamicInputs[inputField.submitLabel].isNullOrBlank()) {
                dynamicInputValidations[inputField.submitLabel] = true

                isValidForm = false
            }
        }

        if (!isValidForm) {
            val newFormState = formState.copy(
                isValidEmail = isValidEmail,
                dynamicInputValidations = dynamicInputValidations,
            )

            updateFormState(newFormState = newFormState)
        }

        return isValidForm
    }

    private fun updateFormState(newFormState: BugReportFormState) {
        formState = newFormState

        savedStateHandle[FORM_KEY] = newFormState
    }

    private fun generateBugReportDescription(category: Category) = formState.dynamicInputs
        .asIterable()
        .joinToString("\n\n") { inputEntry -> "${inputEntry.key} \n ${inputEntry.value}" }
        .let { fields -> "Category: ${category.submitLabel}\n\n$fields" }

    private companion object {

        private const val CURRENT_STEP_KEY = "bug_report_current_step"

        private const val FORM_KEY = "bug_report_form"

        private const val SELECTED_CATEGORY_KEY = "bug_report_selected_category"

    }

}
