/*
 * Copyright (c) 2025 Proton AG
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

 package com.protonvpn.android.base.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

// Glance-aware stringResource method provider.
val LocalStringProvider = compositionLocalOf<StringProvider> {
    error("LocalStringProvider not provided")
}

// Glance-aware locale provider.
val LocalLocale = compositionLocalOf<Locale> {
    error("LocalLocale not provided")
}

fun interface StringProvider {
    @Composable
    fun getString(@StringRes id: Int, vararg formatArgs: Any): String
}

@Composable
fun glanceAwareStringResource(@StringRes id: Int, vararg arguments: Any): String =
    LocalStringProvider.current.getString(id, *arguments)
