/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.auth.usecase

import android.net.Uri
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import dagger.Reusable
import kotlinx.coroutines.withTimeoutOrNull
import me.proton.core.network.domain.ApiException
import javax.inject.Inject

private const val FALLBACK_URL = "https://account.protonvpn.com/dashboard"
private const val WEB_CHILD_ID = "web-account-lite"
private const val DEFAULT_TIMEOUT_MS = 5_000L

@Reusable
class AutoLoginUrlForWeb @Inject constructor(
    private val api: ProtonApiRetroFit
) {
    suspend operator fun invoke(
        url: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        fallbackUrl: String = FALLBACK_URL
    ): String =
        withTimeoutOrNull(timeoutMs) {
            try {
                val sessionForkResult = api.postSessionFork(WEB_CHILD_ID, "", false)
                val selector = sessionForkResult.valueOrThrow.selector
                addSelectorToUrl(url, selector)
            } catch (apiException: ApiException) {
                ProtonLogger.logCustom(LogLevel.WARN, LogCategory.USER, "Failed to obtain fork selector: $apiException")
                fallbackUrl
            }
        } ?: fallbackUrl

    private fun addSelectorToUrl(url: String, selector: String): String =
        Uri.parse(url).buildUpon().encodedFragment("selector=$selector").toString()
}
