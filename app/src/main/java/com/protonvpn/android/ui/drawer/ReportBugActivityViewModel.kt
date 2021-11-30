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

package com.protonvpn.android.ui.drawer

import android.os.Build
import android.text.TextUtils
import android.util.Patterns
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.R
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.ProtonLoggerImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import me.proton.core.network.domain.ApiResult
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ReportBugActivityViewModel @Inject constructor(
    private val api: ProtonApiRetroFit,
    private val currentUser: CurrentUser
) : ViewModel() {

    sealed class ViewState {
        data class Form(@StringRes val emailError: Int?, @StringRes val reportError: Int?) : ViewState()
        object Submitting : ViewState()
        data class Error(val error: ApiResult.Error) : ViewState()
        object Finish : ViewState()
    }

    private val _state = MutableLiveData<ViewState>(ViewState.Form(null, null))
    val state: LiveData<ViewState> get() { return _state }

    suspend fun prepareAndPostReport(
        emailRaw: String,
        description: String,
        attachLog: Boolean
    ) {
        val email = emailRaw.trim { it <= ' ' }
        val inputValid = checkInput(email, description)
        if (!inputValid)
            return

        _state.value = ViewState.Submitting
        val builder: MultipartBody.Builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("Client", "Android app")
            .addFormDataPart("ClientVersion", BuildConfig.VERSION_NAME)
            .addFormDataPart("Username", currentUser.user()?.displayName ?: "unknown")
            .addFormDataPart("Email", email)
            .addFormDataPart("Plan", currentUser.vpnUser()?.userTierName ?: "unknown")
            .addFormDataPart("OS", "Android")
            .addFormDataPart("OSVersion", Build.VERSION.RELEASE.toString())
            .addFormDataPart("ClientType", "2")
            .addFormDataPart("Country", "Unknown")
            .addFormDataPart("ISP", "Unknown")
            .addFormDataPart("Title", "Report from Android app")
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
                emptyList<ProtonLoggerImpl.LogFile>()
            }
            val result = api.postBugReport(builder.build())
            ProtonLogger.clearUploadTempFiles(logFiles)
            _state.value = result.toViewState()
        } else {
            val result = api.postBugReport(builder.build())
            _state.value = result.toViewState()
        }
    }

    private fun checkInput(email: String, description: String): Boolean {
        val emailError = when {
            TextUtils.isEmpty(email) -> R.string.bugReportErrorEmptyEmail
            !isEmailValid(email) -> R.string.bugReportErrorInvalidEmail
            else -> null
        }

        val descriptionError = if (TextUtils.isEmpty(description)) {
            R.string.bugReportErrorEmptyDescription
        } else {
            null
        }

        _state.value = ViewState.Form(emailError, descriptionError)
        return emailError == null && descriptionError == null
    }

    private fun isEmailValid(email: CharSequence): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun ApiResult<GenericResponse>.toViewState() = when(this) {
        is ApiResult.Success<GenericResponse> -> ViewState.Finish
        is ApiResult.Error -> ViewState.Error(this)
    }
}
