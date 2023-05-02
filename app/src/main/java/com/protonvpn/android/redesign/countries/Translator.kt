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

package com.protonvpn.android.redesign.countries

import com.protonvpn.android.utils.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.proton.core.util.kotlin.takeIfNotBlank
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Translator @Inject constructor(
    mainScope: CoroutineScope,
    private val serverManager: ServerManager
) {
    private var cityTranslations: Map<String, String> = emptyMap()

    init {
        serverManager.serverListVersion
            .onEach {
                cityTranslations = extractTranslations()
            }
            .launchIn(mainScope)
    }

    fun getCity(cityEn: String): String = cityTranslations.getOrDefault(cityEn, cityEn).takeIfNotBlank() ?: cityEn

    private fun extractTranslations(): Map<String, String> =
        serverManager.allServers
            .mapNotNull {
                if (it.city != null && it.getCityTranslation() != null)
                    it.city to it.getCityTranslation()!!
                else
                    null
            }
            .associateTo(HashMap()) { it }
}
