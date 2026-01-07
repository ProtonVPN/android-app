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

package com.protonvpn.android.redesign.settings.ui.excludedlocations

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.countries.ui.ServerFilterType
import com.protonvpn.android.redesign.countries.ui.ServerGroupItemData
import com.protonvpn.android.redesign.countries.ui.sortedForUi
import com.protonvpn.android.excludedlocations.usecases.AddExcludedLocation
import com.protonvpn.android.redesign.search.TextMatch
import com.protonvpn.android.redesign.search.ui.SearchResults
import com.protonvpn.android.redesign.search.ui.SearchViewModelDataAdapter
import com.protonvpn.android.redesign.search.ui.isEmpty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.takeIfNotEmpty
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExcludedLocationsViewModel @Inject constructor(
    private val addExcludedLocation: AddExcludedLocation,
    private val searchDataAdapter: SearchViewModelDataAdapter,
) : ViewModel() {

    sealed interface ExcludedLocationUiItem {

        val id: String

        data class Header(
            @param:StringRes val textResId: Int,
        ) : ExcludedLocationUiItem {

            override val id: String = textResId.toString()

        }

        sealed class Location : ExcludedLocationUiItem {

            override val id: String
                get() = run {
                    // This will provide the following possible values: Country/State/City
                    val locationType = this.javaClass.simpleName
                    val countryCode = countryId.countryCode
                    // This will provide the Country/State/City name. TextMatch should not be null,
                    // but to play safe we provide a random UUID to avoid generating duplicated IDs
                    // that could make the app crash when displaying the locations list
                    val locationIdentifier = textMatch?.fullText ?: UUID.randomUUID().toString()

                    // Generated ID example: Country-CH-Switzerland
                    "$locationType-$countryCode-$locationIdentifier"
                }

            abstract val locationId: Long?

            abstract val countryId: CountryId

            abstract val textMatch: TextMatch?

            data class Country(
                override val locationId: Long? = null,
                override val countryId: CountryId,
                override val textMatch: TextMatch?,
                val countryCities: List<City>,
            ) : Location() {

                val isExpandable: Boolean = countryCities.size > 1

            }

            data class State(
                override val locationId: Long? = null,
                override val countryId: CountryId,
                override val textMatch: TextMatch?,
                val nameEn: String?,
            ) : Location()

            data class City(
                override val locationId: Long? = null,
                override val countryId: CountryId,
                override val textMatch: TextMatch?,
                val nameEn: String?,
            ) : Location()
        }

    }

    sealed class ViewState {

        data class LocationResults(
            val searchQuery: String,
            val uiItems: List<ExcludedLocationUiItem>,
        ) : ViewState()

        data object NoLocationResults : ViewState()

    }

    sealed interface Event {

        data object OnExcludedLocationAdded : Event

    }

    private val localeFlow = MutableSharedFlow<Locale?>(replay = 1)

    private val searchQueryFlow = MutableStateFlow(value = INITIAL_SEARCH_QUERY)

    private val citiesByCountryIdFlow = localeFlow
        .filterNotNull()
        .flatMapConcat { locale ->
            searchDataAdapter.search(term = INITIAL_SEARCH_QUERY, locale = locale)
                .map { searchResultsMap ->
                    searchResultsMap[ServerFilterType.All]
                        ?.cities
                        ?.groupBy(ServerGroupItemData.City::countryId)
                        .orEmpty()
                }
        }

    private val _eventsFlow = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    val eventsFlow: SharedFlow<Event> = _eventsFlow.asSharedFlow()

    val viewStateFlow: StateFlow<ViewState?> = combine(
        searchQueryFlow,
        localeFlow.filterNotNull(),
        citiesByCountryIdFlow,
        ::Triple,
    ).flatMapLatest { (searchQuery, locale, citiesByCountryId) ->
        searchDataAdapter.search(term = searchQuery, locale = locale)
            .map { searchResultsMap ->
                val searchResults = searchResultsMap.getSearchResults()

                if (searchResults.isEmpty()) {
                    ViewState.NoLocationResults
                } else {
                    val uiItems = if (searchQuery.isEmpty()) {
                        searchResults.buildDefaultUiItems(
                            locale = locale,
                            citiesByCountryId = citiesByCountryId,
                        )
                    } else {
                        searchResults.buildFilteredUiItems(
                            locale = locale,
                            citiesByCountryId = citiesByCountryId,
                        )
                    }

                    ViewState.LocationResults(
                        searchQuery = searchQuery,
                        uiItems = uiItems,
                    )
                }
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = null,
    )

    private fun Map<ServerFilterType, SearchResults>.getSearchResults() = this[ServerFilterType.All]
        ?.let { searchResults ->
            if (searchResults.countries.isEmpty() && searchResults.cities.isEmpty() && searchResults.states.isEmpty()) {
                SearchResults.empty
            } else {
                searchResults
            }
        }
        ?: SearchResults.empty

    private fun SearchResults.buildDefaultUiItems(
        locale: Locale,
        citiesByCountryId: Map<CountryId, List<ServerGroupItemData.City>>,
    ): List<ExcludedLocationUiItem> = countries
        .sortedForUi(locale = locale)
        .map { country ->
            val citiesForCountry = citiesByCountryId[country.countryId].orEmpty()

            val uiCities = citiesForCountry
                .sortedForUi(locale = locale)
                .map { city ->
                    ExcludedLocationUiItem.Location.City(
                        countryId = city.countryId,
                        textMatch = city.textMatch,
                        nameEn = city.cityStateId.name,
                    )
                }

            ExcludedLocationUiItem.Location.Country(
                countryId = country.countryId,
                countryCities = uiCities,
                textMatch = country.textMatch,
            )
        }

    private fun SearchResults.buildFilteredUiItems(
        locale: Locale,
        citiesByCountryId: Map<CountryId, List<ServerGroupItemData.City>>,
    ): List<ExcludedLocationUiItem> = buildList {
        buildDefaultUiItems(locale = locale, citiesByCountryId = citiesByCountryId)
            .takeIfNotEmpty()
            ?.also { countryUiItems ->
                addUiItemsSection(textResId = R.string.countries_title, uiItems = countryUiItems)
            }

        states.takeIfNotEmpty()
            ?.sortedForUi(locale = locale)
            ?.map { state ->
                ExcludedLocationUiItem.Location.State(
                    countryId = state.countryId,
                    textMatch = state.textMatch,
                    nameEn = state.cityStateId.name,
                )
            }
            ?.also { stateUiItems ->
                addUiItemsSection(
                    textResId = R.string.country_filter_states,
                    uiItems = stateUiItems
                )
            }

        cities.takeIfNotEmpty()
            ?.sortedForUi(locale = locale)
            ?.map { city ->
                ExcludedLocationUiItem.Location.City(
                    countryId = city.countryId,
                    textMatch = city.textMatch,
                    nameEn = city.cityStateId.name,
                )
            }
            ?.also { cityUiItems ->
                addUiItemsSection(textResId = R.string.country_filter_cities, uiItems = cityUiItems)
            }
    }

    private fun MutableList<ExcludedLocationUiItem>.addUiItemsSection(
        @StringRes textResId: Int,
        uiItems: List<ExcludedLocationUiItem>,
    ) {
        add(element = ExcludedLocationUiItem.Header(textResId = textResId))

        addAll(elements = uiItems)
    }

    fun onLocaleChanged(newLocale: Locale) {
        viewModelScope.launch {
            localeFlow.emit(value = newLocale)
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        searchQueryFlow.update { newQuery }
    }

    fun onExcludedLocationSelected(location: ExcludedLocationUiItem.Location) {
        viewModelScope.launch {
            addExcludedLocation(excludedLocation = location.toExcludedLocation())

            _eventsFlow.emit(value = Event.OnExcludedLocationAdded)
        }
    }

    private companion object {

        private const val INITIAL_SEARCH_QUERY = ""

    }

}
