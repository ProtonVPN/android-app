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

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.netshield.NetShieldActions
import com.protonvpn.android.netshield.NetShieldProtocol
import com.protonvpn.android.redesign.app.ui.MainActivityViewModel
import com.protonvpn.android.redesign.app.ui.SettingsChangeViewModel
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.redesign.base.ui.extraPaddingForWindowSize
import com.protonvpn.android.redesign.home_screen.ui.HomeViewModel.DialogState
import com.protonvpn.android.redesign.main_screen.ui.MainScreenViewModel
import com.protonvpn.android.redesign.recents.ui.RecentBottomSheetDialog
import com.protonvpn.android.redesign.recents.ui.RecentItemViewState
import com.protonvpn.android.redesign.recents.ui.RecentsExpandState
import com.protonvpn.android.redesign.recents.ui.RecentsList
import com.protonvpn.android.redesign.recents.ui.rememberRecentBottomSheetState
import com.protonvpn.android.redesign.recents.ui.rememberRecentsExpandState
import com.protonvpn.android.redesign.recents.usecases.RecentsListViewState
import com.protonvpn.android.redesign.settings.ui.DefaultConnectionSelection
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.redesign.vpn.ui.VpnStatusBottom
import com.protonvpn.android.redesign.vpn.ui.VpnStatusTop
import com.protonvpn.android.redesign.vpn.ui.VpnStatusViewState
import com.protonvpn.android.redesign.vpn.ui.rememberVpnStateAnimationProgress
import com.protonvpn.android.redesign.vpn.ui.vpnStatusOverlayBackground
import com.protonvpn.android.telemetry.UpgradeSource
import com.protonvpn.android.tv.main.CountryHighlight
import com.protonvpn.android.ui.home.vpn.ChangeServerButton
import com.protonvpn.android.ui.planupgrade.CarouselUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradeNetShieldHighlightsFragment
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import com.protonvpn.android.ui.promooffers.ProminentBannerState
import com.protonvpn.android.ui.promooffers.PromoOfferBanner
import com.protonvpn.android.ui.promooffers.PromoOfferBannerState
import com.protonvpn.android.ui.promooffers.PromoOfferProminentBanner
import com.protonvpn.android.utils.Constants
import com.protonvpn.android.utils.openUrl
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnErrorUIManager
import com.protonvpn.android.widget.WidgetAdoptionUiType
import com.protonvpn.android.widget.ui.OnboardingWidgetBottomSheetContent
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme
import kotlin.math.roundToInt
import kotlin.reflect.KClass

typealias ShowcaseRecents = Boolean

