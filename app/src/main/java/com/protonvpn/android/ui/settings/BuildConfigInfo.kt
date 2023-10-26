/*
 * Copyright (c) 2023 Proton AG
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

package com.protonvpn.android.ui.settings

import com.protonvpn.android.BuildConfig
import me.proton.core.humanverification.presentation.HumanVerificationApiHost
import me.proton.core.network.data.di.AlternativeApiPins
import me.proton.core.network.data.di.BaseProtonApiUrl
import me.proton.core.network.data.di.CertificatePins
import me.proton.core.network.data.di.DohProviderUrls
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildConfigInfo @Inject constructor(
    @BaseProtonApiUrl val apiUrl: HttpUrl,
    @DohProviderUrls val dohProviders: Array<String>,
    @CertificatePins val certificatePins: Array<String>,
    @AlternativeApiPins val alternativeApiPins: List<String>,
    @HumanVerificationApiHost val humanVerificationApiHost: String,
) {
    operator fun invoke() = """
            API: $apiUrl
            HV: $humanVerificationApiHost
            DOH: ${dohProviders.contentToString()}
            PINS: ${certificatePins.contentToString().takeWithEllipsis(10)}
            ALT PINS: ${alternativeApiPins.toString().takeWithEllipsis(10)}
            ROOT CERT: ${if (BuildConfig.VPN_SERVER_ROOT_CERT != null) "[override]" else "[default]"}
            BUILD FLAVOR: ${BuildConfig.FLAVOR}
        """.trimIndent()

    private fun String.takeWithEllipsis(n: Int) = if (length > n)
            take(n) + Typography.ellipsis
        else
            this
}
