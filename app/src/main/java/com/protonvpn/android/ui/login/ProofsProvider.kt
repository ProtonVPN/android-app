/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.login

import com.protonvpn.android.models.login.LoginInfoResponse
import kotlinx.coroutines.withContext
import me.proton.core.util.kotlin.DispatcherProvider
import srp.Auth
import srp.Proofs
import java.util.Arrays
import javax.inject.Inject

private const val PROOFS_LENGTH_BITS = 2048L

class ProofsProvider @Inject constructor(private val dispatcherProvider: DispatcherProvider) {
    suspend fun getProofs(
        username: String,
        password: ByteArray,
        infoResponse: LoginInfoResponse
    ): Proofs? = withContext(dispatcherProvider.Comp) {
        val auth = Auth(
            infoResponse.getVersion(),
            username,
            password,
            infoResponse.salt,
            infoResponse.modulus,
            infoResponse.serverEphemeral
        )
        val result = auth.generateProofs(PROOFS_LENGTH_BITS)
        Arrays.fill(password, 0)
        result
    }
}
