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

import com.datatheorem.android.trustkit.config.PublicKeyPin
import okhttp3.OkHttpClient
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class ProtonAlternativeApiBackend(
    baseUrl: String,
    private val pinnedKeyHashes: List<String>
) : ProtonApiBackend(baseUrl) {

    class PinningTrustManager(pinnedKeyHashes: List<String>) : X509TrustManager {

        private val pins: List<PublicKeyPin> = pinnedKeyHashes.map { PublicKeyPin(it) }

        @Throws(CertificateException::class)
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (PublicKeyPin(chain.first()) !in pins)
                throw CertificateException("Pin verification failed")
        }

        @Throws(CertificateException::class)
        override fun checkClientTrusted(chain: Array<X509Certificate?>?, authType: String?) {
            throw CertificateException("Client certificates not supported!")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate?>? = arrayOfNulls(0)
    }

    init {
        initialize()
    }

    override fun setupOkBuilder(builder: OkHttpClient.Builder) {
        val trustManager = PinningTrustManager(pinnedKeyHashes)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        builder.hostnameVerifier(HostnameVerifier { _, _ ->
            // Verification is based solely on SPKI pinning of leaf certificate
            true
        })
    }
}
