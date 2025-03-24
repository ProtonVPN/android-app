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
package com.protonvpn.android.ui.home.vpn

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonOutlinedNeutralButton
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.base.ui.VpnOutlinedNeutralButton
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.protonOutlinedNeutralButtonColors
import com.protonvpn.android.base.ui.ProtonVpnPreview
import com.protonvpn.android.redesign.vpn.ui.ChangeServerViewState
import com.protonvpn.android.ui.planupgrade.CarouselUpgradeDialogActivity
import com.protonvpn.android.ui.planupgrade.UpgradePlusCountriesHighlightsFragment
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultUnspecified
import me.proton.core.compose.theme.defaultWeak
import me.proton.core.presentation.R as CoreR

@Composable
fun ChangeServerButton(
    state: ChangeServerViewState,
    onChangeServerClick: () -> Unit,
    onUpgradeButtonShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dialogShown by rememberSaveable { mutableStateOf(false) }
    ProtonOutlinedNeutralButton(
        onClick = {
            when (state) {
                is ChangeServerViewState.Unlocked -> onChangeServerClick()
                is ChangeServerViewState.Disabled -> {}
                is ChangeServerViewState.Locked -> {
                    dialogShown = true
                    onUpgradeButtonShown()
                }
            }
        },
        contained = false,
        enabled = state != ChangeServerViewState.Disabled,
        colors = ButtonDefaults.protonOutlinedNeutralButtonColors(
            backgroundColor = ProtonTheme.colors.backgroundSecondary,
            disabledBackgroundColor = ProtonTheme.colors.backgroundSecondary,
            disabledContentColor = ProtonTheme.colors.textHint,
        ),
        border = BorderStroke(1.dp, color = ProtonTheme.colors.separatorNorm),
        modifier = modifier,
    ) {
        when (state) {
            is ChangeServerViewState.Unlocked,
            is ChangeServerViewState.Disabled ->{
                Text(
                    stringResource(id = R.string.server_change_button_title),
                    style = ProtonTheme.typography.defaultUnspecified,
                    textAlign = TextAlign.Center
                )
            }

            is ChangeServerViewState.Locked -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp), // Additional content padding.
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(id = R.string.server_change_button_title),
                        style = ProtonTheme.typography.defaultUnspecified,
                        color = ProtonTheme.colors.textWeak
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("remainingTimeRow")
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_proton_hourglass),
                            contentDescription = null,
                            tint = ProtonTheme.colors.iconNorm
                        )

                        val timeLeftContentDescription = contentDescriptionAvailableTime(state)
                        Text(
                            formatTime(state),
                            style = ProtonTheme.typography.defaultNorm,
                            modifier = Modifier.semantics {
                                contentDescription = timeLeftContentDescription
                            }
                        )
                    }
                }
            }
        }
    }

    if (dialogShown) {
        ChangeServerBottomSheetComposable(
            state = state,
            onDismissRequest = { dialogShown = false },
            onChangeServerClick = {
                dialogShown = false
                onChangeServerClick()
            },
        )
    }
}

@Composable
private fun ChangeServerBottomSheetComposable(
    state: ChangeServerViewState?,
    onDismissRequest: () -> Unit,
    onChangeServerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    SimpleModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        UpgradeModalContent(
            state = state,
            onChangeServerClick = onChangeServerClick,
            onUpgradeClick = {
                CarouselUpgradeDialogActivity.launch<UpgradePlusCountriesHighlightsFragment>(context)
            }
        )
    }
}

