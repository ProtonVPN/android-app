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

package com.protonvpn.android.servers

import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.utils.DefaultLocaleProvider
import dagger.Reusable
import me.proton.core.network.domain.ApiResult
import javax.inject.Inject

@Reusable
class UpdateServerTranslations @Inject constructor(
    private val api: ProtonApiRetroFit,
    private val translator: Translator,
    private val getLocale: DefaultLocaleProvider,
) {
    suspend operator fun invoke() {
        val languageTag = getLocale().toLanguageTag()
        when (val result = api.getServerCities(languageTag)) {
            is ApiResult.Success-> {
                ProtonLogger.logCustom(LogCategory.API, "Got server translations for ${result.value.languageCode}")
                translator.updateTranslations(result.value.cities, result.value.states)
            }

            else -> {
                ProtonLogger.logCustom(LogCategory.API, "Failed to update server translations: $result")
            }
        }
    }
}