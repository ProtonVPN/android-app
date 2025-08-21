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

package com.protonvpn.test.shared

import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateManager
import com.protonvpn.android.appconfig.periodicupdates.PeriodicUpdateSpec
import com.protonvpn.android.appconfig.periodicupdates.UpdateAction

/**
 * An implementation that executes actions for executeNow.
 * Other methods do nothing.
 */
class ImmediatePeriodicUpdateManager : PeriodicUpdateManager {
    override fun start() = Unit

    override fun <T, R> registerUpdateAction(
        action: UpdateAction<T, R>,
        vararg updateSpec: PeriodicUpdateSpec
    ) = Unit

    override fun unregister(action: UpdateAction<*, *>) = Unit

    override suspend fun <T, R> executeNow(action: UpdateAction<T, R>): R = action.executeWithDefault().result

    override suspend fun <T, R> executeNow(
        action: UpdateAction<T, R>,
        input: T
    ): R = action.execute(input).result

    override suspend fun processPeriodic() = Unit
}
