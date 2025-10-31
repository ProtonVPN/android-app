package com.protonvpn.android.redesign.reports.ui

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.DynamicReportModel
import com.protonvpn.android.models.config.bugreport.Suggestion
import com.protonvpn.android.update.AppUpdateBannerState
import com.protonvpn.android.update.AppUpdateBannerStateFlow
import com.protonvpn.android.update.AppUpdateInfo
import com.protonvpn.android.update.AppUpdateManager
import com.protonvpn.android.utils.FileUtils
import com.protonvpn.android.utils.Storage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

@HiltViewModel
class BugReportViewModel @Inject constructor(
    private val appUpdateManager: AppUpdateManager,
    private val dispatcherProvider: VpnDispatcherProvider,
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
    ) {

        val initialStep: BugReportSteps = BugReportSteps.Menu

        val canCancelBugReport: Boolean = currentStep == BugReportSteps.Suggestions

        val canMoveToPreviousStep: Boolean = currentStep != initialStep

        val stepsCount: Int = BugReportSteps.entries.size

        val subtitle: String? = selectedCategory?.label

        val suggestions: List<Suggestion> = selectedCategory?.suggestions.orEmpty()

    }

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

    val viewStateFlow: StateFlow<ViewState?> = combine(
        currentStepFlow.onEach { currentStep ->
            when (currentStep) {
                BugReportSteps.Menu -> onResetCategory()
                BugReportSteps.Suggestions,
                BugReportSteps.Form -> Unit
            }
        },
        categoriesFlow,
        selectedCategoryFlow,
        appUpdateBannerStateFlow,
        ::ViewState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    fun onAppUpdateStart(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        appUpdateManager.launchUpdateFlow(activity, appUpdateInfo)
    }

    private fun onResetCategory() {
        savedStateHandle[SELECTED_CATEGORY_KEY] = null
    }

    fun onSelectCategory(category: Category) {
        savedStateHandle[SELECTED_CATEGORY_KEY] = category
    }

    fun onUpdateCurrentStep(newCurrentStep: BugReportSteps) {
        currentStepFlow.update { newCurrentStep }
    }

    private companion object {

        private const val BUG_REPORT_FILE_NAME = "defaultbugreport.json"

        private const val SELECTED_CATEGORY_KEY = "bug_report_selected_category"

    }

}
