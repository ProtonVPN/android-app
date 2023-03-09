/*
 * Copyright (c) 2023 Proton AG
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

package com.protonvpn.android.auth

import io.sentry.Sentry
import io.sentry.SentryEvent
import me.proton.core.account.domain.repository.AccountRepository
import me.proton.core.accountmanager.data.SessionListenerImpl
import me.proton.core.network.domain.HttpResponseCodes
import me.proton.core.network.domain.session.Session
import me.proton.core.network.domain.session.SessionId
import javax.inject.Inject

class VpnSessionListener @Inject constructor(
    private val accountRepository: AccountRepository
) : SessionListenerImpl(accountRepository) {

    override suspend fun onSessionForceLogout(session: Session, httpCode: Int) {
        val username = accountRepository.getAccountOrNull(session.sessionId)?.username
        reportForceLogout(username, session.sessionId, httpCode);
        super.onSessionForceLogout(session, httpCode)
    }
}

private class SessionClosedInfo : Throwable("Force logout event 400")
private fun reportForceLogout(username: String?, sessionId: SessionId, httpCode: Int) {
    // 400 is returned for unexpected logouts
    if (httpCode == HttpResponseCodes.HTTP_BAD_REQUEST) {
        val event = SentryEvent(SessionClosedInfo())
        username?.let { event.setExtra("Username", it) }
        event.setExtra("SessionId", sessionId.toString())
        Sentry.captureEvent(event)
    }
}