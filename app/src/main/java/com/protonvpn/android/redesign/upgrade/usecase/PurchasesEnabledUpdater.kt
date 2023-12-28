/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.redesign.upgrade.usecase

import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.appconfig.periodicupdates.IsInForeground
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchasesEnabledUpdater @Inject constructor(
    private val mainScope: CoroutineScope,
    private val purchasesEnabled: Lazy<CachedPurchaseEnabled>,
    @IsInForeground private val inForeground: Flow<Boolean>,
) {

    fun start() {
        inForeground.distinctUntilChanged().onEach { foreground ->
            if (foreground)
                purchasesEnabled.get().refreshIfNeeded()
        }.launchIn(mainScope)
    }
}