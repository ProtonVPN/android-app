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

package com.protonvpn.android.profiles.usecases

import android.content.Context
import android.net.Uri
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.utils.doesDefaultBrowserSupportEphemeralCustomTabs
import com.protonvpn.android.utils.getEphemeralCustomTabsBrowser
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class PrivateBrowsingAvailability {
    AvailableWithDefault,
    AvailableWithOther,
    NotAvailable
}

fun interface GetPrivateBrowsingAvailability {
    suspend operator fun invoke(): PrivateBrowsingAvailability
}

private val EXAMPLE_URL = Uri.parse("https://proton.me")

@Reusable
class GetPrivateBrowsingAvailabilityImpl @Inject constructor(
    @ApplicationContext val appContext: Context,
    val dispatcherProvider: VpnDispatcherProvider,
    val isProfileAutoOpenPrivateBrowsingFeatureFlagEnabled: IsProfileAutoOpenPrivateBrowsingFeatureFlagEnabled
) : GetPrivateBrowsingAvailability {

    override suspend operator fun invoke(): PrivateBrowsingAvailability = withContext(dispatcherProvider.Io) {
        when {
            !isProfileAutoOpenPrivateBrowsingFeatureFlagEnabled() ->
                PrivateBrowsingAvailability.NotAvailable

            appContext.doesDefaultBrowserSupportEphemeralCustomTabs() ->
                PrivateBrowsingAvailability.AvailableWithDefault

            appContext.getEphemeralCustomTabsBrowser(EXAMPLE_URL) != null ->
                PrivateBrowsingAvailability.AvailableWithOther

            else ->
                PrivateBrowsingAvailability.NotAvailable
        }
    }
}