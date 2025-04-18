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

import android.net.Uri
import android.util.Patterns
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.LabelBadge
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.base.ui.theme.VpnTheme
import com.protonvpn.android.profiles.data.ProfileAutoOpen
import com.protonvpn.android.redesign.CityStateId
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.DIALOG_CONTENT_PADDING
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.redesign.base.ui.FlagDimensions
import com.protonvpn.android.redesign.base.ui.ProtonBasicAlert
import com.protonvpn.android.redesign.base.ui.ProtonDialogButton
import com.protonvpn.android.redesign.base.ui.ProtonOutlinedTextField
import com.protonvpn.android.redesign.base.ui.SettingsRadioItemSmall
import com.protonvpn.android.redesign.settings.ui.DnsConflictBanner
import com.protonvpn.android.redesign.settings.ui.NatType
import com.protonvpn.android.redesign.settings.ui.ProtocolSettingsList
import com.protonvpn.android.redesign.vpn.ui.label
import com.protonvpn.android.redesign.vpn.ui.viaCountry
import com.protonvpn.android.vpn.DnsOverride
import com.protonvpn.android.vpn.ProtocolSelection
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultWeak
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
        online = true,
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
        items(allTypes) { profileType ->
            SettingsRadioItemSmall(
                title = stringResource(profileType.nameRes),
                description = null,
                selected = profileType == selectedProfileType,
                onSelected = { onSelect(profileType) },
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
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

@Composable
fun ProfileCountryItem(
    secureCore: Boolean,
    exitCountry: TypeAndLocationScreenState.CountryItem,
    entryCountry: TypeAndLocationScreenState.CountryItem?,
    allCountries: List<TypeAndLocationScreenState.CountryItem>,
    allEntries: List<TypeAndLocationScreenState.CountryItem>,
    onSelectExit: (TypeAndLocationScreenState.CountryItem) -> Unit,
    onSelectEntry: (TypeAndLocationScreenState.CountryItem) -> Unit,
) {
    Column {
        ProfileValueItem(
            labelRes = R.string.create_profile_pick_country_title,
            valueText = exitCountry.id.label(),
            online = exitCountry.online,
            iconContent = {
                Flag(
                    exitCountry = exitCountry.id,
                    entryCountry = if (secureCore) CountryId.fastest else null,
                    modifier = Modifier.scale(24f / FlagDimensions.singleFlagSize.width.value)
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
                valueText = viaCountry(entryCountry.id),
                online = entryCountry.online,
                iconContent = {
                    Flag(
                        exitCountry = entryCountry.id,
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
    selectedCountry: TypeAndLocationScreenState.CountryItem,
    allCountries: List<TypeAndLocationScreenState.CountryItem>,
    onSelect: (TypeAndLocationScreenState.CountryItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        title =
            if (isVia) R.string.create_profile_pick_middle_country_title
            else R.string.create_profile_pick_country_title,
        onDismissRequest = onDismissRequest
    ) {
        items(allCountries) { country ->
            SettingsRadioItemSmall(
                title = if (isVia) viaCountry(country.id) else country.id.label(),
                description = null,
                titleColor = if (country.online) ProtonTheme.colors.textNorm else ProtonTheme.colors.textHint,
                selected = country == selectedCountry,
                onSelected = { onSelect(country) },
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
                leadingContent = {
                    Flag(
                        exitCountry = country.id,
                        entryCountry = if (isSecureCore) CountryId.fastest else null,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .scale(24f / FlagDimensions.singleFlagSize.width.value)
                    )
                },
                trailingTitleContent = {
                    AvailabilityIndicator(country.online)
                }
            )
        }
    }
}

@Composable
fun ProfileGatewayItem(
    currentValue: TypeAndLocationScreenState.GatewayItem,
    allGateways: List<TypeAndLocationScreenState.GatewayItem>,
    onSelect: (TypeAndLocationScreenState.GatewayItem) -> Unit,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_gateway_title,
        valueText = currentValue.name,
        online = currentValue.online,
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
    selectedGateway: TypeAndLocationScreenState.GatewayItem,
    allGateways: List<TypeAndLocationScreenState.GatewayItem>,
    onSelect: (TypeAndLocationScreenState.GatewayItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        R.string.create_profile_pick_gateway_title,
        onDismissRequest = onDismissRequest
    ) {
        items(allGateways) { gateway ->
            val iconTint =
                if (!gateway.online) ProtonTheme.colors.textHint
                else ProtonTheme.colors.iconNorm
            SettingsRadioItemSmall(
                title = gateway.name,
                titleColor =
                    if (!gateway.online) ProtonTheme.colors.textHint
                    else ProtonTheme.colors.textNorm,
                description = null,
                selected = gateway == selectedGateway,
                onSelected = { onSelect(gateway) },
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
                leadingContent = {
                    Icon(
                        painterResource(CoreR.drawable.ic_proton_servers),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .size(20.dp)
                    )
                },
                trailingTitleContent = {
                    AvailabilityIndicator(gateway.online)
                }
            )
        }
    }
}

@Composable
private fun AvailabilityIndicator(online: Boolean, modifier: Modifier = Modifier) {
    val iconTint = if (online) ProtonTheme.colors.iconNorm else ProtonTheme.colors.textHint
    if (!online) {
        Icon(
            painterResource(CoreR.drawable.ic_proton_wrench),
            contentDescription = stringResource(R.string.accessibility_item_unavailable),
            tint = iconTint,
            modifier = modifier
                .padding(horizontal = 4.dp)
                .size(20.dp)
        )
    }
}

@Composable
fun ProfileCityOrStateItem(
    currentValue: TypeAndLocationScreenState.CityOrStateItem,
    allCityStates: List<TypeAndLocationScreenState.CityOrStateItem>,
    onSelect: (TypeAndLocationScreenState.CityOrStateItem) -> Unit,
) {
    val isState = currentValue.id.isState
    ProfileValueItem(
        labelRes =
            if (isState) R.string.create_profile_pick_state_title
            else R.string.create_profile_pick_city_title,
        valueText = currentValue.name ?:
            if (isState) stringResource(R.string.create_profile_fastest_state)
            else stringResource(R.string.create_profile_fastest_city),
        online = currentValue.online,
        iconContent = {
            Icon(
                painterResource(
                    if (currentValue.id.isFastest) R.drawable.ic_proton_bolt_filled
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
    selectedCityState: TypeAndLocationScreenState.CityOrStateItem,
    allCityStates: List<TypeAndLocationScreenState.CityOrStateItem>,
    onSelect: (TypeAndLocationScreenState.CityOrStateItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        if (isState) R.string.create_profile_pick_state_title
        else R.string.create_profile_pick_city_title,
        onDismissRequest = onDismissRequest
    ) {
        items(allCityStates) { cityState ->
            SettingsRadioItemSmall(
                title = cityState.name
                    ?: if (isState) stringResource(R.string.create_profile_fastest_state)
                    else stringResource(R.string.create_profile_fastest_city),
                description = null,
                selected = cityState.id == selectedCityState.id,
                onSelected = { onSelect(cityState) },
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
                leadingContent = {
                    Icon(
                        painterResource(
                            if (cityState.id.isFastest) R.drawable.ic_proton_bolt_filled
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

@Composable
fun ProfileServerItem(
    serverInfo: TypeAndLocationScreenState.ServerItem,
    allServers: List<TypeAndLocationScreenState.ServerItem>,
    onSelect: (TypeAndLocationScreenState.ServerItem) -> Unit,
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_server_title,
        valueText = serverInfo.name ?: stringResource(R.string.create_profile_fastest_server),
        online = serverInfo.online,
        iconContent = {
            if (serverInfo.flagCountryId != null) {
                Flag(
                    exitCountry = serverInfo.flagCountryId,
                    modifier = Modifier.size(24.dp, 16.dp)
                )
            } else {
                Icon(
                    painterResource(
                        if (serverInfo.isFastest) R.drawable.ic_proton_bolt_filled
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
    selectedServer: TypeAndLocationScreenState.ServerItem,
    allServers: List<TypeAndLocationScreenState.ServerItem>,
    onSelect: (TypeAndLocationScreenState.ServerItem) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        R.string.create_profile_pick_server_title,
        onDismissRequest = onDismissRequest
    ) {
        items(allServers) { server ->
            SettingsRadioItemSmall(
                title = server.name ?: stringResource(R.string.create_profile_fastest_server),
                description = null,
                titleColor = if (server.online) ProtonTheme.colors.textNorm else ProtonTheme.colors.textHint,
                selected = server.id == selectedServer.id,
                onSelected = { onSelect(server) },
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
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
                                if (server.isFastest) R.drawable.ic_proton_bolt_filled
                                else CoreR.drawable.ic_proton_servers
                            ),
                            contentDescription = null,
                            tint = if (server.online) ProtonTheme.colors.iconNorm else ProtonTheme.colors.iconHint,
                            modifier = Modifier
                                .padding(end = 14.dp)
                                .padding(horizontal = 2.dp)
                                .size(20.dp)
                        )
                    }
                },
                trailingTitleContent = {
                    AvailabilityIndicator(server.online)
                }
            )
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
        labelRes = R.string.create_profile_pick_protocol_title,
        valueText = stringResource(id = value.displayName),
        online = true,
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
        item {
            ProtocolSettingsList(value, onSelect, horizontalContentPadding = DIALOG_CONTENT_PADDING)
        }
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
        online = true,
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
        R.string.create_profile_pick_nat_title,
        onDismissRequest = onDismissRequest
    ) {
        NatType.entries.forEach { type ->
            item {
                SettingsRadioItemSmall(
                    title = stringResource(id = type.labelRes),
                    description = stringResource(id = type.descriptionRes),
                    selected = type == currentNat,
                    horizontalContentPadding = DIALOG_CONTENT_PADDING,
                    onSelected = { onSelect(type) },
                )
            }
        }
    }
}

@Composable
fun ProfileNetShieldItem(
    modifier: Modifier = Modifier,
    value: Boolean,
    onNetShieldChange: (Boolean) -> Unit,
    onDisableCustomDns: () -> Unit,
    onDisablePrivateDns: () -> Unit,
    onCustomDnsLearnMore: () -> Unit,
    onPrivateDnsLearnMore: () -> Unit,
    dnsOverride: DnsOverride,
) {
    val netshieldStateResource = when {
        dnsOverride != DnsOverride.None -> R.string.netshield_state_unavailable
        !value -> R.string.netshield_state_off
        else -> R.string.netshield_state_on
    }
    ProfileValueItem(
        labelRes = R.string.create_profile_pick_netshield_title,
        valueText = stringResource(netshieldStateResource),
        online = true,
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
            when (dnsOverride) {
                DnsOverride.None ->
                    PickNetShield(
                        selected = value,
                        onSelect = {
                            onNetShieldChange(it)
                            closeModal()
                        },
                        onDismissRequest = closeModal
                    )
                DnsOverride.CustomDns ->
                    NetShieldConflictDialog(
                        titleRes = R.string.custom_dns_conflict_banner_netshield_title,
                        descriptionRes = R.string.custom_dns_conflict_banner_netshield_description,
                        buttonRes = R.string.custom_dns_conflict_banner_disable_custom_dns_button,
                        onButtonClicked = onDisableCustomDns,
                        onLearnMore = onCustomDnsLearnMore,
                        onDismissRequest = closeModal
                    )
                DnsOverride.SystemPrivateDns ->
                    NetShieldConflictDialog(
                        titleRes = R.string.private_dns_conflict_banner_netshield_title,
                        descriptionRes = R.string.private_dns_conflict_banner_custom_dns_description,
                        buttonRes = R.string.private_dns_conflict_banner_network_settings_button,
                        onButtonClicked = onDisablePrivateDns,
                        onLearnMore = onPrivateDnsLearnMore,
                        onDismissRequest = closeModal
                    )
            }
        },
        modifier = modifier
    )
}

@Composable
fun NetShieldConflictDialog(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    @StringRes buttonRes: Int,
    onDismissRequest: () -> Unit,
    onButtonClicked: () -> Unit,
    onLearnMore: () -> Unit,
) {
    ProtonBasicAlert(
        content = {
            DnsConflictBanner(
                titleRes = titleRes,
                descriptionRes = descriptionRes,
                buttonRes = buttonRes,
                onLearnMore = onLearnMore,
                onButtonClicked = {
                    onDismissRequest()
                    onButtonClicked()
                },
                modifier = Modifier,
                backgroundColor = Color.Transparent,
                contentPadding = PaddingValues(horizontal = 24.dp)
            )
        },
        onDismissRequest = onDismissRequest,
        isWideDialog = true
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
        description = R.string.create_profile_pick_netshield_description,
        onDismissRequest = onDismissRequest
    ) {
        listOf(true, false).forEach { value ->
            item {
                SettingsRadioItemSmall(
                    title = stringResource(if (value) R.string.netshield_state_on else R.string.netshield_state_off),
                    description = null,
                    selected = value == selected,
                    onSelected = {
                        onSelect(value)
                        onDismissRequest()
                    },
                    horizontalContentPadding = DIALOG_CONTENT_PADDING,
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
}

@Composable
fun ProfileLanConnectionsItem(
    value: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ProfileValueItem(
        labelRes = R.string.settings_advanced_allow_lan_title,
        valueText = stringResource(if (value) R.string.lan_state_on else R.string.lan_state_off),
        online = true,
        modal = { closeModal ->
            PickLanConnection(
                selected = value,
                onSelect = onSelect,
                onDismissRequest = closeModal
            )
        },
        modifier = modifier
    )
}

@Composable
fun ProfileCustomDnsItem(
    value: Boolean,
    dnsOverride: DnsOverride,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ProfileValueItem(
        labelRes = R.string.settings_custom_dns_title,
        valueText = stringResource(
            when {
                !value -> R.string.custom_dns_state_off
                dnsOverride == DnsOverride.SystemPrivateDns -> R.string.custom_dns_state_unavailable
                else -> R.string.custom_dns_state_on
            }
        ),
        online = true,
        modifier = modifier,
        onClick = onClick,
        isDropdown = false
    )
}

@Composable
fun ProfileAutoOpenItem(
    value: ProfileAutoOpen,
    onChange: (ProfileAutoOpen) -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean
) {
    ProfileValueItem(
        labelRes = R.string.create_profile_connect_and_go_label,
        valueText = when (value) {
            is ProfileAutoOpen.None -> stringResource(R.string.profile_auto_open_off)
            is ProfileAutoOpen.App -> value.packageName
            is ProfileAutoOpen.Url -> value.url.toString()
        },
        online = true,
        modal = { closeModal ->
            AutoOpenModal(
                value = value,
                onChange = onChange,
                onDismissRequest = closeModal
            )
        },
        labelBadge = R.string.create_profile_auto_open_label_badge.takeIf { isNew },
        modifier = modifier
    )
}

@Composable
fun AutoOpenModal(
    value: ProfileAutoOpen,
    onChange: (ProfileAutoOpen) -> Unit,
    onDismissRequest: () -> Unit
) {
    var isOnState by rememberSaveable(value) { mutableStateOf(value !is ProfileAutoOpen.None) }
    var urlState by rememberSaveable(value, stateSaver = TextFieldValue.Saver) {
        val text = when (value) {
            is ProfileAutoOpen.Url -> value.url.toString()
            is ProfileAutoOpen.App -> "app:${value.packageName}"
            is ProfileAutoOpen.None -> value.savedText
        }
        // Move cursor to the end on focusing
        mutableStateOf(TextFieldValue(text, TextRange(0)))
    }
    var errorState by rememberSaveable(value) { mutableStateOf<Int?>(null) }

    BaseItemPickerDialog(
        R.string.create_profile_connect_and_go_label,
        description = R.string.create_profile_auto_open_description,
        onDismissRequest = onDismissRequest,
        onSave = {
            val (autoOpen, error) = fixAndValidateAutoOpenUri(isOnState, urlState.text)
            if (error != null) {
                errorState = error
            } else {
                onChange(autoOpen)
                onDismissRequest()
            }
        }
    ) {
        item {
            SettingsRadioItemSmall(
                title = stringResource(R.string.profile_auto_open_off),
                description = null,
                selected = !isOnState,
                onSelected = {
                    isOnState = false
                    errorState = null
                },
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
            )
        }
        item {
            Column(
                modifier = Modifier.clickable { isOnState = true }
            ) {
                SettingsRadioItemSmall(
                    title = stringResource(R.string.profile_auto_open_on),
                    description = null,
                    selected = isOnState,
                    onSelected = { isOnState = true },
                    horizontalContentPadding = DIALOG_CONTENT_PADDING,
                )
                val focusRequester = remember { FocusRequester() }
                if (isOnState) {
                    LaunchedEffect(true) {
                        focusRequester.requestFocus()
                    }
                }
                ProtonOutlinedTextField(
                    value = urlState,
                    onValueChange = {
                        urlState = it
                        errorState = null
                    },
                    textStyle =
                        if (isOnState) ProtonTheme.typography.defaultNorm
                        else ProtonTheme.typography.defaultWeak,
                    labelText = stringResource(R.string.create_profile_auto_open_url_input_label),
                    assistiveText = errorState?.let { stringResource(it) } ?: "",
                    isError = errorState != null,
                    enabled = isOnState,
                    placeholderText = stringResource(R.string.create_profile_auto_open_url_input_placeholder),
                    maxLines = 1,
                    singleLine = true,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .padding(horizontal = DIALOG_CONTENT_PADDING)
                        .focusRequester(focusRequester)
                        .fillMaxWidth(),
                    backgroundColor = ProtonTheme.colors.backgroundNorm,
                )
            }
        }
    }
}

@VisibleForTesting
fun fixAndValidateAutoOpenUri(isOn: Boolean, text: String) : Pair<ProfileAutoOpen, Int?> {
    if (!isOn) return ProfileAutoOpen.None(text) to null
    var uri = Uri.parse(text)
    if (uri.scheme.isNullOrBlank())
        uri = Uri.parse("https://$text")

    val invalid = ProfileAutoOpen.None(text) to R.string.profile_auto_open_error_invalid_url
    return try {
        when (uri.scheme) {
            "app" -> ProfileAutoOpen.App(uri.schemeSpecificPart) to null
            "https" -> if (Patterns.WEB_URL.matcher(uri.toString()).matches()) ProfileAutoOpen.Url(uri) to null else invalid
            else -> invalid
        }
    } catch (e: IllegalArgumentException) {
        invalid
    }
}

@Composable
private fun PickLanConnection(
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BaseItemPickerDialog(
        R.string.settings_advanced_allow_lan_title,
        description = R.string.settings_advanced_allow_lan_description,
        onDismissRequest = onDismissRequest
    ) {
        items(listOf(true, false)) { value ->
            SettingsRadioItemSmall(
                title = stringResource(if (value) R.string.netshield_state_on else R.string.netshield_state_off),
                description = null,
                selected = value == selected,
                onSelected = {
                    onSelect(value)
                    onDismissRequest()
                },
                horizontalContentPadding = DIALOG_CONTENT_PADDING,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ProfileValueItem(
    @StringRes labelRes: Int?,
    valueText: String,
    online: Boolean,
    modal: @Composable (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    labelBadge: Int? = null,
    iconContent: (@Composable RowScope.() -> Unit)? = null,
    bottomPadding: Dp = 20.dp,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    ProfileValueItem(
        labelRes = labelRes,
        valueText = valueText,
        online = online,
        modifier = modifier,
        labelBadge = labelBadge,
        iconContent = iconContent,
        bottomPadding = bottomPadding,
        onClick = { showDialog = true }
    )
    if (showDialog)
        modal { showDialog = false }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ProfileValueItem(
    @StringRes labelRes: Int?,
    valueText: String,
    online: Boolean,
    modifier: Modifier = Modifier,
    labelBadge: Int? = null,
    isDropdown: Boolean = true,
    iconContent: (@Composable RowScope.() -> Unit)? = null,
    bottomPadding: Dp = 20.dp,
    onClick: () -> Unit,
) {
    val textColor = if (online) ProtonTheme.colors.textNorm else ProtonTheme.colors.textHint
    Column(
        modifier = modifier
            .padding(vertical = 8.dp)
            .padding(bottom = bottomPadding)
    ) {
        val label = labelRes?.let { stringResource(it) }
        if (label != null) {
            Row(
                Modifier
                    .padding(bottom = 8.dp)
                    .semantics { invisibleToUser() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = ProtonTheme.typography.captionMedium,
                    color = textColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (labelBadge != null) {
                    LabelBadge(
                        text = stringResource(labelBadge),
                        textColor = ProtonTheme.colors.notificationWarning,
                        borderColor = ProtonTheme.colors.notificationWarning,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ProtonTheme.shapes.medium)
                .semantics {
                    role = if (isDropdown) Role.DropdownList else Role.Button
                    if (label != null) text = AnnotatedString(label)
                }
                .clickable(onClick = onClick)
                .background(ProtonTheme.colors.backgroundSecondary)
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconContent != null) {
                iconContent()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = valueText,
                    style = ProtonTheme.typography.body1Medium,
                    color = textColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
                AvailabilityIndicator(online, Modifier.padding(horizontal = 12.dp))
            }
            Icon(
                painter = painterResource(id = if (isDropdown) CoreR.drawable.ic_proton_chevron_down else CoreR.drawable.ic_proton_chevron_right),
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
            )
        }
    }
}

@Composable
fun BaseItemPickerDialog(
    @StringRes title: Int,
    onDismissRequest: () -> Unit,
    onSave: (() -> Unit)? = null,
    @StringRes description: Int? = null,
    itemList: LazyListScope.() -> Unit,
) {
    ProtonBasicAlert(
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val textPaddingModifier = Modifier
                    .padding(horizontal = DIALOG_CONTENT_PADDING)
                    .padding(bottom = 12.dp)
                Text(
                    stringResource(title),
                    style = ProtonTheme.typography.body1Bold,
                    modifier = textPaddingModifier
                )
                if (description != null) {
                    Text(
                        stringResource(description),
                        style = ProtonTheme.typography.body2Regular,
                        color = ProtonTheme.colors.textWeak,
                        modifier = textPaddingModifier
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(weight = 1f, fill = false)
                ) {
                    itemList()
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = DIALOG_CONTENT_PADDING)
                ) {
                    ProtonDialogButton(
                        onClick = onDismissRequest,
                        text = stringResource(R.string.cancel),
                    )
                    if (onSave != null) {
                        ProtonDialogButton(
                            onClick = onSave,
                            text = stringResource(R.string.saveButton),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        isWideDialog = true,
        onDismissRequest = onDismissRequest
    )
}

@Preview
@Composable
private fun AutoOpenModalPreview() {
    VpnTheme(isDark = true) {
        Surface {
            AutoOpenModal(ProfileAutoOpen.None(""), {}, {})
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
                TypeAndLocationScreenState.CountryItem(CountryId.fastestExcludingMyCountry, false),
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
                TypeAndLocationScreenState.CountryItem(CountryId("PL"), true),
                TypeAndLocationScreenState.CountryItem(CountryId.switzerland, false),
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
                TypeAndLocationScreenState.CountryItem(CountryId("PL"), true),
                listOf(
                    TypeAndLocationScreenState.CountryItem(CountryId("DE"), true),
                    TypeAndLocationScreenState.CountryItem(CountryId("PL"), false),
                    TypeAndLocationScreenState.CountryItem(CountryId("US"), true),
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
                TypeAndLocationScreenState.CountryItem(CountryId("PL"), true),
                listOf(
                    TypeAndLocationScreenState.CountryItem(CountryId.sweden, true),
                    TypeAndLocationScreenState.CountryItem(CountryId.switzerland, true),
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
                TypeAndLocationScreenState.CityOrStateItem(
                    "New York",
                    CityStateId("NY", isState = false),
                    true
                ),
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
                selectedCityState = TypeAndLocationScreenState.CityOrStateItem(
                    "New York",
                    CityStateId("NY", isState = false),
                    true
                ),
                allCityStates = listOf(
                    TypeAndLocationScreenState.CityOrStateItem(null, CityStateId("", isState = false), true),
                    TypeAndLocationScreenState.CityOrStateItem(
                        "New York",
                        CityStateId("NY", isState = false),
                        true
                    ),
                    TypeAndLocationScreenState.CityOrStateItem(
                        "Los Angeles",
                        CityStateId("CA", isState = false),
                        true
                    ),
                    TypeAndLocationScreenState.CityOrStateItem(
                        "Chicago",
                        CityStateId("IL", isState = false),
                        true
                    ),
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
                TypeAndLocationScreenState.ServerItem("US-TX#1", "1", null, true),
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
                TypeAndLocationScreenState.ServerItem("US-TX#1", "1", null, false),
                listOf(
                    TypeAndLocationScreenState.ServerItem(null, "0", null, false),
                    TypeAndLocationScreenState.ServerItem("US-TX#1", "1", null, false),
                    TypeAndLocationScreenState.ServerItem("US-TX#2", "2", null, true),
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
                onNetShieldChange = {},
                onDisableCustomDns = {},
                onDisablePrivateDns = {},
                onCustomDnsLearnMore = {},
                onPrivateDnsLearnMore = {},
                dnsOverride = DnsOverride.None,
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
private fun ProfileLanConnectionsItemPreview() {
    ProtonVpnPreview {
        ProfileLanConnectionsItem(true, {})
    }
}

@Preview
@Composable
private fun PickLanConnectionsPreview() {
    ProtonVpnPreview {
        PickLanConnection(true, {}, {})
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
