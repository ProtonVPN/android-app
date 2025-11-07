package com.protonvpn.android.redesign.reports.ui

import android.app.Activity
import android.util.Patterns
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.models.config.bugreport.Suggestion
import com.protonvpn.android.models.config.bugreport.TYPE_DROPDOWN
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.ui.drawer.bugreport.PrepareAndPostBugReport
import com.protonvpn.android.update.AppUpdateBannerState
import com.protonvpn.android.update.AppUpdateBannerStateFlow
import com.protonvpn.android.update.AppUpdateInfo
import com.protonvpn.android.update.AppUpdateManager
import com.protonvpn.android.utils.FileUtils
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.combine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import me.proton.core.util.kotlin.takeIfNotBlank
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject

@HiltViewModel
class BugReportViewModel @Inject constructor(
    private val appUpdateManager: AppUpdateManager,
    private val currentUser: CurrentUser,
    private val dispatcherProvider: VpnDispatcherProvider,
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
        val form: BugReportForm,
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

    data class BugReportForm(
        val initialEmail: String = "",
        val email: String = "",
        val isValidEmail: Boolean = true,
        val reportInputs: Map<String, BugReportFormInput> = emptyMap(),
        val sendLogs: Boolean = true,
    ) {

        fun getValueForField(field: InputField): String = reportInputs[field.submitLabel]
            ?.value
            .orEmpty()

        fun getIsErrorForField(field: InputField): Boolean = reportInputs[field.submitLabel]
            ?.isError
            ?: false

    }

    enum class BugReportNetworkError(@param:StringRes val resId: Int) {
        NoInternet(resId = R.string.dynamic_report_completion_error_description_no_internet),
        Timeout(resId = R.string.dynamic_report_completion_error_description_timeout),
        Unknown(resId = R.string.dynamic_report_completion_error_description_generic),
    }

    sealed interface Event {

        data class OnBugReportSubmitError(val networkError: BugReportNetworkError) : Event

        data object OnBugReportSubmitSuccess : Event

    }

    private val bugReportFormFlow = MutableStateFlow(value = BugReportForm())

    private val categoriesFlow: Flow<List<Category>> = flow {
        val reportModel = withContext(dispatcherProvider.Io) {
            Storage.load<DynamicReportModel>(DynamicReportModel::class.java) {
                DynamicReportModel(
                    categories = FileUtils.getObjectFromAssets(
                        serializer = ListSerializer(elementSerializer = Category.serializer()),
                        jsonAssetPath = BUG_REPORT_FILE_NAME,
                    )
                )
            }
        }

        emit(value = reportModel.categories)
    }

    private val currentStepFlow = MutableStateFlow<BugReportSteps>(value = BugReportSteps.Menu)

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
        bugReportFormFlow,
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
                initForm()
            }
        }
    }

    private suspend fun initForm() {
        val inputFields = viewStateFlow.value?.inputFields ?: emptyList()

        val currentForm = bugReportFormFlow.value

        val initialEmail = currentUser.user()?.email.orEmpty()

        val email = currentForm.email.takeIfNotBlank() ?: initialEmail

        val initialReportInputs = inputFields.associate { field ->
            field.submitLabel to field.toFormInput(
                value = currentForm.getValueForField(field = field),
                isError = currentForm.getIsErrorForField(field = field),
            )
        }

        bugReportFormFlow.update {
            BugReportForm(
                initialEmail = initialEmail,
                email = email,
                reportInputs = initialReportInputs,
            )
        }
    }

    private fun resetForm() {
        bugReportFormFlow.update { BugReportForm() }
    }

    private fun resetCategory() {
        savedStateHandle[SELECTED_CATEGORY_KEY] = null
    }

    fun onAppUpdateStart(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        appUpdateManager.launchUpdateFlow(activity, appUpdateInfo)
    }

    fun onSelectCategory(category: Category) {
        savedStateHandle[SELECTED_CATEGORY_KEY] = category
    }

    fun onUpdateCurrentStep(newCurrentStep: BugReportSteps) {
        currentStepFlow.update { newCurrentStep }
    }

    fun onFormEmailChanged(newEmail: String) {
        bugReportFormFlow.update { currentForm ->
            currentForm.copy(
                email = newEmail,
                isValidEmail = true,
            )
        }
    }

    fun onFormFieldChanged(field: InputField, newValue: String) {
        bugReportFormFlow.update { currentForm ->
            val updatedInput = field.submitLabel to field.toFormInput(
                value = newValue,
                isError = false,
            )

            currentForm.copy(
                reportInputs = currentForm.reportInputs + updatedInput
            )
        }
    }

    fun onFormSendLogsChanged(newSendLogs: Boolean) {
        bugReportFormFlow.update { currentForm ->
            currentForm.copy(sendLogs = newSendLogs)
        }
    }

    fun onSubmitReport() {
        val viewState = viewStateFlow.value ?: return

        if (viewState.selectedCategory == null) return

        if (!validateBugReportForm(form = viewState.form)) return

        mainScope.launch {
            isLoadingFlow.update { true }

            val networkError = prepareAndPostBugReport(
                email = viewState.form.email,
                attachLog = viewState.form.sendLogs,
                userGeneratedDescription = generateBugReportDescription(
                    form = viewState.form,
                    category = viewState.selectedCategory,
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

    private fun InputField.toFormInput(
        value: String,
        isError: Boolean
    ): BugReportFormInput = when (type) {
        TYPE_DROPDOWN -> BugReportFormInput.Dropdown(
            value = value,
            isError = isError,
            isMandatory = isMandatory,
            dropdownOptions = dropdownOptions,
        )

        else -> BugReportFormInput.TextField(
            value = value,
            isError = isError,
            isMandatory = isMandatory,
        )
    }

    private fun validateBugReportForm(form: BugReportForm): Boolean {
        val isValidEmail = Patterns.EMAIL_ADDRESS.matcher(form.email).matches()

        var isValidForm = isValidEmail

        val validatedInputs = form.reportInputs.toMutableMap()

        form.reportInputs.forEach { (id, input) ->
            if (input.isMandatory && input.submitValue.isNullOrBlank()) {
                validatedInputs[id] = input.copy(isError = true)

                isValidForm = false
            }
        }

        if (!isValidForm) {
            bugReportFormFlow.update {
                form.copy(
                    isValidEmail = isValidEmail,
                    reportInputs = validatedInputs,
                )
            }
        }

        return isValidForm
    }

    private fun generateBugReportDescription(form: BugReportForm, category: Category) =
        form.reportInputs
            .asIterable()
            .joinToString("\n\n") { inputEntry -> "${inputEntry.key} \n ${inputEntry.value.submitValue}" }
            .let { fields -> "Category: ${category.submitLabel}\n\n$fields" }

    private companion object {

        private const val BUG_REPORT_FILE_NAME = "defaultbugreport.json"

        private const val SELECTED_CATEGORY_KEY = "bug_report_selected_category"

    }

}
