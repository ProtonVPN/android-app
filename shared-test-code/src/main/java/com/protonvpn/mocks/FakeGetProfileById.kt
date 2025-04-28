/*
 * Copyright (c) 2025. Proton AG
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

import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.usecases.GetProfileById
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeGetProfileById : GetProfileById {

    private val profiles = MutableStateFlow<List<Profile>>(emptyList())

    fun set(vararg profiles: Profile) {
        this.profiles.value = profiles.toList()
    }

    override suspend fun invoke(id: Long): Profile? = profiles.value.findById(id)

    override fun observe(id: Long): Flow<Profile?> = profiles.map { profiles -> profiles.findById(id) }

    private fun List<Profile>.findById(id: Long) = find { it.info.id == id }
}
