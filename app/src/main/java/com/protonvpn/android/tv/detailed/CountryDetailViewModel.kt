/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.android.tv.detailed

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.protonvpn.android.R
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.components.BaseTvActivity
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.ServerWrapper
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.servers.GetStreamingServices
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.tv.models.CountryCard
import com.protonvpn.android.tv.usecases.GetCountryCard
import com.protonvpn.android.tv.usecases.SetFavoriteCountry
import com.protonvpn.android.tv.usecases.TvUiConnectDisconnectHelper
import com.protonvpn.android.tv.vpn.createProfileForCountry
import com.protonvpn.android.userstorage.ProfileManager
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

data class ViewState(
    val countryCard: CountryCard,
    @StringRes val countryContentDescription: Int,
    val isConnectedToThisCountry: Boolean,
    val isDefaultCountry: Boolean,
    val isAccessible: Boolean,
    val isPlusUser: Boolean,
    val hasAccessToStreaming: Boolean,
    val showConnectButtons: Boolean,
    val showConnectFastest: Boolean,
    val showConnectToStreaming: Boolean,
    @StringRes val connectButtonText: Int,
    @StringRes val disconnectButtonText: Int,
)

@HiltViewModel
class CountryDetailViewModel @Inject constructor(
    private val serverManager: ServerManager2,
    private val getCountryCard: GetCountryCard,
    val streamingServices: GetStreamingServices,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val connectHelper: TvUiConnectDisconnectHelper,
    private val profileManager: ProfileManager,
    private val setFavoriteCountry: SetFavoriteCountry,
    private val currentUser: CurrentUser
) : ViewModel() {

    fun getState(countryCode: String): Flow<ViewState> = flow {
        val country = serverManager.getVpnExitCountry(countryCode, false)
            ?: throw IllegalArgumentException("No country for $countryCode")
        val countryCard = getCountryCard(country)
        val hasStreamingServices = streamingServices(countryCode).isNotEmpty() // This doesn't change often.
        emitAll(getViewState(countryCard, hasStreamingServices))
    }

    private fun getViewState(countryCard: CountryCard, hasStreamingServices: Boolean): Flow<ViewState> = combine(
        vpnStatusProviderUI.status,
        currentUser.vpnUserFlow
    ) { vpnStatus, vpnUser ->
        val country = countryCard.vpnCountry
        val isPlusUser = vpnUser?.isUserPlusOrAbove == true
        ViewState(
            countryCard = countryCard,
            countryContentDescription = getCountryDescription(country, hasStreamingServices, vpnUser),
            isConnectedToThisCountry = vpnStatus.isConnectingToCountry(country.flag),
            isDefaultCountry = profileManager.findDefaultProfile()?.wrapper?.country == country.flag,
            isAccessible = country.hasAccessibleServer(vpnUser),
            showConnectButtons = showConnectButtons(country, vpnStatus, vpnUser),
            isPlusUser = isPlusUser,
            hasAccessToStreaming = isPlusUser,
            showConnectFastest = showConnectToFastest(country, vpnStatus, vpnUser),
            showConnectToStreaming = showConnectToStreaming(country, vpnStatus, vpnUser),
            connectButtonText = if (isPlusUser) R.string.tv_detail_connect else R.string.tv_detail_connect_streaming,
            disconnectButtonText = disconnectText(country, vpnStatus, vpnUser)
        )
    }

    fun connect(activity: BaseTvActivity, countryCode: String) {
        connectHelper.connect(
            activity,
            ConnectIntent.FastestInCountry(CountryId(countryCode), emptySet()),
            ConnectTrigger.Country("country details (TV)")
        )
    }

    fun disconnect(trigger: DisconnectTrigger) = connectHelper.disconnect(trigger)

    fun setAsDefaultCountry(checked: Boolean, countryCode: String) = setFavoriteCountry(countryCode.takeIf { checked })

    private fun showConnectButtons(country: VpnCountry, vpnStatus: VpnStateMonitor.Status, vpnUser: VpnUser?) =
        !vpnStatus.isConnectingToCountry(country.flag) && country.hasAccessibleServer(vpnUser)

    private fun showConnectToFastest(country: VpnCountry, vpnStatus: VpnStateMonitor.Status, vpnUser: VpnUser?) =
        country.hasAccessibleServer(vpnUser) &&
            vpnUser?.isUserPlusOrAbove != true &&
            !vpnStatus.isConnectingToCountry(country.flag)

    private fun showConnectToStreaming(country: VpnCountry, vpnStatus: VpnStateMonitor.Status, vpnUser: VpnUser?) =
        showConnectButtons(country, vpnStatus, vpnUser) || vpnUser?.isUserPlusOrAbove != true

    private fun disconnectText(country: VpnCountry, vpnStatus: VpnStateMonitor.Status, vpnUser: VpnUser?) =
        if (!showConnectButtons(country, vpnStatus, vpnUser) && vpnStatus.state.isEstablishingConnection)
            R.string.cancel
        else
            R.string.disconnect

    private fun VpnStateMonitor.Status.isConnectingToCountry(countryCode: String) =
        (state == VpnState.Connected || state.isEstablishingConnection)
            && connectionParams?.server?.exitCountry == countryCode

    @StringRes
    fun getCountryDescription(
        vpnCountry: VpnCountry,
        hasAnyStreamingServices: Boolean,
        vpnUser: VpnUser?
    ) = when {
        vpnUser?.isUserPlusOrAbove == true -> R.string.tv_detail_description_plus
        !vpnCountry.hasAccessibleServer(vpnUser) -> R.string.tv_detail_description_country_not_available
        hasAnyStreamingServices -> R.string.tv_detail_description_no_streaming_country
        else -> R.string.tv_detail_description_streaming_country
    }
}
