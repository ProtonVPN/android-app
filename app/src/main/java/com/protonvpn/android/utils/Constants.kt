/*
 * Copyright (c) 2019 Proton Technologies AG
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

object Constants {

    const val MAX_LOG_SIZE = 200 * 1024.toLong()
    const val NOTIFICATION_ID = 6
    const val SIGNUP_URL = "https://account.protonvpn.com/signup?from=mobile"
    const val PRIMARY_VPN_API_URL = "https://" + BuildConfig.API_DOMAIN + "/"
    const val ALTERNATIVE_ROUTING_LEARN_URL =
            "https://protonmail.com/blog/anti-censorship-alternative-routing"
    const val DASHBOARD_URL = "https://account.protonvpn.com/dashboard"
    val AVAILABLE_LOCALES = listOf("en", "es", "pl", "pt", "it", "fr", "nl", "de", "ru", "fa")

    const val TLS_AUTH_KEY_HEX =
            "6acef03f62675b4b1bbd03e53b187727\n" +
            "423cea742242106cb2916a8a4c829756\n" +
            "3d22c7e5cef430b1103c6f66eb1fc5b3\n" +
            "75a672f158e2e2e936c3faa48b035a6d\n" +
            "e17beaac23b5f03b10b868d53d03521d\n" +
            "8ba115059da777a60cbfd7b2c9c57472\n" +
            "78a15b8f6e68a3ef7fd583ec9f398c8b\n" +
            "d4735dab40cbd1e3c62a822e97489186\n" +
            "c30a0b48c7c38ea32ceb056d3fa5a710\n" +
            "e10ccc7a0ddb363b08c3d2777a3395e1\n" +
            "0c0b6080f56309192ab5aacd4b45f55d\n" +
            "a61fc77af39bd81a19218a79762c3386\n" +
            "2df55785075f37d8c71dc8a42097ee43\n" +
            "344739a0dd48d03025b0450cf1fb5e8c\n" +
            "aeb893d9a96d1f15519bb3c4dcb40ee3\n" +
            "16672ea16c012664f8a9f11255518deb\n"
}
