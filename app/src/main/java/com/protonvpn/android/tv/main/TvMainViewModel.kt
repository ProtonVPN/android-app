/*
 * Copyright (c) 2020 Proton AG
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
package com.protonvpn.android.tv.main

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached
import com.protonvpn.android.tv.models.Card
import com.protonvpn.android.tv.models.ConnectIntentCard
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.tv.models.DrawableImage
import com.protonvpn.android.tv.models.QuickConnectCard
import com.protonvpn.android.tv.models.Title
import com.protonvpn.android.tv.settings.IsTvAutoConnectFeatureFlagEnabled
import com.protonvpn.android.tv.settings.IsTvCustomDnsSettingFeatureFlagEnabled
import com.protonvpn.android.tv.settings.IsTvNetShieldSettingFeatureFlagEnabled
import com.protonvpn.android.tv.usecases.GetCountryCard
import com.protonvpn.android.tv.usecases.TvUiConnectDisconnectHelper
import com.protonvpn.android.tv.vpn.createIntentForDefaultProfile
import com.protonvpn.android.tv.vpn.getConnectCountry
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.AndroidUtils.toInt
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.autoconnect.AutoConnectVpn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import me.proton.core.presentation.R as CoreR

@HiltViewModel
class TvMainViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val profileManager: ProfileManager,
    private val mainScope: CoroutineScope,
    serverListUpdater: ServerListUpdater,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    vpnStateMonitor: VpnStateMonitor,
    private val connectHelper: TvUiConnectDisconnectHelper,
    private val recentsManager: RecentsManager,
    private val featureFlags: GetFeatureFlags,
    private val getCountryCard: GetCountryCard,
    private val currentUser: CurrentUser,
    private val logoutUseCase: Logout,
    private val effectiveCurrentUserSettingsCached: EffectiveCurrentUserSettingsCached,
    val purchaseEnabled: CachedPurchaseEnabled,
    isTvAutoConnectFeatureFlagEnabled: IsTvAutoConnectFeatureFlagEnabled,
    isTvNetShieldSettingFeatureFlagEnabled: IsTvNetShieldSettingFeatureFlagEnabled,
    isTvCustomDnsSettingFeatureFlagEnabled: IsTvCustomDnsSettingFeatureFlagEnabled,
    autoConnectVpn: AutoConnectVpn,
) : ViewModel() {

    data class VpnViewState(val vpnStatus: VpnStateMonitor.Status, val ipToDisplay: String?)

    val displayStreamingIcons get() = featureFlags.value.streamingServicesLogos
    val selectedCountryFlag = MutableLiveData<String?>()
    val connectedCountryFlag = MutableLiveData<String>()
    val mapRegion = MutableLiveData<MapRegion>()

    val vpnViewState: Flow<VpnViewState> = combine(
        vpnStatusProviderUI.status,
        serverListUpdater.ipAddress,
        vpnStateMonitor.exitIp
    ) { vpnStatus, myIp, exitIp ->
        val ipToDisplay = when(vpnStatus.state) {
            VpnState.Connected -> exitIp?.ipV4 //NOTE: IPv6 is not supported for TV for now
            else -> myIp
        }
        VpnViewState(vpnStatus, ipToDisplay)
    }
    val vpnStatus = vpnStatusProviderUI.status
    val showVersion = MutableStateFlow(false)

    // Simplified vpn connection state change stream for UI elements interested in distinct changes between 3 states
    enum class ConnectionState { None, Connecting, Connected }
    val vpnConnectionState = vpnStatusProviderUI.status.map {
        it.state
    }.map {
        when {
            it == VpnState.Disabled -> ConnectionState.None
            it.isEstablishingConnection -> ConnectionState.Connecting
            else -> ConnectionState.Connected
        }
    }.distinctUntilChanged().asLiveData()

    val settingsProtocol get() = effectiveCurrentUserSettingsCached.value.protocol

    init {
        viewModelScope.launch {
            vpnStatusProviderUI.status.collect {
                connectedCountryFlag.value = if (isConnected()) it.server!!.flag else ""
            }
        }
    }

    // serverListVersion and userTier are only included to trigger UI refresh when they change.
    // The UI pulls all data by calling various functions on the viewmodel, it doesn't use any kind of view state.
    data class MainViewState(
        val isFreeUser: Boolean,
        val serverListVersion: Int,
        val userTier: Int,
        val showAutoConnectSetting: Boolean,
        val showNetShieldSetting: Boolean,
        val showCustomDnsSetting: Boolean,
    )

    val mainViewState = combine(
        serverManager.serverListVersion,
        currentUser.vpnUserFlow,
        isTvAutoConnectFeatureFlagEnabled.observe(),
        isTvNetShieldSettingFeatureFlagEnabled.observe(),
        isTvCustomDnsSettingFeatureFlagEnabled.observe(),
    ) { serverListVersion, vpnUser, isAutoConnectAvailable, isNetShieldAvailable, isCustomDnsAvailable ->
        MainViewState(
            isFreeUser = vpnUser?.isFreeUser != false,
            serverListVersion = serverListVersion,
            userTier = vpnUser?.userTier ?: VpnUser.FREE_TIER,
            showAutoConnectSetting = isAutoConnectAvailable,
            showNetShieldSetting = isNetShieldAvailable,
            showCustomDnsSetting = isCustomDnsAvailable,
        )
    }.onStart {
        // The main TV UI is synchronous and assumes all servers are loaded - changing this is tricky.
        // Therefore let's delay the main state until servers are loaded.
        serverManager.ensureLoaded()
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        null
    ).filterNotNull()

    fun setSelectedCountry(flag: String?) {
        selectedCountryFlag.value = flag
    }

    fun getCountryCardMap(): Map<CountryTools.Continent?, List<CountryCard>> {
        return serverManager.getVpnCountries().groupBy({
            val continent = CountryTools.oldMapLocations[it.flag]?.continent
            DebugUtils.debugAssert("Unknown location for ${it.flag}") { continent != null || it.flag == "XX" }
            continent
        }, { country ->
            getCountryCard(country)
        }).mapValues { continent ->
            continent.value.sortedWith(compareBy {
                !it.vpnCountry.hasAccessibleOnlineServer(currentUser.vpnUserCached())
            })
        }
    }

    fun getRecentCardList(context: Context): List<Card> {
        val recentsList = mutableListOf<Card>()
        recentsList.add(constructQuickConnect(context))

        val defaultConnection = createIntentForDefaultProfile(profileManager.getDefaultOrFastest())
        val defaultConnectCountry = getConnectCountry(profileManager.getDefaultOrFastest())
        val shouldAddFavorite = (isConnected() || isEstablishingConnection()) &&
            !vpnStatusProviderUI.isConnectingToCountry(defaultConnectCountry)

        if (shouldAddFavorite) {
            recentsList.add(
                ConnectIntentCard(
                    title = context.getString(
                        if (profileManager.getDefaultOrFastest().isPreBakedProfile)
                            R.string.tv_quick_connect_recommened
                        else
                            R.string.tv_quick_connect_favourite
                    ),
                    titleDrawable = profileCardTitleIcon(defaultConnection),
                    backgroundImage = CountryTools.getLargeFlagResource(context, defaultConnectCountry),
                    connectIntent = defaultConnection,
                    connectCountry = defaultConnectCountry
                )
            )
        }
        recentsManager.getRecentCountries()
            .filterNot { country ->
                vpnStatusProviderUI.isConnectingToCountry(country) ||
                    getConnectCountry(profileManager.getDefaultOrFastest()) == country
            }
            .take(RecentsManager.RECENT_MAX_SIZE - shouldAddFavorite.toInt())
            .forEach { country ->
                val connectIntent = createIntentForCountry(country)
                recentsList.add(
                    ConnectIntentCard(
                        title = CountryTools.getFullName(country),
                        titleDrawable = profileCardTitleIcon(connectIntent),
                        backgroundImage = CountryTools.getLargeFlagResource(context, country),
                        connectIntent = connectIntent,
                        connectCountry = country
                    )
                )
            }
        return recentsList
    }

    @DrawableRes
    private fun profileCardTitleIcon(connectIntent: ConnectIntent): Int {
        val defaultConnection = profileManager.getDefaultOrFastest()
        val server = serverManager.getBestServerForConnectIntent(connectIntent, currentUser.vpnUserCached(), settingsProtocol)
        return when {
            server == null -> CoreR.drawable.ic_proton_lock_filled
            server.online && connectIntent == ConnectIntent.Default -> CoreR.drawable.ic_proton_bolt
            server.online && connectIntent == createIntentForDefaultProfile(defaultConnection) -> CoreR.drawable.ic_proton_star
            server.online -> CoreR.drawable.ic_proton_clock_rotate_left
            else -> CoreR.drawable.ic_proton_wrench
        }
    }

    @DrawableRes
    private fun quickConnectTitleIcon(): Int {
        val defaultConnection = profileManager.getDefaultOrFastest()
        val server = serverManager.getServerForProfile(defaultConnection, currentUser.vpnUserCached(), settingsProtocol)
        return when {
            isConnected() || isEstablishingConnection() -> 0
            server?.online == true ->
                if (defaultConnection.isPreBakedProfile) CoreR.drawable.ic_proton_bolt else CoreR.drawable.ic_proton_star
            else -> CoreR.drawable.ic_proton_wrench
        }
    }

    private fun constructQuickConnect(context: Context): Card {
        val labelRes = when {
            isConnected() -> R.string.disconnect
            isEstablishingConnection() -> R.string.cancel
            profileManager.getDefaultOrFastest().isPreBakedProfile -> R.string.tv_quick_connect_recommened
            else -> R.string.tv_quick_connect_favourite
        }
        return QuickConnectCard(
            title = Title(
                text = context.getString(labelRes),
                resId = quickConnectTitleIcon(),
                backgroundColorRes = if (isConnected() || isEstablishingConnection())
                    R.color.tvAlert else R.color.tvGridItemOverlay
            ),
            backgroundImage = DrawableImage(
                resId = quickConnectBackground(context),
                tintRes = if (isConnected() || isEstablishingConnection())
                    R.color.tvDisconnectButtonTint else CoreR.color.transparent
            )
        )
    }

    fun isConnected() = vpnStatusProviderUI.isConnected

    fun isEstablishingConnection() = vpnStatusProviderUI.isEstablishingConnection

    val quickConnectFlag get() = if (isConnected() || isEstablishingConnection())
        vpnStatusProviderUI.connectingToServer?.flag
    else
        getConnectCountry(profileManager.getDefaultOrFastest())

    private fun quickConnectBackground(context: Context) =
        CountryTools.getLargeFlagResource(context, quickConnectFlag)

    fun onQuickConnectAction(activity: BaseTvActivity) {
        if (vpnStatusProviderUI.isConnected || vpnStatusProviderUI.isEstablishingConnection) {
            disconnect(DisconnectTrigger.QuickConnect("quick connect (TV)"))
        } else {
            connectHelper.connect(
                activity,
                createIntentForDefaultProfile(profileManager.getDefaultOrFastest()),
                ConnectTrigger.QuickConnect("quick connect (TV)")
            )
        }
    }

    fun connect(activity: BaseTvActivity, countryCode: String, trigger: ConnectTrigger) {
        connectHelper.connect(activity, createIntentForCountry(countryCode), trigger)
    }

    fun connect(activity: BaseTvActivity, card: ConnectIntentCard) {
        connect(activity, card.connectCountry, ConnectTrigger.QuickConnect("recents (TV)"))
    }

    fun disconnect(trigger: DisconnectTrigger) = connectHelper.disconnect(trigger)

    fun showMaintenanceDialog(context: Context) = connectHelper.showMaintenanceDialog(context)

    fun resetMap() {
        mapRegion.value = TvMapRenderer.FULL_REGION
    }

    fun logout() = mainScope.launch {
        logoutUseCase()
    }

    fun onLastRowSelection(selected: Boolean) {
        showVersion.value = selected
    }

    private fun getConnectCountry(profile: Profile): String =
        getConnectCountry(serverManager, currentUser, settingsProtocol, profile)

    private fun createIntentForDefaultProfile(profile: Profile): ConnectIntent =
        createIntentForDefaultProfile(serverManager, currentUser, settingsProtocol, profile)

    private fun createIntentForCountry(countryCode: String): ConnectIntent =
        ConnectIntent.FastestInCountry(CountryId(countryCode), emptySet())
}
