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

package com.protonvpn.android.vpn

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.proton.gopenpgp.ed25519.KeyPair
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.periodicupdates.IsLoggedIn
import com.protonvpn.android.appconfig.periodicupdates.PeriodicActionResult
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.registerAction
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserCertCurrentState
import com.protonvpn.android.logging.UserCertNew
import com.protonvpn.android.logging.UserCertRefresh
import com.protonvpn.android.logging.UserCertRefreshError
import com.protonvpn.android.logging.UserCertScheduleRefresh
import com.protonvpn.android.logging.UserCertStoreError
import com.protonvpn.android.models.vpn.CertificateResponse
import com.protonvpn.android.utils.UserPlanManager
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.proton.core.crypto.validator.domain.prefs.CryptoPrefs
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.retryAfter
import me.proton.core.network.domain.session.SessionId
import me.proton.core.util.kotlin.DispatcherProvider
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.serialize
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_REFRESH_COUNT = 4
val MIN_CERT_REFRESH_DELAY = TimeUnit.SECONDS.toMillis(30)
private val FALLBACK_REFRESH_DELAY_MS = TimeUnit.HOURS.toMillis(12)

@Serializable
data class CertInfo(
    val privateKeyPem: String,
    val publicKeyPem: String,
    val x25519Base64: String,
    val expiresAt: Long = 0,
    val refreshAt: Long = 0,
    val certificatePem: String? = null,
    val refreshCount: Int = 0,
)

