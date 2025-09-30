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

package com.protonvpn.android.widget.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import com.protonvpn.android.R
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentSecondaryLabel
import com.protonvpn.android.widget.WidgetStateUpdater
import com.protonvpn.android.widget.WidgetViewState
import com.protonvpn.android.widget.hasMaterialYouTheme
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

class ProtonVpnGlanceWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun getWidgetStateUpdater(): WidgetStateUpdater
    }

    companion object {
        private val SHORT = 149.dp
        private val MEDIUM = 200.dp
        private val TALL = 331.dp
        private val XTALL = 426.dp

        private val NARROW = 100.dp
        private val SLIM = 140.dp
        private val WIDE = 210.dp
        private val XWIDE = 280.dp

        private val SHORT_NARROW_SIZE = DpSize(NARROW, SHORT)
        private val SHORT_SLIM_SIZE = DpSize(SLIM, SHORT)
        private val SHORT_WIDE_SIZE = DpSize(WIDE, SHORT)

        private val MEDIUM_NARROW_SIZE = DpSize(NARROW, MEDIUM)
        private val MEDIUM_WIDE_SIZE = DpSize(WIDE, MEDIUM)

        private val TALL_WIDE_SIZE = DpSize(WIDE, TALL)
        private val TALL_XWIDE_SIZE = DpSize(XWIDE, TALL)

        private val XTALL_WIDE_SIZE = DpSize(WIDE, XTALL)
        private val XTALL_XWIDE_SIZE = DpSize(XWIDE, XTALL)
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(
            SHORT_NARROW_SIZE, SHORT_SLIM_SIZE, SHORT_WIDE_SIZE,
            MEDIUM_NARROW_SIZE, MEDIUM_WIDE_SIZE,
            TALL_WIDE_SIZE, TALL_XWIDE_SIZE,
            XTALL_WIDE_SIZE, XTALL_XWIDE_SIZE
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = GlanceAppWidgetManager(context)
        val widgetInto = AppWidgetManager.getInstance(context).getAppWidgetInfo(manager.getAppWidgetId(id))

        provideContent {
            ProtonGlanceTheme(isMaterialYou = hasMaterialYouTheme(widgetInto.provider)) {
                val entryPoints = remember { EntryPoints.get(context, Dependencies::class.java) }
                val viewState = entryPoints.getWidgetStateUpdater()
                    .widgetViewStateFlow
                    .collectAsState()
                    .value
                    ?: return@ProtonGlanceTheme

                val size = LocalSize.current

                // Using wrapContentHeight with 2 spacers, theoretically shouldn't do anything but
                // there seems to be a bug on API 31 where wrap content doesn't seem to be
                // applied sometimes and we get max height.
                Column(
                    GlanceModifier
                        .fillMaxWidth()
                        .wrapContentHeight() // Somehow keeps widget centered in the grid bounds
                ) {
                    Spacer(GlanceModifier.defaultWeight())
                    Box(
                        modifier = GlanceModifier
                            .appWidgetBackground()
                            .background(ImageProvider(viewState.widgetBackground()))
                            .clickable(viewState.launchMainActivityAction)
                    ) {
                        val contentModifier = GlanceModifier
                            .padding(if (size.width >= WIDE && size.height >= TALL) 12.dp else 8.dp)
                            .fillMaxWidth()
                        when (viewState) {
                            is WidgetViewState.NeedLogin -> NeedLogin(
                                viewState.launchMainActivityAction,
                                contentModifier
                            )
                            is WidgetViewState.LoggedIn -> {
                                val gradient = viewState.statusGradient()
                                if (gradient != null) {
                                    Spacer(
                                        GlanceModifier
                                            .background(ImageProvider(gradient))
                                            .fillMaxWidth()
                                            .height(if (size.height >= TALL) 100.dp else 72.dp)
                                    )
                                }
                                LoggedIn(viewState, contentModifier)
                            }

                            is WidgetViewState.NoServersAvailable -> {
                                WidgetUnavailableServers(
                                    modifier = contentModifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }

    @Composable
    fun NeedLogin(
        mainActivityAction: Action,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val size = LocalSize.current
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = GlanceModifier.padding(vertical = 20.dp).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    ImageProvider(ProtonGlanceTheme.resources.logoIcon),
                    colorFilter = ProtonGlanceTheme.colors.logoIcon?.let { ColorFilter.tint(it) },
                    contentDescription = null,
                    modifier = GlanceModifier.size(36.dp)
                )
                if (size.width >= WIDE) {
                    Image(
                        ImageProvider(R.drawable.protonvpn_text_logo),
                        colorFilter = ColorFilter.tint(ProtonGlanceTheme.colors.logoText),
                        contentDescription = null,
                        modifier = GlanceModifier.padding(start = 8.dp).height(36.dp)
                    )
                }
            }
            GlanceButton(R.string.widget_sign_in, mainActivityAction)
        }
    }

    @Composable
    fun LoggedIn(
        viewState: WidgetViewState.LoggedIn,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val size = LocalSize.current
        val width = size.width
        val height = size.height
        val isWide = width >= WIDE
        Column(modifier) {
            GlanceVpnStatus(viewState.vpnStatus, small = height < TALL, wide = isWide)

            val intentDimensions = size.connectCardDimensions()
            Box(
                // Use fixed height for connect card so that font scaling doesn't affect it's size.
                // cardIntentFixedHeight will need to be updated if the card layout changes.
                GlanceModifier.height(intentDimensions.cardIntentFixedHeight()),
                contentAlignment = Alignment.CenterStart
            ) {
                val isFastestFreeServer = viewState.connectCard.secondaryLabel is ConnectIntentSecondaryLabel.FastestFreeServer
                val card =
                    if (isFastestFreeServer && width < WIDE) viewState.connectCard.copy(secondaryLabel = null)
                    else viewState.connectCard

                GlanceConnectIntent(card, dimensions = intentDimensions, center = false)
            }
            GlanceConnectButton(viewState.connectCardAction, viewState.vpnStatus)

            if (height >= TALL && width >= WIDE) {
                val recents = viewState.recentsWithoutPinnedConnectCard()
                if (recents.isNotEmpty()) {
                    Text(
                        text = glanceStringResource(R.string.recents_headline),
                        style = ProtonGlanceTheme.typography.mediumSecondary,
                        modifier = GlanceModifier.padding(top = 16.dp, bottom = 12.dp)
                    )
                    val maxRows = if (size.height >= XTALL) 2 else 1
                    GlanceRecents(
                        recents,
                        maxColumns = size.toMaxColumns(),
                        maxRows = maxRows,
                    )
                }
            }
        }
    }

    @Composable
    fun WidgetUnavailableServers(modifier: GlanceModifier = GlanceModifier) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(resId = R.drawable.globe_error),
                contentDescription = null,
            )

            Spacer(modifier = GlanceModifier.height(height = 12.dp))

            Text(
                text = glanceStringResource(id = R.string.no_connections_title),
                style = ProtonGlanceTheme.typography.bigSecondary.copy(
                    textAlign = TextAlign.Center,
                )
            )
        }
    }

    private fun GlanceIntentDimensions.cardIntentFixedHeight(): Dp {
        // 2 x vertical padding (16dp or 24dp) that will be consumed if labels font will be scaled with
        // system setting
        val isBig = labelSize == GlanceIntentLabelSize.Big
        val extraSpace = if (isBig) 48.dp else 32.dp
        return extraSpace + if (isBig) {
            if (horizontal) 36.dp else 64.dp
        } else {
            if (horizontal) 35.dp else 59.dp
        }
    }

    private fun DpSize.connectCardDimensions() : GlanceIntentDimensions {
        val isLargerCardIntent = height >= TALL && width >= WIDE
        val intentSize = if (isLargerCardIntent) GlanceIntentLabelSize.Big else GlanceIntentLabelSize.Medium
        val intentHorizontal = this != MEDIUM_NARROW_SIZE
        return GlanceIntentDimensions(
            labelSize = intentSize,
            forceMaxHeight = false,
            horizontal = intentHorizontal,
            showIcon = this != SHORT_NARROW_SIZE,
        )
    }

    private fun DpSize.toMaxColumns() = when (width) {
        XWIDE -> 3
        WIDE -> 2
        else -> 1
    }
}
