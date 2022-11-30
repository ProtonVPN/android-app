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

package com.protonvpn.data

import com.protonvpn.android.models.vpn.Partner
import com.protonvpn.android.models.vpn.PartnerType
import com.protonvpn.android.models.vpn.SERVER_FEATURE_PARTNER_SERVER
import com.protonvpn.test.shared.createServer

object PartnersData {
    val newsPartner = Partner(
        "ProtonNews",
        "This is ProtonNews",
        "data:image/png;base64,${DefaultData.PNG_BASE64}",
        listOf("partner1", "partner2")
    )

    val newsPartner2 = Partner(
        "Partner 2",
        "This is Partner 2",
        "data:image/png;base64,${DefaultData.PNG_BASE64}",
        listOf("partner2")
    )

    val newsPartnerType = PartnerType(
        "News servers",
        "You can get news here",
        "data:image/png;base64,${DefaultData.PNG_BASE64}",
        listOf(newsPartner)
    )

    val newsPartnerType2 = PartnerType(
        "Partner 2 servers",
        "You can get partner 2 services here",
        "data:image/png;base64,${DefaultData.PNG_BASE64}",
        listOf(newsPartner2)
    )

    val partnerServerNews = createServer(
        "partner1",
        features = SERVER_FEATURE_PARTNER_SERVER,
        serverName = "PL-FREE#NEWS",
        score = 1f
    )

    val partnerServerNews2 = createServer(
        "partner2",
        features = SERVER_FEATURE_PARTNER_SERVER,
        serverName = "PL-FREE#PARTNER2",
        score = 2f
    )

    val multiplePartnerServers = listOf(partnerServerNews, partnerServerNews2)
}