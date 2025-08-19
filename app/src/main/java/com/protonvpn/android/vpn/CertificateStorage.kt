/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.vpn

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserCertStoreError
import com.protonvpn.android.userstorage.JsonDataStoreSerializer
import com.protonvpn.android.userstorage.LocalDataStoreFactory
import dagger.Reusable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import me.proton.core.crypto.common.keystore.EncryptedString
import me.proton.core.crypto.common.keystore.KeyStoreCrypto
import me.proton.core.crypto.common.keystore.decryptOrElse
import me.proton.core.crypto.common.keystore.encryptOrElse
import me.proton.core.crypto.validator.domain.prefs.CryptoPrefs
import me.proton.core.network.domain.session.SessionId
import me.proton.core.user.domain.entity.Role
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.serialize
import javax.inject.Inject
import javax.inject.Singleton

@VisibleForTesting
@Serializable
sealed class StoredCertInfo {
    @Serializable
    data class Encrypted(val encryptedCertInfoJson: String) : StoredCertInfo()

    // Due to unreliability of the Android Keystore on some devices we'll allow fallback to storing
    // unencrypted data.
    @Serializable
    data class Fallback(val certInfoJson: String) : StoredCertInfo()
}

interface CertStorageCrypto {
    val useInsecureKeystore: Boolean
    suspend fun encryptOrElse(text: String, onError: (Throwable) -> String?): String?
    suspend fun decryptOrElse(ciphertext: EncryptedString, onError: (Throwable) -> String?): String?
}

@Reusable
class CertStorageCryptoImpl @Inject constructor(
    private val cryptoPrefs: CryptoPrefs,
    private val keyStoreCrypto: KeyStoreCrypto,
    private val dispatchers: VpnDispatcherProvider,
) : CertStorageCrypto {
    override val useInsecureKeystore: Boolean
        get() = cryptoPrefs.useInsecureKeystore ?: false

    override suspend fun encryptOrElse(text: String, onError: (Throwable) -> String?): EncryptedString? =
        withContext(dispatchers.Comp) {
            text.encryptOrElse(keyStoreCrypto, onError)
        }

    override suspend fun decryptOrElse(ciphertext: EncryptedString, onError: (Throwable) -> String?): String? =
        withContext(dispatchers.Comp) {
            ciphertext.decryptOrElse(keyStoreCrypto, onError)
        }
}

fun interface CanUseInsecureCertStorage {
    suspend operator fun invoke(): Boolean
}

@Reusable
class CanUseInsecureCertStorageImpl @Inject constructor(
    private val currentUser: CurrentUser
) : CanUseInsecureCertStorage {
    override suspend fun invoke(): Boolean = currentUser.user().let { user ->
        user == null || user.role == Role.NoOrganization
    }
}

// SessionId cannot be used as key in serialized Data. Introduce alias to be used with SessionId.id
private typealias SessionIdRaw = String

@Singleton
class CertificateStorage @Inject constructor(
    mainScope: CoroutineScope,
    private val crypto: CertStorageCrypto,
    private val canUseInsecureCertStorage: CanUseInsecureCertStorage,
    private val localDataStoreFactory: LocalDataStoreFactory,
) {
    @Serializable
    data class Data(
        val sessionCerts: Map<SessionIdRaw, StoredCertInfo>,
    )

    private val inMemoryCache = mutableMapOf<String, CertInfo>()
    private val dataStore = mainScope.async(start = CoroutineStart.LAZY) {
        localDataStoreFactory.getDataStore(
            FILE_NAME,
            JsonDataStoreSerializer(Data(emptyMap()), Data.serializer()),
            emptyList()
        )
    }

    private suspend fun getStore() = dataStore.await()

    suspend fun get(sessionId: SessionId): CertInfo? =
        inMemoryCache[sessionId.id] ?: getFromStore(sessionId)?.also {
            inMemoryCache[sessionId.id] = it
        }

    suspend fun put(sessionId: SessionId, info: CertInfo) {
        inMemoryCache[sessionId.id] = info
        putInStore(sessionId, info)
    }

    suspend fun remove(sessionId: SessionId) {
        inMemoryCache.remove(sessionId.id)
        getStore().updateData { data ->
            data.copy(sessionCerts = data.sessionCerts - sessionId.id)
        }
    }

    private suspend fun getFromStore(sessionId: SessionId): CertInfo? {
        val storedInfo = getStoredCertInfo(sessionId)
        return try {
            when (storedInfo) {
                is StoredCertInfo.Encrypted ->
                    crypto.decryptOrElse(storedInfo.encryptedCertInfoJson) { e ->
                        ProtonLogger.log(UserCertStoreError, "Failed to decrypt certificate info: $e")
                        null
                    }?.deserialize()

                is StoredCertInfo.Fallback ->
                    storedInfo.certInfoJson.deserialize()

                null -> null
            }
            // Deserialization can throw IllegalArgumentException and IllegalStateException (e.g. on EOF).
        } catch (e: IllegalArgumentException) {
            ProtonLogger.log(UserCertStoreError, "Failed to deserialize certificate info: $e")
            null
        } catch (e: IllegalStateException) {
            ProtonLogger.log(UserCertStoreError, "Failed to deserialize certificate info: $e")
            null
        }
    }

    @VisibleForTesting
    suspend fun getStoredCertInfo(sessionId: SessionId): StoredCertInfo? {
        val data = getStore().data.first()
        return data.sessionCerts[sessionId.id]
    }

    private suspend fun putInStore(sessionId: SessionId, info: CertInfo) {
        val certInfoJson = try {
            info.serialize()
        } catch (e: SerializationException) {
            ProtonLogger.log(UserCertStoreError, "Failed to serialize certificate info: $e")
            return
        }
        putInStore(sessionId, certInfoJson)
    }

    @VisibleForTesting
    suspend fun putInStore(sessionId: SessionId, certInfoJson: String) {
        val storedInfo = if (crypto.useInsecureKeystore && canUseInsecureCertStorage()) {
            StoredCertInfo.Fallback(certInfoJson)
        } else {
            val encryptedJson = crypto.encryptOrElse(certInfoJson) { e ->
                ProtonLogger.log(UserCertStoreError, "Failed to encrypt certificate info: $e")
                null
            }
            if (encryptedJson != null) {
                StoredCertInfo.Encrypted(encryptedJson)
            } else if (canUseInsecureCertStorage()) {
                // Fallback to storing unencrypted data if encryption fails
                ProtonLogger.log(UserCertStoreError, "Using fallback for certificate info storage")
                StoredCertInfo.Fallback(certInfoJson)
            } else {
                ProtonLogger.log(UserCertStoreError, "Failed to store certificate info securely")
                return
            }
        }

        getStore().updateData { data ->
            data.copy(sessionCerts = data.sessionCerts + (sessionId.id to storedInfo))
        }
    }

    companion object {
        private const val FILE_NAME = "cert_data_store"
    }
}
