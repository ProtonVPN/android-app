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

package com.protonvpn.app.vpn

import com.protonvpn.android.vpn.CertInfo
import com.protonvpn.android.vpn.CertStorageCrypto
import com.protonvpn.android.vpn.CertificateStorage
import com.protonvpn.android.vpn.StoredCertInfo
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import kotlinx.coroutines.test.runTest
import me.proton.core.crypto.common.keystore.EncryptedString
import me.proton.core.network.domain.session.SessionId
import me.proton.core.util.kotlin.serialize
import org.junit.Test
import kotlin.test.assertEquals

private const val PRIVATE_KEY_PEM_OK = "__ok__"
private const val PRIVATE_KEY_PEM_FAIL_TO_DECRYPT = "__fail_to_decrypt__"
private const val PRIVATE_KEY_PEM_FAIL_TO_ENCRYPT = "__fail_to_encrypt__"

private val SESSION_1 = SessionId("session_1")

class CertificateStorageTests {

    @Test
    fun `GIVEN crypto works WHEN cert is stored THEN it can be read on next launch`() = runTest {
        val dataStoreFactory = InMemoryDataStoreFactory()
        val crypto = TestCertStorageCrypto()
        val certOk = createTestCert(PRIVATE_KEY_PEM_OK)
        val storage = CertificateStorage(this, crypto, dataStoreFactory)

        assertEquals(null, storage.get(SESSION_1))
        storage.put(SESSION_1, certOk)

        // Cert is readable and stored encrypted in current launch
        assertEquals(certOk, storage.get(SESSION_1))
        assertEquals(
            StoredCertInfo.Encrypted("ENCRYPTED_${certOk.serialize()}"),
            storage.getStoredCertInfo(SESSION_1)
        )

        // Cert is readable on next launch
        val storageOnNextLaunch = CertificateStorage(this, crypto, dataStoreFactory)
        assertEquals(certOk, storageOnNextLaunch.get(SESSION_1))
    }

    @Test
    fun `GIVEN decryption broken WHEN cert is stored THEN it can be read in current launch`() = runTest {
        val dataStoreFactory = InMemoryDataStoreFactory()
        val crypto = TestCertStorageCrypto()
        val storage = CertificateStorage(this, crypto, dataStoreFactory)

        val certDecryptFail = createTestCert(PRIVATE_KEY_PEM_FAIL_TO_DECRYPT)
        storage.put(SESSION_1, certDecryptFail)

        // Cert is readable and stored encrypted in current launch
        assertEquals(certDecryptFail, storage.get(SESSION_1))
        assertEquals(
            StoredCertInfo.Encrypted("ENCRYPTED_${certDecryptFail.serialize()}"),
            storage.getStoredCertInfo(SESSION_1)
        )

        val storageOnNextLaunch = CertificateStorage(this, crypto, dataStoreFactory)
        // Cert was not stored, so it fails to load on next launch
        assertEquals(null, storageOnNextLaunch.get(SESSION_1))
    }

    @Test
    fun `GIVEN encryption broken THEN cert is stored plaintext`() = runTest {
        val dataStoreFactory = InMemoryDataStoreFactory()
        val crypto = TestCertStorageCrypto()
        val storage = CertificateStorage(this, crypto, dataStoreFactory)

        val certEncryptFail = createTestCert(PRIVATE_KEY_PEM_FAIL_TO_ENCRYPT)
        storage.put(SESSION_1, certEncryptFail)

        // Cert is readable and stored unencrypted in current launch
        assertEquals(certEncryptFail, storage.get(SESSION_1))
        assertEquals(
            StoredCertInfo.Fallback(certEncryptFail.serialize()),
            storage.getStoredCertInfo(SESSION_1)
        )

        val storageOnNextLaunch = CertificateStorage(this, crypto, dataStoreFactory)
        // Cert was stored with fallback, so it can be read on next launch
        assertEquals(certEncryptFail, storageOnNextLaunch.get(SESSION_1))
    }

    @Test
    fun `GIVEN decryption broken and useInsecureKeystore is true THEN cert is stored with fallback`() = runTest {
        val dataStoreFactory = InMemoryDataStoreFactory()
        val crypto = TestCertStorageCrypto(useInsecureCrypto = true)
        val storage = CertificateStorage(this, crypto, dataStoreFactory)

        val certDecryptFail = createTestCert(PRIVATE_KEY_PEM_FAIL_TO_DECRYPT)
        storage.put(SESSION_1, certDecryptFail)

        // Cert is readable and stored with fallback in current launch
        assertEquals(certDecryptFail, storage.get(SESSION_1))
        assertEquals(
            StoredCertInfo.Fallback(certDecryptFail.serialize()),
            storage.getStoredCertInfo(SESSION_1)
        )

        val storageOnNextLaunch = CertificateStorage(this, crypto, dataStoreFactory)
        // Cert was stored with fallback, so it can be read on next launch
        assertEquals(certDecryptFail, storageOnNextLaunch.get(SESSION_1))
    }

    @Test
    fun `GIVEN deserialization issue THEN cert is not stored`() = runTest {
        val dataStoreFactory = InMemoryDataStoreFactory()
        val crypto = TestCertStorageCrypto()
        val storage = CertificateStorage(this, crypto, dataStoreFactory)

        val malformedCertJson = "INVALID_JSON"
        storage.putInStore(SESSION_1, malformedCertJson)

        // Cert should not be retrievable but not crashing
        val storageAfterLaunch = CertificateStorage(this, crypto, dataStoreFactory)
        assertEquals(
            StoredCertInfo.Fallback(malformedCertJson),
            storageAfterLaunch.getStoredCertInfo(SESSION_1)
        )
        assertEquals(null, storageAfterLaunch.get(SESSION_1))
    }
}

class TestCertStorageCrypto(val useInsecureCrypto: Boolean = false) : CertStorageCrypto {
    override val useInsecureKeystore: Boolean get() = useInsecureCrypto

    override suspend fun encryptOrElse(text: String, onError: (Throwable) -> String?): String? = when {
        text.contains(PRIVATE_KEY_PEM_OK) || text.contains(PRIVATE_KEY_PEM_FAIL_TO_DECRYPT) -> "ENCRYPTED_$text"
        else -> null
    }

    override suspend fun decryptOrElse(ciphertext: EncryptedString, onError: (Throwable) -> String?): String? = when {
        ciphertext.contains(PRIVATE_KEY_PEM_OK) -> ciphertext.removePrefix("ENCRYPTED_")
        else -> null
    }
}

private fun createTestCert(
    privateKeyPem: String = PRIVATE_KEY_PEM_OK,
) = CertInfo(
    privateKeyPem = privateKeyPem,
    publicKeyPem = "PUBLIC_KEY_PEM",
    x25519Base64 = "X25519BASE64",
)