@Composable
fun HomeRoute(
    mainScreenViewModel: MainScreenViewModel,
    onConnectionCardClick: () -> Unit
) {
    val activity = LocalActivity.current as ComponentActivity
    val vpnUiDelegate = LocalVpnUiDelegate.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activityViewModel: MainActivityViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val homeViewModel: HomeViewModel = hiltViewModel()
    val settingsChangeViewModel: SettingsChangeViewModel = hiltViewModel(viewModelStoreOwner = activity)
    // The MainActivity keeps the splash screen on until VPN status is available so that it can be shown here
    // instantly. That's why the flow from MainActivityViewModel is used, and not an independent flow in HomeViewModel.
    val vpnStatusViewState = activityViewModel.vpnStateViewFlow.collectAsStateWithLifecycle().value
    val recentsViewState = homeViewModel.recentsViewState.collectAsStateWithLifecycle(null)
    val mapState =
        homeViewModel.mapHighlightState.collectAsStateWithLifecycle(initialValue = null).value
    val dialogState = homeViewModel.dialogStateFlow.collectAsStateWithLifecycle().value
    val changeServerState =
        homeViewModel.changeServerViewState.collectAsStateWithLifecycle(null).value
    val upsellCarouselState = homeViewModel.upsellCarouselState.collectAsStateWithLifecycle().value
    val bottomPromoBannerState =
        homeViewModel.bottomPromoBannerStateFlow.collectAsStateWithLifecycle().value
    val prominentPromoBannerState =
        homeViewModel.prominentPromoBannerStateFlow.collectAsStateWithLifecycle().value
    val snackError = homeViewModel.snackbarErrorFlow.collectAsStateWithLifecycle().value
    val showWidgetAdoption = homeViewModel.showWidgetAdoptionFlow.collectAsStateWithLifecycle().value

    // Not using material3 snackbar because of inability to show multiline correctly
    val snackbarHostState = remember { androidx.compose.material.SnackbarHostState() }
    val fullyDrawn by remember { derivedStateOf { recentsViewState.value != null } }

    val bottomPromoComponent = bottomPromoBannerState?.let {
        BottomPromoComponent(
            state = it,
            onClick = {
                homeViewModel.openPromoOffer(it, context)
            },
            onDismiss = {
                homeViewModel.dismissPromoOffer(it.notificationId)
            }
        )
    }

    val connectionCardComponent = ConnectionCardComponent(
        changeServerState = changeServerState,
        onConnect = remember(vpnUiDelegate) {
            {
                coroutineScope.launch {
                    homeViewModel.connect(
                        vpnUiDelegate,
                        ConnectTrigger.ConnectionCard
                    )
                }
            }
        },
        onDisconnect = remember(homeViewModel) {
            { homeViewModel.disconnect(DisconnectTrigger.ConnectionCard) }
        },
        onChangeServerClick = { homeViewModel.changeServer(vpnUiDelegate) },
        onChangeServerUpgradeButtonShown = homeViewModel::onChangeServerUpgradeButtonShown,
        onConnectionCardClick = onConnectionCardClick
    )

    val recentsComponent = RecentsComponent(
        recentsViewState = recentsViewState,
        onRecentPinToggle = homeViewModel::togglePinned,
        onRecentRemove = homeViewModel::removeRecent,
        eventCollapseRecents = mainScreenViewModel.eventCollapseRecents,
        onEventCollapseRecentsConsumed = { mainScreenViewModel.consumeEventCollapseRecents() },
        onRecentClickedAction = remember(vpnUiDelegate) {
            { item -> coroutineScope.launch { homeViewModel.onRecentClicked(item, vpnUiDelegate) } }
        },
        recentsExpandState = rememberRecentsExpandState()
    )

    val prominentBannerPromo = prominentPromoBannerState?.let {
        ProminentPromoComponent(
            state = it,
            onClick = {
                homeViewModel.openPromoOffer(it, context)
            },
            onDismiss = {
                homeViewModel.dismissPromoOffer(it.notificationId)
            }
        )
    }
    val widgetAdoptionComponent = when(showWidgetAdoption) {
        WidgetAdoptionUiType.None -> null
        WidgetAdoptionUiType.AddWidgetButton ->
            WidgetAdoptionComponent(homeViewModel::onWidgetAdoptionShown, homeViewModel::onAddWidget)
        WidgetAdoptionUiType.Instructions ->
            WidgetAdoptionComponent(homeViewModel::onWidgetAdoptionShown, null)
    }

    HomeView(
        vpnState = vpnStatusViewState,
        mapState = mapState,
        dialogState = dialogState,
        upsellCarouselState = upsellCarouselState,
        elapsedRealtimeClock = homeViewModel.elapsedRealtimeClock,
        onDismissDialog = homeViewModel::dismissDialog,
        onNetshieldValueChanged = { homeViewModel.setNetShieldProtocol(it) },
        onDisableCustomDns = {
            if (homeViewModel.disableCustomDns())
                settingsChangeViewModel.showDnsReconnectionDialog(vpnUiDelegate)
        },
        snackbarHostState = snackbarHostState,
        bottomPromoComponent = bottomPromoComponent,
        prominentPromoComponent = prominentBannerPromo,
        connectionCardComponent = connectionCardComponent,
        recentsComponent = recentsComponent,
        widgetAdoptionComponent = widgetAdoptionComponent
    )

    LaunchedEffect(key1 = Unit) {
        homeViewModel.eventNavigateToUpgrade.collect {
            CarouselUpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(context)
        }
    }

    if (snackError != null) {
        LaunchedEffect(snackError) {
            handleSnackbarError(context, snackbarHostState, snackError)
            homeViewModel.consumeErrorMessage()
        }
    }

    if (fullyDrawn) {
        LaunchedEffect(key1 = Unit) {
            (context as Activity).reportFullyDrawn()
        }
    }
}

