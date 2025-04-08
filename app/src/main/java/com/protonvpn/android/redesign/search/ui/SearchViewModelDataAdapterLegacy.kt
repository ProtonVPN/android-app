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

package com.protonvpn.android.redesign.search.ui

import androidx.annotation.VisibleForTesting
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.countries.ui.ServerFilterType
import com.protonvpn.android.redesign.countries.ui.ServerGroupItemData
import com.protonvpn.android.redesign.countries.ui.isMatching
import com.protonvpn.android.redesign.countries.ui.matches
import com.protonvpn.android.redesign.countries.ui.toCityItem
import com.protonvpn.android.redesign.countries.ui.toCountryItem
import com.protonvpn.android.redesign.countries.ui.toServerItem
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.third_party.ApacheStringUtils
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.addToList
import com.protonvpn.android.utils.addToSet
import dagger.Reusable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject

@Reusable
class SearchViewModelDataAdapterLegacy @Inject constructor(
    private val serverManager2: ServerManager2,
    private val translator: Translator,
) : SearchViewModelDataAdapter {

    override fun search(term: String, locale: Locale): Flow<Map<ServerFilterType, SearchResults>> =
        serverManager2.allServersFlow.map { servers ->
            val normalizedTerm = ApacheStringUtils.stripAccents(term)

            val allExitCountryCodes = mutableSetOf<String>()
            val allCitiesEn = mutableMapOf<String, MutableSet<String>>() // Country code to cities
            val allStatesEn = mutableMapOf<String, MutableSet<String>>() // Country code to states

            val serverResults = mutableMapOf<ServerFilterType, MutableList<ServerGroupItemData.Server>>()

            val serverSearchTerm = addServerNameHash(term)
            val normalizedServerSearchTerm = addServerNameHash(normalizedTerm)
            // This runs though all servers so all operations should be as fast as possible.
            servers.forEach { server ->
                // For servers search for matches in this loop
                val serverMatch = match(serverSearchTerm, normalizedServerSearchTerm, server.serverName)
                if (serverMatch != null) {
                    ServerFilterType.entries.forEach { filter ->
                        if (filter.isMatching(server))
                            serverResults.addToList(filter, server.toServerItem(serverMatch))
                    }
                }

                // Gather all cities and states
                allExitCountryCodes.add(server.exitCountry)
                if (server.city != null) allCitiesEn.addToSet(server.exitCountry, server.city)
                if (server.state != null) allStatesEn.addToSet(server.exitCountry, server.state)
            }

            val countriesResult = countriesResult(term, normalizedTerm, allExitCountryCodes, locale)
            val citiesResult = citiesResult(term, normalizedTerm, allCitiesEn)
            val statesResult = statesResult(term, normalizedTerm, allStatesEn)

            ServerFilterType.entries.associateWith { filter ->
                SearchResults(
                    countries = countriesResult[filter] ?: emptyList(),
                    cities = citiesResult[filter] ?: emptyList(),
                    states = statesResult[filter] ?: emptyList(),
                    servers = serverResults[filter] ?: emptyList(),
                )
            }
        }

    private suspend fun countriesResult(
        term: String,
        normalizedTerm: String,
        allExitCountryCodes: Set<String>,
        locale: Locale,
    ) : Map<ServerFilterType, List<ServerGroupItemData.Country>> {
        val countriesNames = allExitCountryCodes.map {
            Triple(CountryId(it), CountryTools.getFullName(locale, it), CountryTools.getFullName(Locale.US, it))
        }
        return buildMap<ServerFilterType, MutableList<ServerGroupItemData.Country>> {
            countriesNames.forEach { (countryId, nameLocalized, nameEn) ->
                val match = matchLocalizedAndEnglish(term, normalizedTerm, nameLocalized, nameEn)
                if (match != null) {
                    addItem(countryId) { isSecureCore ->
                        toCountryItem(
                            countryId.countryCode,
                            if (isSecureCore) CountryId.fastest else null,
                            match
                        )
                    }
                }
            }
        }
    }

    private suspend fun <T: ServerGroupItemData> MutableMap<ServerFilterType, MutableList<T>>.addItem(
        countryId: CountryId,
        cityStateId: CityStateId? = null,
        toItem: List<Server>.(Boolean) -> T?
    ) {
        val allServers = serversFor(countryId, cityStateId, secureCore = false)
        val secureCoreServers = serversFor(countryId, cityStateId, secureCore = true)
        ServerFilterType.entries.forEach { filter ->
            val secureCore = filter == ServerFilterType.SecureCore
            val servers = if (secureCore) secureCoreServers else allServers
            val item = servers
                .filter { filter.isMatching(it) }
                .takeIf { it.isNotEmpty() }
                ?.toItem(secureCore)
            if (item != null) addToList(filter, item)
        }
    }

    private suspend fun serversFor(
        countryId: CountryId,
        cityStateId: CityStateId?,
        secureCore: Boolean
    ) : List<Server> = serverManager2
        .getVpnExitCountry(countryId.countryCode, secureCore)
        ?.serverList
        ?.let { servers ->
            if (cityStateId == null) servers
            else servers.filter { cityStateId.matches(it) }
        } ?: emptyList()

    private suspend fun citiesResult(
        term: String,
        normalizedTerm: String,
        countriesWithCities: Map<String, Set<String>>,
    ) : Map<ServerFilterType, List<ServerGroupItemData.City>> {
        val countryToCitiesTranslated = countriesWithCities.mapValues { (_, citiesEn) ->
            citiesEn.map { it to translator.getCity(it) }
        }
        return buildMap<ServerFilterType, MutableList<ServerGroupItemData.City>> {
            countryToCitiesTranslated.forEach { (countryCode, cities) ->
                cities.forEach { (cityEn, cityLocalized) ->
                    val match = matchLocalizedAndEnglish(term, normalizedTerm, cityLocalized, cityEn)
                    if (match != null) {
                        addItem(CountryId(countryCode), CityStateId(cityEn, isState = false)) { _ ->
                            toCityItem(translator, isState = false, cityEn, this, match)
                        }
                    }
                }
            }
        }
    }

    private suspend fun statesResult(
        term: String,
        normalizedTerm: String,
        countriesWithStates: Map<String, Set<String>>,
    ) : Map<ServerFilterType, List<ServerGroupItemData.City>> {
        val countriesWithStatesTranslated = countriesWithStates.mapValues { (_, statesEn) ->
            statesEn.map { it to translator.getState(it) }
        }
        return buildMap<ServerFilterType, MutableList<ServerGroupItemData.City>> {
            countriesWithStatesTranslated.forEach { (countryCode, states) ->
                states.forEach { (stateEn, stateLocalized) ->
                    val match = matchLocalizedAndEnglish(term, normalizedTerm, stateLocalized, stateEn)
                    if (match != null)
                        addItem(CountryId(countryCode), CityStateId(stateEn, isState = true)) { _ ->
                            toCityItem(translator, isState = true, stateEn, this, match)
                        }
                }
            }
        }
    }
}

