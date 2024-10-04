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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CreateLaunchIntent @Inject constructor(
    private val isTv: IsTvCheck,
) {
    private var leanbackLaunchIntent: Intent? = null
    private var launchIntent: Intent? = null

    fun invalidateCache() {
        launchIntent = null
        leanbackLaunchIntent = null
    }

    fun forNotification(context: Context) = withFlags(context, Intent.FLAG_ACTIVITY_NEW_TASK)

    fun withFlags(context: Context, intentFlags: Int): Intent {
        val intent =
            if (isTv()) getLeanbackLaunchIntent(context)
            else getLaunchIntent(context)
        return intent!!.apply { flags = intentFlags }
    }

    private fun getLeanbackLaunchIntent(context: Context): Intent? {
        if (leanbackLaunchIntent == null)
            leanbackLaunchIntent = context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
        return launchIntent?.let { Intent(it) }
    }

    private fun getLaunchIntent(context: Context): Intent? {
        if (launchIntent == null)
            launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return launchIntent?.let { Intent(it) }
    }
}
