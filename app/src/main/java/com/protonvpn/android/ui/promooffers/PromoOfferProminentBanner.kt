/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.ui.promooffers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.protonvpn.android.appconfig.ApiNotificationProminentBannerStyle
import com.protonvpn.android.base.ui.ProtonSecondaryButton
import com.protonvpn.android.base.ui.protonSecondaryButtonColors
import com.protonvpn.android.base.ui.ProtonVpnPreview
import kotlinx.coroutines.launch
import me.proton.core.compose.component.VerticalSpacer
import me.proton.core.compose.theme.ProtonTheme

private data class ProminentBannerColors(
    val container: Color,
    val text: Color,
    val mainButtonColor: Color,
)

private val warningColors: ProminentBannerColors
    @Composable
    get() = ProminentBannerColors(
        container = ProtonTheme.colors.notificationError,
        text = ProtonTheme.colors.textInverted,
        mainButtonColor = ProtonTheme.colors.interactionStrongNorm,
    )
private val regularColors: ProminentBannerColors
    @Composable
    get() = ProminentBannerColors(
        container = ProtonTheme.colors.interactionWeakNorm,
        text = ProtonTheme.colors.textNorm,
        mainButtonColor = ProtonTheme.colors.interactionStrongNorm,
    )


@Composable
fun PromoOfferProminentBanner(
    state: ProminentBannerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets(0),
    onAction: (suspend () -> Unit)? = null
) {
    val colors = when (state.style) {
        ApiNotificationProminentBannerStyle.REGULAR -> regularColors
        ApiNotificationProminentBannerStyle.WARNING -> warningColors
    }
    PromoOfferProminentBanner(
        title = state.title,
        description = state.description,
        actionButtonText = state.actionButton?.text,
        dismissButtonText = state.dismissButtonText,
        onAction = onAction,
        onDismiss = onDismiss,
        colors = colors,
        contentWindowInsets = contentWindowInsets,
        modifier = modifier,
    )
}

@Composable
private fun PromoOfferProminentBanner(
    dismissButtonText: String,
    colors: ProminentBannerColors,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentWindowInsets: WindowInsets = WindowInsets(0),
    title: String? = null,
    description: String? = null,
    actionButtonText: String? = null,
    onAction: (suspend () -> Unit)? = null,
) {
    Surface(
        color = colors.container,
        contentColor = colors.text,
        shadowElevation = 16.dp,
        modifier = modifier,
    ) {
        val coroutineScope = rememberCoroutineScope()
        Column(
            modifier = Modifier
                .windowInsetsPadding(contentWindowInsets)
                .padding(top = 24.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            if (title != null) {
                Text(title, style = ProtonTheme.typography.body2Medium)
                if (description != null) {
                    VerticalSpacer(height = 4.dp)
                }
            }
            if (description != null) {
                Text(description, style = ProtonTheme.typography.body2Regular)
            }
            VerticalSpacer(height = 16.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                val mainButtonColors = ButtonDefaults.protonSecondaryButtonColors(
                    backgroundColor = ProtonTheme.colors.interactionStrongNorm,
                    contentColor = ProtonTheme.colors.textInverted,
                    disabledBackgroundColor = ProtonTheme.colors.interactionStrongNorm.copy(alpha = 0.3f),
                    disabledContentColor = ProtonTheme.colors.textInverted.copy(alpha = 0.3f)
                )
                val secondButtonColors = ButtonDefaults.protonSecondaryButtonColors(
                    backgroundColor = Color.Transparent,
                    contentColor = colors.text,
                )
                val hasOnlyCloseButton = actionButtonText == null || onAction == null
                ProtonSecondaryButton(
                    onClick = onDismiss,
                    colors = if (hasOnlyCloseButton) mainButtonColors else secondButtonColors,
                ) {
                    Text(dismissButtonText)
                }
                if (actionButtonText != null && onAction != null) {
                    var isProcessingAction by remember { mutableStateOf(false) }
                    val action: () -> Unit = {
                        coroutineScope.launch {
                            isProcessingAction = true
                            onAction()
                            isProcessingAction = false
                        }
                    }
                    ProtonSecondaryButton(
                        onClick = action,
                        colors = mainButtonColors,
                        enabled = !isProcessingAction,
                    ) {
                        Text(actionButtonText)
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewPromoOfferProminentBannerRegular() {
    ProtonVpnPreview {
        PromoOfferProminentBanner(
            title = "Title",
            description = "This is a very long description because we might have a lot to say here. Hopefully not so much that the banner completely covers the screen though :)",
            dismissButtonText = "OK",
            onDismiss = {},
            colors = regularColors,
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }
}

@Preview
@Composable
private fun PreviewPromoOfferProminentBannerWarning() {
    ProtonVpnPreview {
        PromoOfferProminentBanner(
            title = "Your subscription is about to expire!",
            actionButtonText = "Renew",
            onAction = {},
            dismissButtonText = "Not now",
            onDismiss = {},
            colors = warningColors,
            modifier = Modifier.padding(bottom = 32.dp)
        )
    }
}