private fun matchLocalizedAndEnglish(term: String, normalizedTerm: String, textLocalized: String, textEn: String): TextMatch? =
    match(term, normalizedTerm, textLocalized) ?: match(term, normalizedTerm, textEn)

private fun addServerNameHash(term: String): String {
    return if (term.matches(SERVER_SEARCH_ENHANCE_PATTERN)) {
        val digitsStart = term.indexOfFirst { it in "0123456789" }
        term.substring(0, digitsStart) + "#" + term.substring(digitsStart)
    } else {
        term
    }
}

private val SERVER_SEARCH_ENHANCE_PATTERN = Regex("^[a-zA-Z-]+[0-9]+$")
private val ADDITIONAL_SEPARATORS = charArrayOf('-', '#')

data class TextMatch(val index: Int, val length: Int, val fullText: String)

@VisibleForTesting
fun match(
    term: String,
    normalizedTerm: String,
    text: String,
    ignoreCase: Boolean = true,
    matchOnlyWordPrefixes: Boolean = true,
    additionalSeparators: CharArray = ADDITIONAL_SEPARATORS // Separators that are not whitespace
): TextMatch? {
    fun Char.isSeparator() = isWhitespace() || this in additionalSeparators

    val normalizedText = ApacheStringUtils.stripAccents(text)
    val idx = normalizedText.indexOf(normalizedTerm, ignoreCase = ignoreCase)
    return when {
        idx < 0 -> null
        !matchOnlyWordPrefixes
            || idx == 0
            || normalizedText[idx - 1].isSeparator()
            || normalizedText[idx].isSeparator() -> TextMatch(idx, term.length, text)
        else -> {
            var idxAcc = 0
            val matched = normalizedText.splitToSequence(*additionalSeparators).any { word ->
                word.startsWith(normalizedTerm, ignoreCase = ignoreCase).also { matched ->
                    if (!matched)
                        idxAcc += word.length + 1
                }
            }
            if (matched)
                TextMatch(idxAcc, term.length, text)
            else
                null
        }
    }
}
