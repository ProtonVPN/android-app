/*
 * Copyright (c) 2023. Proton Technologies AG
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonOutlinedNeutralButton
import com.protonvpn.android.base.ui.VpnOutlinedNeutralButton
import com.protonvpn.android.base.ui.VpnSolidButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultUnspecified
import me.proton.core.compose.theme.defaultWeak

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeServerComposable(
    state: StateFlow<ChangeServerViewState>,
    onChangeServerClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    onUpgradeModalOpened: () -> Unit,
) {
    val currentState = state.collectAsStateWithLifecycle().value
    val isModalVisible = remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (currentState != ChangeServerViewState.Hidden) {
        ProtonOutlinedNeutralButton(
            onClick = {
                when (currentState) {
                    is ChangeServerViewState.Unlocked -> onChangeServerClick()
                    is ChangeServerViewState.Locked -> {
                        onUpgradeModalOpened()
                        isModalVisible.value = true
                    }

                    else -> Unit
                }
            },
            contained = false,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (currentState) {
                is ChangeServerViewState.Unlocked -> {
                    Text(
                        stringResource(id = R.string.server_change_button_title),
                        style = ProtonTheme.typography.defaultUnspecified,
                        color = ProtonTheme.colors.textNorm,
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
                                painter = painterResource(id = R.drawable.ic_proton_hourglass),
                                contentDescription = null,
                                tint = ProtonTheme.colors.iconNorm
                            )
                            Text(
                                currentState.remainingTimeText,
                                style = ProtonTheme.typography.defaultNorm
                            )
                        }
                    }
                }

                is ChangeServerViewState.Hidden -> {}
            }
        }
        if (isModalVisible.value) {
            ModalBottomSheet(
                sheetState = bottomSheetState,
                containerColor = ProtonTheme.colors.backgroundNorm,
                onDismissRequest = { isModalVisible.value = false }
            ) {
                UpgradeModalContent(
                    state = currentState,
                    onChangeServerClick = onChangeServerClick,
                    onUpgradeClick = onUpgradeClick
                )
            }
        }
    }
}

@Composable
private fun UpgradeModalContent(
    state: ChangeServerViewState,
    onChangeServerClick: () -> Unit,
    onUpgradeClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 48.dp),
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
                val animationTimeMs = remember(animatedProgress) { state.remainingSeconds * 1_000 }
                LaunchedEffect(animatedProgress) {
                    animatedProgress.animateTo(0f, tween(animationTimeMs, easing = LinearEasing))
                }
            }
            val remainingTimeText =
                if (state is ChangeServerViewState.Locked) state.remainingTimeText else "00:00"

            CircularProgressIndicator(
                progress = animatedProgress.value,
                strokeWidth = 8.dp,
                color = ProtonTheme.colors.brandNorm,
                trackColor = if (state is ChangeServerViewState.Locked) ProtonTheme.colors.backgroundSecondary
                    else ProtonTheme.colors.notificationSuccess,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.size(100.dp)
            )
            if (state is ChangeServerViewState.Locked) {
                Text(
                    text = remainingTimeText,
                    style = ProtonTheme.typography.defaultNorm
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_proton_checkmark),
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

@Preview
@Composable
fun UnlockedButtonPreview() {
    ChangeServerComposable(
        state = MutableStateFlow(ChangeServerViewState.Unlocked),
        onChangeServerClick = { },
        onUpgradeClick = {},
        onUpgradeModalOpened = {},
    )
}

@Preview
@Composable
fun LockedButtonPreview() {
    ChangeServerComposable(
        state = MutableStateFlow(ChangeServerViewState.Locked("00:12", 12, 20, true)),
        onChangeServerClick = { },
        onUpgradeClick = {},
        onUpgradeModalOpened = {},
    )
}

@Preview
@Composable
fun BottomSheetContentPreview() {
    UpgradeModalContent(
        state = ChangeServerViewState.Locked("00:12", 12, 20, true),
        onChangeServerClick = {},
        onUpgradeClick = {}
    )
}
