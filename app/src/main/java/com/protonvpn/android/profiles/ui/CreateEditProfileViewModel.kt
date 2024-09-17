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
import com.protonvpn.android.redesign.countries.Translator
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.recents.data.toCountryId
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.settings.ui.NatType.Moderate
import com.protonvpn.android.redesign.settings.ui.NatType.Strict
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.proton.core.presentation.savedstate.state
import javax.inject.Inject
import me.proton.core.presentation.R as CoreR

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

@Parcelize
sealed class TypeAndLocationScreenState : Parcelable {

    interface StandardWithFeatures {
        val countryId: CountryId
        val cityOrState: CityOrState?
        val server: Server?
        val features: Set<ServerFeature>
    }

    @Parcelize
    data class Standard(
        override val countryId: CountryId,
        override val cityOrState: CityOrState?,
        override val server: Server?,
    ) : TypeAndLocationScreenState(), StandardWithFeatures {
        override val features: Set<ServerFeature> get() = emptySet()
    }

    @Parcelize
    data class SecureCore(
        val exitCountry: CountryId,
        val entryCountry: CountryId? = null,
    ) : TypeAndLocationScreenState()

    @Parcelize
    data class P2P(
        override val countryId: CountryId,
        override val cityOrState: CityOrState?,
        override val server: Server?,
    ) : TypeAndLocationScreenState(), StandardWithFeatures {
        override val features: Set<ServerFeature> get() = setOf(ServerFeature.P2P)
    }

    @Parcelize
    data class Gateway(
        val gateway: String,
        val server: Server? = null,
    ) : TypeAndLocationScreenState()

    @Parcelize
    data class Server(val name: String?, val id: String?) : Parcelable {
        val isFastest get() = id == null
    }

