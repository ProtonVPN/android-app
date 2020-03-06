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

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.models.config.UserData

class AlternativeApiManagerProd(
    userData: UserData,
    now: () -> Long
) : AlternativeApiManager(BuildConfig.API_DOMAIN, userData, now) {

    private val alternativeApiPins = listOf(
        "EU6TS9MO0L/GsDHvVc9D5fChYLNy5JdGYpJw0ccgetM=",
        "iKPIHPnDNqdkvOnTClQ8zQAIKG0XavaPkcEo0LBAABA=",
        "MSlVrBCdL0hKyczvgYVSRNm88RicyY04Q2y5qrBt0xA=",
        "C2UxW0T1Ckl9s+8cXfjXxlEqwAfPM4HiW2y3UdtBeCw=")

    private val dnsOverHttpsProviders =
        arrayOf(DnsOverHttpsProviderRFC8484("https://dns11.quad9.net/dns-query/"),
                DnsOverHttpsProviderRFC8484("https://dns.google/dns-query/"))

    override fun createAltBackend(baseUrl: String) =
        ProtonAlternativeApiBackend(baseUrl, alternativeApiPins)

    override fun getDnsOverHttpsProviders() =
        dnsOverHttpsProviders
}
