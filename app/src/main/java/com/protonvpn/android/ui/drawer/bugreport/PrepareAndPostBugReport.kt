/*
 * Copyright (c) 2024. Proton AG
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
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.api.GuestHole
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.login.GenericResponse
import com.protonvpn.android.tv.IsTvCheck
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.SentryIntegration
import dagger.Reusable
import me.proton.core.network.domain.ApiResult
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException
import javax.inject.Inject

@Reusable
class PrepareAndPostBugReport @Inject constructor(
    private val currentUser: CurrentUser,
    private val api: ProtonApiRetroFit,
    private val serverListUpdater: ServerListUpdater,
    private val telephony: TelephonyManager?,
    private val guestHole: GuestHole,
    private val isTv: IsTvCheck,
) {

    suspend operator fun invoke(
        email: String,
        userGeneratedDescription: String,
        attachLog: Boolean
    ): ApiResult<GenericResponse> {
        val description =
            "$userGeneratedDescription\n\nSentry user ID: ${SentryIntegration.getInstallationId()}"
        val client = if (isTv()) "Android TV app" else "Android app"
        val builder: MultipartBody.Builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("Client", client)
            .addFormDataPart("ClientVersion", BuildConfig.VERSION_NAME)
            .addFormDataPart("Username", currentUser.user()?.name ?: "")
            .addFormDataPart("Email", email)
            .addFormDataPart("OS", "Android")
            .addFormDataPart("OSVersion", Build.VERSION.RELEASE.toString())
            .addFormDataPart("ClientType", "2")
            .addFormDataPart("Title", "Report from $client")
            .addFormDataPart("Description", description)
            .apply {
                getIspValue()?.let {
                    addFormDataPart("ISP", it)
                }
                currentUser.vpnUser()?.userTierName?.let {
                    addFormDataPart("Plan",  it)
                }
                serverListUpdater.lastKnownCountry?.let {
                    addFormDataPart("Country", it)
                }
            }


        var cleanUp: () -> Unit = {}
        if (attachLog) {
            val logFiles = try {
                @Suppress("BlockingMethodInNonBlockingContext")
                val logFiles = ProtonLogger.getLogFilesForUpload()
                logFiles.forEach { (name, file) ->
                    builder.addFormDataPart(name, name, file.asRequestBody(name.toMediaTypeOrNull()))
                }
                logFiles
            } catch (e: IOException) {
                emptyList()
            }

            cleanUp = { ProtonLogger.clearUploadTempFiles(logFiles) }
        }
        return try {
            guestHole.runWithGuestHoleFallback {
                api.postBugReport(builder.build())
            }
        } finally {
            cleanUp()
        }
    }

    private fun getIspValue(): String? {
        val lastKnownIsp = serverListUpdater.lastKnownIsp
        val mobileNetwork = telephony?.networkOperatorName
        return when {
            lastKnownIsp != null && mobileNetwork != null -> "$lastKnownIsp, mobile network: $mobileNetwork"
            mobileNetwork != null -> "mobile network: $mobileNetwork"
            else -> lastKnownIsp
        }
    }
}
