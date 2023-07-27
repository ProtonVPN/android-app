/*
 * Copyright (c) 2022 Proton AG
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

package com.protonvpn.android.search

import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.models.vpn.ServerGroup
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.third_party.ApacheStringUtils
import com.protonvpn.android.utils.ServerManager
import dagger.Reusable
import java.util.Locale
import javax.inject.Inject

@Reusable
class Search @Inject constructor(
    private val serverManager: ServerManager,
) {
    data class TextMatch(val text: String, val index: Int)
    data class Match<T>(val textMatch: TextMatch, val value: T) {
        val text get() = textMatch.text
        val index get() = textMatch.index
    }
    data class Result(
        val cities: List<Match<List<Server>>>,
        val countries: List<Match<VpnCountry>>,
        val servers: List<Match<Server>>,
    ) {
        val isEmpty get() = cities.isEmpty() && countries.isEmpty() && servers.isEmpty()
    }

    operator fun invoke(term: String, secureCore: Boolean): Result {
        if (term.isBlank()) return Result(emptyList(), emptyList(), emptyList())

        val countries =
            if (secureCore) serverManager.getSecureCoreExitCountries()
            else serverManager.getVpnCountries()
        val allServerCountries =
            if (secureCore) serverManager.getSecureCoreExitCountries().asSequence()
            else sequenceOf(serverManager.getVpnCountries() + serverManager.getGateways()).flatten()
        val normalizedTerm = normalize(term)
        return Result(
            searchCities(normalizedTerm, countries),
            searchCountries(normalizedTerm, countries),
            searchServers(normalizedTerm, allServerCountries)
        )
    }

    private fun find(term: String, text: String, normalize: Boolean): TextMatch? {
        val normalizedText = if (normalize) normalize(text) else text
        val idx = normalizedText.lowercase().indexOf(term)
        return if (idx == 0 || idx > 0 && normalizedText[idx - 1].isSeparator())
            TextMatch(text, idx)
        else
            null
    }

    private fun Char.isSeparator() = isWhitespace() || this in SEPARATORS

    private fun searchCities(term: String, countries: List<VpnCountry>): List<Match<List<Server>>> {
        val lang = Locale.getDefault().language
        val results = linkedMapOf<TextMatch, MutableList<Server>>()
        countries.forEach { country ->
            val cityMatches = country.serverList.asSequence().map { it.city }.filterNotNull().distinct().mapNotNull {
                find(term, it, true)
            }.associateBy { it.text }
            val translatedCityMatches = if (serverManager.translationsLang == lang) {
                country.serverList.asSequence().map { it.getCityTranslation() }.filterNotNull().distinct().mapNotNull {
                    find(term, it, true)
                }.associateBy { it.text }
            } else
                null
            val regionMatches = country.serverList.asSequence().map { it.region }.filterNotNull().distinct()
                .mapNotNull { find(term, it, true) }.associateBy { it.text }
            country.serverList.forEach { server ->
                translatedCityMatches?.get(server.getCityTranslation())?.let { match ->
                    results.getOrPut(match) { mutableListOf() } += server
                } ?: cityMatches[server.city]?.let { match ->
                    results.getOrPut(match) { mutableListOf() } += server
                }
                regionMatches[server.region]?.let { match ->
                    results.getOrPut(match) { mutableListOf() } += server
                }
            }
        }
        return results.map { Match(it.key, it.value.toList()) }.toList()
    }

    private fun searchCountries(term: String, countries: List<VpnCountry>): List<Match<VpnCountry>> {
        return countries.asSequence().mapNotNull { country ->
            find(term, country.countryName, true)?.let { match ->
                Match(match, country)
            }
        }.toList()
    }

    private fun searchServers(term: String, countries: Sequence<ServerGroup>): List<Match<Server>> {
        return countries.asSequence().flatMap { it.serverList.asSequence() }.mapNotNull { server ->
            find(term, server.serverName, false)?.let { match ->
                Match(match, server)
            }
        }.toList()
    }

    private fun normalize(text: String) = ApacheStringUtils.stripAccents(text).lowercase()

    companion object {
        val SEPARATORS = arrayOf('-', '#')
    }
}
