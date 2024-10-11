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

package com.protonvpn.mocks

import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

private val LOCALHOST_CERTIFICATE = """
    -----BEGIN CERTIFICATE-----
    MIIBZzCCAQ6gAwIBAgIBATAKBggqhkjOPQQDAjAvMS0wKwYDVQQDDCRkNzdiMWJk
    Mi02MDc2LTQ4NjgtOTg5OC1kOTFiZmY0ODI2MzEwHhcNMjIxMTE1MTAxNTI0WhcN
    MzIxMTEyMTAxNTI0WjAvMS0wKwYDVQQDDCRkNzdiMWJkMi02MDc2LTQ4NjgtOTg5
    OC1kOTFiZmY0ODI2MzEwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATnPfqdAwLW
    JHG+nfA84cFDE82B5zpYpkL6Fmk+GfjCmCC6/zwuwB9yfkEo2m8oMx/SLDMAXgXd
    JFjHtXKWSczioxswGTAXBgNVHREBAf8EDTALgglsb2NhbGhvc3QwCgYIKoZIzj0E
    AwIDRwAwRAIgfJQTVMFSqm/15y+hRI5eiz7nnD2SUPyGf0nGEWM17YsCIG3qfMim
    tnKzZ3m3Eic9txz5M5ZXWrD6aeREf7X2cXnZ
    -----END CERTIFICATE-----
""".trimIndent() +
    "-----BEGIN PRIVATE KEY-----" + // gitleaks:allow nosemgrep
    """
    MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgnddyPTeBqCYw91EH
    wCLja6wSYNxMvC3OhcEdgB8Q9BmhRANCAATnPfqdAwLWJHG+nfA84cFDE82B5zpY
    pkL6Fmk+GfjCmCC6/zwuwB9yfkEo2m8oMx/SLDMAXgXdJFjHtXKWSczi
    -----END PRIVATE KEY-----
""".trimIndent()

object MockWebServerCertificates {
    fun getServerCertificates(): HandshakeCertificates {
        val certificate = HeldCertificate.decode(LOCALHOST_CERTIFICATE)
        return HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .addTrustedCertificate(certificate.certificate)
            .addPlatformTrustedCertificates()
            .build()
    }
}
