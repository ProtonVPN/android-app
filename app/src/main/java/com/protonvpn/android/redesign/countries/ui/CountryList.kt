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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.Alignment
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
import com.protonvpn.android.ui.onboarding.heroNorm
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultUnspecified
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
            true -> NewCountryListRoute(onNavigateToHomeOnConnect, onNavigateToSearch)
            false -> OldCountryListRoute(onNavigateToHomeOnConnect, onNavigateToSearch)
        }
    }
}

@Composable
fun NewCountryListRoute(
    onNavigateToHomeOnConnect: (ShowcaseRecents) -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val viewModel = hiltViewModel<CountryListViewModel>()
    val uiDelegate = LocalVpnUiDelegate.current
    val context = LocalContext.current

    val locale = LocalConfiguration.current.currentLocale()
    LaunchedEffect(Unit) {
        viewModel.localeFlow.value = locale
    }

    val navigateToHome = { showcaseRecents: ShowcaseRecents -> onNavigateToHomeOnConnect(showcaseRecents) }
    val navigateToUpsell = { UpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(context) }

    val mainState = viewModel.stateFlow.collectAsStateWithLifecycle().value ?: return

    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .fillMaxSize()
    ) {
        Column {
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_proton_magnifier),
                contentDescription = stringResource(R.string.accessibility_action_search),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp, bottom = 4.dp, end = 12.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNavigateToSearch)
                    .padding(12.dp),
            )
            Text(
                text = stringResource(id = R.string.tabsCountries),
                style = ProtonTheme.typography.heroNorm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp, bottom = 8.dp)
            )

            FiltersRow(
                buttonActions = mainState.filterButtons,
                allLabelRes = R.string.country_filter_all
            )
            Spacer(modifier = Modifier.size(8.dp))
        }

        CountryList(
            modifier = Modifier.weight(1f),
            mainState.items,
            onCountryClick = { viewModel.onItemConnect(uiDelegate, it, mainState.savedState.filter, navigateToHome, navigateToUpsell) },
            onOpenCountry = { viewModel.onItemOpen(it, mainState.savedState.filter.type) }
        )
    }

    val subScreenState = viewModel.subScreenStateFlow.collectAsStateWithLifecycle().value
    if (subScreenState != null) {
        CountryBottomSheet(
            modifier = Modifier,
            screen = subScreenState,
            onNavigateBack = { onHide -> viewModel.onNavigateBack(onHide) },
            onNavigateToItem = { item -> viewModel.onItemOpen(item, subScreenState.savedState.filter.type) },
            onItemClicked = { viewModel.onItemConnect(uiDelegate, it, subScreenState.savedState.filter, navigateToHome, navigateToUpsell) },
            onClose = { viewModel.onClose() }
        )
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
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    val filterTitleRes = when (filterButton.filter) {
                        ServerFilterType.All -> allLabelRes
                        ServerFilterType.SecureCore -> R.string.country_filter_secure_core
                        ServerFilterType.P2P -> R.string.country_filter_p2p
                        ServerFilterType.Tor -> R.string.country_filter_tor
                    }
                    Text(
                        text = stringResource(id = filterTitleRes),
                        style = ProtonTheme.typography.defaultUnspecified
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