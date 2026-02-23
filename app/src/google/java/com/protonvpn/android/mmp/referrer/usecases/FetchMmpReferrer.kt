/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.mmp.referrer.usecases

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.mmp.referrer.MmpReferrer
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Reusable
class FetchMmpReferrer @Inject constructor(@param:ApplicationContext private val context: Context) {

    @OptIn(ExperimentalUuidApi::class)
    suspend fun getMmpReferrer(): MmpReferrer? = suspendCancellableCoroutine { continuation ->
        InstallReferrerClient.newBuilder(context)
            .build()
            .apply {
                startConnection(object : InstallReferrerStateListener {

                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        when (responseCode) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> {
                                installReferrer
                                    .also { endConnection() }
                                    .let { referrerDetails ->
                                        MmpReferrer(
                                            asid = Uuid.random().toString(),
                                            referrerLink = referrerDetails.installReferrer,
                                        )
                                    }
                                    .run(continuation::resume)
                            }

                            InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                                logFetchingInstallReferrerIssue(reason = "Feature not supported")

                                continuation.resume(value = null)
                            }

                            InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                                logFetchingInstallReferrerIssue(reason = "Service unavailable")

                                continuation.resume(value = null)
                            }
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        logFetchingInstallReferrerIssue(reason = "Service disconnected")

                        continuation.resume(value = null)
                    }
                })
            }
    }

    private fun logFetchingInstallReferrerIssue(reason: String) {
        ProtonLogger.logCustom(
            category = LogCategory.MMP,
            message = "Cannot fetch install referrer: $reason",
        )
    }

}
