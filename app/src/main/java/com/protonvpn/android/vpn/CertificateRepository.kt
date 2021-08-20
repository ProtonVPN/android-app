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
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.ReschedulableTask
import com.protonvpn.android.utils.UserPlanManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.proton.core.network.domain.ApiResult
import me.proton.core.network.domain.session.SessionId
import me.proton.core.util.kotlin.deserialize
import me.proton.core.util.kotlin.serialize
import me.proton.govpn.ed25519.KeyPair
import java.util.Date
import java.util.concurrent.TimeUnit

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

class CertificateRepository(
    val mainScope: CoroutineScope,
    val appContext: Context,
    val userData: UserData,
    val api: ProtonApiRetroFit,
    val wallClock: () -> Long,
    val userPlanManager: UserPlanManager
) {
    sealed class CertificateResult {
        data class Error(val error: ApiResult.Error?) : CertificateResult()
        data class Success(val certificate: String, val privateKeyPem: String) : CertificateResult()
    }

    private val certPrefs = EncryptedSharedPreferences.create(
        "cert_data",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        appContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val certRequests = mutableMapOf<SessionId, Deferred<CertificateResult>>()

    private val guestX25519Key by lazy { KeyPair().toX25519Base64() }

    private val refreshCertTask = ReschedulableTask(mainScope, wallClock) {
        updateCurrentCert(force = false)
    }

    init {
        refreshCertTask.scheduleIn(0)
        mainScope.launch {
            userData.sessionId?.let {
                val certInfo = getCertInfo(it)
                ProtonLogger.log("Current cert: ${if (certInfo.certificatePem == null)
                    null else "expires ${Date(certInfo.expiresAt)} (refresh at ${Date(certInfo.refreshAt)})"}")
            }
            userPlanManager.infoChangeFlow.collect { changes ->
                for (change in changes) when (change) {
                    is UserPlanManager.InfoChange.PlanChange.Downgrade,
                    is UserPlanManager.InfoChange.PlanChange.Upgrade,
                    is UserPlanManager.InfoChange.UserBecameDelinquent ->
                        updateCurrentCert(force = true)
                    else -> {}
                }
            }
        }
    }

    fun checkCertificateValidity() {
        refreshCertTask.scheduleIn(0)
    }

    private fun rescheduleRefreshTo(time: Long) {
        ProtonLogger.log("Certificate refresh scheduled to " + Date(time))
        refreshCertTask.scheduleTo(time)
    }

    private fun setInfo(sessionId: SessionId, info: CertInfo) {
        certPrefs.edit {
            putString(sessionId.id, info.serialize())
        }
    }

    suspend fun generateNewKey(sessionId: SessionId): CertInfo = withContext(mainScope.coroutineContext) {
        val keyPair = KeyPair()
        val info = CertInfo(keyPair.privateKeyPKIXPem(), keyPair.publicKeyPKIXPem(), keyPair.toX25519Base64())

        certRequests.remove(sessionId)?.cancel()
        setInfo(sessionId, info)
        launch {
            updateCertificate(sessionId, cancelOngoing = true)
        }
        info
    }

    private fun updateCurrentCert(force: Boolean) {
        mainScope.launch {
            userData.sessionId?.let {
                val certInfo = getCertInfo(it)
                if (force || certInfo.certificatePem == null || wallClock() >= certInfo.refreshAt)
                    updateCertificate(it, cancelOngoing = force)
                else
                    rescheduleRefreshTo(certInfo.refreshAt)
            }
        }
    }

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
        return when (val response = api.getCertificate(info.publicKeyPem)) {
            is ApiResult.Success -> {
                val cert = response.value
                val newInfo = info.copy(
                    expiresAt = cert.expirationTimeMs,
                    refreshAt = cert.refreshTimeMs,
                    certificatePem = cert.certificate,
                    refreshCount = 0)
                setInfo(sessionId, newInfo)
                ProtonLogger.log("New certificate expires at: " + Date(cert.expirationTimeMs))
                if (sessionId == userData.sessionId)
                    rescheduleRefreshTo(cert.refreshTimeMs)
                CertificateResult.Success(cert.certificate, info.privateKeyPem)
            }
            is ApiResult.Error -> {
                if (info.certificatePem == null)
                    ProtonLogger.log("Failed to get certificate (${info.refreshCount})")
                else
                    ProtonLogger.log("Failed to refresh (${info.refreshCount}) certificate expiring at " +
                        Date(info.expiresAt))

                if (info.refreshCount < MAX_REFRESH_COUNT) {
                    setInfo(sessionId, info.copy(refreshCount = info.refreshCount + 1))

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
        certPrefs.getString(sessionId.id, null)?.deserialize() ?: run {
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

    suspend fun clear(sessionId: SessionId) = withContext(mainScope.coroutineContext) {
        certRequests.remove(sessionId)?.cancel()
        certPrefs.edit {
            remove(sessionId.id)
        }
    }
}
