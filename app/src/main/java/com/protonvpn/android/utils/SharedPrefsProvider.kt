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

package com.protonvpn.android.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface SharedPreferencesProvider {
    fun getPrefs(name: String): SharedPreferences
}

@Reusable
class AndroidSharedPreferencesProvider @Inject constructor(
    @ApplicationContext private val appContext: Context
) : SharedPreferencesProvider {

    override fun getPrefs(name: String): SharedPreferences =
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
}
