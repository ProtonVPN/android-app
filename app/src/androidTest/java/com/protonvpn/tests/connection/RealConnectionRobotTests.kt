package com.protonvpn.tests.connection

import androidx.test.core.app.ActivityScenario
import com.protonvpn.MockSwitch
import com.protonvpn.actions.LoginRobot
import com.protonvpn.actions.RealConnectionRobot
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.ui.login.LoginActivity
import com.protonvpn.android.utils.Storage
import com.protonvpn.data.DefaultData
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testsHelper.ServerManagerHelper
import com.protonvpn.testsHelper.TestSetup
import com.protonvpn.testsHelper.UserDataHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import javax.inject.Inject

/**
 * [RealConnectionRobotTests] Contains tests related to real VPN connection.
 */
@RunWith(Parameterized::class)
@HiltAndroidTest
class RealConnectionRobotTests(private val protocol: VpnProtocol) {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var api: ProtonApiRetroFit

    private val loginRobot = LoginRobot()
    private val connectionRobot = RealConnectionRobot()
    private lateinit var userDataHelper: UserDataHelper

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun data() : List<VpnProtocol> {
            return listOf(
                    VpnProtocol.IKEv2,
                    VpnProtocol.OpenVPN,
                    VpnProtocol.WireGuard,
            )
        }
    }

    @Before
    fun setUp(){
        MockSwitch.mockedConnectionUsed = false
        MockSwitch.mockedServersUsed = false
        hiltRule.inject()
        userDataHelper = UserDataHelper()
        userDataHelper.logoutUser()
        TestSetup.setCompletedOnboarding()

        ActivityScenario.launch(LoginActivity::class.java)
        ServerManagerHelper().serverManager.clearCache()
        userDataHelper.setProtocol(protocol)
    }

    @Test
    //Don't run this test case individually, Junit has a bug https://github.com/android/android-test/issues/960
    fun realConnection() {
            loginRobot.loginWithWait(TestUser.getPlusUser())
            connectionRobot
                    .connectThroughQuickConnectRealConnection()
                    .verify {
                        runBlocking {
                            checkIfConnectedAndCorrectIpAddressIsDisplayed(api)
                        }
                        checkProtocol(protocol)
                    }
            connectionRobot
                    .disconnectFromVPN()
                    .verify { checkIfDisconnected() }
    }

    @After
    fun tearDown(){
        userDataHelper.logoutUser()
        Storage.clearAllPreferences()
        MockSwitch.mockedConnectionUsed = true
        MockSwitch.mockedServersUsed = true
        userDataHelper.setProtocol(DefaultData.DEFAULT_PROTOCOL)
    }
}