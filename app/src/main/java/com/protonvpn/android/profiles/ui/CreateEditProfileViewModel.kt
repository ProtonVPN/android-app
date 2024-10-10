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
package com.protonvpn.android.profiles.ui

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfileInfo
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.data.toProfileEntity
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.flatMapLatestNotNull
import com.protonvpn.android.utils.sortedByLocaleAware
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.proton.core.presentation.savedstate.state
import java.util.Locale
import javax.inject.Inject
import me.proton.core.presentation.R as CoreR

private val defaultSettingScreenState = SettingsScreenState(
    protocol = ProtocolSelection.SMART,
    netShield = true,
    natType = NatType.Strict,
    lanConnections = true,
)

@Parcelize
data class NameScreenState(
    val name: String,
    val color: ProfileColor,
    val icon: ProfileIcon,
) : Parcelable

enum class ProfileType(
    @StringRes val nameRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Standard(R.string.create_profile_type_standard, CoreR.drawable.ic_proton_earth),
    SecureCore(R.string.create_profile_type_secure_core, CoreR.drawable.ic_proton_locks),
    P2P(R.string.create_profile_type_p2p, CoreR.drawable.ic_proton_arrow_right_arrow_left),
    Gateway(R.string.create_profile_gateway, CoreR.drawable.ic_proton_servers),
}

// This state is used only in saved state handle and actual view state is computed from it
// as TypeAndLocationScreenState
@Parcelize
data class TypeAndLocationScreenSaveState(
    val type: ProfileType,
    val countryId: CountryId? = null,
    val cityOrState: CityStateId? = null,
    val serverId: String? = null,
    val gateway: String? = null,
    val exitCountrySecureCore: CountryId? = null,
    val entryCountrySecureCore: CountryId? = null,
) : Parcelable

sealed class TypeAndLocationScreenState {

    abstract val availableTypes: List<ProfileType>

    interface StandardWithFeatures {
        val country: CountryItem
        val cityOrState: CityOrStateItem?
        val server: ServerItem?
        val features: Set<ServerFeature>
        val selectableCountries: List<CountryItem>
        val selectableCitiesOrStates: List<CityOrStateItem>
        val selectableServers: List<ServerItem>
    }

    data class Standard(
        override val availableTypes: List<ProfileType>,
        override val country: CountryItem,
        override val cityOrState: CityOrStateItem?,
        override val server: ServerItem?,
        override val selectableCountries: List<CountryItem>,
        override val selectableCitiesOrStates: List<CityOrStateItem>,
        override val selectableServers: List<ServerItem>,
    ) : TypeAndLocationScreenState(), StandardWithFeatures {
        override val features: Set<ServerFeature> get() = emptySet()
    }

    data class P2P(
        override val availableTypes: List<ProfileType>,
        override val country: CountryItem,
        override val cityOrState: CityOrStateItem?,
        override val server: ServerItem?,
        override val selectableCountries: List<CountryItem>,
        override val selectableCitiesOrStates: List<CityOrStateItem>,
        override val selectableServers: List<ServerItem>,
    ) : TypeAndLocationScreenState(), StandardWithFeatures {
        override val features: Set<ServerFeature> get() = setOf(ServerFeature.P2P)
    }

    data class SecureCore(
        override val availableTypes: List<ProfileType>,
        val exitCountry: CountryItem,
        val entryCountry: CountryItem? = null,
        val selectableExitCountries: List<CountryItem>,
        val selectableEntryCountries: List<CountryItem>,
    ) : TypeAndLocationScreenState()

    data class Gateway(
        override val availableTypes: List<ProfileType>,
        val gateway: GatewayItem,
        val server: ServerItem,
        val selectableGateways: List<GatewayItem>,
        val selectableServers: List<ServerItem>,
    ) : TypeAndLocationScreenState()

    data class CountryItem(val id: CountryId, val online: Boolean)
    data class GatewayItem(val name: String, val online: Boolean)

    data class ServerItem(val name: String?, val id: String?, val flagCountryId: CountryId?, val online: Boolean) {
        val isFastest get() = id == null
        companion object {
            fun Fastest(online: Boolean) = ServerItem(null, null, null, online)
        }
    }

