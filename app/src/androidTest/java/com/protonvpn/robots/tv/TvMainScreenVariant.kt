/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.robots.tv

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.protonvpn.android.tv.main_screen.IsTvMainScreenRedesignEnabled
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking

// Remove together with the TvMainScreenRedesign feature flag.

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TvMainScreenVariantEntryPoint {
    fun isTvMainScreenRedesignEnabled(): IsTvMainScreenRedesignEnabled
}

/** Whether the app shows the redesigned TV main screen. Robots use it to pick the right UI interactions. */
fun isTvMainScreenRedesign(): Boolean {
    val entryPoint = EntryPointAccessors.fromApplication(
        ApplicationProvider.getApplicationContext<Application>(),
        TvMainScreenVariantEntryPoint::class.java
    )
    return runBlocking { entryPoint.isTvMainScreenRedesignEnabled()() }
}
