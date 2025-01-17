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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
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
import com.protonvpn.android.R
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.widget.WidgetStateUpdater
import com.protonvpn.android.widget.WidgetViewState
import com.protonvpn.android.widget.WidgetVpnStatus
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
        private val SHORT = 126.dp
        private val MEDIUM = 160.dp
        private val TALL = 260.dp
        private val XTALL = 368.dp

        private val NARROW = 100.dp
        private val WIDE = 180.dp
        private val XWIDE = 254.dp

        private val SHORT_NARROW_SIZE = DpSize(NARROW, SHORT)
        private val SHORT_WIDE_SIZE = DpSize(WIDE, SHORT)
        private val SHORT_XWIDE_SIZE = DpSize(XWIDE, SHORT)

        private val MEDIUM_NARROW_SIZE = DpSize(NARROW, MEDIUM)
        private val MEDIUM_WIDE_SIZE = DpSize(WIDE, MEDIUM)
        private val MEDIUM_XWIDE_SIZE = DpSize(XWIDE, MEDIUM)

        private val TALL_NARROW_SIZE = DpSize(NARROW, TALL)
        private val TALL_WIDE_SIZE = DpSize(WIDE, TALL)
        private val TALL_XWIDE_SIZE = DpSize(XWIDE, TALL)

        private val XTALL_NARROW_SIZE = DpSize(NARROW, XTALL)
        private val XTALL_WIDE_SIZE = DpSize(WIDE, XTALL)
        private val XTALL_XWIDE_SIZE = DpSize(XWIDE, XTALL)
    }

    override val sizeMode = SizeMode.Responsive(
        setOf(
            SHORT_NARROW_SIZE, SHORT_WIDE_SIZE, SHORT_XWIDE_SIZE,
            MEDIUM_NARROW_SIZE, MEDIUM_WIDE_SIZE, MEDIUM_XWIDE_SIZE,
            TALL_NARROW_SIZE, TALL_WIDE_SIZE, TALL_XWIDE_SIZE,
            XTALL_NARROW_SIZE, XTALL_WIDE_SIZE, XTALL_XWIDE_SIZE
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

                val mainActivityAction = actionStartActivity(viewState.launchActivity)
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .background(ImageProvider(viewState.widgetBackground()))
                        .clickable(mainActivityAction)
                        .padding(8.dp)
                ) {
                    when (viewState) {
                        is WidgetViewState.NeedLogin -> NeedLogin(mainActivityAction)
                        is WidgetViewState.LoggedIn -> LoggedIn(context, viewState)
                    }
                }
            }
        }
    }

    @Composable
    private fun NeedLogin(mainActivityAction: Action) {
        val size = LocalSize.current
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    ImageProvider(ProtonGlanceTheme.resources.logoIcon),
                    colorFilter = ProtonGlanceTheme.colors.logoIcon?.let { ColorFilter.tint(it) },
                    contentDescription = null,
                    modifier = GlanceModifier.size(36.dp)
                )
                if (size.width >= XWIDE) {
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
    private fun LoggedIn(
        context: Context,
        viewState: WidgetViewState.LoggedIn,
    ) {
        val size = LocalSize.current
        val isDisconnected = viewState.vpnStatus == WidgetVpnStatus.Disconnected
        Column(GlanceModifier.fillMaxSize()) {
            when {
                size == SHORT_NARROW_SIZE ->
                    ConnectWithIntent(
                        context,
                        viewState,
                        showConnecting = true,
                        wide = false,
                        GlanceModifier.fillMaxSize()
                    )

                size == MEDIUM_NARROW_SIZE ->
                    Column(GlanceModifier.fillMaxSize()) {
                        GlanceVpnStatus(viewState.vpnStatus, wide = false)
                        ConnectWithIntent(
                            context,
                            viewState,
                            showConnecting = false,
                            wide = false,
                            GlanceModifier.fillMaxWidth().defaultWeight()
                        )
                    }

                size.height <= MEDIUM && size.width >= WIDE ->
                    Column(GlanceModifier.fillMaxSize()) {
                        val showStatus = size.height >= MEDIUM
                        if (showStatus) {
                            GlanceVpnStatus(viewState.vpnStatus, wide = size.width >= XWIDE)
                            Spacer(modifier = GlanceModifier.height(8.dp))
                        }
                        if (isDisconnected) {
                            GlanceRecents(
                                context,
                                viewState.mergedRecents(),
                                maxColumns = size.toMaxColumns(),
                                maxRows = 1
                            )
                        } else {
                            ConnectWithIntent(
                                context,
                                viewState,
                                showConnecting = !showStatus,
                                wide = true,
                                GlanceModifier.defaultWeight()
                            )
                        }
                    }

                size.height >= TALL ->
                    Column(GlanceModifier.fillMaxSize()) {
                        val isWide = size.width >= WIDE
                        GlanceVpnStatus(viewState.vpnStatus, wide = isWide)
                        ConnectWithIntent(
                            context,
                            viewState,
                            showConnecting = false,
                            wide = isWide,
                            GlanceModifier.defaultWeight()
                        )
                        Spacer(modifier = GlanceModifier.height(8.dp))
                        val maxRows = if (size.height >= XTALL) 2 else 1
                        GlanceRecents(
                            context,
                            viewState.recentsWithoutPinnedConnectCard(),
                            maxColumns = size.toMaxColumns(),
                            maxRows = maxRows
                        )
                    }

                else -> DebugUtils.debugAssert("Unsupported widget size: $size") { false }
            }
        }
    }

    private fun DpSize.toMaxColumns() = when (width) {
        XWIDE -> 3
        WIDE -> 2
        else -> 1
    }
}
