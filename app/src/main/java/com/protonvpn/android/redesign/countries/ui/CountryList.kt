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

package com.protonvpn.android.redesign.countries.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.protonElevation
import com.protonvpn.android.redesign.app.ui.MainActivityViewModel
import com.protonvpn.android.redesign.base.ui.LocalVpnUiDelegate
import com.protonvpn.android.redesign.base.ui.VpnDivider
import com.protonvpn.android.redesign.home_screen.ui.ShowcaseRecents
import com.protonvpn.android.redesign.settings.ui.CollapsibleToolbarScaffold
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultSmallUnspecified
import me.proton.core.presentation.utils.currentLocale
import me.proton.core.presentation.R as CoreR

@Composable
fun CountryListRoute(
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val activity = LocalContext.current as ComponentActivity
    val activityViewModel: MainActivityViewModel = hiltViewModel(viewModelStoreOwner = activity)
    val showNewUI = activityViewModel.showNewCountryList.collectAsStateWithLifecycle().value
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (showNewUI) {
            null -> {}
            true -> BaseCountryListRoute(
                onNavigateToHomeOnConnect,
                onNavigateToSearch,
                hiltViewModel<CountryListViewModel>(),
                R.string.countries_title
            )
            false -> OldCountryListRoute(onNavigateToHomeOnConnect, onNavigateToSearch)
        }
    }
}

// This route is shared by both Gateways and Countries main screens.
@Composable
fun BaseCountryListRoute(
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    onNavigateToSearch: (() -> Unit)?,
    viewModel: BaseCountryListViewModel,
    @StringRes titleRes: Int,
) {
    val uiDelegate = LocalVpnUiDelegate.current
    val context = LocalContext.current

    val locale = LocalConfiguration.current.currentLocale()
    LaunchedEffect(Unit) {
        viewModel.localeFlow.value = locale
    }
    val mainState = viewModel.stateFlow.collectAsStateWithLifecycle().value ?: return
    val navigateToHome = { showcaseRecents: ShowcaseRecents -> onNavigateToHomeOnConnect(showcaseRecents) }
    val navigateToUpsell = { UpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(context) }
    fun createOnItemOpen(filter: ServerFilterType): (CountryListItemState) -> Unit = { item ->
        viewModel.onItemOpen(item, filter)
    }
    fun createOnConnectAction(filter: ServerListFilter): (CountryListItemState) -> Unit = { item ->
        viewModel.onItemConnect(
            vpnUiDelegate = uiDelegate,
            item = item,
            filter = filter,
            navigateToHome = navigateToHome,
            navigateToUpsell = navigateToUpsell
        )
    }

    ToolbarWithFilters(
        onNavigateToSearch = onNavigateToSearch,
        toolbarFilters = mainState.filterButtons,
        titleRes = titleRes,
        content = {
            CountryList(
                modifier = Modifier.padding(it),
                mainState.items,
                onCountryClick = createOnConnectAction(mainState.savedState.filter),
                onOpenCountry = createOnItemOpen(mainState.savedState.filter.type),
            )
        }
    )

    val subScreenState = viewModel.subScreenStateFlow.collectAsStateWithLifecycle().value

    if (subScreenState != null) {
        CountryBottomSheet(
            modifier = Modifier,
            screen = subScreenState,
            onNavigateBack = { onHide -> viewModel.onNavigateBack(onHide) },
            onNavigateToItem = createOnItemOpen(subScreenState.savedState.filter.type),
            onItemClicked = createOnConnectAction(subScreenState.savedState.filter),
            onClose = { viewModel.onClose() }
        )
    }
}

@Composable
fun ToolbarWithFilters(
    onNavigateToSearch: (() -> Unit)?,
    toolbarFilters: List<FilterButton>?,
    @StringRes titleRes: Int,
    content: @Composable (PaddingValues) -> Unit,
) {
    CollapsibleToolbarScaffold(
        titleResId = titleRes,
        contentWindowInsets = WindowInsets.statusBars,
        toolbarActions = {
            if (onNavigateToSearch != null) Icon(
                painter = painterResource(id = CoreR.drawable.ic_proton_magnifier),
                contentDescription = stringResource(R.string.accessibility_action_search),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onNavigateToSearch)
                    .padding(12.dp),
            )
        },
        toolbarAdditionalContent = {
            if (toolbarFilters != null) FiltersRow(
                buttonActions = toolbarFilters,
                allLabelRes = R.string.country_filter_all,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        },
    ) { padding ->
        content(padding)
    }
}

@Composable
fun FiltersRow(buttonActions: List<FilterButton>, modifier: Modifier = Modifier, @StringRes allLabelRes: Int) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(
            items = buttonActions,
            itemContent = { filterButton ->
                Button(
                    onClick = filterButton.onClick,
                    modifier = Modifier.heightIn(min = ButtonDefaults.MinHeight),
                    elevation = ButtonDefaults.protonElevation(),
                    shape = ProtonTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (filterButton.isSelected) ProtonTheme.colors.brandNorm else ProtonTheme.colors.interactionWeakNorm,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val iconRes = when (filterButton.filter) {
                        ServerFilterType.All -> null
                        ServerFilterType.SecureCore -> CoreR.drawable.ic_proton_locks
                        ServerFilterType.P2P -> CoreR.drawable.ic_proton_arrow_right_arrow_left
                        ServerFilterType.Tor -> CoreR.drawable.ic_proton_brand_tor
                    }
                    iconRes?.let {
                        Icon(
                            painter = painterResource(id = it),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    val filterTitleRes = when (filterButton.filter) {
                        ServerFilterType.All -> allLabelRes
                        ServerFilterType.SecureCore -> R.string.country_filter_secure_core
                        ServerFilterType.P2P -> R.string.country_filter_p2p
                        ServerFilterType.Tor -> R.string.country_filter_tor
                    }
                    Text(
                        text = stringResource(id = filterTitleRes),
                        style = ProtonTheme.typography.defaultSmallUnspecified
                    )
                }
            }
        )
    }
}

@Composable
fun CountryList(
    modifier: Modifier,
    countries: List<CountryListItemState>,
    onOpenCountry: (CountryListItemState) -> Unit,
    onCountryClick: (CountryListItemState) -> Unit
) {
    LazyColumn(modifier = modifier) {
        countries.forEach { country ->
            item {
                CountryListItem(country, onOpenCountry, onCountryClick)
                VpnDivider()
            }
        }
    }
}