@Singleton
class CertificateStorage @Inject constructor(
    val mainScope: CoroutineScope,
    val dispatcherProvider: DispatcherProvider,
    val cryptoPrefs: CryptoPrefs,
    @ApplicationContext val appContext: Context
) {
    // Use getCertPrefs() to access this.
    private val certPreferences = mainScope.async(dispatcherProvider.Io, start = CoroutineStart.LAZY) {
        @Suppress("BlockingMethodInNonBlockingContext")
        val encryptedPrefs = if (cryptoPrefs.useInsecureKeystore == true)
            null
        else {
            try {
                EncryptedSharedPreferences.create(
                    "cert_data",
                    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                    appContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: GeneralSecurityException) {
                ProtonLogger.log(UserCertStoreError, e.message ?: e.javaClass.simpleName)
                null
            } catch (e: IOException) {
                ProtonLogger.log(UserCertStoreError, e.message ?: e.javaClass.simpleName)
                null
            }
        }
        encryptedPrefs ?: getFallbackPrefs()
    }

    // Due to a number of issues with EncryptedSharedPreferences on some devices we fallback to unencrypted storage.
    private fun getFallbackPrefs() = appContext.getSharedPreferences("cert_data_fallback", Context.MODE_PRIVATE)

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
}

// Allows running unit tests without the go library, see VPNAND-797.
@Reusable
class CertificateKeyProvider @Inject constructor() {
    fun generateCertInfo() = with(KeyPair()) {
        CertInfo(privateKeyPKIXPem(), publicKeyPKIXPem(), toX25519Base64())
    }

    fun generateX25519Base64(): String = KeyPair().toX25519Base64()
}

@Singleton
@OptIn(kotlin.time.ExperimentalTime::class)
class CertificateRepository @Inject constructor(
    private val mainScope: CoroutineScope,
    private val certificateStorage: CertificateStorage,
    private val keyProvider: CertificateKeyProvider,
    private val api: ProtonApiRetroFit,
    @WallClock private val wallClock: () -> Long,
    userPlanManager: UserPlanManager,
    private val currentUser: CurrentUser,
    private val periodicUpdateManager: PeriodicUpdateManager,
    @IsLoggedIn loggedIn: Flow<Boolean>
) {
    sealed class CertificateResult {
        data class Error(val error: ApiResult.Error?) : CertificateResult()
        data class Success(val certificate: String, val privateKeyPem: String) : CertificateResult()
    }

    private val certRequests = mutableMapOf<SessionId, Deferred<PeriodicActionResult<out CertificateResult>>>()

    private val guestX25519Key by lazy { keyProvider.generateX25519Base64() }

    private val _currentCertUpdateFlow = MutableSharedFlow<CertificateResult.Success>()
    val currentCertUpdateFlow: Flow<CertificateResult.Success> get() = _currentCertUpdateFlow

    private val certificateUpdate = periodicUpdateManager.registerAction(
        "vpn_certificate",
        ::updateCertificateInternal,
        { currentUser.sessionId() },
        // The update task overrides next update delay. FALLBACK_REFRESH_DELAY_MS is only used if certificate
        // refresh fails MAX_REFRESH_COUNT.
        PeriodicUpdateSpec(FALLBACK_REFRESH_DELAY_MS, setOf(loggedIn))
    )

    init {
        mainScope.launch {
            currentUser.sessionId()?.let {
                val certInfo = getCertInfo(it)
                val certString =
                    if (certInfo.certificatePem == null) {
                        "none"
                    } else {
                        val expires = ProtonLogger.formatTime(certInfo.expiresAt)
                        val refreshes = ProtonLogger.formatTime(certInfo.refreshAt)
                        "expires $expires (refresh at $refreshes)"
                    }
                ProtonLogger.log(UserCertCurrentState, "Current cert: $certString")
            }
        }
        userPlanManager.infoChangeFlow.onEach { changes ->
            for (change in changes) when (change) {
                is UserPlanManager.InfoChange.PlanChange.Downgrade,
                is UserPlanManager.InfoChange.PlanChange.Upgrade,
                is UserPlanManager.InfoChange.UserBecameDelinquent -> {
                    ProtonLogger.log(UserCertRefresh, "reason: user plan change: $change")
                    currentUser.sessionId()?.let { sessionId ->
                        clearCert(sessionId)
                        updateCertificate(sessionId, cancelOngoing = true)
                    }
                }
                else -> {}
            }
        }.launchIn(mainScope)
    }

    suspend fun generateNewKey(sessionId: SessionId): CertInfo = withContext(mainScope.coroutineContext) {
        val info = keyProvider.generateCertInfo()

        certRequests.remove(sessionId)?.cancel()
        certificateStorage.put(sessionId, info)
        info
    }

    suspend fun updateCertificate(sessionId: SessionId, cancelOngoing: Boolean): CertificateResult =
        withContext(mainScope.coroutineContext) {
            if (cancelOngoing)
                certRequests.remove(sessionId)?.cancel()
            periodicUpdateManager.executeNow(certificateUpdate, sessionId)
        }

    @VisibleForTesting
    suspend fun updateCertificateInternal(sessionId: SessionId?): PeriodicActionResult<out CertificateResult> {
        val cancelResult = PeriodicActionResult(CertificateResult.Error(null), false)
        if (sessionId == null) {
            // This function should not be scheduled when no user is logged in but in theory it's possible that it is
            // called just as the user logs out.
            return cancelResult
        }
        return withContext(mainScope.coroutineContext) {
            val request = certRequests[sessionId]
                ?: async {
                    updateCertificateFromBackend(sessionId).also {
                        certRequests.remove(sessionId)
                    }
                }.also { deferredResult ->
                    certRequests[sessionId] = deferredResult
                }

            @Suppress("SwallowedException")
            try {
                request.await()
            } catch (e: CancellationException) {
                cancelResult
            }
        }
    }

    private suspend fun updateCertificateFromBackend(
        sessionId: SessionId
    ): PeriodicActionResult<out CertificateResult> {
        val info = getCertInfo(sessionId)
        ProtonLogger.log(UserCertRefresh, "retry count: ${info.refreshCount}")
        return when (val response = api.getCertificate(sessionId, info.publicKeyPem)) {
            is ApiResult.Success -> {
                val cert = response.value
                val newInfo = info.copy(
                    expiresAt = cert.expirationTimeMs,
                    refreshAt = cert.refreshTimeMs,
                    certificatePem = cert.certificate,
                    refreshCount = 0
                )
                certificateStorage.put(sessionId, newInfo)
                ProtonLogger.log(
                    UserCertNew,
                    "expires at ${ProtonLogger.formatTime(cert.expirationTimeMs)}"
                )
                val result = CertificateResult.Success(cert.certificate, info.privateKeyPem)
                if (sessionId == currentUser.sessionId())
                    _currentCertUpdateFlow.emit(result)
                PeriodicActionResult(result, true, nextRefreshDelay(response, newInfo))
            }
            is ApiResult.Error -> {
                val certString = if (info.certificatePem == null)
                    "current certificate: none"
                else
                    "current certificate expiring at ${ProtonLogger.formatTime(info.expiresAt)}"
                ProtonLogger.log(
                    UserCertRefreshError,
                    "$certString, retry count: ${info.refreshCount}, error: $response"
                )
                certificateStorage.put(sessionId, info.copy(refreshCount = info.refreshCount + 1))
                PeriodicActionResult(CertificateResult.Error(response), false, nextRefreshDelay(response, info))
            }
        }
    }

    private fun nextRefreshDelay(apiResult: ApiResult<CertificateResponse>, certInfo: CertInfo): Long? {
        val now = wallClock()
        val timestampMs = when (apiResult) {
            is ApiResult.Success -> certInfo.refreshAt
            is ApiResult.Error -> {
                val retryAfter = apiResult.retryAfter()
                if (retryAfter != null) {
                    now + retryAfter.inWholeMilliseconds
                } else {
                    ((now + certInfo.expiresAt) / 2).coerceAtLeast(now + MIN_CERT_REFRESH_DELAY)
                        .takeIf { certInfo.refreshCount < MAX_REFRESH_COUNT }
                }
            }
        }
        val refreshTimeString = if (timestampMs != null) ProtonLogger.formatTime(timestampMs) else "default interval"
        ProtonLogger.log(UserCertScheduleRefresh, "at: $refreshTimeString")
        return timestampMs?.let { timestampMs - now }
    }

    private suspend fun getCertInfo(sessionId: SessionId) =
        certificateStorage.get(sessionId) ?: run {
            generateNewKey(sessionId)
        }

    suspend fun getX25519Key(sessionId: SessionId?): String =
        sessionId?.let { getCertInfo(it).x25519Base64 } ?: guestX25519Key

    suspend fun getCertificate(sessionId: SessionId, cancelOngoing: Boolean = false): CertificateResult =
        withContext(mainScope.coroutineContext) {
            val certInfo = getCertInfo(sessionId)
            if (certInfo.certificatePem != null && certInfo.expiresAt > wallClock())
                CertificateResult.Success(certInfo.certificatePem, certInfo.privateKeyPem)
            else
                updateCertificate(sessionId, cancelOngoing = cancelOngoing)
        }

    /**
     * Returns the locally stored certificate.
     * Does not try to fetch it if there isn't one nor refresh it if its expired.
     * In most cases getCertificate should be used.
     */
    suspend fun getCertificateWithoutRefresh(sessionId: SessionId): CertificateResult =
        withContext(mainScope.coroutineContext) {
            val certInfo = getCertInfo(sessionId)
            if (certInfo.certificatePem != null)
                CertificateResult.Success(certInfo.certificatePem, certInfo.privateKeyPem)
            else
                CertificateResult.Error(null)
        }

    suspend fun clear(sessionId: SessionId) = withContext(mainScope.coroutineContext) {
        certRequests.remove(sessionId)?.cancel()
        certificateStorage.remove(sessionId)
    }

    // Invalidates cert for given session, keeping the keys
    private suspend fun clearCert(sessionId: SessionId) = withContext(mainScope.coroutineContext) {
        certRequests.remove(sessionId)?.cancel()
        certificateStorage.get(sessionId)?.let {
            certificateStorage.put(sessionId, CertInfo(
                privateKeyPem = it.privateKeyPem,
                publicKeyPem = it.publicKeyPem,
                x25519Base64 = it.x25519Base64))
        }
    }
}
