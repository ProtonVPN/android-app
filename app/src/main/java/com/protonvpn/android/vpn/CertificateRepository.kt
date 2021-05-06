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

@Serializable
data class CertInfo(
    val privateKeyPem: String,
    val publicKeyPem: String,
    val x25519Base64: String,
    val expiresAt: Long = 0,
    val refreshAt: Long = 0,
    val certificatePem: String? = null,
)

class CertificateRepository(
    val mainScope: CoroutineScope,
    val appContext: Context,
    val userData: UserData,
    val api: ProtonApiRetroFit,
    val wallClock: () -> Long,
    val userPlanManager: UserPlanManager,
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

    private val guestX25519Key by lazy { ed25519.KeyPair().toX25519Base64() }

    private val refreshCertTask = ReschedulableTask(mainScope, wallClock) {
        updateCurrentCert(force = false)
    }

    init {
        refreshCertTask.scheduleIn(0)
        mainScope.launch {
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

    suspend fun generateNewKey(sessionId: SessionId): CertInfo = withContext(mainScope.coroutineContext) {
        val keyPair = ed25519.KeyPair()
        val info = CertInfo(keyPair.privateKeyPKIXPem(), keyPair.publicKeyPKIXPem(), keyPair.toX25519Base64())

        certRequests.remove(sessionId)?.cancel()
        certPrefs.edit {
            putString(sessionId.id, info.serialize())
        }
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
                    refreshCertTask.scheduleTo(certInfo.refreshAt)
            }
        }
    }

    suspend fun updateCertificate(sessionId: SessionId, cancelOngoing: Boolean): CertificateResult =
        withContext(mainScope.coroutineContext) {
            if (cancelOngoing)
                certRequests.remove(sessionId)?.cancel()
            val request = certRequests.getOrElse(sessionId) {
                async {
                    val info = getCertInfo(sessionId)
                    val response = try {
                        api.getCertificate(info.publicKeyPem)
                    } finally {
                        certRequests.remove(sessionId)
                    }
                    when (response) {
                        is ApiResult.Success -> {
                            val cert = response.value
                            val newInfo = info.copy(
                                expiresAt = cert.expirationTimeMs,
                                refreshAt = cert.refreshTimeMs,
                                certificatePem = cert.certificate)
                            certPrefs.edit {
                                putString(sessionId.id, newInfo.serialize())
                            }
                            if (sessionId == userData.sessionId)
                                refreshCertTask.scheduleTo(cert.refreshTimeMs)
                            CertificateResult.Success(cert.certificate, info.privateKeyPem)
                        }
                        is ApiResult.Error ->
                            CertificateResult.Error(response)
                    }
                }
            }
            try {
                request.await()
            } catch (e: CancellationException) {
                CertificateResult.Error(null)
            }
        }

    suspend fun getCertInfo(sessionId: SessionId) =
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
