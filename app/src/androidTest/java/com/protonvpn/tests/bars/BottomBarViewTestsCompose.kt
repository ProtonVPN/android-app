package com.protonvpn.tests.bars

import com.protonvpn.android.R
import com.protonvpn.android.redesign.main_screen.ui.BottomBarView
import com.protonvpn.testRules.setVpnContent
import me.proton.test.fusion.Fusion.node
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Test

class BottomBarViewTestsCompose : FusionComposeTest() {

    @Test
    fun whenShowCountriesIsFalse_thenCountriesNavigationBarItemIsHidden() {
        val showCountries = false

        composeRule.setVpnContent {
            BottomBarView(
                showCountries = showCountries,
                showGateways = true,
                selectedTarget = null,
                notificationDots = emptySet(),
                navigateTo = {},
            )
        }

        node.withText(R.string.bottom_nav_countries).assertDoesNotExist()
    }

    @Test
    fun whenShowCountriesIsTrue_thenCountriesNavigationBarItemIsShown() {
        val showCountries = true

        composeRule.setVpnContent {
            BottomBarView(
                showCountries = showCountries,
                showGateways = true,
                selectedTarget = null,
                notificationDots = emptySet(),
                navigateTo = {},
            )
        }

        node.withText(R.string.bottom_nav_countries).assertIsDisplayed()
    }

    @Test
    fun whenShowGatewaysIsFalse_thenGatewaysNavigationBarItemIsHidden() {
        val showGateways = false

        composeRule.setVpnContent {
            BottomBarView(
                showCountries = true,
                showGateways = showGateways,
                selectedTarget = null,
                notificationDots = emptySet(),
                navigateTo = {},
            )
        }

        node.withText(R.string.bottom_nav_gateways).assertDoesNotExist()
    }

    @Test
    fun whenShowGatewaysIsTrue_thenGatewaysNavigationBarItemIsShown() {
        val showGateways = true

        composeRule.setVpnContent {
            BottomBarView(
                showCountries = true,
                showGateways = showGateways,
                selectedTarget = null,
                notificationDots = emptySet(),
                navigateTo = {},
            )
        }

        node.withText(R.string.bottom_nav_gateways).assertIsDisplayed()
    }

}
