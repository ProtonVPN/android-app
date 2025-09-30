/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.app.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.R
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentPrimaryLabel
import com.protonvpn.android.redesign.vpn.ui.ConnectIntentViewState
import com.protonvpn.android.widget.WidgetActionHandler
import com.protonvpn.android.widget.WidgetRecent
import com.protonvpn.android.widget.WidgetViewState
import com.protonvpn.android.widget.WidgetVpnStatus
import com.protonvpn.android.widget.ui.ProtonGlanceTheme
import com.protonvpn.android.widget.ui.ProtonVpnGlanceWidget
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetTests {
    @Test
    fun widgetNeedLogin() = runGlanceAppWidgetUnitTest {
        val widgetSelectors = setupWidget(WidgetSize.MEDIUM_WIDGET) {
            ProtonVpnGlanceWidget().NeedLogin(mainActivityAction = WidgetData.actionPlaceholder)
        }

        onNode(widgetSelectors.signInButton).assertExists()
    }

    @Test
    fun widgetSmallDisconnected() = runGlanceAppWidgetUnitTest {
        val widgetSelectors = setupWidget(WidgetSize.SMALL_WIDGET) {
            ProtonVpnGlanceWidget().LoggedIn(viewState = WidgetData.widgetViewState(WidgetVpnStatus.Disconnected))
        }

        onNode(widgetSelectors.connectButton).assertExists()
        onNode(widgetSelectors.fastestCountry).assertExists()
        onNode(widgetSelectors.unprotectedLabel).assertDoesNotExist()
        onNode(widgetSelectors.recentsHeadline).assertDoesNotExist()
        onNode(hasTextEqualTo(WidgetData.PROFILE_NAME)).assertDoesNotExist()
    }

    @Test
    fun widgetMediumDisconnected() = runGlanceAppWidgetUnitTest {
        val widgetSelectors = setupWidget(WidgetSize.MEDIUM_WIDGET) {
            ProtonVpnGlanceWidget().LoggedIn(viewState = WidgetData.widgetViewState(WidgetVpnStatus.Disconnected))
        }

        onNode(widgetSelectors.connectButton).assertExists()
        onNode(widgetSelectors.fastestCountry).assertExists()
        onNode(widgetSelectors.unprotectedLabel).assertExists()
        onNode(widgetSelectors.recentsHeadline).assertDoesNotExist()
        onNode(hasTextEqualTo(WidgetData.PROFILE_NAME)).assertDoesNotExist()
    }

    @Test
    fun widgetLargeWithRecentDisconnected() = runGlanceAppWidgetUnitTest {
        val widgetSelectors = setupWidget(WidgetSize.LARGE_WIDGET) {
            ProtonVpnGlanceWidget().LoggedIn(viewState = WidgetData.widgetViewState(WidgetVpnStatus.Disconnected))
        }

        onNode(widgetSelectors.connectButton).assertExists()
        onNode(widgetSelectors.fastestCountry).assertExists()
        onNode(widgetSelectors.unprotectedLabel).assertExists()
        onNode(widgetSelectors.recentsHeadline).assertExists()
        onNode(hasTextEqualTo(WidgetData.PROFILE_NAME)).assertExists()
    }

    @Test
    fun widgetSmallConnected() = runGlanceAppWidgetUnitTest {
        val widgetSelectors = setupWidget(WidgetSize.SMALL_WIDGET) {
            ProtonVpnGlanceWidget().LoggedIn(viewState = WidgetData.widgetViewState(WidgetVpnStatus.Connected))
        }

        onNode(widgetSelectors.disconnectButton).assertExists()
        onNode(widgetSelectors.fastestCountry).assertExists()
        onNode(widgetSelectors.unprotectedLabel).assertDoesNotExist()
        onNode(widgetSelectors.recentsHeadline).assertDoesNotExist()
        onNode(hasTextEqualTo(WidgetData.PROFILE_NAME)).assertDoesNotExist()
    }

    @Test
    fun widgetLargeWithRecentConnected() = runGlanceAppWidgetUnitTest {
        val widgetSelectors = setupWidget(WidgetSize.LARGE_WIDGET) {
            ProtonVpnGlanceWidget().LoggedIn(viewState = WidgetData.widgetViewState(WidgetVpnStatus.Connected))
        }

        onNode(widgetSelectors.disconnectButton).assertExists()
        onNode(widgetSelectors.fastestCountry).assertExists()
        onNode(widgetSelectors.protectedLabel).assertExists()
        onNode(widgetSelectors.recentsHeadline).assertExists()
        onNode(hasTextEqualTo(WidgetData.PROFILE_NAME)).assertExists()
    }

    @Test
    fun widgetSmallUnavailable() = runGlanceAppWidgetUnitTest {
        val widgetSelectors = setupWidget(WidgetSize.SMALL_WIDGET) {
            ProtonVpnGlanceWidget().WidgetUnavailableServers()
        }

        onNode(widgetSelectors.unavailableLabel).assertExists()
    }

    @Test
    fun widgetMediumUnavailable() = runGlanceAppWidgetUnitTest {
        val widgetSelectors = setupWidget(WidgetSize.MEDIUM_WIDGET) {
            ProtonVpnGlanceWidget().WidgetUnavailableServers()
        }

        onNode(widgetSelectors.unavailableLabel).assertExists()
    }

    @Test
    fun widgetLargeUnavailable() = runGlanceAppWidgetUnitTest {
        val widgetSelectors = setupWidget(WidgetSize.LARGE_WIDGET) {
            ProtonVpnGlanceWidget().WidgetUnavailableServers()
        }

        onNode(widgetSelectors.unavailableLabel).assertExists()
    }

    private fun GlanceAppWidgetUnitTest.setupWidget(size: DpSize, content: @Composable () -> Unit): WidgetSelectors {
        setAppWidgetSize(size)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setContext(context)

        provideComposable {
            ProtonGlanceTheme {
                content()
            }
        }
        return WidgetSelectors(context)
    }
}

