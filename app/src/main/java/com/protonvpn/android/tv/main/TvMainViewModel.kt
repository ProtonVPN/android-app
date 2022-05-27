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
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.tv.TvUpgradeActivity
import com.protonvpn.android.tv.models.Card
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.tv.models.DrawableImage
import com.protonvpn.android.tv.models.ProfileCard
import com.protonvpn.android.tv.models.QuickConnectCard
import com.protonvpn.android.tv.models.Title
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.AndroidUtils.launchActivity
import com.protonvpn.android.utils.AndroidUtils.toInt
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.StreamingViewModelHelper
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TvMainViewModel @Inject constructor(
    override val appConfig: AppConfig,
    override val serverManager: ServerManager,
    val mainScope: CoroutineScope,
    val serverListUpdater: ServerListUpdater,
    val vpnStateMonitor: VpnStateMonitor,
    val vpnConnectionManager: VpnConnectionManager,
    private val recentsManager: RecentsManager,
    val userData: UserData,
    currentUser: CurrentUser,
    logoutUseCase: Logout,
    userPlanManager: UserPlanManager,
    certificateRepository: CertificateRepository,
    purchaseEnabled: CachedPurchaseEnabled
) : MainViewModel(
    mainScope,
    userPlanManager,
    certificateRepository,
    logoutUseCase,
    currentUser,
    purchaseEnabled
), StreamingViewModelHelper {

    val selectedCountryFlag = MutableLiveData<String?>()
    val connectedCountryFlag = MutableLiveData<String>()
    val mapRegion = MutableLiveData<TvMapRenderer.MapRegion>()

    val vpnStatus = vpnStateMonitor.status.asLiveData()
    val showVersion = MutableStateFlow(false)

    // Simplified vpn connection state change stream for UI elements interested in distinct changes between 3 states
    enum class ConnectionState { None, Connecting, Connected }
    val vpnConnectionState = vpnStateMonitor.status.map {
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
            vpnStateMonitor.status.collect {
                connectedCountryFlag.value = if (isConnected())
                    it.server!!.flag else ""
            }
        }
    }

    val listVersion = serverManager.serverListVersion

    fun onViewInit(lifecycle: Lifecycle) {
        serverListUpdater.startSchedule(lifecycle, null)
        lifecycle.addObserver(this)
    }

    val haveAccessToStreaming get() = currentUser.vpnUserCached()?.isUserPlusOrAbove == true

    fun showConnectButtons(card: CountryCard) =
        !isConnectedToThisCountry(card) && card.vpnCountry.hasAccessibleServer(currentUser.vpnUserCached())

    fun showConnectToStreamingButton(card: CountryCard) = showConnectButtons(card) || !isPlusUser()

    fun isConnectedToThisCountry(card: CountryCard) =
        vpnStateMonitor.isConnectingToCountry(card.vpnCountry.flag)

    fun disconnectText(card: CountryCard) =
        if (!showConnectButtons(card) && vpnStateMonitor.state.isEstablishingConnection)
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
            !vpnStateMonitor.isConnectingToCountry(defaultConnection.connectCountry)

        if (shouldAddFavorite) {
            recentsList.add(
                ProfileCard(
                    title = context.getString(
                        if (serverManager.defaultConnection.isPreBakedProfile)
                            R.string.tv_quick_connect_recommened
                        else
                            R.string.tv_quick_connect_favourite
                    ),
                    titleDrawable = profileCardTitleIcon(defaultConnection),
                    backgroundImage = CountryTools.getLargeFlagResource(context, defaultConnection.connectCountry),
                    profile = defaultConnection
                )
            )
        }
        recentsManager.getRecentCountries()
            .take(RecentsManager.RECENT_MAX_SIZE - shouldAddFavorite.toInt())
            .forEach {
                recentsList.add(
                    ProfileCard(
                        title = it.getDisplayName(context).ifEmpty { it.server?.displayName ?: "" },
                        titleDrawable = profileCardTitleIcon(it),
                        backgroundImage = CountryTools.getLargeFlagResource(context, it.connectCountry),
                        profile = it
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
        return when {
            !currentUser.vpnUserCached().hasAccessToServer(profile.server) -> R.drawable.ic_proton_lock_filled
            profile.server?.online == true && profile.isPreBakedProfile -> R.drawable.ic_proton_bolt
            profile.server?.online == true && profile.server == defaultConnection.server -> R.drawable.ic_proton_star
            profile.server?.online == true -> R.drawable.ic_proton_clock_rotate_left
            else -> R.drawable.ic_proton_wrench
        }
    }

    @DrawableRes
    private fun quickConnectTitleIcon(): Int {
        val defaultConnection = serverManager.defaultConnection
        return when {
            isConnected() || isEstablishingConnection() -> 0
            defaultConnection.server?.online == true ->
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

    fun disconnect(uiElementName: String) {
        ProtonLogger.log(UiDisconnect, uiElementName)
        vpnConnectionManager.disconnect("user via $uiElementName")
    }

    fun isConnected() = vpnStateMonitor.isConnected

    fun isEstablishingConnection() = vpnStateMonitor.isEstablishingConnection

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
        vpnStateMonitor.connectingToServer?.flag
    else
        serverManager.defaultConnection.connectCountry

    private fun quickConnectBackground(context: Context) =
        CountryTools.getLargeFlagResource(context, quickConnectFlag)

    fun onQuickConnectAction(activity: BaseTvActivity) {
        if (vpnStateMonitor.isConnected || vpnStateMonitor.isEstablishingConnection) {
            disconnect("quick connect (TV)")
        } else {
            connect(activity, serverManager.defaultConnection, "quick connect (TV)")
        }
    }

    fun connect(activity: BaseTvActivity, card: CountryCard?) {
        val profile = if (card != null) {
            serverManager.getBestScoreServer(card.vpnCountry)?.let {
                Profile.getTempProfile(it, serverManager)
            }
        } else {
            serverManager.defaultConnection
        }
        connect(activity, profile, "country card (TV)")
    }

    private fun connect(activity: BaseTvActivity, profile: Profile?, uiElement: String) {
        if (profile != null) {
            ProtonLogger.log(UiConnect, uiElement)
            vpnConnectionManager.connect(activity.getVpnUiDelegate(), profile, "user via $uiElement")
        } else {
            showMaintenanceDialog(activity)
        }
    }

    fun connect(activity: BaseTvActivity, card: ProfileCard) {
        connect(activity, card.profile, "recents (TV)")
    }

    fun resetMap() {
        mapRegion.value = TvMapRenderer.MapRegion(0f, 0f, 1f)
    }

    private fun TvMapRenderer.MapRegion.isZoomedIn() = x != 0f || y != 0f || w != 1f

    fun isDefaultCountry(vpnCountry: VpnCountry) =
        userData.defaultConnection?.wrapper?.country == vpnCountry.flag

    fun setAsDefaultCountry(checked: Boolean, vpnCountry: VpnCountry) {
        userData.defaultConnection = if (checked) Profile(
            vpnCountry.countryName,
            null,
            ServerWrapper.makeFastestForCountry(vpnCountry.flag, serverManager),
            null,
            null
        ) else null
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
}
