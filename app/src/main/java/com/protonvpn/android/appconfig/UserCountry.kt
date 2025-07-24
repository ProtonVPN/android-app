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
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.ui.home.ServerListUpdaterPrefs
import com.protonvpn.android.utils.mobileCountryCode
import dagger.Reusable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface UserCountryTelephonyBased {
    operator fun invoke(): CountryId?
}

@Reusable
class DefaultUserCountryTelephonyBased @Inject constructor(
    private val telephonyManager: TelephonyManager?,
    private val debugApiPrefs: DebugApiPrefs?,
) : UserCountryTelephonyBased {
    override fun invoke(): CountryId? = (debugApiPrefs?.country ?: telephonyManager?.mobileCountryCode())
        ?.let { CountryId(it) }
}

@Reusable
class UserCountryIpBased @Inject constructor(
    private val prefs: ServerListUpdaterPrefs,
    private val debugApiPrefs: DebugApiPrefs?,
) {
    operator fun invoke(): CountryId? = (debugApiPrefs?.country ?: prefs.lastKnownCountry)
        ?.let { CountryId(it) }

    @Suppress("IfThenToElvis")
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<CountryId?> =
        if (debugApiPrefs != null) {
            debugApiPrefs.countryFlow.flatMapLatest { debugOverrideCountry ->
                if (debugOverrideCountry != null) flowOf(debugOverrideCountry)
                else prefs.lastKnownCountryFlow
            }
        } else {
            prefs.lastKnownCountryFlow
        }.map { CountryId(it) }
}

@Reusable
class UserCountryPhysical @Inject constructor(
    private val telephony: UserCountryTelephonyBased,
    private val ip: UserCountryIpBased,
) {
    operator fun invoke(): CountryId? = telephony() ?: ip()
}
