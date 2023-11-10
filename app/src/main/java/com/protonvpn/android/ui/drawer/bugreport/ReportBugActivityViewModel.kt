/*
 * Copyright (c) 2021. Proton Technologies AG
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

import android.os.Build
import android.telephony.TelephonyManager
import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.FileLogWriter
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.bugreport.Category
import com.protonvpn.android.models.config.bugreport.InputField
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.SentryIntegration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiResult
import me.proton.core.presentation.ui.view.ProtonInput
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ReportBugActivityViewModel @Inject constructor(
    private val mainScope: CoroutineScope,
    private val appConfig: AppConfig,
    private val api: ProtonApiRetroFit,
    private val currentUser: CurrentUser,
    private val serverListUpdater: ServerListUpdater,
    private val telephony: TelephonyManager?,
    private val guestHole: GuestHole,
) : ViewModel() {

    interface DynamicInputUI {
        fun getSubmitText(): String?
        fun setInputError(error: String)
    }

    sealed class ViewState {
        data class Categories(val categoryList: List<Category>) : ViewState()
        data class Suggestions(val category: Category) : ViewState()
        data class Report(val category: Category) : ViewState()
        data class Error(val error: ApiResult.Error) : ViewState()
        object SubmittingReport : ViewState()
        object Finish : ViewState()
    }

    private val _state = MutableLiveData<ViewState>(ViewState.Categories(getCategories()))
    val state: LiveData<ViewState> = _state

    suspend fun getUserEmail() = currentUser.user()?.email

    fun getCategories() = appConfig.dynamicReportModelObservable.value!!.categories

    fun navigateToSuggestions(category: Category) {
        if (category.suggestions.isNotEmpty()) {
            _state.value = ViewState.Suggestions(category)
        } else {
            navigateToReport(category)
        }
    }

    fun navigateToReport(category: Category) {
        _state.value = ViewState.Report(category)
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

    fun prepareAndPostReport(
        isTV: Boolean,
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
            val description =
                "$userGeneratedDescription\n\nSentry user ID: ${SentryIntegration.getInstallationId()}"
            val client = if (isTV) "Android TV app" else "Android app"
            val builder: MultipartBody.Builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("Client", client)
                .addFormDataPart("ClientVersion", BuildConfig.VERSION_NAME)
                .addFormDataPart("Username", currentUser.user()?.name ?: "")
                .addFormDataPart("Email", email)
                .addFormDataPart("Plan", currentUser.vpnUser()?.userTierName ?: "unknown")
                .addFormDataPart("OS", "Android")
                .addFormDataPart("OSVersion", Build.VERSION.RELEASE.toString())
                .addFormDataPart("ClientType", "2")
                .addFormDataPart("Country", serverListUpdater.lastKnownCountry ?: "Unknown")
                .addFormDataPart("ISP", getIspValue())
                .addFormDataPart("Title", "Report from $client")
                .addFormDataPart("Description", description)

            if (attachLog) {
                val logFiles = try {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    val logFiles = ProtonLogger.getLogFilesForUpload()
                    logFiles.forEach { (name, file) ->
                        builder.addFormDataPart(name, name, file.asRequestBody(name.toMediaTypeOrNull()))
                    }
                    logFiles
                } catch (e: IOException) {
                    emptyList<FileLogWriter.LogFile>()
                }

                val result = guestHole.runWithGuestHoleFallback {
                    api.postBugReport(builder.build())
                }
                ProtonLogger.clearUploadTempFiles(logFiles)
                _state.value = result.toViewState()
            } else {
                val result = api.postBugReport(builder.build())
                _state.value = result.toViewState()
            }
        }
    }

    private fun isEmailValid(email: CharSequence): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun getIspValue(): String {
        val lastKnownIsp = serverListUpdater.lastKnownIsp ?: "Unknown"
        val mobileNetwork = telephony?.networkOperatorName
        return if (mobileNetwork != null) {
            "$lastKnownIsp, mobile network: $mobileNetwork"
        } else {
            lastKnownIsp
        }
    }

    private fun ApiResult<GenericResponse>.toViewState() = when (this) {
        is ApiResult.Success<GenericResponse> -> ViewState.Finish
        is ApiResult.Error -> ViewState.Error(this)
    }
}
