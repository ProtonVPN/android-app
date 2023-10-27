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

package com.protonvpn.android.utils

import com.protonvpn.android.BuildConfig

object BuildConfigUtils {
    fun isDev() = BuildConfig.FLAVOR_distribution.startsWith("dev")
    fun isBlack() = BuildConfig.FLAVOR_environment.startsWith("black")
    fun useAltRoutingCertVerificationForMainRoute() = BuildConfig.ALT_ROUTING_CERT_FOR_MAIN_ROUTE
    fun displayInfo() = BuildConfig.DEBUG || isBlack()
    fun isCertificatePinningFlavor() = !(isDev() || isBlack())

    // Make sure that DoH services URLs end with a slash (expected by OkHttp)
    fun sanitizedDohServices() = BuildConfig.DOH_SERVICES_URLS?.map {
        if (it.endsWith("/")) it else "$it/"
    }
}
