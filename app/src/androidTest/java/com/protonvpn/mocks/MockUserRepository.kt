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
package com.protonvpn.mocks

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import me.proton.core.domain.entity.SessionUserId
import me.proton.core.user.data.repository.UserRepositoryImpl
import me.proton.core.user.domain.entity.User
import me.proton.core.user.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockUserRepository @Inject constructor(
    private val userRepositoryImpl: UserRepositoryImpl
) : UserRepository by userRepositoryImpl {

    private val useMockUser = MutableStateFlow(false)
    private val mockUser = MutableSharedFlow<User>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    suspend fun setMockUser(user: User) {
        useMockUser.value = true
        mockUser.emit(user)
    }

    override suspend fun getUser(sessionUserId: SessionUserId, refresh: Boolean) =
        if (useMockUser.value) mockUser.first() else userRepositoryImpl.getUser(sessionUserId, refresh)

    override fun observeUser(sessionUserId: SessionUserId, refresh: Boolean): Flow<User?> =
        useMockUser.flatMapLatest { useMockUser ->
            when {
                useMockUser -> mockUser
                else -> userRepositoryImpl.observeUser(sessionUserId, refresh)
            }
        }
}
