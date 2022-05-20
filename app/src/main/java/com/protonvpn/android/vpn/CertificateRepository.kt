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
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.proton.gopenpgp.ed25519.KeyPair
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.components.AppInUseMonitor
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UserCertCurrentState
import com.protonvpn.android.logging.UserCertNew
import com.protonvpn.android.logging.UserCertRefresh
import com.protonvpn.android.logging.UserCertRefreshError
import com.protonvpn.android.logging.UserCertScheduleRefresh
import com.protonvpn.android.logging.UserCertStoreError
import com.protonvpn.android.utils.UserPlanManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.proton.core.crypto.validator.domain.prefs.CryptoPrefs
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.NetworkManager
import me.proton.core.network.domain.NetworkStatus
import me.proton.core.network.domain.session.SessionId
import me.proton.core.util.kotlin.DispatcherProvider
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.serialize
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_REFRESH_COUNT = 2
private val MIN_REFRESH_DELAY = TimeUnit.SECONDS.toMillis(30)

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
class CertificateKeyProvider @Inject constructor() {
    fun generateCertInfo() = with(KeyPair()) {
        CertInfo(privateKeyPKIXPem(), publicKeyPKIXPem(), toX25519Base64())
    }

    fun generateX25519Base64(): String = KeyPair().toX25519Base64()
}

@Singleton
class CertificateRepository @Inject constructor(
    private val mainScope: CoroutineScope,
    private val certificateStorage: CertificateStorage,
    private val keyProvider: CertificateKeyProvider,
    private val api: ProtonApiRetroFit,
    @WallClock private val wallClock: () -> Long,
    private val userPlanManager: UserPlanManager,
    private val currentUser: CurrentUser,
    private val certRefreshScheduler: CertRefreshScheduler,
    private val appInUseMonitor: AppInUseMonitor,
    networkManager: NetworkManager
) {
    sealed class CertificateResult {
        data class Error(val error: ApiResult.Error?) : CertificateResult()
        data class Success(val certificate: String, val privateKeyPem: String) : CertificateResult()
    }

    private val certRequests = mutableMapOf<SessionId, Deferred<CertificateResult>>()

    private val guestX25519Key by lazy { keyProvider.generateX25519Base64() }

    val currentCertUpdateFlow = MutableSharedFlow<CertificateResult.Success>()

    init {
        networkManager.observe().onEach { status ->
            if (status != NetworkStatus.Disconnected)
                updateCertificateIfNeeded()
        }.launchIn(mainScope)
        mainScope.launch {
            appInUseMonitor.isInUseFlow.collect { isInUse ->
                if (isInUse) onAppInUse()
            }
        }
        mainScope.launch {
            currentUser.sessionId()?.let {
                val certInfo = getCertInfo(it)
                val certString =
                    if (certInfo.certificatePem == null) {
                        "none"
                    } else {
                        val expires = ProtonLogger.formatTime(certInfo.expiresAt)
                        val refreshes = ProtonLogger.formatTime(certInfo.refreshAt)
                        "expires ${expires} (refresh at ${refreshes})"
                    }
                ProtonLogger.log(UserCertCurrentState, "Current cert: $certString")
            }
        }
        mainScope.launch {
            userPlanManager.infoChangeFlow.collect { changes ->
                for (change in changes) when (change) {
                    is UserPlanManager.InfoChange.PlanChange.Downgrade,
                    is UserPlanManager.InfoChange.PlanChange.Upgrade,
                    is UserPlanManager.InfoChange.UserBecameDelinquent -> {
                        ProtonLogger.log(UserCertRefresh, "reason: user plan change: $change")
                        currentUser.sessionId()?.let {
                            updateCertificate(it, true)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun onAppInUse() {
        currentUser.sessionId()?.let { sessionId ->
            val certInfo = getCertInfo(sessionId)
            if (needsUpdate(certInfo)) {
                // Update schedules next refresh.
                updateCertificate(sessionId, false)
            } else {
                rescheduleRefreshTo(certInfo.refreshAt.coerceAtLeast(wallClock()))
            }
        }
    }

    private fun rescheduleRefreshTo(time: Long) {
        ProtonLogger.log(UserCertScheduleRefresh, "at: ${ProtonLogger.formatTime(time)}")
        certRefreshScheduler.rescheduleAt(time)
    }

    suspend fun generateNewKey(sessionId: SessionId): CertInfo = withContext(mainScope.coroutineContext) {
        val info = keyProvider.generateCertInfo()

        certRequests.remove(sessionId)?.cancel()
        certificateStorage.put(sessionId, info)
        info
    }

    suspend fun updateCertificateIfNeeded() {
        currentUser.sessionId()?.let {
            val certInfo = getCertInfo(it)
            if (needsUpdate(certInfo)) {
                updateCertificate(it, cancelOngoing = false)
            }
        }
    }

    private fun needsUpdate(certInfo: CertInfo) = certInfo.certificatePem == null || wallClock() >= certInfo.refreshAt

    suspend fun updateCertificate(sessionId: SessionId, cancelOngoing: Boolean): CertificateResult =
        withContext(mainScope.coroutineContext) {
            if (cancelOngoing)
                certRequests.remove(sessionId)?.cancel()
            val request = certRequests[sessionId]
                ?: async {
                    updateCertificateInternal(sessionId).apply {
                        certRequests.remove(sessionId)
                    }
                }.apply {
                    certRequests[sessionId] = this
                }

            try {
                request.await()
            } catch (e: CancellationException) {
                CertificateResult.Error(null)
            }
        }

    private suspend fun updateCertificateInternal(sessionId: SessionId): CertificateResult {
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
                if (sessionId == currentUser.sessionId() && appInUseMonitor.isInUse)
                    rescheduleRefreshTo(cert.refreshTimeMs)
                val result = CertificateResult.Success(cert.certificate, info.privateKeyPem)
                if (sessionId == currentUser.sessionId())
                    currentCertUpdateFlow.emit(result)
                result
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

                if (info.refreshCount < MAX_REFRESH_COUNT && appInUseMonitor.isInUse) {
                    certificateStorage.put(sessionId, info.copy(refreshCount = info.refreshCount + 1))

                    val now = wallClock()
                    val newRefresh = ((now + info.expiresAt) / 2)
                        .coerceAtLeast(now + MIN_REFRESH_DELAY)
                    rescheduleRefreshTo(newRefresh)
                }
                CertificateResult.Error(response)
            }
        }
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
}