private val ListBgGradientHeightBasic = 100.dp
private val ListBgGradientOffsetBasic = ListBgGradientHeightBasic - 32.dp
private val ListBgGradientHeightExpanded = 200.dp
private val PromoOfferBannerState?.peekOffset get() = if (this?.isDismissible == true) 60.dp else 44.dp

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun HomeView(
    vpnState: VpnStatusViewState,
    mapState: Pair<String, CountryHighlight>?,
    dialogState: DialogState?,
    upsellCarouselState: UpsellCarouselState?,
    elapsedRealtimeClock: () -> Long,
    onDismissDialog: () -> Unit,
    onNetshieldValueChanged: (protocol: NetShieldProtocol) -> Unit,
    onDisableCustomDns: () -> Unit,
    bottomPromoComponent: BottomPromoComponent?,
    prominentPromoComponent: ProminentPromoComponent?,
    connectionCardComponent: ConnectionCardComponent,
    snackbarHostState: SnackbarHostState,
    recentsComponent: RecentsComponent,
    widgetAdoptionComponent: WidgetAdoptionComponent?,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vpnStateTransitionProgress = rememberVpnStateAnimationProgress(vpnState)
    val recentBottomSheetState = rememberRecentBottomSheetState()

    val changeServerButton: (@Composable ColumnScope.() -> Unit)? = connectionCardComponent.changeServerState?.let { state ->
        @Composable {
            ChangeServerButton(
                state,
                onChangeServerClick = connectionCardComponent.onChangeServerClick,
                onUpgradeButtonShown = connectionCardComponent.onChangeServerUpgradeButtonShown
            )
        }
    }
    widgetAdoptionComponent?.let {
        SimpleModalBottomSheet(
            onDismissRequest = it.onDismiss,
        ) {
            OnboardingWidgetBottomSheetContent(
                onClose = it.onDismiss,
                onAddWidget = it.onAddWidget
            )
        }
    }

    var defaultConnectionModalShown by remember { mutableStateOf(false) }
    val onDefaultConnectionOpen = { defaultConnectionModalShown = true }
    if (defaultConnectionModalShown) {
        val dismiss = { defaultConnectionModalShown = false }
        SimpleModalBottomSheet(
            onDismissRequest = dismiss,
        ) {
            Text(
                text = stringResource(R.string.select_default_connection),
                style = ProtonTheme.typography.body1Regular,
                modifier = Modifier.padding(16.dp)
            )
            DefaultConnectionSelection(
                onClose = dismiss
            )
        }
    }

    val bottomPromoBanner: (@Composable (Modifier) -> Unit)? =
        bottomPromoComponent?.let {
            @Composable { modifier ->
                PromoOfferBanner(
                    state = it.state,
                    onClick = it.onClick,
                    onDismiss = it.onDismiss,
                    modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                )
            }
        }

    val upsellCarouselContent: (@Composable (Modifier, Dp) -> Unit)? = upsellCarouselState?.let { state ->
        @Composable { modifier, paddingValues ->
            HomeUpsellCarousel(
                state = state,
                horizontalMargin = paddingValues + 16.dp,
                onOpenUpgradeScreen = { focus, upgradeSource -> launchUpgradeDialog(context, focus, upgradeSource) },
                modifier = modifier.padding(bottom = 24.dp),
            )
        }
    }

    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        val (vpnStatusTop, vpnStatusBottom, map) = createRefs()
        if (mapState != null) {
            Box(modifier = Modifier
                .constrainAs(map) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                }
            ) {
                val parallaxAlignment = remember(recentsComponent.recentsExpandState.fullExpandProgress) {
                    MapParallaxAlignment(recentsComponent.recentsExpandState.fullExpandProgress)
                }
                HomeMap(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(parallaxAlignment),
                    coroutineScope,
                    mapState,
                    elapsedRealtimeClock,
                )
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .vpnStatusOverlayBackground(vpnState)
        )
        val netShieldActions = remember {
            NetShieldActions(
                onChangeServerPromoUpgrade = { CarouselUpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(context) },
                onNetShieldValueChanged = onNetshieldValueChanged,
                onUpgradeNetShield = { CarouselUpgradeDialogActivity.launch<UpgradeNetShieldHighlightsFragment>(context) },
                onNetShieldLearnMore = { context.openUrl(Constants.URL_NETSHIELD_LEARN_MORE) },
                onDisableCustomDns = onDisableCustomDns,
            )
        }

        VpnStatusBottom(
            state = vpnState,
            transitionValue = { vpnStateTransitionProgress.value },
            netShieldActions = netShieldActions,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .constrainAs(vpnStatusBottom) {
                    top.linkTo(vpnStatusTop.bottom)
                    centerHorizontallyTo(parent)
                }
        )

        val listBgColor = ProtonTheme.colors.backgroundNorm
        val listBgGradientColors = listOf(Color.Transparent, listBgColor)
        val recentsShowcaseRevealHeight = with (LocalDensity.current) { 56.dp.toPx() }
        recentsComponent.eventCollapseRecents.collectAsEffect { showcaseRecents ->
            recentsComponent.onEventCollapseRecentsConsumed()
            recentsComponent.recentsExpandState.collapse()
            if (showcaseRecents) {
                recentsComponent.recentsExpandState.peekBelowTheFold(recentsShowcaseRevealHeight)
            }
        }
        BoxWithConstraints {
            val viewportSize = DpSize(maxWidth, maxHeight)
            val widthSizeClass = remember(viewportSize) {
                // Normally the window size class should be computed from the screen size (e.g. in activity).
                // The Home view can however be displayed side-by-side with the connection panel, so compute its
                // size class here to take that into account.
                WindowSizeClass.calculateFromSize(viewportSize).widthSizeClass
            }
            val recentsViewStateValue = recentsComponent.recentsViewState.value
            if (recentsViewStateValue != null) {
                val maxHeightPx = LocalDensity.current.run { maxHeight.toPx() }
                recentsComponent.recentsExpandState.setMaxHeight(maxHeightPx.roundToInt())
                val horizontalPadding = ProtonTheme.extraPaddingForWindowSize(viewportSize)

                val listBgGradientHeight =
                    if (widthSizeClass == WindowWidthSizeClass.Compact) ListBgGradientHeightBasic else ListBgGradientHeightExpanded
                val listBgGradientOffset =
                    if (widthSizeClass == WindowWidthSizeClass.Compact) ListBgGradientOffsetBasic else ListBgGradientHeightExpanded / 2

                RecentsList(
                    viewState = recentsViewStateValue,
                    expandState = recentsComponent.recentsExpandState,
                    changeServerButton = changeServerButton,
                    promoBanner = bottomPromoBanner,
                    promoBannerPeekOffset = bottomPromoComponent?.state.peekOffset ?: 0.dp,
                    upsellContent = upsellCarouselContent,
                    onConnectClicked = connectionCardComponent.onConnect,
                    onDisconnectClicked = connectionCardComponent.onDisconnect,
                    onOpenConnectionPanelClicked = connectionCardComponent.onConnectionCardClick,
                    onOpenDefaultConnection = onDefaultConnectionOpen,
                    onRecentClicked = recentsComponent.onRecentClickedAction,
                    onRecentOpen = { recentBottomSheetState.onRecentSettingOpen(it) },
                    horizontalPadding = horizontalPadding,
                    topPadding = listBgGradientOffset,
                    errorSnackBar = snackbarHostState,
                    modifier = Modifier
                        .offset { IntOffset(0, recentsComponent.recentsExpandState.listOffsetPx) }
                        .drawBehind {
                            val gradientBottom = listBgGradientHeight.toPx()
                            drawRect(
                                brush = Brush.linearGradient(
                                    listBgGradientColors,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, gradientBottom)
                                )
                            )
                            drawRect(listBgColor, topLeft = Offset(0f, gradientBottom))
                        }
                )
            }
        }

        RecentBottomSheetDialog(
            state = recentBottomSheetState,
            onRecentPinToggle = recentsComponent.onRecentPinToggle,
            onRecentRemove = recentsComponent.onRecentRemove
        )

        val vpnStatusTopMinHeight = 48.dp
        val fullCoverThresholdPx = LocalDensity.current.run {
            (ListBgGradientOffsetBasic - vpnStatusTopMinHeight).toPx()
        }
        val coverAlpha = remember(fullCoverThresholdPx) {
            derivedStateOf { calculateOverlayAlpha(recentsComponent.recentsExpandState.listOffsetPx, fullCoverThresholdPx) }
        }
        VpnStatusTop(
            vpnState,
            transitionValue = { vpnStateTransitionProgress.value },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = vpnStatusTopMinHeight)
                .recentsScrollOverlayBackground(coverAlpha)
                .statusBarsPadding()
                .constrainAs(vpnStatusTop) {}
        )

        prominentPromoComponent?.let {
            PromoOfferProminentBanner(
                state = it.state,
                onDismiss = it.onDismiss,
                onAction = it.onClick,
                contentWindowInsets = WindowInsets.statusBars,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    HomeDialog(dialogState, onDismiss = onDismissDialog)
}

data class BottomPromoComponent(
    val state: PromoOfferBannerState,
    val onDismiss: () -> Unit,
    val onClick: (suspend () -> Unit)
)

data class WidgetAdoptionComponent(
    val onDismiss: () -> Unit,
    val onAddWidget: (() -> Unit)?
)

data class ProminentPromoComponent(
    val state: ProminentBannerState,
    val onDismiss: () -> Unit,
    val onClick: (suspend () -> Unit)
)

data class ConnectionCardComponent(
    val changeServerState: ChangeServerViewState?,
    val onConnect: () -> Unit,
    val onDisconnect: () -> Unit,
    val onChangeServerClick: () -> Unit,
    val onChangeServerUpgradeButtonShown: () -> Unit,
    val onConnectionCardClick: () -> Unit
)

data class RecentsComponent(
    val recentsViewState: State<RecentsListViewState?>,
    val onRecentPinToggle: (RecentItemViewState) -> Unit,
    val onRecentRemove: (RecentItemViewState) -> Unit,
    val eventCollapseRecents: SharedFlow<ShowcaseRecents>,
    val onEventCollapseRecentsConsumed: () -> Unit,
    val onRecentClickedAction: (RecentItemViewState) -> Unit,
    val recentsExpandState: RecentsExpandState
)

private suspend fun handleSnackbarError(
    context: Context,
    snackbarHostState: androidx.compose.material.SnackbarHostState,
    snackError: VpnErrorUIManager.SnackError,
) {
    val snackbarResult = snackbarHostState.showSnackbar(
        message = context.getString(snackError.errorRes, snackError.additionalDetails),
        actionLabel = snackError.helpUrl?.actionTitleRes?.let { actionRes ->
            context.getString(
                actionRes
            )
        },
        duration = if (snackError.helpUrl == null) androidx.compose.material.SnackbarDuration.Long else androidx.compose.material.SnackbarDuration.Short
    )

    if (snackError.helpUrl != null && snackbarResult == androidx.compose.material.SnackbarResult.ActionPerformed) {
        context.openUrl(snackError.helpUrl.actionUrl)
    }
}

@Composable
private fun HomeDialog(dialog: DialogState?, onDismiss: () -> Unit) {
    if (dialog != null) {
        val message = when (dialog) {
            DialogState.CountryInMaintenance -> stringResource(R.string.message_country_servers_in_maintenance)
            DialogState.CityInMaintenance -> stringResource(R.string.message_city_servers_in_maintenance)
            DialogState.StateInMaintenance -> stringResource(R.string.message_state_servers_in_maintenance)
            DialogState.ServerInMaintenance -> stringResource(R.string.message_server_in_maintenance)
            DialogState.GatewayInMaintenance -> stringResource(R.string.message_gateway_in_maintenance)
            DialogState.ServerNotAvailable -> stringResource(R.string.message_server_not_available)
            is DialogState.ProfileNotAvailable ->
                stringResource(R.string.profile_unavailable_dialog_message, dialog.profileName)
        }
        ProtonAlert(
            title = null,
            text = message,
            confirmLabel = stringResource(id = R.string.ok),
            onConfirm = { onDismiss() },
            onDismissRequest = onDismiss
        )
    }
}

private fun Modifier.recentsScrollOverlayBackground(
    alpha: State<Float>
): Modifier = composed {
    val separatorHeight = 1.dp
    val backgroundColor = ProtonTheme.colors.backgroundNorm
    val separatorColor = ProtonTheme.colors.separatorNorm
    drawBehind {
        drawRect(color = backgroundColor, alpha = alpha.value)
        drawRect(
            color = separatorColor,
            topLeft = Offset(0f, size.height - separatorHeight.toPx()),
            size = Size(size.width, separatorHeight.toPx()),
            alpha = alpha.value
        )
    }
}

private fun calculateOverlayAlpha(offset: Int, fullCoverPx: Float): Float {
    return when {
        offset >= 0 && offset < fullCoverPx -> 1f - offset.toFloat() / fullCoverPx
        else -> 0f
    }
}

private fun launchUpgradeDialog(context: Context, focusFragment: KClass<out Fragment>, upgradeSource: UpgradeSource) {
    CarouselUpgradeDialogActivity.launch(context, upgradeSource, focusFragment)
}
