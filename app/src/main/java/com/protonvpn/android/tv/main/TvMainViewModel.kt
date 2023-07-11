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
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.Theme
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.appconfig.CachedPurchaseEnabled
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.auth.usecase.Logout
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiConnect
import com.protonvpn.android.logging.UiDisconnect
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.tv.TvUpgradeActivity
import com.protonvpn.android.tv.models.Card
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.tv.models.DrawableImage
import com.protonvpn.android.tv.models.ProfileCard
import com.protonvpn.android.tv.models.QuickConnectCard
import com.protonvpn.android.tv.models.Title
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.AndroidUtils.toInt
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.StreamingViewModelHelper
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnConnectionManager
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
    override val appConfig: AppConfig,
    override val serverManager: ServerManager,
    private val profileManager: ProfileManager,
    private val mainScope: CoroutineScope,
    val serverListUpdater: ServerListUpdater,
    val vpnStatusProviderUI: VpnStatusProviderUI,
    val vpnStateMonitor: VpnStateMonitor,
    val vpnConnectionManager: VpnConnectionManager,
    private val recentsManager: RecentsManager,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
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
), StreamingViewModelHelper {

    data class VpnViewState(val vpnStatus: VpnStateMonitor.Status, val ipToDisplay: String?)

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
                connectedCountryFlag.value = if (isConnected())
                    it.server!!.flag else ""
            }
        }
    }

    val listVersion = serverManager.serverListVersion

    fun onViewInit(lifecycle: Lifecycle) {
        lifecycle.addObserver(this)
    }

    val haveAccessToStreaming get() = currentUser.vpnUserCached()?.isUserPlusOrAbove == true

    fun showConnectButtons(card: CountryCard) =
        !isConnectedToThisCountry(card) && card.vpnCountry.hasAccessibleServer(currentUser.vpnUserCached())

    fun showConnectToStreamingButton(card: CountryCard) = showConnectButtons(card) || !isPlusUser()

    fun isConnectedToThisCountry(card: CountryCard) =
        vpnStatusProviderUI.isConnectingToCountry(card.vpnCountry.flag)

    fun disconnectText(card: CountryCard) =
        if (!showConnectButtons(card) && vpnStatusProviderUI.state.isEstablishingConnection)
            R.string.cancel
        else
            R.string.disconnect

    fun setSelectedCountry(flag: String?) {
        selectedCountryFlag.value = flag
    }

    private fun countryListItemIcon(country: VpnCountry) = when {
        country.isUnderMaintenance() -> R.drawable.ic_proton_wrench
        currentUser.vpnUserCached()?.isFreeUser != true -> null
        country.hasAccessibleServer(currentUser.vpnUserCached()) -> R.drawable.ic_free
        else -> R.drawable.ic_proton_lock_filled
    }

    fun getCountryCardMap(context: Context): Map<CountryTools.Continent?, List<CountryCard>> {
        return serverManager.getVpnCountries().groupBy({
            val continent = CountryTools.locationMap[it.flag]?.continent
            DebugUtils.debugAssert { continent != null }
            continent
        }, { country ->
            getCountryCard(context, country)
        }).mapValues { continent ->
            continent.value.sortedWith(compareBy {
                !it.vpnCountry.hasAccessibleOnlineServer(currentUser.vpnUserCached())
            })
        }
    }

    fun getCountryCard(context: Context, vpnCountryFlag: String): CountryCard? {
        val country = serverManager.getVpnExitCountry(vpnCountryFlag, false) ?: return null
        return getCountryCard(context, country)
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

    private fun getCountryCard(context: Context, country: VpnCountry): CountryCard =
        CountryCard(
            countryName = country.countryName,
            hasStreamingService = streamingServices(country.flag).isNotEmpty(),
            backgroundImage = DrawableImage(CountryTools.getLargeFlagResource(context, country.flag)),
            bottomTitleResId = countryListItemIcon(country),
            vpnCountry = country
        )

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

    fun disconnect(trigger: DisconnectTrigger) {
        ProtonLogger.log(UiDisconnect, trigger.description)
        vpnConnectionManager.disconnect(trigger)
    }

    fun isConnected() = vpnStatusProviderUI.isConnected

    fun isEstablishingConnection() = vpnStatusProviderUI.isEstablishingConnection

    fun isPlusUser() = currentUser.vpnUserCached()?.isUserPlusOrAbove == true

    fun hasAccessibleServers(country: VpnCountry) = country.hasAccessibleServer(currentUser.vpnUserCached())

    @StringRes
    fun getCountryDescription(vpnCountry: VpnCountry) = when {
        isPlusUser() -> R.string.tv_detail_description_plus
        !hasAccessibleServers(vpnCountry) -> R.string.tv_detail_description_country_not_available
        streamingServices(vpnCountry.flag).isEmpty() -> R.string.tv_detail_description_no_streaming_country
        else -> R.string.tv_detail_description_streaming_country
    }

    fun showConnectToFastest(card: CountryCard) = card.vpnCountry.hasAccessibleServer(currentUser.vpnUserCached()) &&
        !isPlusUser() && !isConnectedToThisCountry(card)

    fun onUpgradeClicked(context: Context) {
        context.launchActivity<TvUpgradeActivity>()
    }

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
            connect(activity, serverManager.defaultConnection, ConnectTrigger.QuickConnect("quick connect (TV)"))
        }
    }

    fun connect(activity: BaseTvActivity, countryCode: String, trigger: ConnectTrigger) {
        connect(activity, createProfileForCountry(countryCode), trigger)
    }

    fun connect(activity: BaseTvActivity, card: ProfileCard) {
        connect(activity, card.connectCountry, ConnectTrigger.QuickConnect("recents (TV)"))
    }

    private fun connect(activity: BaseTvActivity, profile: Profile?, trigger: ConnectTrigger) {
        if (profile != null) {
            ProtonLogger.log(UiConnect, trigger.description)
            vpnConnectionManager.connect(activity.getVpnUiDelegate(), profile, trigger)
        } else {
            showMaintenanceDialog(activity)
        }
    }

    fun resetMap() {
        mapRegion.value = TvMapRenderer.MapRegion(0f, 0f, 1f)
    }

    private fun TvMapRenderer.MapRegion.isZoomedIn() = x != 0f || y != 0f || w != 1f

    fun isDefaultCountry(vpnCountry: VpnCountry) =
        profileManager.findDefaultProfile()?.wrapper?.country == vpnCountry.flag

    fun setAsDefaultCountry(checked: Boolean, vpnCountry: VpnCountry) {
        profileManager.deleteProfile(profileManager.findDefaultProfile())
        if (checked) {
            val newDefaultProfile = createProfileForCountry(vpnCountry.flag)
            profileManager.addToProfileList(newDefaultProfile)
            mainScope.launch {
                userSettingsManager.updateDefaultProfile(newDefaultProfile.id)
            }
        }
    }

    fun showMaintenanceDialog(context: Context) {
        MaterialDialog.Builder(context)
            .theme(Theme.DARK)
            .negativeFocus(true)
            .title(R.string.tv_country_maintenance_dialog_title)
            .content(R.string.tv_country_maintenance_dialog_description)
            .negativeText(R.string.ok)
            .show()
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

    private fun createProfileForCountry(countryCode: String): Profile =
        Profile(
            CountryTools.getFullName(countryCode),
            null,
            ServerWrapper.makeFastestForCountry(countryCode),
            null,
            null
        )
}
