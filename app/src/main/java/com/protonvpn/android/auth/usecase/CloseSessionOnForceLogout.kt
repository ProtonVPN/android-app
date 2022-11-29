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

package com.protonvpn.android.auth.usecase

import io.sentry.Sentry
import io.sentry.SentryEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.account.domain.entity.SessionState
import me.proton.core.accountmanager.domain.AccountManager
import me.proton.core.accountmanager.domain.onSessionState
import javax.inject.Inject
import javax.inject.Singleton

private class SessionClosedInfo : Throwable("Session closed event")

@Singleton
class CloseSessionOnForceLogout @Inject constructor(
    mainScope: CoroutineScope,
    accountManager: AccountManager,
    onSessionClosed: OnSessionClosed
) {
    init {
        accountManager.onSessionState(SessionState.ForceLogout)
            .onEach {
                Sentry.captureEvent(SentryEvent(SessionClosedInfo()))
                onSessionClosed.invoke(it)
            }
            .launchIn(mainScope)
    }
}
