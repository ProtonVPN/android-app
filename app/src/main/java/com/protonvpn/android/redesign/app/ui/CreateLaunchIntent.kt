/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.redesign.app.ui

import android.content.Context
import android.content.Intent
import com.protonvpn.android.tv.IsTvCheck
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateLaunchIntent @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val isTv: IsTvCheck,
) {
    private var leanbackLaunchIntent: Intent? = null
    private var launchIntent: Intent? = null

    fun invalidateCache() {
        launchIntent = null
        leanbackLaunchIntent = null
    }

    fun forNotification() = withFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun withFlags(intentFlags: Int): Intent {
        val intent =
            if (isTv()) getLeanbackLaunchIntent()
            else getLaunchIntent()
        return intent!!.apply { flags = intentFlags }
    }

    private fun getLeanbackLaunchIntent(): Intent? {
        if (leanbackLaunchIntent == null)
            leanbackLaunchIntent = appContext.packageManager.getLeanbackLaunchIntentForPackage(appContext.packageName)
        return leanbackLaunchIntent?.let { Intent(it) }
    }

    private fun getLaunchIntent(): Intent? {
        if (launchIntent == null)
            launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
        return launchIntent?.let { Intent(it) }
    }
}
