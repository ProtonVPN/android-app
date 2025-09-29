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

package com.protonvpn.android.redesign.vpn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.ActiveDot
import com.protonvpn.android.redesign.base.ui.optional
import com.protonvpn.android.redesign.base.ui.unavailableServerAlpha
import com.protonvpn.android.redesign.vpn.ServerFeature
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionUnspecified
import me.proton.core.compose.theme.headlineSmallNorm
import java.util.EnumSet
import me.proton.core.presentation.R as CoreR

sealed interface ConnectIntentPrimaryLabel {
    data class Fastest(val connectedCountry: CountryId?, val isSecureCore: Boolean, val isFree: Boolean) : ConnectIntentPrimaryLabel
    data class Country(val exitCountry: CountryId, val entryCountry: CountryId?) : ConnectIntentPrimaryLabel

    data class Gateway(val gatewayName: String, val country: CountryId?) : ConnectIntentPrimaryLabel
    data class Profile(val name: String, val country: CountryId, val isGateway: Boolean, val icon: ProfileIcon,
           val color: ProfileColor, val isAutoOpen: Boolean = false) : ConnectIntentPrimaryLabel
}

sealed interface ConnectIntentSecondaryLabel {
    data class FastestFreeServer(val freeServerCountries: Int) : ConnectIntentSecondaryLabel
    data class Country(val country: CountryId, val serverNumberLabel: String? = null) : ConnectIntentSecondaryLabel
    data class SecureCore(val exit: CountryId?, val entry: CountryId) : ConnectIntentSecondaryLabel
    data class RawText(val text: String) : ConnectIntentSecondaryLabel
}

@Composable
private fun ConnectIntentAvailability.accessibilityAction(): String? =
    when (this) {
        ConnectIntentAvailability.ONLINE -> R.string.accessibility_action_connect
        ConnectIntentAvailability.UNAVAILABLE_PLAN -> R.string.accessibility_action_upgrade
        ConnectIntentAvailability.AVAILABLE_OFFLINE,
        ConnectIntentAvailability.UNAVAILABLE_PROTOCOL -> null
        ConnectIntentAvailability.NO_SERVERS -> null
    }?.let { stringResource(it) }

@Composable
private fun ConnectIntentAvailability.extraContentDescription(): String? =
    when(this) {
        ConnectIntentAvailability.UNAVAILABLE_PLAN,
        ConnectIntentAvailability.UNAVAILABLE_PROTOCOL -> R.string.accessibility_item_unavailable
        ConnectIntentAvailability.AVAILABLE_OFFLINE -> R.string.accessibility_item_in_maintenance
        ConnectIntentAvailability.ONLINE -> null
        ConnectIntentAvailability.NO_SERVERS -> null
    }?.let { stringResource(it) }

@Composable
fun ConnectIntentRow(
    availability: ConnectIntentAvailability,
    connectIntent: ConnectIntentViewState,
    isConnected: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    leadingComposable: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    semanticsStateDescription: String? = null,
) {
    val customAccessibilityActions = listOf(
        CustomAccessibilityAction(stringResource(id = R.string.accessibility_action_open)) { onOpen(); true },
    )
    val extraContentDescription = availability.extraContentDescription()
    val clickActionLabel = availability.accessibilityAction()
    val semantics = Modifier
        .clickable(onClick = onClick, onClickLabel = clickActionLabel)
        .testTag("intentRow")
        .semantics(mergeDescendants = true) {
            semanticsStateDescription?.let { stateDescription = it }
            if (extraContentDescription != null) contentDescription = extraContentDescription
            customActions = customAccessibilityActions
        }
    val isUnavailable = availability != ConnectIntentAvailability.ONLINE
    ConnectIntentBlankRow(
        title = connectIntent.primaryLabel.label(),
        subTitle = connectIntent.secondaryLabel?.label(),
        serverFeatures = connectIntent.serverFeatures,
        isConnected = isConnected,
        modifier = modifier.then(semantics).clickable(onClick = onClick).padding(horizontal = 16.dp),
        isUnavailable = isUnavailable,
        leadingComposable = leadingComposable,
        trailingComposable = {
            if (availability == ConnectIntentAvailability.AVAILABLE_OFFLINE) {
                Icon(
                    painterResource(id = CoreR.drawable.ic_proton_wrench),
                    tint = ProtonTheme.colors.iconWeak,
                    contentDescription = null, // Description is added on the whole row.
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .unavailableServerAlpha(true)
                        .padding(end = 8.dp)
                )
            }
            val interactionSource = remember { MutableInteractionSource() }
            val iconOverflow = 8.dp // How much the icon sticks out into edgePadding
            Box(
                modifier = Modifier
                    .height(IntrinsicSize.Max)
                    .testTag("intentOpen")
                    .clearAndSetSemantics {} // Accessibility handled via semantics on the whole row.
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null, // Indication only on the icon.
                        onClick = onOpen
                    )
                    .padding(end = 16.dp - iconOverflow)
            ) {
                Icon(
                    painterResource(CoreR.drawable.ic_proton_three_dots_horizontal),
                    tint = ProtonTheme.colors.iconNorm,
                    modifier = Modifier
                        .clip(CircleShape)
                        .indication(interactionSource, ripple())
                        .padding(8.dp),
                    contentDescription = null // Accessibility handled via semantics on the whole row.
                )
            }
        }
    )
}

