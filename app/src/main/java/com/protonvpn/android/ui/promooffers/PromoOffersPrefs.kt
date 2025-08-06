/*
 * Copyright (c) 2022. Proton AG
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

package com.protonvpn.android.ui.promooffers

import android.content.SharedPreferences
import com.protonvpn.android.utils.SharedPreferencesProvider
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.proton.core.util.android.sharedpreferences.PreferencesProvider
import me.proton.core.util.android.sharedpreferences.list
import me.proton.core.util.android.sharedpreferences.observe
import javax.inject.Inject

private const val PREFS_NAME = "PromoOffersPrefs"
private const val VISITED_OFFERS_KEY = "visitedOffers"

@Reusable
class PromoOffersPrefs @Inject constructor(
    prefsProvider: SharedPreferencesProvider
) : PreferencesProvider {
    override val preferences: SharedPreferences = prefsProvider.getPrefs(PREFS_NAME)

    var visitedOffers: List<String> by list(emptyList(), VISITED_OFFERS_KEY)
        private set
    val visitedOffersFlow: Flow<List<String>> =
        preferences.observe<List<String>>(VISITED_OFFERS_KEY).map { it ?: emptyList() }

    fun addVisitedOffer(id: String) {
        if (!visitedOffers.contains(id)) {
            visitedOffers = visitedOffers + id
        }
    }
}
