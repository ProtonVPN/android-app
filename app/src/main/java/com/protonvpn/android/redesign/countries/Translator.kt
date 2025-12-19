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

import android.content.Context
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.servers.Server
import com.protonvpn.android.utils.BytesFileWriter
import com.protonvpn.android.utils.FileObjectStore
import com.protonvpn.android.utils.KotlinCborObjectSerializer
import com.protonvpn.android.utils.ObjectStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.proton.core.util.kotlin.filterNotNullValues
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

typealias TranslationsMap = Map<CountryId, Map<String, String>>

@Serializable
data class TranslationsData(
    val cities: TranslationsMap,
    val states: TranslationsMap,
)

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
@Singleton
class Translator(
    mainScope: CoroutineScope,
    private val store: ObjectStore<TranslationsData>
) {
    @Inject
    constructor(
        mainScope: CoroutineScope,
        @ApplicationContext context: Context,
        dispatcherProvider: VpnDispatcherProvider
    ) : this(
        mainScope,
        FileObjectStore(
            File(context.filesDir, "server_translations_data"),
            mainScope,
            dispatcherProvider,
            KotlinCborObjectSerializer(TranslationsData.serializer()),
            BytesFileWriter(),
        )
    )

    private val translations = MutableStateFlow<TranslationsData?>(null)
    val flow: StateFlow<TranslationsData?> = translations

    val current get() = translations.value

    init {
        mainScope.launch {
            translations.value = store.read()
        }
    }

    fun updateTranslations(
        cities: Map<String, Map<String, String?>>,
        states: Map<String, Map<String, String?>>
    ) {
        val newTranslations = TranslationsData(
            cities = cities.mapCountryCodesAndFilterOutNulls(),
            states = states.mapCountryCodesAndFilterOutNulls(),
        )
        translations.value = newTranslations
        store.store(newTranslations)
    }

    private fun Map<String, Map<String, String?>>.mapCountryCodesAndFilterOutNulls(): Map<CountryId, Map<String, String>> =
        entries.associate { (countryCode, translations) ->
            CountryId(countryCode) to translations.filterNotNullValues()
        }
}

fun TranslationsData?.city(country: CountryId, cityEn: String): String {
    if (this == null) return cityEn
    return cities
        .getOrDefault(country, emptyMap())
        .getOrDefault(cityEn, cityEn)
}

fun TranslationsData?.state(country: CountryId, stateEn: String): String {
    if (this == null) return stateEn
    return states
        .getOrDefault(country, emptyMap())
        .getOrDefault(stateEn, stateEn)
}

fun TranslationsData?.city(server: Server): String? =
    server.city?.let { city(CountryId(server.exitCountry), server.city) }

fun TranslationsData?.state(server: Server): String? =
    server.state?.let { state(CountryId(server.exitCountry), server.state) }