@Composable
private fun UpgradeModalContent(
    state: ChangeServerViewState?,
    onChangeServerClick: () -> Unit,
    onUpgradeClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(R.dimen.connection_bottom_sheet_button_distance)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val animatedProgress = remember(state is ChangeServerViewState.Locked) {
                Animatable(if (state is ChangeServerViewState.Locked) state.progress else 0f)
            }
            if (state is ChangeServerViewState.Locked) {
                val animationTimeMs = remember(animatedProgress) { state.remainingTimeInSeconds * 1_000 }
                LaunchedEffect(animatedProgress) {
                    animatedProgress.animateTo(0f, tween(animationTimeMs, easing = LinearEasing))
                }
            }
            val remainingTimeText =
                if (state is ChangeServerViewState.Locked) formatTime(state) else "00:00"

            val timeLeftContentDescription = if (state is ChangeServerViewState.Locked)
                contentDescriptionAvailableTime(state)
            else
                stringResource(R.string.server_change_progress_finished_contend_description)
            CircularProgressIndicator(
                progress = animatedProgress.value,
                strokeWidth = 8.dp,
                color = ProtonTheme.colors.brandNorm,
                trackColor = if (state is ChangeServerViewState.Locked) ProtonTheme.colors.backgroundSecondary
                    else ProtonTheme.colors.notificationSuccess,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.size(100.dp).clearAndSetSemantics {
                    contentDescription = timeLeftContentDescription
                }
            )
            if (state is ChangeServerViewState.Locked) {
                Text(
                    text = remainingTimeText,
                    style = ProtonTheme.typography.defaultNorm,
                    modifier = Modifier.clearAndSetSemantics { /* progress indicator has */ }
                )
            } else {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_proton_checkmark),
                    contentDescription = null,
                    tint = ProtonTheme.colors.iconNorm
                )
            }
        }

        if (state is ChangeServerViewState.Locked) {
            if (state.isFullLocked) {
                Text(
                    text = stringResource(id = R.string.server_change_max_reached),
                    textAlign = TextAlign.Center,
                    style = ProtonTheme.typography.defaultNorm,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Text(
                text = stringResource(id = R.string.server_change_upsell),
                style = ProtonTheme.typography.defaultWeak,
                modifier = Modifier.padding(8.dp)
            )
        }

        val horizontalPadding = Modifier.padding(horizontal = 16.dp)
        if (state is ChangeServerViewState.Locked) {
            VpnSolidButton(
                text = stringResource(R.string.upgrade),
                onClick = onUpgradeClick,
                modifier = horizontalPadding,
            )
        } else {
            VpnOutlinedNeutralButton(
                text = stringResource(id = R.string.server_change_button_title),
                onClick = onChangeServerClick,
                modifier = horizontalPadding,
            )
        }
    }
}

private fun formatTime(state: ChangeServerViewState.Locked): String =
    String.format("%02d:%02d", state.remainingTimeMinutes, state.remainingTimeSeconds)

@Composable
private fun contentDescriptionAvailableTime(state: ChangeServerViewState.Locked): String = with(state) {
    val seconds =
        pluralStringResource(id = R.plurals.time_left_seconds, count = remainingTimeSeconds, remainingTimeSeconds)
    return if (state.remainingTimeMinutes > 0) {
        val minutes =
            pluralStringResource(id = R.plurals.time_left_minutes, count = remainingTimeMinutes, remainingTimeMinutes)
        stringResource(id = R.string.server_change_button_time_left_content_description, minutes, seconds)
    } else {
        stringResource(id = R.string.server_change_button_time_left_seconds_content_description, seconds)
    }
}

@Preview
@Composable
fun UnlockedButtonPreview() {
    ProtonVpnPreview {
        ChangeServerButton(
            state = ChangeServerViewState.Unlocked,
            onChangeServerClick = { },
            onUpgradeButtonShown = {},
        )
    }
}

@Preview
@Composable
fun LockedButtonPreview() {
    ProtonVpnPreview {
        ChangeServerButton(
            state = ChangeServerViewState.Locked( 12, 20, true),
            onChangeServerClick = { },
            onUpgradeButtonShown = {},
        )
    }
}

@Preview
@Composable
fun BottomSheetContentPreview() {
    ProtonVpnPreview {
        Surface {
            UpgradeModalContent(
                state = ChangeServerViewState.Locked(12, 20, true),
                onChangeServerClick = {},
                onUpgradeClick = {}
            )
        }
    }
}
