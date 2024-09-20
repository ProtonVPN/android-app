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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.base.ui.FlagDefaults
import com.protonvpn.android.redesign.base.ui.ProtonBasicAlert
import com.protonvpn.android.redesign.base.ui.ProtonDialogButton
import com.protonvpn.android.redesign.base.ui.SettingsRadioItemSmall
import com.protonvpn.android.redesign.base.ui.largeScreenContentPadding
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.settings.ui.ProtocolBadge
import com.protonvpn.android.redesign.settings.ui.ProtocolItem
import com.protonvpn.android.redesign.settings.ui.SettingsSectionHeading
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.redesign.vpn.ui.viaCountry
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.presentation.R as CoreR

@Composable
fun ProfileTypeItem(
    currentValue: ProfileType,
    allTypes: List<ProfileType>,
    onChangeType: (ProfileType) -> Unit,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_type_title,
        valueText = stringResource(currentValue.nameRes),
        iconContent = {
            Icon(
                painterResource(currentValue.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(20.dp)
            )
        },
        modal = { closeModal ->
            PickProfileType(
                currentValue,
                allTypes = allTypes,
                onSelect = {
                    onChangeType(it)
                    closeModal()
                },
                onDismissRequest = closeModal
            )
        }
    )
}

@Composable
private fun PickProfileType(
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
    secureCore: Boolean,
    exitCountry: CountryId,
    entryCountry: CountryId?,
    allCountries: List<CountryId>,
    allEntries: List<CountryId>,
    onSelectExit: (CountryId) -> Unit,
    onSelectEntry: (CountryId) -> Unit,
) {
    Column {
        ProfileValueItem(
            labelRes = R.string.create_profile_pick_country_title,
            valueText = exitCountry.label(),
            iconContent = {
                Flag(
                    exitCountry = exitCountry,
                    entryCountry = if (secureCore) CountryId.fastest else null,
                    modifier = Modifier.scale(24f / FlagDefaults.singleFlagSize.width.value)
                )
            },
            modal = { closeModal ->
                PickCountry(
                    isVia = false,
                    isSecureCore = secureCore,
                    selectedCountry = exitCountry,
                    allCountries = allCountries,
                    onSelect = { country ->
                        onSelectExit(country)
                        closeModal()
                    },
                    onDismissRequest = closeModal
                )
            },
            bottomPadding = if (secureCore) 0.dp else 20.dp
        )
        if (entryCountry != null) {
            ProfileValueItem(
                labelRes = null,
                valueText = viaCountry(entryCountry),
                iconContent = {
                    Flag(
                        exitCountry = entryCountry,
                        modifier = Modifier.size(24.dp, 16.dp)
                    )
                },
                modal = { closeModal ->
                    PickCountry(
                        isVia = true,
                        isSecureCore = false,
                        selectedCountry = entryCountry,
                        allCountries = allEntries,
                        onSelect = {
                            onSelectEntry(it)
                            closeModal()
                        },
                        onDismissRequest = closeModal
                    )
                }
            )
        }
    }
}

