/*
 * Copyright (c) 2025. Proton AG
 *
 *  This file is part of ProtonVPN.
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

package com.protonvpn.android.update

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf

open class AppUpdateInfo(
    val stalenessDays: Int,
    val availableVersionCode: Int,
)

abstract class AppUpdateManager {
    abstract val checkForUpdateFlow: Flow<AppUpdateInfo?>

    abstract suspend fun checkForUpdate(): AppUpdateInfo?

    abstract fun launchUpdateFlow(activity: Activity, updateInfo: AppUpdateInfo)
}

open class NoopAppUpdateManager : AppUpdateManager() {
    override val checkForUpdateFlow: Flow<AppUpdateInfo?> = flowOf(null)

    override suspend fun checkForUpdate(): AppUpdateInfo? = null

    override fun launchUpdateFlow(activity: Activity, updateInfo: AppUpdateInfo) = Unit
}