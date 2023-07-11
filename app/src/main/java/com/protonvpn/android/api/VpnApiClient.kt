/*
 * Copyright (c) 2020 Proton Technologies AG
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
package com.protonvpn.android.api

import android.os.Build
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.utils.BuildConfigUtils
import com.protonvpn.android.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import me.proton.core.network.domain.ApiClient
import java.util.Locale

class VpnApiClient(
    private val scope: CoroutineScope,
    private val dohEnabled: DohEnabled
) : ApiClient {

    val eventForceUpdate = MutableSharedFlow<String>(replay = 1)

    override val appVersionHeader get() =
        "${Constants.CLIENT_ID}@" + BuildConfig.VERSION_NAME + BuildConfig.STORE_SUFFIX
    override val enableDebugLogging = BuildConfig.DEBUG
    override val shouldUseDoh get() = dohEnabled()

    override val userAgent: String
        get() = String.format(Locale.US, "ProtonVPN/%s (Android %s; %s %s)",
                BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, Build.BRAND, Build.MODEL)

    override val connectTimeoutSeconds get() = 5L
    override val readTimeoutSeconds get() = 10L
    override val writeTimeoutSeconds get() = 10L
    override val callTimeoutSeconds get() = 30L

    override val pingTimeoutSeconds: Int get() = 5

    override val dohRecordType get() = ApiClient.DohRecordType.A
    override val useAltRoutingCertVerificationForMainRoute get() =
        BuildConfigUtils.useAltRoutingCertVerificationForMainRoute()

    override fun forceUpdate(errorMessage: String) {
        scope.launch {
            eventForceUpdate.emit(errorMessage)
        }
    }
}
