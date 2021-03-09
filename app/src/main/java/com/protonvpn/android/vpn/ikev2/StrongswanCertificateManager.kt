/*
 * Copyright (c) 2021 Proton Technologies AG
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

package com.protonvpn.android.vpn.ikev2

import android.content.Context
import org.strongswan.android.logic.TrustedCertificateManager
import java.io.IOException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object StrongswanCertificateManager {

    fun init(context: Context) {
        storeCertificate(parseCertificate(context))
    }

    /**
     * Load the file from the given URI and try to parse it as X.509 certificate.
     *
     * @return certificate or null
     */
    fun parseCertificate(context: Context): X509Certificate? =
        try {
            val factory: CertificateFactory = CertificateFactory.getInstance("X.509")
            val input = context.assets.open("pro-root.der")
            factory.generateCertificate(input) as X509Certificate
            /* we don't check whether it's actually a CA certificate or not */
        } catch (e: CertificateException) {
            e.printStackTrace()
            null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }

    /**
     * Try to store the given certificate in the KeyStore.
     *
     * @param certificate
     * @return whether it was successfully stored
     */
    fun storeCertificate(certificate: X509Certificate?) =
        try {
            val store = KeyStore.getInstance("LocalCertificateStore")
            store.load(null, null)
            store.setCertificateEntry(null, certificate)
            TrustedCertificateManager.getInstance().reset()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
}
