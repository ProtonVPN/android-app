/*
 * Copyright (c) 2023 Proton AG
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
package com.protonvpn.android.redesign.home_screen.ui

import android.annotation.TargetApi
import android.content.Context
import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.ApiNotificationActions
import com.protonvpn.android.appconfig.ApiNotificationOfferButton
import com.protonvpn.android.appconfig.UserCountryIpBased
import com.protonvpn.android.di.ElapsedRealtimeClock
import com.protonvpn.android.excludedlocations.usecases.ObserveShowExcludedLocationsAdoption
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.logging.UiConnect
import com.protonvpn.android.logging.UiDisconnect
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.redesign.recents.data.RecentConnection
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.usecases.GetQuickConnectIntent
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewStateFlow
import com.protonvpn.android.redesign.recents.usecases.RecentsManager
import com.protonvpn.android.redesign.settings.IsAutomaticConnectionPreferencesFeatureFlagEnabled
import com.protonvpn.android.redesign.vpn.ChangeServerManager
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewStateFlow
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentAvailability
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.usecases.DisableCustomDnsForCurrentConnection
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.telemetry.UpgradeTelemetry
import com.protonvpn.android.tv.main.CountryHighlight
import com.protonvpn.android.ui.planupgrade.UpgradeFlowType
import com.protonvpn.android.ui.promooffers.HomeScreenProminentBannerFlow
import com.protonvpn.android.ui.promooffers.HomeScreenPromoBannerFlow
import com.protonvpn.android.ui.promooffers.ProminentBannerState
import com.protonvpn.android.ui.promooffers.PromoOfferBannerState
import com.protonvpn.android.ui.promooffers.PromoOfferButtonActions
import com.protonvpn.android.ui.promooffers.PromoOfferIapActivity
import com.protonvpn.android.ui.promooffers.PromoOffersPrefs
import com.protonvpn.android.ui.storage.UiStateStorage
import com.protonvpn.android.utils.TrafficMonitor
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnErrorUIManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.android.widget.WidgetAdoptionUiType
import com.protonvpn.android.widget.WidgetManager
import dagger.Reusable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import me.proton.core.presentation.savedstate.state
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val DialogStateKey = "dialog"

@Reusable
class SetNetShield @Inject constructor(
    private val profilesDao: ProfilesDao,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    private val userSettingsManager: CurrentUserLocalSettingsManager,
) {
    suspend operator fun invoke(protocol: NetShieldProtocol) {
        val profileId = vpnStatusProviderUI.connectionIntent?.profileId
        if (profileId != null) {
            profilesDao.updateNetShield(profileId, protocol)
        } else {
            userSettingsManager.updateNetShield(protocol)
        }
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    recentsListViewStateFlow: RecentsListViewStateFlow,
    private val recentsManager: RecentsManager,
    private val uiStateStorage: UiStateStorage,
    private val vpnConnectionManager: VpnConnectionManager,
    private val quickConnectIntent: GetQuickConnectIntent,
    changeServerViewStateFlow: ChangeServerViewStateFlow,
    private val changeServerManager: ChangeServerManager,
    private val upgradeTelemetry: UpgradeTelemetry,
    private val vpnStatusProviderUI: VpnStatusProviderUI,
    userCountryIpBased: UserCountryIpBased,
    private val vpnErrorUIManager: VpnErrorUIManager,
    upsellCarouselStateFlow: UpsellCarouselStateFlow,
    private val bottomPromoBannerFlow: HomeScreenPromoBannerFlow,
    prominentPromoBannerFlow: HomeScreenProminentBannerFlow,
    private val promoOfferButtonActions: PromoOfferButtonActions,
    private val promoOffersPrefs: PromoOffersPrefs,
    @param:ElapsedRealtimeClock val elapsedRealtimeClock: () -> Long,
    private val setNetShield: SetNetShield,
    private val widgetManager: WidgetManager,
    private val disableCustomDnsForCurrentConnection: DisableCustomDnsForCurrentConnection,
    private val isAutomaticConnectionEnabled: IsAutomaticConnectionPreferencesFeatureFlagEnabled,
    observeShowExcludedLocationsAdoption: ObserveShowExcludedLocationsAdoption,
    private val trafficMonitor: TrafficMonitor,
) : ViewModel() {

    private val connectionMapHighlightsFlow = vpnStatusProviderUI.uiStatus.map {
        val highlight = it.state.toMapHighlightState()
        val exit = it.server?.exitCountry
        if (highlight != null && exit != null)
            exit to highlight
        else
            null
    }.distinctUntilChanged()

    val adoptionComponentFlow: StateFlow<AdoptionComponent?> = combine(
        observeShowExcludedLocationsAdoption(),
        widgetManager.showWidgetAdoptionFlow,
    ) { showExcludedLocationsAdoption, widgetAdoptionType ->
        if (showExcludedLocationsAdoption) {
            AdoptionComponent.ExcludedLocationsAdoptionComponent(
                onDismiss = ::onExcludedLocationsAdoptionClosed,
                onExcludeLocationsClick = ::onExcludedLocationsAdoptionOpened,
            )
        } else {
            when (widgetAdoptionType) {
                WidgetAdoptionUiType.None -> null
                WidgetAdoptionUiType.AddWidgetButton -> {
                    AdoptionComponent.WidgetAdoptionComponent(
                        onDismiss = ::onWidgetAdoptionShown,
                        onAddWidget = ::onAddWidget,
                    )
                }

                WidgetAdoptionUiType.Instructions -> {
                    AdoptionComponent.WidgetAdoptionComponent(
                        onDismiss = ::onWidgetAdoptionShown,
                        onAddWidget = null,
                    )
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(1_000),
        initialValue = null,
    )

    val mapHighlightState: Flow<Pair<String, CountryHighlight>?> = combine(
        connectionMapHighlightsFlow,
        userCountryIpBased.observe().distinctUntilChanged()
    ) { connectionHighlight, realCountry ->
        connectionHighlight ?: realCountry?.let { it.countryCode to CountryHighlight.SELECTED }
    }

    val recentsViewState = recentsListViewStateFlow
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    val prominentPromoBannerStateFlow: StateFlow<ProminentBannerState?> = prominentPromoBannerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val changeServerViewState: SharedFlow<ChangeServerViewState?> = changeServerViewStateFlow
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    val upsellCarouselState: StateFlow<UpsellCarouselState?> = upsellCarouselStateFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    sealed class DialogState : Parcelable {

        @get:StringRes
        open val titleResId: Int = R.string.cannot_connect

        @get:StringRes
        abstract val messageResId: Int

        open val messageArgs: Array<Any> = emptyArray()

        @get:StringRes
        open val confirmLabelResId: Int = R.string.ok

        @get:StringRes
        open val cancelLabelResId: Int? = null

        open val onCancelClick: (() -> Unit)? = null

        @Parcelize
        object CountryInMaintenance : DialogState() {

            @IgnoredOnParcel
            override val messageResId: Int = R.string.message_country_servers_in_maintenance

        }

        @Parcelize
        object CityInMaintenance : DialogState() {

            @IgnoredOnParcel
            override val messageResId: Int = R.string.message_city_servers_in_maintenance

        }

        @Parcelize
        object StateInMaintenance : DialogState() {

            @IgnoredOnParcel
            override val messageResId: Int = R.string.message_state_servers_in_maintenance

        }

        @Parcelize
        object ServerInMaintenance : DialogState() {

            @IgnoredOnParcel
            override val messageResId: Int = R.string.message_server_in_maintenance

        }

        @Parcelize
        object GatewayInMaintenance : DialogState() {

            @IgnoredOnParcel
            override val messageResId: Int = R.string.message_gateway_in_maintenance

        }

        @Parcelize
        object ServerNotAvailable : DialogState() {

            @IgnoredOnParcel
            override val messageResId: Int = R.string.message_server_not_available

        }

        @Parcelize
        data class ServerLocationExcluded(
            override val onCancelClick: (() -> Unit)?,
        ) : DialogState() {

            @IgnoredOnParcel
            override val messageResId: Int = R.string.message_server_location_excluded

            @IgnoredOnParcel
            override val cancelLabelResId: Int = R.string.excluded_locations

        }

        @Parcelize
        data class ProfileNotAvailable(val profileName: String) : DialogState() {

            @IgnoredOnParcel
            override val messageResId: Int = R.string.profile_unavailable_dialog_message

            @IgnoredOnParcel
            override val messageArgs: Array<Any> = arrayOf(profileName)

        }

        @Parcelize
        data class SmartConnectionPreferencesDiscovery(
            override val onCancelClick: (() -> Unit)?,
        ) : DialogState() {

            @IgnoredOnParcel
            override val titleResId: Int = R.string.connection_preferences_smart_discovery_dialog_title

            @IgnoredOnParcel
            override val messageResId: Int = R.string.connection_preferences_smart_discovery_dialog_message

            @IgnoredOnParcel
            override val confirmLabelResId: Int = R.string.skip

            @IgnoredOnParcel
            override val cancelLabelResId: Int = R.string.exclude_locations

        }

    }

    sealed interface Event {

        data object OnNavigateToConnectionPreferences : Event

        data object OnNavigateToUpgrade : Event

    }

    private var dialogState by savedStateHandle.state<DialogState?>(null, DialogStateKey)
    val dialogStateFlow = savedStateHandle.getStateFlow<DialogState?>(DialogStateKey, null)

    private val _eventFlow = MutableSharedFlow<Event>(extraBufferCapacity = 1)

    val eventFlow = _eventFlow.asSharedFlow()

    val snackbarErrorFlow = vpnErrorUIManager.snackErrorFlow

    suspend fun consumeErrorMessage() = vpnErrorUIManager.consumeErrorMessage()

    fun getBottomPromoBannerStateFlow(isNightMode: Boolean): StateFlow<PromoOfferBannerState?> =
        bottomPromoBannerFlow(isNightMode)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    fun setNetShieldProtocol(netShieldProtocol: NetShieldProtocol) {
        viewModelScope.launch {
            setNetShield(netShieldProtocol)
        }
    }

    suspend fun connect(vpnUiDelegate: VpnUiDelegate, trigger: ConnectTrigger) {
        ProtonLogger.log(UiConnect, "Home: ${trigger.description}")
        vpnConnectionManager.connect(vpnUiDelegate, quickConnectIntent(), trigger)
    }

    fun changeServer(vpnUiDelegate: VpnUiDelegate) = changeServerManager.changeServer(vpnUiDelegate)

    fun onChangeServerUpgradeButtonShown() {
        upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.CHANGE_SERVER)
    }

    suspend fun onRecentClicked(item: RecentItemViewState, vpnUiDelegate: VpnUiDelegate) {
        val recent = recentsManager.getRecentById(item.id)
        if (recent != null) {
            uiStateStorage.update { it.copy(hasUsedRecents = true) }
            when (item.availability) {
                ConnectIntentAvailability.UNAVAILABLE_PLAN -> _eventFlow.emit(value = Event.OnNavigateToUpgrade)
                ConnectIntentAvailability.UNAVAILABLE_PROTOCOL -> dialogState = DialogState.ServerNotAvailable
                ConnectIntentAvailability.NO_SERVERS -> dialogState = DialogState.ServerNotAvailable
                ConnectIntentAvailability.EXCLUDED -> {
                    dialogState = DialogState.ServerLocationExcluded(onCancelClick = ::openConnectionPreferences)
                }
                ConnectIntentAvailability.AVAILABLE_OFFLINE -> dialogState = recent.toMaintenanceDialogType()
                ConnectIntentAvailability.ONLINE -> {
                    val trigger = if (recent.isPinned) ConnectTrigger.RecentPinned else ConnectTrigger.RecentRegular
                    ProtonLogger.log(UiConnect, "home ${trigger.description}")
                    vpnConnectionManager.connect(vpnUiDelegate, recent.connectIntent, trigger)
                }
            }
        }
    }

    fun disconnect(trigger: DisconnectTrigger) {
        ProtonLogger.log(UiDisconnect, "Home: ${trigger.description}")
        vpnConnectionManager.disconnect(trigger)

        viewModelScope.launch {
            if (shouldShowSmartConnectionPreferencesDiscovery()) {
                dialogState = DialogState.SmartConnectionPreferencesDiscovery(
                    onCancelClick = ::openConnectionPreferences,
                )

                uiStateStorage.update {
                    it.copy(hasShownConnectionPreferencesSmartDiscovery = true)
                }
            }
        }
    }

    private suspend fun shouldShowSmartConnectionPreferencesDiscovery(): Boolean {
        if (!isAutomaticConnectionEnabled() || uiStateStorage.state.first().hasShownConnectionPreferencesSmartDiscovery) {
            return false
        }

        val vpnUiStatus = vpnStatusProviderUI.uiStatus.first()

        if (
            vpnUiStatus.state !is VpnState.Connected ||
            vpnUiStatus.connectIntent?.profileId != null ||
            vpnUiStatus.connectIntent?.canBeExcluded != true
        ) {
            return false
        }

        val trafficStatus = trafficMonitor.trafficStatus.value ?: return false

        return trafficStatus.sessionTimeSeconds.seconds < 1.minutes
    }

    fun dismissDialog() {
        dialogState = null
    }

    fun togglePinned(item: RecentItemViewState) {
        if (item.isPinned) {
            recentsManager.unpin(item.id)
        } else {
            recentsManager.pin(item.id)
        }
    }

    fun removeRecent(item: RecentItemViewState) {
        recentsManager.remove(item.id)
    }

    suspend fun openPromoOffer(banner: PromoOfferBannerState, context: Context) {
        openPromoOffer(banner.action, banner.notificationId, banner.reference, context)
    }

    suspend fun openPromoOffer(banner: ProminentBannerState, context: Context) {
        openPromoOffer(banner.actionButton!!, banner.notificationId,  banner.reference, context)
    }

    private suspend fun openPromoOffer(
        action: ApiNotificationOfferButton,
        notificationId: String,
        reference: String?,
        context: Context
    ) {
        when {
            ApiNotificationActions.isOpenUrl(action.action) -> {
                val url = promoOfferButtonActions.getButtonUrl(action)
                if (url != null) { // It's not null on correctly defined notifications.
                    upgradeTelemetry.onUpgradeFlowStarted(UpgradeSource.PROMO_OFFER, reference)
                    upgradeTelemetry.onUpgradeAttempt(UpgradeFlowType.EXTERNAL)
                    context.openUrl(url)
                }
            }
            ApiNotificationActions.isInAppPurchasePopup(action.action) -> {
                if (action.panel != null) {
                    // Telemetry events are reported by the activity.
                    PromoOfferIapActivity.launch(context, notificationId)
                }
            }
        }
    }

    fun dismissPromoOffer(notificationId: String) {
        promoOffersPrefs.addVisitedOffer(notificationId)
    }

    fun onWidgetAdoptionShown() {
        widgetManager.onWidgetAdoptionShown()
    }

    @TargetApi(26)
    fun onAddWidget() {
        widgetManager.addWidget()
    }

    fun openConnectionPreferences() {
        dismissDialog()

        viewModelScope.launch {
            uiStateStorage.update { it.copy(isConnectionPreferencesDiscovered = true) }

            _eventFlow.emit(value = Event.OnNavigateToConnectionPreferences)
        }
    }

    fun onExcludedLocationsAdoptionClosed() {
        viewModelScope.launch {
            uiStateStorage.update { it.copy(shouldShowExcludedLocationsAdoption = false) }
        }
    }

    fun onExcludedLocationsAdoptionOpened() {
        viewModelScope.launch {
            uiStateStorage.update { it.copy(shouldShowExcludedLocationsAdoption = false) }

            openConnectionPreferences()
        }
    }

    private fun RecentConnection.toMaintenanceDialogType() = when(this) {
        is RecentConnection.UnnamedRecent -> connectIntent.let { intent ->
            when (intent) {
                is ConnectIntent.FastestInCountry -> DialogState.CountryInMaintenance
                is ConnectIntent.FastestInCity -> DialogState.CityInMaintenance
                is ConnectIntent.FastestInState -> DialogState.StateInMaintenance
                is ConnectIntent.SecureCore,
                is ConnectIntent.Server -> DialogState.ServerInMaintenance

                is ConnectIntent.Gateway ->
                    if (intent.serverId != null) DialogState.ServerInMaintenance
                    else DialogState.GatewayInMaintenance
            }
        }

        is RecentConnection.ProfileRecent -> DialogState.ProfileNotAvailable(profile.info.name)
    }

    // Returns true if reconnection is needed to apply the change
    fun disableCustomDns(): Boolean =
        disableCustomDnsForCurrentConnection()
}

private fun VpnState.toMapHighlightState() = when {
    this == VpnState.Connected -> CountryHighlight.CONNECTED
    isEstablishingConnection -> CountryHighlight.CONNECTING
    else -> null
}
