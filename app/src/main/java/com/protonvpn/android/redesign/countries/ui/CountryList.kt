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

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ComposableCountryListBinding
import com.protonvpn.android.redesign.base.ui.collectAsEffect
import com.protonvpn.android.ui.home.InformationActivity
import com.protonvpn.android.ui.home.countries.CountryListViewModel
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultStrongNorm


@Composable
fun CountryListRoute(
    onNavigateToHomeOnConnect: () -> Unit,
    onNavigateToSearch: () -> Unit,
) {
    val context = LocalContext.current as AppCompatActivity
    val viewModel: CountryListViewModel = hiltViewModel(viewModelStoreOwner = context)

    // Bridge back navigation to home screen upon connection
    viewModel.navigateToHomeEvent.collectAsEffect(block = {
        onNavigateToHomeOnConnect()
    })
    CountryList(onNavigateToSearch = onNavigateToSearch, onNavigateToInformation = {
        context.startActivity(
            InformationActivity.createIntent(
                context,
                InformationActivity.InfoType.generic
            )
        )
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryList(
    onNavigateToSearch: () -> Unit,
    onNavigateToInformation: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(id = R.string.tabsCountries),
                    style = ProtonTheme.typography.defaultStrongNorm,
                )
            },
            actions = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_info_circle),
                    contentDescription = stringResource(id = R.string.activity_information_title),
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(20))
                        .clickable(onClick = onNavigateToInformation)
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = stringResource(id = R.string.server_search_menu_title),
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(20))
                        .clickable(onClick = onNavigateToSearch)
                )
            }
        )

        AndroidViewBinding(ComposableCountryListBinding::inflate)
    }
}
