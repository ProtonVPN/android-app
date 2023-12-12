/*
 * Copyright (c) 2020 Proton Technologies AG
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.appconfig.GetFeatureFlags
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.tv.models.Card
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.tv.models.DrawableImage
import com.protonvpn.android.tv.models.ProfileCard
import com.protonvpn.android.tv.models.QuickConnectCard
import com.protonvpn.android.tv.models.Title
import com.protonvpn.android.tv.usecases.GetCountryCard
import com.protonvpn.android.tv.usecases.TvUiConnectDisconnectHelper
import com.protonvpn.android.tv.vpn.createProfileForCountry
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.toInt
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.proton.core.util.kotlin.takeIfNotBlank
import javax.inject.Inject

@HiltViewModel
class TvMainViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val mainScope: CoroutineScope,
    serverListUpdater: ServerListUpdater,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    vpnStateMonitor: VpnStateMonitor,
    private val connectHelper: TvUiConnectDisconnectHelper,
    private val recentsManager: RecentsManager,
    private val featureFlags: GetFeatureFlags,
    private val getCountryCard: GetCountryCard,
    currentUser: CurrentUser,
    logoutUseCase: Logout,
    userPlanManager: UserPlanManager,
    purchaseEnabled: CachedPurchaseEnabled,
) : MainViewModel(
    mainScope,
    userPlanManager,
    logoutUseCase,
    currentUser,
    purchaseEnabled,
) {

    data class VpnViewState(val vpnStatus: VpnStateMonitor.Status, val ipToDisplay: String?)

    val displayStreamingIcons get() = featureFlags.value.streamingServicesLogos
    val selectedCountryFlag = MutableLiveData<String?>()
    val connectedCountryFlag = MutableLiveData<String>()
    val mapRegion = MutableLiveData<TvMapRenderer.MapRegion>()

    val vpnViewState: Flow<VpnViewState> = combine(
        vpnStatusProviderUI.status,
        serverListUpdater.ipAddress,
        vpnStateMonitor.exitIp
    ) { vpnStatus, myIp, exitIp ->
        val ipToDisplay = when(vpnStatus.state) {
            VpnState.Connected -> exitIp
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

    init {
        viewModelScope.launch {
            vpnStatusProviderUI.status.collect {
                connectedCountryFlag.value = if (isConnected()) it.server!!.flag else ""
            }
        }
    }

    val listVersion = serverManager.serverListVersion

    fun onViewInit(lifecycle: Lifecycle) {
        lifecycle.addObserver(this)
    }

    fun setSelectedCountry(flag: String?) {
        selectedCountryFlag.value = flag
    }

    fun getCountryCardMap(): Map<CountryTools.Continent?, List<CountryCard>> {
        return serverManager.getVpnCountries().groupBy({
            val continent = CountryTools.locationMap[it.flag]?.continent
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

        val defaultConnection = serverManager.defaultConnection
        val shouldAddFavorite = (isConnected() || isEstablishingConnection()) &&
            !vpnStatusProviderUI.isConnectingToCountry(getConnectCountry(defaultConnection))

        if (shouldAddFavorite) {
            val connectCountry = getConnectCountry(defaultConnection)
            recentsList.add(
                ProfileCard(
                    title = context.getString(
                        if (serverManager.defaultConnection.isPreBakedProfile)
                            R.string.tv_quick_connect_recommened
                        else
                            R.string.tv_quick_connect_favourite
                    ),
                    titleDrawable = profileCardTitleIcon(defaultConnection),
                    backgroundImage = CountryTools.getLargeFlagResource(context, connectCountry),
                    profile = defaultConnection,
                    connectCountry = connectCountry
                )
            )
        }
        recentsManager.getRecentCountries()
            .filterNot { country ->
                vpnStatusProviderUI.isConnectingToCountry(country) ||
                    getConnectCountry(serverManager.defaultConnection) == country
            }
            .take(RecentsManager.RECENT_MAX_SIZE - shouldAddFavorite.toInt())
            .forEach { country ->
                val profile = createProfileForCountry(country)
                recentsList.add(
                    ProfileCard(
                        title = CountryTools.getFullName(country),
                        titleDrawable = profileCardTitleIcon(profile),
                        backgroundImage = CountryTools.getLargeFlagResource(context, country),
                        profile = profile,
                        connectCountry = country
                    )
                )
            }
        return recentsList
    }

    @DrawableRes
    private fun profileCardTitleIcon(profile: Profile): Int {
        val defaultConnection = serverManager.defaultConnection
        val server = serverManager.getServerForProfile(profile, currentUser.vpnUserCached())
        return when {
            server == null -> R.drawable.ic_proton_lock_filled
            server.online && profile.isPreBakedProfile -> R.drawable.ic_proton_bolt
            server.online && profile == defaultConnection -> R.drawable.ic_proton_star
            server.online -> R.drawable.ic_proton_clock_rotate_left
            else -> R.drawable.ic_proton_wrench
        }
    }

    @DrawableRes
    private fun quickConnectTitleIcon(): Int {
        val defaultConnection = serverManager.defaultConnection
        val server = serverManager.getServerForProfile(defaultConnection, currentUser.vpnUserCached())
        return when {
            isConnected() || isEstablishingConnection() -> 0
            server?.online == true ->
                if (defaultConnection.isPreBakedProfile) R.drawable.ic_proton_bolt else R.drawable.ic_proton_star
            else -> R.drawable.ic_proton_wrench
        }
    }

    private fun constructQuickConnect(context: Context): Card {
        val labelRes = when {
            isConnected() -> R.string.disconnect
            isEstablishingConnection() -> R.string.cancel
            serverManager.defaultConnection.isPreBakedProfile -> R.string.tv_quick_connect_recommened
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
                tint = if (isConnected() || isEstablishingConnection())
                    R.color.tvDisconnectButtonTint else R.color.transparent
            )
        )
    }

    fun isConnected() = vpnStatusProviderUI.isConnected

    fun isEstablishingConnection() = vpnStatusProviderUI.isEstablishingConnection

    val quickConnectFlag get() = if (isConnected() || isEstablishingConnection())
        vpnStatusProviderUI.connectingToServer?.flag
    else
        getConnectCountry(serverManager.defaultConnection)

    private fun quickConnectBackground(context: Context) =
        CountryTools.getLargeFlagResource(context, quickConnectFlag)

    fun onQuickConnectAction(activity: BaseTvActivity) {
        if (vpnStatusProviderUI.isConnected || vpnStatusProviderUI.isEstablishingConnection) {
            disconnect(DisconnectTrigger.QuickConnect("quick connect (TV)"))
        } else {
            connectHelper.connect(
                activity, serverManager.defaultConnection, ConnectTrigger.QuickConnect("quick connect (TV)")
            )
        }
    }

    fun connect(activity: BaseTvActivity, countryCode: String, trigger: ConnectTrigger) {
        connectHelper.connect(activity, createProfileForCountry(countryCode), trigger)
    }

    fun connect(activity: BaseTvActivity, card: ProfileCard) {
        connect(activity, card.connectCountry, ConnectTrigger.QuickConnect("recents (TV)"))
    }

    fun disconnect(trigger: DisconnectTrigger) = connectHelper.disconnect(trigger)

    fun showMaintenanceDialog(context: Context) = connectHelper.showMaintenanceDialog(context)

    fun resetMap() {
        mapRegion.value = TvMapRenderer.MapRegion(0f, 0f, 1f)
    }

    fun onLastRowSelection(selected: Boolean) {
        showVersion.value = selected
    }

    private fun getConnectCountry(profile: Profile): String {
        DebugUtils.debugAssert("Random profile not supported in TV") {
            profile.wrapper.type != ServerWrapper.ProfileType.RANDOM
        }
        return profile.country.takeIfNotBlank()
            ?: serverManager.getServerForProfile(profile, currentUser.vpnUserCached())?.exitCountry
            ?: ""
    }
}
