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

package com.protonvpn.android.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

// The login and signup flow implementation is based on AccountViewModel in MainActivity always observing state changes
// in AccountManager, even (or especially!) when MainActivity is covered by other activities
// (see AccountViewModel.init()).
// Therefore it is impossible to properly implement login/signup flows from other activities on top.
// This helper allows other activities to tell AccountViewModel to start an auth flow with its AuthOrchestrator.
// It is obviously a hack, it won't work if there is no underlying MainActivity or it's killed by the OS (but then auth
// flows won't work anyway, see CP-7817 and AccountViewModel.init()).
@Singleton
class AuthFlowStartHelper @Inject constructor() {

    enum class Type {
        SignIn, CreateAccount
    }

    private val event = MutableSharedFlow<Type>(extraBufferCapacity = 1)
    val startAuthEvent: SharedFlow<Type> = event

    fun startAuthFlow(type: Type) {
        event.tryEmit(type)
    }
}
