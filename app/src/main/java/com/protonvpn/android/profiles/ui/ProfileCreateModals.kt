/*
 * Copyright (c) 2024 Proton AG
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

package com.protonvpn.android.profiles.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.base.ui.ProtonBasicAlert
import com.protonvpn.android.redesign.base.ui.ProtonDialogButton
import com.protonvpn.android.redesign.base.ui.SettingsRadioItemSmall
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.vpn.ui.label
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

enum class ProfileType(
    @StringRes val nameRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Standard(R.string.create_profile_type_standard, CoreR.drawable.ic_proton_earth),
    SecureCore(R.string.create_profile_type_secure_core, CoreR.drawable.ic_proton_locks),
    P2P(R.string.create_profile_type_p2p, CoreR.drawable.ic_proton_arrow_right_arrow_left),
    Gateway(R.string.create_profile_gateway, CoreR.drawable.ic_proton_servers),
}

@Composable
fun ProfileTypeItem(
    currentValue: ProfileType,
    onClick: () -> Unit,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_type_title,
        valueText = stringResource(currentValue.nameRes),
        iconContent = {
            Icon(
                painterResource(currentValue.iconRes),
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 2.dp).size(20.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
fun PickProfileType(
    selectedProfileType: ProfileType,
    allTypes: List<ProfileType>,
    onSelect: (ProfileType) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        R.string.create_profile_pick_type_title,
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn {
            items(allTypes) { profileType ->
                SettingsRadioItemSmall(
                    title = stringResource(profileType.nameRes),
                    description = null,
                    selected = profileType == selectedProfileType,
                    onSelected = { onSelect(profileType) },
                    leadingContent = {
                        Icon(
                            painterResource(profileType.iconRes),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(20.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileCountryItem(
    currentValue: CountryId,
    onClick: () -> Unit,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_country_title,
        valueText = currentValue.label(),
        iconContent = { Flag(exitCountry = currentValue, modifier = Modifier.size(24.dp, 16.dp)) },
        onClick = onClick
    )
}

@Composable
fun PickCountry(
    selectedCountry: CountryId,
    allCountries: List<CountryId>,
    onSelect: (CountryId) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        R.string.create_profile_pick_country_title,
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn {
            items(allCountries) { country ->
                SettingsRadioItemSmall(
                    title = country.label(),
                    description = null,
                    selected = country == selectedCountry,
                    onSelected = { onSelect(country) },
                    leadingContent = {
                        Flag(exitCountry = selectedCountry, modifier = Modifier.padding(end = 12.dp).size(24.dp, 16.dp))
                    }
                )
            }
        }
    }
}

data class CityStateInfo(
    val name: String?,
    val id: CityStateId,
)

@Composable
fun ProfileCityOrStateItem(
    currentValue: CityStateInfo,
    onClick: () -> Unit,
) {
    val isState = currentValue.id.isState
    ProfileValueItem(
        labelRes =
            if (isState) R.string.create_profile_pick_state_title
            else R.string.create_profile_pick_city_title,
        valueText = currentValue.name ?:
            if (isState) stringResource(R.string.create_profile_fastest_state)
            else stringResource(R.string.create_profile_fastest_city),
        iconContent = {
            Icon(
                painterResource(
                    if (currentValue.id.isFastest) CoreR.drawable.ic_proton_bolt //TODO: add filled
                    else CoreR.drawable.ic_proton_map_pin
                ),
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 2.dp).size(20.dp)
            )
        },
        onClick = onClick
    )
}

//TODO: use check instead of radio
@Composable
fun PickCityOrState(
    isState: Boolean,
    selectedCityState: CityStateInfo,
    allCityStates: List<CityStateInfo>,
    onSelect: (CityStateInfo) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        if (isState) R.string.create_profile_pick_state_title
        else R.string.create_profile_pick_city_title,
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn {
            items(allCityStates) { cityState ->
                SettingsRadioItemSmall(
                    title = cityState.name ?:
                        if (isState) stringResource(R.string.create_profile_fastest_state)
                        else stringResource(R.string.create_profile_fastest_city),
                    description = null,
                    selected = cityState.id == selectedCityState.id,
                    onSelected = { onSelect(cityState) },
                    leadingContent = {
                        Icon(
                            painterResource(
                                if (cityState.id.isFastest) CoreR.drawable.ic_proton_bolt //TODO: add filled
                                else CoreR.drawable.ic_proton_map_pin
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 14.dp)
                                .size(20.dp)
                        )
                    }
                )
            }
        }
    }
}

data class ServerInfo(
    val name: String?,
    val id: String,
    val isFastest: Boolean,
    val isGateway: Boolean,
)

@Composable
fun ProfileServerOrGatewayInfo(
    serverInfo: ServerInfo,
    onClick: () -> Unit,
) {
    val isGateway = serverInfo.isGateway
    ProfileValueItem(
        labelRes =
            if (isGateway) R.string.create_profile_pick_gateway_title
            else R.string.create_profile_pick_server_title,
        valueText = serverInfo.name ?: stringResource(R.string.create_profile_fastest_server),
        iconContent = {
            Icon(
                painterResource(
                    if (serverInfo.isFastest) CoreR.drawable.ic_proton_bolt //TODO: add filled
                    else CoreR.drawable.ic_proton_servers
                ),
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 2.dp).size(20.dp)
            )
        },
        onClick = onClick,
    )
}

//TODO: use check instead of radio
@Composable
fun PickServerOrGateway(
    isGateway: Boolean,
    selectedServer: ServerInfo,
    allServers: List<ServerInfo>,
    onSelect: (ServerInfo) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        if (isGateway) R.string.create_profile_pick_gateway_title
        else R.string.create_profile_pick_server_title,
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn {
            items(allServers) { server ->
                SettingsRadioItemSmall(
                    title = server.name ?: stringResource(R.string.create_profile_fastest_server),
                    description = null,
                    selected = server.id == selectedServer.id,
                    onSelected = { onSelect(server) },
                    leadingContent = {
                        Icon(
                            painterResource(
                                if (server.isFastest) CoreR.drawable.ic_proton_bolt //TODO: add filled
                                else CoreR.drawable.ic_proton_servers
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 14.dp)
                                .size(20.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileNetShieldItem(
    value: Boolean,
    onClick: () -> Unit,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_netshield_title,
        valueText = stringResource(if (value) R.string.netshield_state_on else R.string.netshield_state_off),
        iconContent = {
            Image(
                painterResource(if (value) R.drawable.feature_netshield_on else R.drawable.ic_netshield_off),
                contentDescription = null,
                modifier = Modifier.padding(horizontal = 2.dp).size(26.67.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
fun PickNetShield(
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        R.string.create_profile_pick_netshield_title,
        onDismissRequest = onDismissRequest
    ) {
        listOf(true, false).forEach { value ->
            SettingsRadioItemSmall(
                title = stringResource(if (value) R.string.netshield_state_on else R.string.netshield_state_off),
                description = null,
                selected = value == selected,
                onSelected = { onSelect(value) },
                leadingContent = {
                    Image(
                        painterResource(if (value) R.drawable.feature_netshield_on else R.drawable.ic_netshield_off),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(26.67.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun ProfileValueItem(
    @StringRes labelRes: Int,
    valueText: String,
    iconContent: (@Composable RowScope.() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(labelRes),
            style = ProtonTheme.typography.captionMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ProtonTheme.shapes.medium)
                .clickable(onClick = onClick)
                .background(ProtonTheme.colors.backgroundSecondary)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconContent != null) {
                iconContent()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = valueText,
                style = ProtonTheme.typography.body1Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_proton_chevron_down),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun BaseItemPickerDialog(
    @StringRes title: Int,
    onDismissRequest: () -> Unit,
    itemList: @Composable () -> Unit,
) {
    ProtonBasicAlert(
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .largeScreenContentPadding()
        ) {
            Text(
                stringResource(title),
                style = ProtonTheme.typography.body1Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            itemList()
            ProtonDialogButton(
                onClick = onDismissRequest,
                text = stringResource(R.string.cancel),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Preview
@Composable
fun ProfileTypeItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileTypeItem(currentValue = ProfileType.Standard, onClick = {})
        }
    }
}

@Preview
@Composable
fun PickProfileTypePreview() {
    Surface {
        VpnTheme(isDark = true) {
            PickProfileType(
                ProfileType.Standard,
                ProfileType.entries,
                {},
                {}
            )
        }
    }
}

@Preview
@Composable
fun ProfileCountryItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileCountryItem(
                CountryId("PL"),
                {}
            )
        }
    }
}

@Preview
@Composable
fun PickCountryDialogPreview() {
    Surface {
        VpnTheme(isDark = true) {
            PickCountry(
                CountryId("PL"),
                listOf(
                    CountryId("DE"),
                    CountryId("PL"),
                    CountryId("US"),
                ),
                {},
                {}
            )
        }
    }
}

@Preview
@Composable
fun ProfileCityOrStateItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileCityOrStateItem(
                CityStateInfo("New York", CityStateId("NY", isState = false)),
                {}
            )
        }
    }
}

@Preview
@Composable
fun PickCityStatePreview() {
    VpnTheme(isDark = true) {
        Surface {
            PickCityOrState(
                isState = false,
                selectedCityState = CityStateInfo("New York", CityStateId("NY", isState = false)),
                allCityStates = listOf(
                    CityStateInfo(null, CityStateId("", isState = false)),
                    CityStateInfo("New York", CityStateId("NY", isState = false)),
                    CityStateInfo("Los Angeles", CityStateId("CA", isState = false)),
                    CityStateInfo("Chicago", CityStateId("IL", isState = false)),
                ),
                {},
                {}
            )
        }
    }
}

@Preview
@Composable
fun ProfileServerOrGatewayItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileServerOrGatewayInfo(
                ServerInfo("US-TX#1", "1", isFastest = false, isGateway = false),
                {}
            )
        }
    }
}

@Preview
@Composable
fun PickServerPreview() {
    VpnTheme(isDark = true) {
        Surface {
            PickServerOrGateway(
                isGateway = false,
                ServerInfo("US-TX#1", "1", isFastest = false, isGateway = false),
                listOf(
                    ServerInfo(null, "0", isFastest = true, isGateway = false),
                    ServerInfo("US-TX#1", "1", isFastest = false, isGateway = false),
                    ServerInfo("US-TX#2", "2", isFastest = false, isGateway = false),
                ),
                {},
                {}
            )
        }
    }
}

@Preview
@Composable
fun ProfileNetShieldItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileNetShieldItem(
                true,
                {}
            )
        }
    }
}

@Preview
@Composable
fun PickNetShieldPreview() {
    VpnTheme(isDark = true) {
        Surface {
            PickNetShield(
                selected = true,
                {},
                {}
            )
        }
    }
}