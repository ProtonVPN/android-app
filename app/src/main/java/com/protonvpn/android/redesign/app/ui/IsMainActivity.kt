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

import android.app.Activity
import com.protonvpn.android.tv.main.TvMainActivity

private val MOBILE_MAIN_ACTIVITY_CLASS: Class<out Activity> = MainActivity::class.java
private val TV_MAIN_ACTIVITY_CLASS: Class<out Activity> = TvMainActivity::class.java

fun isMainActivity(activity: Activity, isTv: Boolean): Boolean =
    mainActivityClass(isTv).isInstance(activity)

private fun mainActivityClass(isTv: Boolean) =
    if (isTv) TV_MAIN_ACTIVITY_CLASS else MOBILE_MAIN_ACTIVITY_CLASS