    data class CityOrStateItem(val name: String?, val id: CityStateId, val online: Boolean)

    val type get() = when (this) {
        is Standard -> ProfileType.Standard
        is SecureCore -> ProfileType.SecureCore
        is P2P -> ProfileType.P2P
        is Gateway -> ProfileType.Gateway
    }
}

@Parcelize
data class SettingsScreenState(
    val netShield: Boolean,
    val protocol: ProtocolSelection,
    val natType: NatType,
    val lanConnections: Boolean,
) : Parcelable {
    fun toSettingsOverrides() = SettingsOverrides(
        protocolData = protocol.toData(),
        netShield = if (netShield) NetShieldProtocol.ENABLED_EXTENDED else NetShieldProtocol.DISABLED,
        randomizedNat = natType.toRandomizedNat(),
        lanConnections = lanConnections,
    )
}

@HiltViewModel
class CreateEditProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profilesDao: ProfilesDao,
    private val currentUser: CurrentUser,
    private val adapter: ProfilesServerDataAdapter,
    @WallClock private val wallClock: () -> Long,
) : ViewModel() {

    private var editedProfileId: Long? = null

    private var nameScreenState by savedStateHandle.state<NameScreenState?>(null, "nameScreenState")
    private var typeAndLocationScreenSavedState by savedStateHandle.state<TypeAndLocationScreenSaveState?>(null, "typeAndLocationScreenState")
    private var settingsScreenState by savedStateHandle.state<SettingsScreenState?>(null, "settingsScreenState")

    val nameScreenStateFlow = savedStateHandle.getStateFlow<NameScreenState?>("nameScreenState", null)
    private val typeAndLocationScreenSavedStateFlow = savedStateHandle.getStateFlow<TypeAndLocationScreenSaveState?>("typeAndLocationScreenState", null)
    val settingsScreenStateFlow = savedStateHandle.getStateFlow<SettingsScreenState?>("settingsScreenState", null)

    val localeFlow = MutableStateFlow<Locale?>(null)
    private val availableTypesFlow = adapter.hasAnyGatewaysFlow.map {
        if (it) ProfileType.entries else ProfileType.entries.minus(ProfileType.Gateway)
    }

    val typeAndLocationScreenStateFlow : Flow<TypeAndLocationScreenState> = typeAndLocationScreenSavedStateFlow.flatMapLatestNotNull { savedState ->
        combine(
            availableTypesFlow,
            localeFlow.filterNotNull(),
            adapter.serverListVersion
        ) { availableTypes, locale, _ ->
            when (savedState.type) {
                ProfileType.Standard,
                ProfileType.P2P -> standardOrP2P(
                    availableTypes,
                    locale,
                    savedState.countryId ?: CountryId.fastest,
                    savedState.cityOrState,
                    savedState.serverId,
                    savedState.type == ProfileType.P2P,
                )
                ProfileType.SecureCore -> secureCore(
                    availableTypes,
                    locale,
                    savedState.exitCountrySecureCore ?: CountryId.fastest,
                    savedState.entryCountrySecureCore,
                )
                ProfileType.Gateway -> gateway(
                    availableTypes,
                    locale,
                    savedState.gateway,
                    savedState.serverId,
                )
            }
        }
    }

    fun setEditedProfileId(profileId: Long?) {
        editedProfileId = profileId
        viewModelScope.launch {
            if (nameScreenState == null && profileId != null) {
                profilesDao.getProfileById(profileId)?.let {
                    initializeFromProfile(it)
                }
            } else {
                initializeDefault()
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            currentUser.vpnUser()?.userId?.let { userId ->
                profilesDao.upsert(getProfile().toProfileEntity(userId, wallClock()))
            }
        }
    }

    private fun initializeDefault() {
        nameScreenState = NameScreenState(
            "",
            ProfileColor.Color1,
            ProfileIcon.Icon1,
        )
        typeAndLocationScreenSavedState = standardTypeDefault()
        settingsScreenState = defaultSettingScreenState
    }

    private suspend fun initializeFromProfile(profile: Profile) {
        nameScreenState = NameScreenState(
            profile.info.name,
            profile.info.color,
            profile.info.icon,
        )
        val intent = profile.connectIntent
        typeAndLocationScreenSavedState = when (intent) {
            is ConnectIntent.Gateway ->
                TypeAndLocationScreenSaveState(ProfileType.Gateway, gateway = intent.gatewayName, serverId = intent.serverId)
            is ConnectIntent.SecureCore ->
                TypeAndLocationScreenSaveState(ProfileType.SecureCore, exitCountrySecureCore = intent.exitCountry, entryCountrySecureCore = intent.entryCountry)
            else -> {
                val intentData = intent.toData()
                val serverId = intentData.serverId
                val cityOrState = intentData.cityStateId() ?: adapter.getCityOrStateForServerId(serverId)
                TypeAndLocationScreenSaveState(
                    if (intent.features.contains(ServerFeature.P2P)) ProfileType.P2P else ProfileType.Standard,
                    countryId = intentData.exitCountry?.let { CountryId(it) } ?: CountryId.fastest,
                    serverId = serverId,
                    cityOrState = cityOrState,
                )
            }
        }
        settingsScreenState = SettingsScreenState(
            netShield = intent.settingsOverrides?.netShield?.let { it != NetShieldProtocol.DISABLED } ?: defaultSettingScreenState.netShield,
            protocol = intent.settingsOverrides?.protocolData?.toProtocolSelection() ?: defaultSettingScreenState.protocol,
            natType = intent.settingsOverrides?.randomizedNat?.let { NatType.fromRandomizedNat(it) } ?: defaultSettingScreenState.natType,
            lanConnections = intent.settingsOverrides?.lanConnections ?: defaultSettingScreenState.lanConnections,
        )
    }

    private suspend fun getProfile(): Profile {
        val profileId = editedProfileId
        val nameScreen = requireNotNull(nameScreenState)
        val typeAndLocationScreen = requireNotNull(typeAndLocationScreenStateFlow.first())
        val settingsScreen = requireNotNull(settingsScreenState)
        val overrides = settingsScreen.toSettingsOverrides()
        return Profile(
            ProfileInfo(
                profileId ?: 0L,
                nameScreen.name,
                nameScreen.color,
                nameScreen.icon,
                (typeAndLocationScreen as? TypeAndLocationScreenState.Gateway)?.gateway?.name,
            ),
            when (typeAndLocationScreen) {
                is TypeAndLocationScreenState.P2P,
                is TypeAndLocationScreenState.Standard -> {
                    typeAndLocationScreen as TypeAndLocationScreenState.StandardWithFeatures
                    val country = typeAndLocationScreen.country
                    val serverId = typeAndLocationScreen.server?.id
                    val cityOrState = typeAndLocationScreen.cityOrState
                    val features = typeAndLocationScreen.features
                    if (serverId != null) {
                        ConnectIntent.Server(
                            serverId = serverId,
                            exitCountry = country.id,
                            features = features,
                            profileId = profileId,
                            settingsOverrides = overrides,
                        )
                    } else if (cityOrState?.id?.isState == true) {
                        ConnectIntent.FastestInState(
                            country = country.id,
                            stateEn = cityOrState.id.name,
                            features = features,
                            profileId = profileId,
                            settingsOverrides = overrides,
                        )
                    } else if (cityOrState?.id?.isState == false) {
                        ConnectIntent.FastestInCity(
                            country = country.id,
                            cityEn = cityOrState.id.name,
                            features = features,
                            profileId = profileId,
                            settingsOverrides = overrides,
                        )
                    } else {
                        ConnectIntent.FastestInCountry(
                            country = country.id,
                            features = features,
                            profileId = profileId,
                            settingsOverrides = overrides,
                        )
                    }
                }
                is TypeAndLocationScreenState.SecureCore -> ConnectIntent.SecureCore(
                    exitCountry = typeAndLocationScreen.exitCountry.id,
                    entryCountry = typeAndLocationScreen.entryCountry?.id ?: CountryId.fastest,
                    profileId = profileId,
                    settingsOverrides = overrides,
                )
                is TypeAndLocationScreenState.Gateway -> ConnectIntent.Gateway(
                    gatewayName = typeAndLocationScreen.gateway.name,
                    profileId = profileId,
                    serverId = typeAndLocationScreen.server.id,
                    settingsOverrides = overrides,
                )
            }
        )
    }

    private fun standardTypeDefault() =
        TypeAndLocationScreenSaveState(
            type = ProfileType.Standard,
            countryId = CountryId.fastest
        )

    private fun ConnectIntentData.cityStateId() = when {
        region != null -> CityStateId(region, true)
        city != null -> CityStateId(city, false)
        else -> null
    }

    private suspend fun standardOrP2P(
        availableTypes: List<ProfileType>,
        locale: Locale,
        requestedCountryId: CountryId,
        requestedCityOrState: CityStateId?,
        requestedServerId: String?,
        isP2P: Boolean,
    ) : TypeAndLocationScreenState {
        val serverFeature = if (isP2P) ServerFeature.P2P else null
        val countries = adapter.countries(serverFeature)
        val haveOnlineCountry = countries.any { it.online }
        val fastestCountry = TypeAndLocationScreenState.CountryItem(CountryId.fastest, haveOnlineCountry)

        // Fallback to fastest if country not longer available
        val country = countries.find { it.id == requestedCountryId } ?: fastestCountry

        val citiesOrStates = adapter.citiesOrStates(country.id, secureCore = false, serverFeature)
        val hasOnlineCityOrState = citiesOrStates.any { it.online }
        val fastestCityOrState = TypeAndLocationScreenState.CityOrStateItem(
            null,
            if (citiesOrStates.any { it.id.isState }) CityStateId.fastestState
            else CityStateId.fastestCity,
            hasOnlineCityOrState
        )
        val cityOrState = if (country.id.isFastest) {
            // Don't show city/state selection for fastest country
            null
        } else {
            citiesOrStates.find { it.id == requestedCityOrState } ?: fastestCityOrState
        }

        val servers = cityOrState?.let { adapter.servers(country.id, it.id, secureCore = false, serverFeature) } ?: emptyList()
        val fastestServer = TypeAndLocationScreenState.ServerItem.Fastest(servers.any { it.online })
        val server = when {
            cityOrState == null || cityOrState.id.isFastest -> null
            requestedServerId != null && servers.any { it.id == requestedServerId } -> adapter.getServerViewModel(requestedServerId)
            else -> fastestServer
        }

        val sortedCountries = countries.sortedByLocaleAware(locale) { CountryTools.getFullName(locale, it.id.countryCode) }
        val sortedCitiesOrStates = citiesOrStates.sortedByLocaleAware { it.name ?: "" }
        val sortedServers = servers.sortedByLocaleAware { it.name ?: "" }

        val selectableCountries = listOf(fastestCountry) + sortedCountries
        val selectableCitiesOrStates = listOf(fastestCityOrState) + sortedCitiesOrStates
        val selectableServers = listOf(fastestServer) + sortedServers
        return if (isP2P) {
            TypeAndLocationScreenState.P2P(availableTypes, country, cityOrState, server, selectableCountries, selectableCitiesOrStates, selectableServers)
        } else {
            TypeAndLocationScreenState.Standard(availableTypes, country, cityOrState, server, selectableCountries, selectableCitiesOrStates, selectableServers)
        }
    }

    private suspend fun secureCore(
        availableTypes: List<ProfileType>,
        locale: Locale,
        requestedExitCountry: CountryId,
        requestedEntryCountry: CountryId?,
    ): TypeAndLocationScreenState.SecureCore {
        val availableExits = adapter.secureCoreExits()
        val fastestExit = TypeAndLocationScreenState.CountryItem(CountryId.fastest, availableExits.any { it.online })
        val exitCountry = availableExits.find { it.id == requestedExitCountry } ?: fastestExit

        val availableEntries = adapter.secureCoreEntries(exitCountry.id)
        val fastestEntry = TypeAndLocationScreenState.CountryItem(CountryId.fastest, availableEntries.any { it.online })
        val entryCountry = if (exitCountry.id.isFastest)
            null
        else
            availableEntries.find { it.id == requestedEntryCountry } ?: fastestEntry

        val sortedExits = availableExits.sortedByLocaleAware(locale) { CountryTools.getFullName(locale, it.id.countryCode) }
        val selectableExits = listOf(fastestExit) + sortedExits

        val sortedEntries = availableEntries.sortedByLocaleAware(locale) { CountryTools.getFullName(locale, it.id.countryCode) }
        val selectableEntries = listOf(fastestEntry) + sortedEntries

        return TypeAndLocationScreenState.SecureCore(
            availableTypes,
            exitCountry = exitCountry,
            entryCountry = entryCountry,
            selectableExitCountries = selectableExits,
            selectableEntryCountries = selectableEntries,
        )
    }

    private suspend fun gateway(
        allTypes: List<ProfileType>,
        locale: Locale,
        requestedGatewayName: String?,
        requestedServerId: String?,
    ): TypeAndLocationScreenState {
        val gateways = adapter.gateways()
        val gateway = gateways.find { it.name == requestedGatewayName } ?:
            gateways.firstOrNull() // Fallback to first gateway if cannot be found
        if (gateway == null)
            // No gateways, fallback to Standard.
            return standardOrP2P(allTypes, locale, CountryId.fastest, null, null, isP2P = false)

        val availableServers = adapter.gatewayServers(gateway.name)
        val fastestServer = TypeAndLocationScreenState.ServerItem.Fastest(availableServers.any { it.online })
        val server = when {
            requestedServerId != null && availableServers.any { it.id == requestedServerId } ->
                adapter.getServerViewModel(requestedServerId)
            else ->
                fastestServer
        }

        val selectableGateways = gateways.sortedByLocaleAware(locale) { it.name }
        val selectableServers = availableServers.sortedByLocaleAware { it.name ?: "" }
        return TypeAndLocationScreenState.Gateway(
            availableTypes = allTypes,
            gateway = gateway,
            server = server,
            selectableGateways = selectableGateways,
            selectableServers =
                listOf(fastestServer) + selectableServers,
        )
    }

    fun setName(name: String) {
        nameScreenState = nameScreenState?.copy(name = name)
    }

    fun setColor(color: ProfileColor) {
        nameScreenState = nameScreenState?.copy(color = color)
    }

    fun setIcon(icon: ProfileIcon) {
        nameScreenState = nameScreenState?.copy(icon = icon)
    }

    fun setType(type: ProfileType) = viewModelScope.launch {
        typeAndLocationScreenSavedState = TypeAndLocationScreenSaveState(type = type)
    }

    fun setCountry(country: TypeAndLocationScreenState.CountryItem) {
        typeAndLocationScreenSavedState = typeAndLocationScreenSavedState?.copy(
            countryId = country.id,
            cityOrState = null,
            serverId = null,
        )
    }

    fun setCityOrState(cityOrState: TypeAndLocationScreenState.CityOrStateItem) {
        typeAndLocationScreenSavedState = typeAndLocationScreenSavedState?.copy(
            cityOrState = cityOrState.id,
            serverId = null,
        )
    }

    fun setServer(server: TypeAndLocationScreenState.ServerItem) {
        typeAndLocationScreenSavedState = typeAndLocationScreenSavedState?.copy(serverId = server.id)
    }

    fun setExitCountrySecureCore(country: TypeAndLocationScreenState.CountryItem) {
        typeAndLocationScreenSavedState = typeAndLocationScreenSavedState?.copy(
            exitCountrySecureCore = country.id,
            entryCountrySecureCore = null,
        )
    }

    fun setEntryCountrySecureCore(country: TypeAndLocationScreenState.CountryItem) {
        typeAndLocationScreenSavedState = typeAndLocationScreenSavedState?.copy(entryCountrySecureCore = country.id)
    }

    fun setGateway(gateway: TypeAndLocationScreenState.GatewayItem) {
        typeAndLocationScreenSavedState = typeAndLocationScreenSavedState?.copy(
            gateway = gateway.name,
            serverId = null,
        )
    }

    fun setNetShield(netShield: Boolean) {
        settingsScreenState = settingsScreenState?.copy(netShield = netShield)
    }

    fun setProtocol(protocol: ProtocolSelection) {
        settingsScreenState = settingsScreenState?.copy(protocol = protocol)
    }

    fun setNatType(natType: NatType) {
        settingsScreenState = settingsScreenState?.copy(natType = natType)
    }

    fun setLanConnections(isEnabled: Boolean) {
        settingsScreenState = settingsScreenState?.copy(lanConnections = isEnabled)
    }
}
