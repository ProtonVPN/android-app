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

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserCertStoreError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import me.proton.core.crypto.validator.domain.prefs.CryptoPrefs
import me.proton.core.featureflag.domain.entity.FeatureId
import me.proton.core.featureflag.domain.repository.FeatureFlagRepository
import me.proton.core.network.domain.session.SessionId
import me.proton.core.util.kotlin.DispatcherProvider
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.serialize
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CertificateStorage @Inject constructor(
    private @ApplicationContext val appContext: Context,
    mainScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val cryptoPrefs: CryptoPrefs,
    featureFlagRepository: FeatureFlagRepository,
    currentUser: CurrentUser,
) {
    // Use getCertPrefs() to access this.
    private val certPreferences = mainScope.async(dispatcherProvider.Io, start = CoroutineStart.LAZY) {
        if (cryptoPrefs.useInsecureKeystore == true) {
            createStorageWithoutEncryption()
        } else {
            val clearStorageOnError = featureFlagRepository.getValue(currentUser.user()?.userId, FEATURE_FLAG) ?: false
            if (clearStorageOnError) {
                createStorageWithClearOnError()
            } else {
                createStorageWithFallback()
            }
        }
    }

    suspend fun get(sessionId: SessionId): CertInfo? =
        getCertPrefs().getString(sessionId.id, null)?.deserialize()

    suspend fun put(sessionId: SessionId, info: CertInfo) {
        getCertPrefs().edit {
            putString(sessionId.id, info.serialize())
        }
    }

    suspend fun remove(sessionId: SessionId) {
        getCertPrefs().edit {
            remove(sessionId.id)
        }
    }

    private suspend fun getCertPrefs() = certPreferences.await()

    private fun createStorageWithClearOnError(): SharedPreferences {
        fun recreatePrefsOnError(e: Throwable): SharedPreferences {
            ProtonLogger.log(UserCertStoreError, e.toString())
            deleteEncryptedPrefs()
            return createEncryptedPrefs()
        }

        return try {
            createEncryptedPrefs()
        } catch (e: GeneralSecurityException) {
            recreatePrefsOnError(e)
        } catch (e: IOException) {
            recreatePrefsOnError(e)
        }
    }

    private fun createStorageWithFallback(): SharedPreferences {
        val encryptedPrefs = try {
            createEncryptedPrefs()
        } catch (e: GeneralSecurityException) {
            ProtonLogger.log(UserCertStoreError, e.toString())
            null
        } catch (e: IOException) {
            ProtonLogger.log(UserCertStoreError, e.toString())
            null
        }
        return encryptedPrefs ?: createStorageWithoutEncryption()
    }

    // Due to a number of issues with EncryptedSharedPreferences on some devices we fallback to unencrypted storage.
    private fun createStorageWithoutEncryption() =
        appContext.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)

    private fun createEncryptedPrefs() = EncryptedSharedPreferences.create(
        PREFS_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        appContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun deleteEncryptedPrefs() {
        if (Build.VERSION.SDK_INT >= 24) {
            appContext.deleteSharedPreferences(PREFS_NAME)
        } else {
            val dir = File(appContext.applicationInfo.dataDir, "shared_prefs")
            File(dir, "$PREFS_NAME.xml").delete()
        }
    }

    companion object {
        private const val PREFS_NAME = "cert_data"
        private const val FALLBACK_PREFS_NAME = "cert_data_fallback"
        private val FEATURE_FLAG = FeatureId("CertStorageRecreateOnErrorEnabled")
    }
}