@Composable
fun ConnectIntentBlankRow(
    modifier: Modifier = Modifier,
    leadingComposable: @Composable () -> Unit,
    trailingComposable: (@Composable RowScope.() -> Unit)? = null,
    title: String,
    subTitle: AnnotatedString?,
    serverFeatures: Set<ServerFeature>,
    isConnected: Boolean = false,
    isUnavailable: Boolean,
) {
    val isLargerRecent = subTitle != null || serverFeatures.isNotEmpty()
    Row(
        modifier = modifier
            .heightIn(min = 64.dp)
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = if (isLargerRecent) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(Modifier.unavailableServerAlpha(isUnavailable)) {
            leadingComposable()
        }
        Column(
            modifier = Modifier
                .optional({ isUnavailable }, Modifier.unavailableServerAlpha(isUnavailable))
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Row {
                Text(
                    text = title,
                    style = ProtonTheme.typography.body1Regular,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isConnected) {
                    ActiveDot(modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (subTitle != null || serverFeatures.isNotEmpty()) {
                ServerDetailsRow(
                    subTitle,
                    null,
                    serverFeatures,
                    detailsStyle = ProtonTheme.typography.body2Regular,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        trailingComposable?.invoke(this)
    }
}

@Composable
fun ConnectIntentLabels(
    primaryLabel: ConnectIntentPrimaryLabel,
    secondaryLabel: ConnectIntentSecondaryLabel?,
    serverFeatures: Set<ServerFeature>,
    isConnected: Boolean,
    secondaryLabelVerticalPadding: Dp,
    primaryLabelStyle: TextStyle = ProtonTheme.typography.headlineSmallNorm,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
    ) {
        Row {
            Text(
                primaryLabel.label(),
                style = primaryLabelStyle,
                modifier = Modifier.testTag("primaryLabel")
            )
            if (isConnected) {
                ActiveDot(modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (secondaryLabel != null || serverFeatures.isNotEmpty()) {
            ServerDetailsRow(
                secondaryLabel?.label(),
                secondaryLabel?.contentDescription(),
                serverFeatures,
                detailsStyle = ProtonTheme.typography.body2Regular,
                modifier = Modifier
                    .padding(top = secondaryLabelVerticalPadding)
                    .testTag("secondaryLabel")
            )
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerDetailsRow(
    detailsText: AnnotatedString?,
    contentDescriptionOverride: String?, // Set to null to use default behavior.
    features: Set<ServerFeature>,
    detailsStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val inlineContent = remember {
        mapOf(
            FREE_USER_FLAGS_PLACEHOLDER to InlineTextContent(
                // Hardcoded ratio of the dimensions of the flags image...
                Placeholder(3.5f * detailsStyle.fontSize, detailsStyle.fontSize, PlaceholderVerticalAlign.Center)
            ) {
                Image(
                    painterResource(id = R.drawable.free_countries_flags),
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = null
                )
            }
        )
    }
    val bulletPadding = 8.dp
    val semantics = contentDescriptionOverride?.let { Modifier.semantics { contentDescription = it }} ?: Modifier
    CompositionLocalProvider(LocalContentColor provides ProtonTheme.colors.textWeak) {
        FlowRow(
            modifier = modifier.then(semantics),
            verticalArrangement = Arrangement.Center
        ) {
            var needsBullet = false
            if (detailsText != null) {
                Text(
                    detailsText,
                    style = detailsStyle,
                    modifier = Modifier.padding(end = bulletPadding),
                    inlineContent = inlineContent
                )
                needsBullet = true
            }
            features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    if (needsBullet) {
                        SeparatorBullet()
                        Spacer(Modifier.width(bulletPadding))
                    }
                    FeatureTag(feature, Modifier.padding(end = bulletPadding))
                }
                needsBullet = true
            }
        }
    }
}

@Composable
private fun SeparatorBullet(
    modifier: Modifier = Modifier,
) {
    Icon(
        painterResource(id = R.drawable.ic_bullet),
        contentDescription = null,
        modifier = modifier,
    )
}

@ProtonVpnPreview
@Composable
private fun ConnectIntentRowPreviewCountry() {
    ProtonVpnPreview {
        Row {
            ConnectIntentLabels(
                primaryLabel = ConnectIntentPrimaryLabel.Country(CountryId.fastest, null),
                secondaryLabel = ConnectIntentSecondaryLabel.RawText("Lithuania"),
                serverFeatures = EnumSet.of(ServerFeature.Tor),
                isConnected = true,
                secondaryLabelVerticalPadding = 2.dp,
            )
        }
    }
}

@ProtonVpnPreview
@Composable
private fun ConnectIntentRowPreviewGateway() {
    ProtonVpnPreview {
        Row {
            ConnectIntentLabels(
                primaryLabel = ConnectIntentPrimaryLabel.Gateway(gatewayName = "Dev VPN", null),
                secondaryLabel = null,
                serverFeatures = EnumSet.of(ServerFeature.Tor),
                isConnected = true,
                secondaryLabelVerticalPadding = 2.dp,
            )
        }
    }
}

@ProtonVpnPreview
@Composable
private fun ConnectIntentRowFastestFreeServer() {
    ProtonVpnPreview {
        Row {
            ConnectIntentLabels(
                primaryLabel = ConnectIntentPrimaryLabel.Fastest(null, isSecureCore = false, isFree = true),
                secondaryLabel = ConnectIntentSecondaryLabel.FastestFreeServer(5),
                serverFeatures = emptySet(),
                isConnected = true,
                secondaryLabelVerticalPadding = 2.dp,
            )
        }
    }
}

@Preview(widthDp = 130)
@Composable
private fun ServerDetailsRowWrappingPreview() {
    ProtonVpnPreview {
        Surface(color = ProtonTheme.colors.backgroundSecondary) {
            ServerDetailsRow(
                detailsText = AnnotatedString("Zurich"),
                contentDescriptionOverride = null,
                features = EnumSet.of(ServerFeature.P2P, ServerFeature.Tor),
                detailsStyle = ProtonTheme.typography.captionUnspecified
            )
        }
    }
}
