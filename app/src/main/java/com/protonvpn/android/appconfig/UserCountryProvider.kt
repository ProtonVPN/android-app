/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.appconfig

import android.telephony.TelephonyManager
import com.protonvpn.android.api.data.DebugApiPrefs
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.mobileCountryCode
import dagger.Reusable
import javax.inject.Inject

interface UserCountryProvider {
    fun getTelephonyCountryCode(): String?
    fun getCountryCode(): String?
}

@Reusable
class DefaultUserCountryProvider @Inject constructor(
    private val telephonyManager: TelephonyManager?,
    private val serverListUpdaterPrefs: ServerListUpdaterPrefs,
    private val debugApiPrefs: DebugApiPrefs?,
) : UserCountryProvider {
    override fun getTelephonyCountryCode(): String? = debugApiPrefs?.country ?: telephonyManager?.mobileCountryCode()?.uppercase()

    override fun getCountryCode(): String? =
        (getTelephonyCountryCode() ?: serverListUpdaterPrefs.lastKnownCountry)?.uppercase()
}
