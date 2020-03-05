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

import android.net.Uri
import com.datatheorem.android.trustkit.TrustKit
import okhttp3.OkHttpClient

class ProtonPrimaryApiBackend(baseUrl: String) : ProtonApiBackend(baseUrl) {

    init {
        initialize()
    }

    override fun setupOkBuilder(builder: OkHttpClient.Builder) {
        val hostname = Uri.parse(baseUrl).host!!
        builder.sslSocketFactory(
            TrustKit.getInstance().getSSLSocketFactory(hostname),
            TrustKit.getInstance().getTrustManager(hostname))
    }
}
