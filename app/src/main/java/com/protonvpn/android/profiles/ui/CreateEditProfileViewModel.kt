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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.profiles.data.Profile
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.profiles.usecases.CreateOrUpdateProfileFromUi
import com.protonvpn.android.profiles.usecases.GetPrivateBrowsingAvailability
import com.protonvpn.android.profiles.usecases.PrivateBrowsingAvailability
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.settings.ui.SettingsViewModel.SettingViewState
import com.protonvpn.android.redesign.settings.ui.customdns.DnsSettingViewModelHelper
import com.protonvpn.android.redesign.settings.ui.customdns.UndoCustomDnsRemove
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.ui.settings.LabeledItem
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.mapState
import com.protonvpn.android.utils.sortedByLocaleAware
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DnsOverride
import com.protonvpn.android.vpn.IsPrivateDnsActiveFlow
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnect
import com.protonvpn.android.vpn.usecases.IsDirectLanConnectionsFeatureFlagEnabled
import com.protonvpn.android.vpn.usecases.TransientMustHaves
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.proton.core.presentation.savedstate.state
import java.util.Locale
import javax.inject.Inject
import me.proton.core.presentation.R as CoreR

private fun defaultSettingScreenState(
    isAutoOpenNew: Boolean,
    lanDirectConnectionsFeatureFlagEnabled: Boolean,
    isPrivateDnsEnabled: Boolean,
    isPrivateBrowsingAvailable: Boolean,
) = SettingsScreenState(
    protocol = ProtocolSelection.SMART,
    netShield = true,
    natType = NatType.Strict,
    lanConnections = true,
    lanConnectionsAllowDirect = if (lanDirectConnectionsFeatureFlagEnabled) false else null,
    autoOpen = ProfileAutoOpen.None,
    customDnsSettings = CustomDnsSettings(false),
    isAutoOpenNew = isAutoOpenNew,
    isPrivateDnsActive = isPrivateDnsEnabled,
    showPrivateBrowsing = isPrivateBrowsingAvailable,
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

sealed interface TypeAndLocationScreenState {

    val availableTypes: List<ProfileType>

    sealed interface StandardWithFeatures : TypeAndLocationScreenState {
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
    ) : StandardWithFeatures {
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
    ) : StandardWithFeatures {
        override val features: Set<ServerFeature> get() = setOf(ServerFeature.P2P)
    }

    data class SecureCore(
        override val availableTypes: List<ProfileType>,
        val exitCountry: CountryItem,
        val entryCountry: CountryItem? = null,
        val selectableExitCountries: List<CountryItem>,
        val selectableEntryCountries: List<CountryItem>,
    ) : TypeAndLocationScreenState

    data class Gateway(
        override val availableTypes: List<ProfileType>,
        val gateway: GatewayItem,
        val server: ServerItem,
        val selectableGateways: List<GatewayItem>,
        val selectableServers: List<ServerItem>,
    ) : TypeAndLocationScreenState

    data class CountryItem(val id: CountryId, val online: Boolean)
    data class GatewayItem(val name: String, val online: Boolean)

    data class ServerItem(val name: String?, val id: String?, val flagCountryId: CountryId?, val online: Boolean) {
        val isFastest get() = id == null
        companion object {
            fun fastest(online: Boolean) = ServerItem(null, null, null, online)
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
    val netShield: Boolean?,
    val isPrivateDnsActive: Boolean,
    val protocol: ProtocolSelection,
    val natType: NatType,
    val lanConnections: Boolean,
    val lanConnectionsAllowDirect: Boolean?,
    val autoOpen: ProfileAutoOpen,
    val isAutoOpenNew: Boolean,
    val customDnsSettings: CustomDnsSettings?,
    val showPrivateBrowsing: Boolean,
) : Parcelable {
    fun toSettingsOverrides() = SettingsOverrides(
        protocolData = protocol.toData(),
        netShield = if (netShield == true) NetShieldProtocol.ENABLED_EXTENDED else NetShieldProtocol.DISABLED,
        randomizedNat = natType.toRandomizedNat(),
        customDns = customDnsSettings,
        lanConnections = lanConnections,
        lanConnectionsAllowDirect = lanConnectionsAllowDirect,
    )

    val dnsOverride get() = when {
        isPrivateDnsActive -> DnsOverride.SystemPrivateDns
        customDnsSettings?.effectiveEnabled == true -> DnsOverride.CustomDns
        else -> DnsOverride.None
    }
}

private const val NAME_SCREEN_STATE_KEY = "nameScreenState"
private const val TYPE_AND_LOCATION_SCREEN_STATE_KEY = "typeAndLocationScreenState"
private const val SETTINGS_SCREEN_STATE_KEY = "settingsScreenState"

@HiltViewModel
class CreateEditProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mainScope: CoroutineScope,
    @ApplicationContext private val appContext: Context,
    private val profilesDao: ProfilesDao,
    private val createOrUpdateProfile: CreateOrUpdateProfileFromUi,
    private val adapter: ProfilesServerDataAdapter,
    private val vpnConnect: VpnConnect,
    private val vpnBackgroundUiDelegate: VpnBackgroundUiDelegate,
    private val shouldAskForReconnection: ShouldAskForProfileReconnection,
    private val uiStateStorage: UiStateStorage,
    private val isPrivateDnsActiveFlow: IsPrivateDnsActiveFlow,
    private val isDirectLanConnectionsFeatureFlagEnabled: IsDirectLanConnectionsFeatureFlagEnabled,
    private val transientMustHaves: TransientMustHaves,
    private val autoOpenAppInfoHelper: AutoOpenAppInfoHelper,
    private val getPrivateBrowsingAvailability: GetPrivateBrowsingAvailability,
) : ViewModel() {

    private var editedProfileId: Long? = null
    private var duplicatedProfileId: Long? = null
    private var editedProfileCreatedAt by savedStateHandle.state<Long?>(null)

    private var nameScreenState by savedStateHandle.state<NameScreenState?>(null, NAME_SCREEN_STATE_KEY)
    private var typeAndLocationScreenSavedState by savedStateHandle.state<TypeAndLocationScreenSaveState?>(null, TYPE_AND_LOCATION_SCREEN_STATE_KEY)
    private var settingsScreenState by savedStateHandle.state<SettingsScreenState?>(null, SETTINGS_SCREEN_STATE_KEY)

    val nameScreenStateFlow = savedStateHandle.getStateFlow<NameScreenState?>(NAME_SCREEN_STATE_KEY, null)
    private val typeAndLocationScreenSavedStateFlow = savedStateHandle.getStateFlow<TypeAndLocationScreenSaveState?>(TYPE_AND_LOCATION_SCREEN_STATE_KEY, null)
    val settingsScreenStateFlow = savedStateHandle.getStateFlow<SettingsScreenState?>(SETTINGS_SCREEN_STATE_KEY, null)

    val localeFlow = MutableStateFlow<Locale?>(null)
    private val availableTypesFlow = adapter.serverTypesFlow.map { types ->
        ProfileType.entries.filter {
            when(it) {
                ProfileType.Standard -> types.contains(ProfilesServerDataAdapter.ServerType.DirectCountry)
                ProfileType.SecureCore -> types.contains(ProfilesServerDataAdapter.ServerType.SecureCore)
                ProfileType.P2P -> types.contains(ProfilesServerDataAdapter.ServerType.P2P)
                ProfileType.Gateway -> types.contains(ProfilesServerDataAdapter.ServerType.Gateway)
            }
        }
    }

    val showReconnectDialogFlow = MutableStateFlow(false)

    val customDnsHelper = DnsSettingViewModelHelper(
        viewModelScope,
        dnsViewState =
            combine(
                settingsScreenStateFlow,
                isPrivateDnsActiveFlow,
            ) { settingsState, isPrivateDnsActive ->
                settingsState?.customDnsSettings?.let {
                    SettingViewState.CustomDns(
                        enabled = it.toggleEnabled,
                        customDns = it.rawDnsList,
                        overrideProfilePrimaryLabel = null,
                        isFreeUser = false,
                        isPrivateDnsActive = isPrivateDnsActive
                    )
                }
            }
    )

    val lanValuesFlow = settingsScreenStateFlow.mapState { profileSettings ->
        profileSettings?.let {
            SettingViewState.LanConnections(
                profileSettings.lanConnections,
                profileSettings.lanConnectionsAllowDirect,
                isFreeUser = false,
                overrideProfilePrimaryLabel = null
            )
        }
    }

    init {
        isPrivateDnsActiveFlow.onEach { active ->
            settingsScreenState = settingsScreenState?.copy(isPrivateDnsActive = active)
        }.launchIn(viewModelScope)
    }

    val isPrivateDnsActive get() = isPrivateDnsActiveFlow.value

    val typeAndLocationScreenStateFlow : Flow<TypeAndLocationScreenState> = combine(
        typeAndLocationScreenSavedStateFlow.filterNotNull(),
        availableTypesFlow,
        localeFlow.filterNotNull(),
        adapter.serverListVersion
    ) { savedState, availableTypes, locale, _ ->
        when (savedState.type) {
            ProfileType.Standard,
            ProfileType.P2P -> getStandardOrP2PScreenState(
                availableTypes,
                locale,
                savedState.countryId ?: CountryId.fastest,
                savedState.cityOrState,
                savedState.serverId,
                savedState.type == ProfileType.P2P,
            )
            ProfileType.SecureCore -> getSecureCoreScreenState(
                availableTypes,
                locale,
                savedState.exitCountrySecureCore ?: CountryId.fastest,
                savedState.entryCountrySecureCore,
            )
            ProfileType.Gateway -> getGatewayScreenState(
                availableTypes,
                locale,
                savedState.gateway,
                savedState.serverId,
            )
        }
    }

    fun setEditedProfileId(profileId: Long?, duplicateProfile: Boolean) {
        if (duplicateProfile) {
            duplicatedProfileId = profileId
        } else {
            editedProfileId = profileId
        }
        setInitialState(profileId, duplicateProfile)
    }

    private fun setInitialState(profileId: Long?, isDuplicate: Boolean) {
        viewModelScope.launch {
            isAutoOpenNew.emit(!uiStateStorage.state.first().hasSeenProfileAutoOpen)
            val hasRestoreState = nameScreenState != null
            if (!hasRestoreState) {
                if (profileId != null) {
                    profilesDao.getProfileById(profileId)?.let {
                        initializeFromProfile(it, isDuplicate)
                    }
                } else {
                    initializeDefault()
                }
            }
        }
    }

    private var isAutoOpenNew = MutableSharedFlow<Boolean>(replay = 1)
    
    suspend fun save(routedFromSettings: Boolean = false) : Deferred<Profile?> =
        createOrUpdateProfile(
            profileId = editedProfileId ?: duplicatedProfileId,
            createdAt = editedProfileCreatedAt,
            createDuplicate = duplicatedProfileId != null,
            nameScreen = requireNotNull(nameScreenState),
            typeAndLocationScreen = typeAndLocationScreenStateFlow.first(),
            settingsScreen = requireNotNull(settingsScreenState),
            routedFromSettings = routedFromSettings
        )

    fun dismissReconnectDialog() {
        showReconnectDialogFlow.value = false
    }

    fun saveAndReconnect(routedFromSettings: Boolean) {
        mainScope.launch {
            val savedProfile = save(routedFromSettings).await()
            savedProfile?.let {
                vpnConnect(
                    vpnBackgroundUiDelegate,
                    it.connectIntent,
                    ConnectTrigger.Profile
                )
            }
            dismissReconnectDialog()
        }
    }

    fun saveOrShowReconnectDialog(routedFromSettings: Boolean, onDismiss: () -> Unit) {
        viewModelScope.launch {
            val askForReconnection = shouldAskForReconnection(
                editedProfileId,
                requireNotNull(nameScreenState),
                typeAndLocationScreenStateFlow.first(),
                requireNotNull(settingsScreenState)
            )
            if (askForReconnection) {
                showReconnectDialogFlow.value = true
            } else {
                save(routedFromSettings)
                onDismiss()
            }
        }
    }

    private suspend fun initializeDefault() {
        nameScreenState = NameScreenState(
            "",
            ProfileColor.Color1,
            ProfileIcon.Icon1,
        )
        val availableTypes = availableTypesFlow.first()
        typeAndLocationScreenSavedState = when {
            availableTypes.contains(ProfileType.Standard) -> standardTypeDefault()
            availableTypes.contains(ProfileType.SecureCore) -> secureCoreTypeDefault()
            availableTypes.contains(ProfileType.Gateway) -> gatewayTypeDefault()
            else -> throw IllegalStateException("Neither gateways nor country servers available")
        }
        settingsScreenState = defaultSettingScreenState(
            isAutoOpenNew.first(),
            lanDirectConnectionsFeatureFlagEnabled = isDirectLanConnectionsFeatureFlagEnabled(),
            isPrivateDnsEnabled = isPrivateDnsActive,
            isPrivateBrowsingAvailable = getPrivateBrowsingAvailability() != PrivateBrowsingAvailability.NotAvailable,
        )
    }

    @SuppressLint("StringFormatInvalid")
    private suspend fun initializeFromProfile(profile: Profile, isDuplicate: Boolean) {
        editedProfileCreatedAt = profile.info.createdAt
        val name = when {
            isDuplicate -> appContext.resources.getString(R.string.create_profile_copy_name, profile.info.name)
            else -> profile.info.name
        }
        nameScreenState = NameScreenState(
            name,
            profile.info.color,
            profile.info.icon,
        )
        val intentForAvailableServers = adapter.updateIntentForExistingServers(profile.connectIntent)
        typeAndLocationScreenSavedState = getTypeAndLocationScreenStateFromIntent(intentForAvailableServers)
        settingsScreenState = getSettingsScreenState(profile)
    }

    private suspend fun getSettingsScreenState(profile: Profile): SettingsScreenState {
        val intent = profile.connectIntent
        val isPrivateBrowsingAvailable = getPrivateBrowsingAvailability() != PrivateBrowsingAvailability.NotAvailable
        val directLanConnectionsFeatureFlagEnabled = isDirectLanConnectionsFeatureFlagEnabled()
        val defaultSettingScreenState = defaultSettingScreenState(
            isAutoOpenNew = isAutoOpenNew.first(),
            lanDirectConnectionsFeatureFlagEnabled = directLanConnectionsFeatureFlagEnabled,
            isPrivateDnsEnabled = isPrivateDnsActive,
            isPrivateBrowsingAvailable = isPrivateBrowsingAvailable,
        )

        val lanConnectionsAllowDirect = if (directLanConnectionsFeatureFlagEnabled) {
            intent.settingsOverrides?.lanConnectionsAllowDirect
                ?: defaultSettingScreenState.lanConnectionsAllowDirect
        } else null

        val netShield = intent.settingsOverrides
            ?.netShield
            ?.let { it != NetShieldProtocol.DISABLED }
            ?: defaultSettingScreenState.netShield

        // Show private browsing switch for auto-open if it's supported or when it was already
        // enabled for given profile
        val showPrivateBrowsing = isPrivateBrowsingAvailable ||
            (profile.autoOpen is ProfileAutoOpen.Url && profile.autoOpen.openInPrivateMode)
        return SettingsScreenState(
            netShield = netShield,
            isPrivateDnsActive = isPrivateDnsActive,
            protocol = intent.settingsOverrides?.protocolData?.toProtocolSelection() ?: defaultSettingScreenState.protocol,
            natType = intent.settingsOverrides?.randomizedNat?.let { NatType.fromRandomizedNat(it) } ?: defaultSettingScreenState.natType,
            lanConnections = intent.settingsOverrides?.lanConnections ?: defaultSettingScreenState.lanConnections,
            lanConnectionsAllowDirect = lanConnectionsAllowDirect,
            autoOpen = profile.autoOpen,
            customDnsSettings = intent.settingsOverrides?.customDns ?: defaultSettingScreenState.customDnsSettings,
            isAutoOpenNew = isAutoOpenNew.first(),
            showPrivateBrowsing = showPrivateBrowsing,
        )
    }

    private suspend fun getTypeAndLocationScreenStateFromIntent(intent: ConnectIntent): TypeAndLocationScreenSaveState {
        return when (intent) {
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
    }

    private fun standardTypeDefault() = TypeAndLocationScreenSaveState(
        type = ProfileType.Standard,
        countryId = CountryId.fastest
    )

    private fun secureCoreTypeDefault() = TypeAndLocationScreenSaveState(
        type = ProfileType.SecureCore,
        countryId = CountryId.fastest,
    )

    private suspend  fun gatewayTypeDefault() = TypeAndLocationScreenSaveState(
        type = ProfileType.Gateway,
        gateway = adapter.gateways().first().name
    )

    private fun ConnectIntentData.cityStateId() = when {
        region != null -> CityStateId(region, true)
        city != null -> CityStateId(city, false)
        else -> null
    }

    private suspend fun getCountryInfo(
        locale: Locale,
        serverFeature: ServerFeature?,
        requestedCountryId: CountryId,
    ): Pair<TypeAndLocationScreenState.CountryItem, List<TypeAndLocationScreenState.CountryItem>> {
        val countries = adapter.countries(serverFeature)
        val haveOnlineCountry = countries.any { it.online }
        val fastestCountry = TypeAndLocationScreenState.CountryItem(CountryId.fastest, haveOnlineCountry)
        val fastestCountryExcludingMyCountry = TypeAndLocationScreenState.CountryItem(CountryId.fastestExcludingMyCountry, haveOnlineCountry)

        // Fallback to fastest if country not longer available
        val selectedCountry = when (requestedCountryId) {
            CountryId.fastest -> fastestCountry
            CountryId.fastestExcludingMyCountry -> fastestCountryExcludingMyCountry
            else  -> countries.find { it.id == requestedCountryId } ?: fastestCountry
        }

        val countryNames = countries.associateWith { country -> CountryTools.getFullName(locale, country.id.countryCode) }
        val sortedCountries = countries.sortedByLocaleAware(locale) { countryNames[it] ?: "" }
        val selectableCountries = listOf(fastestCountry, fastestCountryExcludingMyCountry) + sortedCountries

        return selectedCountry to selectableCountries
    }

    private suspend fun getCityOrStateInfo(
        locale: Locale,
        selectedCountry: TypeAndLocationScreenState.CountryItem,
        serverFeature: ServerFeature?,
        requestedCityOrState: CityStateId?,
    ): Pair<TypeAndLocationScreenState.CityOrStateItem?, List<TypeAndLocationScreenState.CityOrStateItem>> {
        val citiesOrStates = adapter.citiesOrStates(selectedCountry.id, secureCore = false, serverFeature)
        val hasOnlineCityOrState = citiesOrStates.any { it.online }
        val fastestCityOrState = TypeAndLocationScreenState.CityOrStateItem(
            null,
            if (citiesOrStates.any { it.id.isState }) CityStateId.fastestState
            else CityStateId.fastestCity,
            hasOnlineCityOrState
        )
        val selectedCityOrState = if (selectedCountry.id.isFastest) {
            // Don't show city/state selection for fastest country
            null
        } else {
            citiesOrStates.find { it.id == requestedCityOrState } ?: fastestCityOrState
        }

        val sortedCitiesOrStates = citiesOrStates.sortedByLocaleAware(locale) { it.name ?: "" }
        val selectableCitiesOrStates = listOf(fastestCityOrState) + sortedCitiesOrStates
        return selectedCityOrState to selectableCitiesOrStates
    }

    private suspend fun getServerInfo(
        locale: Locale,
        selectedCountry: TypeAndLocationScreenState.CountryItem,
        selectedCityOrState: TypeAndLocationScreenState.CityOrStateItem?,
        serverFeature: ServerFeature?,
        requestedServerId: String?,
    ): Pair<TypeAndLocationScreenState.ServerItem?, List<TypeAndLocationScreenState.ServerItem>> {
        val servers = selectedCityOrState?.let { adapter.servers(selectedCountry.id, it.id, secureCore = false, serverFeature) } ?: emptyList()
        val fastestServer = TypeAndLocationScreenState.ServerItem.fastest(servers.any { it.online })
        val selectedServer = when {
            selectedCityOrState == null || selectedCityOrState.id.isFastest -> null
            requestedServerId != null && servers.any { it.id == requestedServerId } -> adapter.getServerViewModel(requestedServerId)
            else -> fastestServer
        }

        val sortedServers = servers.sortedByLocaleAware(locale) { it.name ?: "" }
        val selectableServers = listOf(fastestServer) + sortedServers
        return selectedServer to selectableServers
    }

    private suspend fun getStandardOrP2PScreenState(
        availableTypes: List<ProfileType>,
        locale: Locale,
        requestedCountryId: CountryId,
        requestedCityOrState: CityStateId?,
        requestedServerId: String?,
        isP2P: Boolean,
    ) : TypeAndLocationScreenState {
        val serverFeature = if (isP2P) ServerFeature.P2P else null
        val (selectedCountry, selectableCountries) = getCountryInfo(locale, serverFeature, requestedCountryId)
        val (selectedCityOrState, selectableCitiesOrStates) = getCityOrStateInfo(locale, selectedCountry, serverFeature, requestedCityOrState)
        val (selectedServer, selectableServers) = getServerInfo(locale, selectedCountry, selectedCityOrState, serverFeature, requestedServerId)
        return if (isP2P) {
            TypeAndLocationScreenState.P2P(availableTypes, selectedCountry, selectedCityOrState, selectedServer, selectableCountries, selectableCitiesOrStates, selectableServers)
        } else {
            TypeAndLocationScreenState.Standard(availableTypes, selectedCountry, selectedCityOrState, selectedServer, selectableCountries, selectableCitiesOrStates, selectableServers)
        }
    }

    private suspend fun getSecureCoreScreenState(
        availableTypes: List<ProfileType>,
        locale: Locale,
        requestedExitCountry: CountryId,
        requestedEntryCountry: CountryId?,
    ): TypeAndLocationScreenState.SecureCore {
        val availableExits = adapter.secureCoreExits()
        val fastestExit = TypeAndLocationScreenState.CountryItem(CountryId.fastest, availableExits.any { it.online })
        val fastestExitExcludingMyCountry = TypeAndLocationScreenState.CountryItem(CountryId.fastestExcludingMyCountry, availableExits.any { it.online })
        val exitCountry = when (requestedExitCountry) {
            CountryId.fastest -> fastestExit
            CountryId.fastestExcludingMyCountry -> fastestExitExcludingMyCountry
            else -> availableExits.find { it.id == requestedExitCountry } ?: fastestExit
        }

        val availableEntries = adapter.secureCoreEntries(exitCountry.id)
        val fastestEntry = TypeAndLocationScreenState.CountryItem(CountryId.fastest, availableEntries.any { it.online })
        val entryCountry = if (exitCountry.id.isFastest)
            null
        else
            availableEntries.find { it.id == requestedEntryCountry } ?: fastestEntry

        val sortedExits = availableExits.sortedByLocaleAware(locale) { CountryTools.getFullName(locale, it.id.countryCode) }
        val selectableExits = listOf(fastestExit, fastestExitExcludingMyCountry) + sortedExits

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

    private suspend fun getGatewayScreenState(
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
            return getStandardOrP2PScreenState(allTypes, locale, CountryId.fastest, null, null, isP2P = false)

        val availableServers = adapter.gatewayServers(gateway.name)
        val fastestServer = TypeAndLocationScreenState.ServerItem.fastest(availableServers.any { it.online })
        val server = when {
            requestedServerId != null && availableServers.any { it.id == requestedServerId } ->
                adapter.getServerViewModel(requestedServerId)
            else ->
                fastestServer
        }

        val selectableGateways = gateways.sortedByLocaleAware(locale) { it.name }
        val selectableServers = availableServers.sortedByLocaleAware(locale) { it.name ?: "" }
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
        if (server.id != null)
            transientMustHaves.add(server.id)
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

    fun toggleCustomDns() {
        val currentDns = settingsScreenState?.customDnsSettings
        currentDns?.let {
            val updatedSettings = CustomDnsSettings(
                toggleEnabled = !it.toggleEnabled,
                rawDnsList = it.rawDnsList
            )
            settingsScreenState = settingsScreenState?.copy(customDnsSettings = updatedSettings)
        }
    }

    fun setCustomDns(customDns: CustomDnsSettings) {
        settingsScreenState = settingsScreenState?.copy(customDnsSettings = customDns)
    }

    fun setNatType(natType: NatType) {
        settingsScreenState = settingsScreenState?.copy(natType = natType)
    }

    fun toggleLanConnections() {
        settingsScreenState = settingsScreenState?.let { state -> state.copy(lanConnections = !state.lanConnections) }
    }

    fun toggleLanAllowDirectConnections() {
        settingsScreenState = settingsScreenState?.let { state ->
            if (state.lanConnectionsAllowDirect != null) {
                state.copy(lanConnectionsAllowDirect = !state.lanConnectionsAllowDirect)
            } else {
                state
            }
        }
    }

    fun setAutoOpen(autoOpen: ProfileAutoOpen) {
        settingsScreenState = settingsScreenState?.copy(autoOpen = autoOpen)
    }

    fun settingsScreenShown() {
        viewModelScope.launch {
            uiStateStorage.update { it.copy(hasSeenProfileAutoOpen = true) }
        }
    }

    private inner class ProfileUndoCustomDnsRemove(
        removedItem: String,
        position: Int
    ) : UndoCustomDnsRemove(removedItem, position) {
        override fun invoke() {
            val currentSettings = settingsScreenStateFlow.value?.customDnsSettings ?: return
            val updatedList = currentSettings.rawDnsList.toMutableList()
            val position = position.coerceIn(0, updatedList.size)
            updatedList.add(position, removedItem)

            val updatedSettings = CustomDnsSettings(
                toggleEnabled = true,
                rawDnsList = updatedList
            )
            setCustomDns(updatedSettings)
        }
    }

    fun updateDnsList(newList: List<String>) {
        val currentSettings = settingsScreenStateFlow.value?.customDnsSettings ?: return
        val shouldBeEnabled = currentSettings.toggleEnabled || newList.isNotEmpty()
        val updatedSettings = CustomDnsSettings(toggleEnabled = shouldBeEnabled, rawDnsList = newList)
        setCustomDns(updatedSettings)
    }

    fun removeDnsAddress(dns: String) : UndoCustomDnsRemove? {
        val currentList = settingsScreenStateFlow.value?.customDnsSettings?.rawDnsList ?: return null
        val updatedList = currentList.filter { it != dns }
        updateDnsList(updatedList)
        return ProfileUndoCustomDnsRemove(removedItem = dns, position = currentList.indexOf(dns))
    }

    fun validateAndAddDnsAddress(dns: String) {
        val state = settingsScreenState ?: return
        val customDns = state.customDnsSettings ?: return

        val currentList = customDns.rawDnsList
        val netShieldConflict = state.netShield == true && !customDns.toggleEnabled
        customDnsHelper.validateAndAddDnsAddress(dns, netShieldConflict) {
            setCustomDns(CustomDnsSettings(toggleEnabled = true, rawDnsList = currentList + dns.trim()))
        }
    }

    suspend fun getAutoOpenAppInfo(packageName: String): LabeledItem? =
        autoOpenAppInfoHelper.getAppInfo(packageName)

    suspend fun getAutoOpenAllAppsInfo(iconSizePx: Int): List<LabeledItem> =
        autoOpenAppInfoHelper.getLaunchableAppsInfo(iconSizePx)
}