@Composable
private fun PickCountry(
    isVia: Boolean,
    isSecureCore: Boolean,
    selectedCountry: CountryId,
    allCountries: List<CountryId>,
    onSelect: (CountryId) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        title =
            if (isVia) R.string.create_profile_pick_middle_country_title
            else R.string.create_profile_pick_country_title,
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn {
            items(allCountries) { country ->
                SettingsRadioItemSmall(
                    title = if (isVia) viaCountry(country) else country.label(),
                    description = null,
                    selected = country == selectedCountry,
                    onSelected = { onSelect(country) },
                    leadingContent = {
                        Flag(
                            exitCountry = country,
                            entryCountry = if (isSecureCore) CountryId.fastest else null,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .scale(24f / FlagDefaults.singleFlagSize.width.value)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileGatewayItem(
    currentValue: String,
    allGateways: List<String>,
    onSelect: (String) -> Unit,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_gateway_title,
        valueText = currentValue,
        iconContent = {
            Image(
                painterResource(R.drawable.ic_gateway_flag),
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(24.dp, 16.dp)
            )
        },
        modal = { closeModal ->
            PickGateway(
                selectedGateway = currentValue,
                allGateways = allGateways,
                onSelect = {
                    onSelect(it)
                    closeModal()
                },
                onDismissRequest = closeModal
            )
        }
    )
}

@Composable
fun PickGateway(
    selectedGateway: String,
    allGateways: List<String>,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        R.string.create_profile_pick_gateway_title,
        onDismissRequest = onDismissRequest
    ) {
        LazyColumn {
            items(allGateways) { gateway ->
                SettingsRadioItemSmall(
                    title = gateway,
                    description = null,
                    selected = gateway == selectedGateway,
                    onSelected = { onSelect(gateway) },
                    leadingContent = {
                        Icon(
                            painterResource(CoreR.drawable.ic_proton_servers),
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
fun ProfileCityOrStateItem(
    currentValue: TypeAndLocationScreenState.CityOrState,
    allCityStates: List<TypeAndLocationScreenState.CityOrState>,
    onSelect: (TypeAndLocationScreenState.CityOrState) -> Unit,
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
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(20.dp)
            )
        },
        modal = { closeModal ->
            PickCityOrState(
                isState = isState,
                selectedCityState = currentValue,
                allCityStates = allCityStates,
                onSelect = {
                    onSelect(it)
                    closeModal()
                },
                onDismissRequest = closeModal
            )
        }
    )
}

@Composable
fun PickCityOrState(
    isState: Boolean,
    selectedCityState: TypeAndLocationScreenState.CityOrState,
    allCityStates: List<TypeAndLocationScreenState.CityOrState>,
    onSelect: (TypeAndLocationScreenState.CityOrState) -> Unit,
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

@Composable
fun ProfileServerItem(
    serverInfo: TypeAndLocationScreenState.Server,
    allServers: List<TypeAndLocationScreenState.Server>,
    onSelect: (TypeAndLocationScreenState.Server) -> Unit,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_server_title,
        valueText = serverInfo.name ?: stringResource(R.string.create_profile_fastest_server),
        iconContent = {
            if (serverInfo.flagCountryId != null) {
                Flag(
                    exitCountry = serverInfo.flagCountryId,
                    modifier = Modifier.size(24.dp, 16.dp)
                )
            } else {
                Icon(
                    painterResource(
                        if (serverInfo.isFastest) CoreR.drawable.ic_proton_bolt //TODO: add filled
                        else CoreR.drawable.ic_proton_servers
                    ),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(20.dp)
                )
            }
        },
        modal = { closeModal ->
            PickServer(
                selectedServer = serverInfo,
                allServers = allServers,
                onSelect = {
                    onSelect(it)
                    closeModal()
                },
                onDismissRequest = closeModal
            )
        }
    )
}

@Composable
fun PickServer(
    selectedServer: TypeAndLocationScreenState.Server,
    allServers: List<TypeAndLocationScreenState.Server>,
    onSelect: (TypeAndLocationScreenState.Server) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        R.string.create_profile_pick_server_title,
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
                        if (server.flagCountryId != null) {
                            Flag(
                                exitCountry = server.flagCountryId,
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .size(24.dp, 16.dp)
                            )
                        } else {
                            Icon(
                                painterResource(
                                    if (server.isFastest) CoreR.drawable.ic_proton_bolt //TODO: add filled
                                    else CoreR.drawable.ic_proton_servers
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .padding(horizontal = 2.dp)
                                    .size(20.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ProfileProtocolItem(
    value: ProtocolSelection,
    onSelect: (ProtocolSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_netshield_title,
        valueText = stringResource(id = value.displayName),
        iconContent = {
        },
        modal = { closeModal ->
            PickProtocol(
                value = value,
                onSelect = {
                    onSelect(it)
                    closeModal()
                },
                onDismissRequest = closeModal
            )
        },
        modifier = modifier
    )
}

@Composable
fun PickProtocol(
    value: ProtocolSelection,
    onSelect: (ProtocolSelection) -> Unit,
    onDismissRequest: () -> Unit,
) {
        BaseItemPickerDialog(
            R.string.create_profile_pick_protocol_title,
            onDismissRequest = onDismissRequest
        ) {
            ProtocolItem(
                itemProtocol = ProtocolSelection.SMART,
                title = R.string.settings_protocol_smart_title,
                description = R.string.settings_protocol_smart_description,
                onProtocolSelected = onSelect,
                selectedProtocol = value,
                trailingTitleContent = {
                    ProtocolBadge(stringResource(R.string.settings_protocol_badge_recommended))
                }
            )
            SettingsSectionHeading(
                text = stringResource(R.string.settings_protocol_section_speed),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            ProtocolItem(
                itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP),
                title = R.string.settings_protocol_wireguard_title,
                description = R.string.settings_protocol_wireguard_udp_description,
                onProtocolSelected = onSelect,
                selectedProtocol = value,
            )
            ProtocolItem(
                itemProtocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.UDP),
                title = R.string.settings_protocol_openvpn_title,
                description = R.string.settings_protocol_openvpn_udp_description,
                onProtocolSelected = onSelect,
                selectedProtocol = value,
            )
            SettingsSectionHeading(
                text = stringResource(R.string.settings_protocol_section_reliability),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            ProtocolItem(
                itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP),
                title = R.string.settings_protocol_wireguard_title,
                description = R.string.settings_protocol_wireguard_tcp_description,
                onProtocolSelected = onSelect,
                selectedProtocol = value,
            )
            ProtocolItem(
                itemProtocol = ProtocolSelection(VpnProtocol.OpenVPN, TransmissionProtocol.TCP),
                title = R.string.settings_protocol_openvpn_title,
                description = R.string.settings_protocol_openvpn_tcp_description,
                onProtocolSelected = onSelect,
                selectedProtocol = value,
            )
            ProtocolItem(
                itemProtocol = ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS),
                title = R.string.settings_protocol_stealth_title,
                description = R.string.settings_protocol_stealth_description,
                onProtocolSelected = onSelect,
                selectedProtocol = value,
            )
        }
}

@Composable
fun ProfileNatItem(
    value: NatType,
    onSelect: (NatType) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_nat_title,
        valueText = stringResource(id = value.shortLabelRes),
        iconContent = {
        },
        modal = { closeModal ->
            PickNat(
                currentNat = value,
                onSelect = {
                    onSelect(it)
                    closeModal()
                },
                onDismissRequest = closeModal
            )
        },
        modifier = modifier
    )
}

@Composable
fun PickNat(
    currentNat: NatType,
    onSelect: (NatType) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        R.string.create_profile_pick_protocol_title,
        onDismissRequest = onDismissRequest
    ) {
        NatType.entries.forEach { type ->
            SettingsRadioItemSmall(
                title = stringResource(id = type.labelRes),
                description = stringResource(id = type.descriptionRes),
                selected = type == currentNat,
                onSelected = { onSelect(type) },
                horizontalContentPadding = 16.dp
            )
        }
    }
}

@Composable
fun ProfileNetShieldItem(
    modifier: Modifier = Modifier,
    value: Boolean,
    onNetShieldChange: (Boolean) -> Unit,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_netshield_title,
        valueText = stringResource(if (value) R.string.netshield_state_on else R.string.netshield_state_off),
        iconContent = {
            Image(
                painterResource(if (value) R.drawable.feature_netshield_on else R.drawable.ic_netshield_off),
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(26.67.dp)
            )
        },
        modal = { closeModal ->
            PickNetShield(
                selected = value,
                onSelect = {
                    onNetShieldChange(it)
                    closeModal()
                },
                onDismissRequest = closeModal
            )
        },
        modifier = modifier
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
                onSelected = {
                    onSelect(value)
                    onDismissRequest()
                },
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
    @StringRes labelRes: Int?,
    valueText: String,
    iconContent: (@Composable RowScope.() -> Unit)?,
    modal: @Composable (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 20.dp,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.padding(vertical = 8.dp).padding(bottom = bottomPadding)) {
        if (labelRes != null) {
            Text(
                text = stringResource(labelRes),
                style = ProtonTheme.typography.captionMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ProtonTheme.shapes.medium)
                .clickable(onClick = { showDialog = true })
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
    }
    if (showDialog)
        modal { showDialog = false }
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
private fun ProfileTypeItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileTypeItem(currentValue = ProfileType.Standard, emptyList(), {})
        }
    }
}

@Preview
@Composable
private fun PickProfileTypePreview() {
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
private fun ProfileCountryItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileCountryItem(
                false,
                CountryId("PL"),
                null,
                emptyList(),
                emptyList(),
                {}, {}
            )
        }
    }
}

@Preview
@Composable
private fun ProfileCountryItemSCPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileCountryItem(
                true,
                CountryId("PL"),
                CountryId.switzerland,
                emptyList(),
                emptyList(),
                {}, {}
            )
        }
    }
}

@Preview
@Composable
private fun PickCountryDialogPreview() {
    Surface {
        VpnTheme(isDark = true) {
            PickCountry(
                isVia = false,
                isSecureCore = false,
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
private fun PickViaCountryDialogPreview() {
    Surface {
        VpnTheme(isDark = true) {
            PickCountry(
                isVia = true,
                isSecureCore = false,
                CountryId("PL"),
                listOf(
                    CountryId.sweden,
                    CountryId.switzerland,
                ),
                {},
                {}
            )
        }
    }
}

@Preview
@Composable
private fun ProfileCityOrStateItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileCityOrStateItem(
                TypeAndLocationScreenState.CityOrState("New York", CityStateId("NY", isState = false)),
                emptyList(),
                {}
            )
        }
    }
}

@Preview
@Composable
private fun PickCityStatePreview() {
    VpnTheme(isDark = true) {
        Surface {
            PickCityOrState(
                isState = false,
                selectedCityState = TypeAndLocationScreenState.CityOrState("New York", CityStateId("NY", isState = false)),
                allCityStates = listOf(
                    TypeAndLocationScreenState.CityOrState(null, CityStateId("", isState = false)),
                    TypeAndLocationScreenState.CityOrState("New York", CityStateId("NY", isState = false)),
                    TypeAndLocationScreenState.CityOrState("Los Angeles", CityStateId("CA", isState = false)),
                    TypeAndLocationScreenState.CityOrState("Chicago", CityStateId("IL", isState = false)),
                ),
                {},
                {}
            )
        }
    }
}

@Preview
@Composable
private fun ProfileServerOrGatewayItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileServerItem(
                TypeAndLocationScreenState.Server("US-TX#1", "1", null),
                emptyList(),
                {}
            )
        }
    }
}

@Preview
@Composable
private fun PickServerPreview() {
    VpnTheme(isDark = true) {
        Surface {
            PickServer(
                TypeAndLocationScreenState.Server("US-TX#1", "1", null),
                listOf(
                    TypeAndLocationScreenState.Server(null, "0", null),
                    TypeAndLocationScreenState.Server("US-TX#1", "1", null),
                    TypeAndLocationScreenState.Server("US-TX#2", "2", null),
                ),
                {},
                {}
            )
        }
    }
}

@Preview
@Composable
private fun ProfileNetShieldItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileNetShieldItem(
                value = true,
                onNetShieldChange = {}
            )
        }
    }
}

@Preview
@Composable
private fun PickNetShieldPreview() {
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

@Preview
@Composable
private fun ProfileProtocolItemPreview() {
    VpnTheme(isDark = true) {
        Surface {
            ProfileProtocolItem(
                ProtocolSelection.SMART,
                {}
            )
        }
    }
}

@Preview
@Composable
private fun PickProtocolPreview() {
    VpnTheme(isDark = true) {
        Surface {
            PickProtocol(
                ProtocolSelection.SMART,
                {},
                {}
            )
        }
    }
}