    @Parcelize
    data class CityOrState(val name: String?, val id: CityStateId) : Parcelable

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
    private val serverManager: ServerManager2,
    private val translator: Translator,
    @WallClock private val wallClock: () -> Long,
) : ViewModel() {

    private var editedProfileId: Long? = null

    private var nameScreenState by savedStateHandle.state<NameScreenState?>(null, "nameScreenState")
    private var typeAndLocationScreenState by savedStateHandle.state<TypeAndLocationScreenState?>(null, "typeAndLocationScreenState")
    private var settingsScreenState by savedStateHandle.state<SettingsScreenState?>(null, "settingsScreenState")

    val nameScreenStateFlow = savedStateHandle.getStateFlow<NameScreenState?>("nameScreenState", null)
    val typeAndLocationScreenStateFlow = savedStateHandle.getStateFlow<TypeAndLocationScreenState?>("typeAndLocationScreenState", null)
    val settingsScreenStateFlow = savedStateHandle.getStateFlow<SettingsScreenState?>("settingsScreenState", null)

    fun setEditedProfileId(profileId: Long?) {
        editedProfileId = profileId
        if (nameScreenState == null && profileId != null) {
            viewModelScope.launch {
                profilesDao.getProfileById(profileId)?.let {
                    initializeFromProfile(it)
                }
            }
        } else {
            initializeDefault()
        }
    }

    fun save() {
        viewModelScope.launch {
            currentUser.vpnUser()?.userId?.let { userId ->
                profilesDao.upsert(getProfile().toProfileEntity(userId, wallClock()))
            }
        }
    }

    private fun getProfile(): Profile {
        val profileId = editedProfileId
        val nameScreen = requireNotNull(nameScreenState)
        val typeAndLocationScreen = requireNotNull(typeAndLocationScreenState)
        val settingsScreen = requireNotNull(settingsScreenState)
        val overrides = settingsScreen.toSettingsOverrides()
        return Profile(
            ProfileInfo(
                profileId ?: 0L,
                nameScreen.name,
                nameScreen.color,
                nameScreen.icon,
                (typeAndLocationScreen as? TypeAndLocationScreenState.Gateway)?.gateway,
            ),
            when (typeAndLocationScreen) {
                is TypeAndLocationScreenState.P2P,
                is TypeAndLocationScreenState.Standard -> {
                    typeAndLocationScreen as TypeAndLocationScreenState.StandardWithFeatures
                    val country = typeAndLocationScreen.countryId
                    val serverId = typeAndLocationScreen.server?.id
                    val cityOrState = typeAndLocationScreen.cityOrState
                    val features = typeAndLocationScreen.features
                    if (serverId != null) {
                        ConnectIntent.Server(
                            serverId = serverId,
                            exitCountry = country,
                            features = features,
                            profileId = profileId,
                            settingsOverrides = overrides,
                        )
                    } else if (cityOrState?.id?.isState == true) {
                        ConnectIntent.FastestInState(
                            country = country,
                            stateEn = cityOrState.id.name,
                            features = features,
                            profileId = profileId,
                            settingsOverrides = overrides,
                        )
                    } else if (cityOrState?.id?.isState == false) {
                        ConnectIntent.FastestInCity(
                            country = country,
                            cityEn = cityOrState.id.name,
                            features = features,
                            profileId = profileId,
                            settingsOverrides = overrides,
                        )
                    } else {
                        ConnectIntent.FastestInCountry(
                            country = country,
                            features = features,
                            profileId = profileId,
                            settingsOverrides = overrides,
                        )
                    }
                }
                is TypeAndLocationScreenState.SecureCore -> ConnectIntent.SecureCore(
                    exitCountry = typeAndLocationScreen.exitCountry,
                    entryCountry = typeAndLocationScreen.entryCountry ?: CountryId.fastest,
                    profileId = profileId,
                    settingsOverrides = overrides,
                )
                is TypeAndLocationScreenState.Gateway -> ConnectIntent.Gateway(
                    gatewayName = typeAndLocationScreen.gateway,
                    profileId = profileId,
                    serverId = typeAndLocationScreen.server?.id,
                    settingsOverrides = overrides,
                )
            }
        )
    }

    private fun initializeDefault() {
        nameScreenState = NameScreenState(
            "",
            ProfileColor.Color1,
            ProfileIcon.Icon1,
        )
        typeAndLocationScreenState = TypeAndLocationScreenState.Standard(
            countryId = CountryId.fastest,
            cityOrState = null,
            server = null,
        )
        settingsScreenState = SettingsScreenState(
            netShield = true,
            protocol = ProtocolSelection.SMART,
            natType = Strict,
            lanConnections = true,
        )
    }

    private suspend fun initializeFromProfile(profile: Profile) {
        nameScreenState = NameScreenState(
            profile.info.name,
            profile.info.color,
            profile.info.icon,
        )
        val intent = profile.connectIntent
        val intentData = intent.toData()
        val gatewayName = profile.info.gatewayName
        val serverInfo = getServerInfo(intentData.serverId)
        val country = intentData.exitCountry.toCountryId()
        val settingsOverride = profile.connectIntent.settingsOverrides
        typeAndLocationScreenState = when {
            gatewayName != null -> {
                TypeAndLocationScreenState.Gateway(
                    gateway = gatewayName,
                    server = serverInfo,
                )
            }
            intent is ConnectIntent.SecureCore -> TypeAndLocationScreenState.SecureCore(
                exitCountry = intentData.exitCountry.toCountryId(),
                entryCountry = intentData.entryCountry.toCountryId(),
            )
            else -> {
                val cityOrState = getCityStateInfo(intentData)
                if (intent.features.contains(ServerFeature.P2P)) {
                    TypeAndLocationScreenState.P2P(country, cityOrState, serverInfo)
                } else {
                    TypeAndLocationScreenState.Standard(country, cityOrState, serverInfo)
                }
            }
        }
        settingsOverride?.let {
            settingsScreenState = SettingsScreenState(
                netShield = it.netShield != NetShieldProtocol.DISABLED,
                lanConnections = it.lanConnections ?: true,
                natType = if (it.randomizedNat == true) Strict else Moderate,
                protocol = settingsOverride.protocol ?: ProtocolSelection.SMART
            )
        }
    }

    private suspend fun getServerInfo(serverId: String?) =
        serverId?.let { serverManager.getServerById(it) }.let {
            // Will be null/null (fastest) if serverId is not found.
            TypeAndLocationScreenState.Server(
                it?.serverName,
                it?.serverId,
            )
        }

    private fun getCityStateInfo(intentData: ConnectIntentData) =
        when {
            intentData.region != null -> TypeAndLocationScreenState.CityOrState(
                translator.getState(intentData.region),
                CityStateId(intentData.region, true),
            )
            intentData.city != null -> TypeAndLocationScreenState.CityOrState(
                translator.getCity(intentData.city),
                CityStateId(intentData.city, false),
            )
            else -> null
        }

    fun setName(name: String) {
        nameScreenState = nameScreenState?.copy(name = name)
    }

    fun setIcon(icon: ProfileIcon) {
        nameScreenState = nameScreenState?.copy(icon = icon)
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

    fun setColor(color: ProfileColor) {
        nameScreenState = nameScreenState?.copy(color = color)
    }

    fun setType(type: ProfileType) {
        typeAndLocationScreenState = when (type) {
            ProfileType.Standard -> TypeAndLocationScreenState.Standard(
                countryId = CountryId.fastest,
                cityOrState = null,
                server = null,
            )
            ProfileType.SecureCore -> TypeAndLocationScreenState.SecureCore(
                exitCountry = CountryId.fastest,
                entryCountry = null,
            )
            ProfileType.P2P -> TypeAndLocationScreenState.P2P(
                countryId = CountryId.fastest,
                cityOrState = null,
                server = null,
            )
            ProfileType.Gateway -> TypeAndLocationScreenState.Gateway(
                gateway = "", //TODO: first gateway
                server = null,
            )
        }
    }
}