private class WidgetSelectors(context: Context) {
    val connectButton = hasTextEqualTo(context.getString(R.string.buttonConnect))
    val signInButton = hasTextEqualTo(context.getString(R.string.widget_sign_in))
    val fastestCountry = hasTextEqualTo(context.getString(R.string.fastest_country))
    val unprotectedLabel = hasTextEqualTo(context.getString(R.string.widget_status_unprotected))
    val recentsHeadline = hasTextEqualTo(context.getString(R.string.recents_headline))
    val disconnectButton = hasTextEqualTo(context.getString(R.string.disconnect))
    val protectedLabel = hasTextEqualTo(context.getString(R.string.vpn_status_connected))
    val unavailableLabel = hasTextEqualTo(context.getString(R.string.no_connections_title))
}

private object WidgetData{
    const val PROFILE_NAME = "Profile Recent"

    val actionPlaceholder = actionStartActivity(
        ComponentName("test","test"),
        WidgetActionHandler.connectActionParameters(null),
    )

    fun widgetViewState(vpnStatus: WidgetVpnStatus) = WidgetViewState.LoggedIn(
        connectCard = ConnectIntentViewState(
            primaryLabel = ConnectIntentPrimaryLabel.Fastest(
                connectedCountry = CountryId.fastest,
                isFree = false,
                isSecureCore = false
            ),
            secondaryLabel = null,
            serverFeatures = setOf()
        ),
        connectCardAction = actionPlaceholder,
        vpnStatus = vpnStatus,
        recents = listOf(
            WidgetRecent(
                action = actionPlaceholder,
                connectIntentViewState = ConnectIntentViewState(
                    primaryLabel = ConnectIntentPrimaryLabel.Profile(PROFILE_NAME, CountryId.iceland, true, ProfileIcon.Icon5, ProfileColor.Color3) ,
                    secondaryLabel = null,
                    serverFeatures = setOf()
                )
            )
        ),
        launchMainActivityAction = actionPlaceholder
    )
}

private object WidgetSize {
    val SMALL_WIDGET = DpSize(140.dp, 149.dp)
    val MEDIUM_WIDGET = DpSize(320.dp, 152.dp)
    val LARGE_WIDGET = DpSize(402.dp, 392.dp)
}
