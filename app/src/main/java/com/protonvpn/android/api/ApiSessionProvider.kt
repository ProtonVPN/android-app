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

import com.protonvpn.android.models.login.LoginResponse
import com.protonvpn.android.utils.DebugUtils.debugAssert
import com.protonvpn.android.utils.Storage
import kotlinx.coroutines.flow.MutableSharedFlow
import me.proton.core.domain.entity.UserId
import me.proton.core.network.domain.humanverification.HumanVerificationDetails
import me.proton.core.network.domain.session.Session
import me.proton.core.network.domain.session.SessionId
import me.proton.core.network.domain.session.SessionListener
import me.proton.core.network.domain.session.SessionProvider

//TODO: true multi-user support will be added when core Auth is integrated.
class ApiSessionProvider : SessionProvider, SessionListener {

    val forceLogoutEvent = MutableSharedFlow<Session>()
    val currentSessionId: SessionId? get() = currentSession?.uid?.let { SessionId(it) }

    // Kept to make sure API call for old session (like logout) will be completed.
    private var oldSession: LoginResponse? = null
    private var currentSession: LoginResponse? = Storage.load(LoginResponse::class.java)

    fun setLoginResponse(value: LoginResponse) {
        currentSession = value
        Storage.save(currentSession)
    }

    fun clear() {
        oldSession = currentSession
        currentSession = null
        Storage.delete(LoginResponse::class.java)
    }

    private fun getLoginResponse(sessionId: SessionId) =
        currentSession?.takeIf { it.uid == sessionId.id } ?: oldSession?.takeIf { it.uid == sessionId.id }

    private fun getLoginResponse(userId: UserId) =
        currentSession?.takeIf { it.userId == userId.id } ?: oldSession?.takeIf { it.userId == userId.id }

    private fun LoginResponse.toSession() =
        Session(SessionId(uid), accessToken, refreshToken, null, scope.split(" "))

    override suspend fun getSession(sessionId: SessionId) =
        getLoginResponse(sessionId)?.toSession()

    override suspend fun getSessionId(userId: UserId) =
        getLoginResponse(userId)?.uid?.let { SessionId(it) }

    override suspend fun getUserId(sessionId: SessionId) =
        getLoginResponse(sessionId)?.userId?.let { UserId(it) }

    // To be implemeted in VPNAND-210
    override suspend fun onHumanVerificationNeeded(session: Session, details: HumanVerificationDetails):
        SessionListener.HumanVerificationResult = SessionListener.HumanVerificationResult.Failure

    override suspend fun onSessionForceLogout(session: Session) {
        forceLogoutEvent.emit(session)
    }

    override suspend fun onSessionTokenRefreshed(session: Session) {
        debugAssert { currentSession != null }
        currentSession?.accessToken = session.accessToken
        currentSession?.refreshToken = session.refreshToken
        Storage.save(currentSession)
    }
}
