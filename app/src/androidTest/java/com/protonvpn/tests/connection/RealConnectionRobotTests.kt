package com.protonvpn.tests.connection

import androidx.test.core.app.ActivityScenario
import com.protonvpn.MockSwitch
import com.protonvpn.actions.LoginRobot
import com.protonvpn.actions.RealConnectionRobot
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.ui.login.LoginActivity
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.TestUser
import com.protonvpn.testsHelper.ServerManagerHelper
import com.protonvpn.testsHelper.ServiceTestHelper
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

/**
 * [RealConnectionRobotTests] Contains tests related to real VPN connection.
 */
@RunWith(Parameterized::class)
@HiltAndroidTest
class RealConnectionRobotTests(private val protocol: VpnProtocol) {

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun data() : List<VpnProtocol> {
            return listOf(
                    VpnProtocol.IKEv2,
                    VpnProtocol.OpenVPN,
                    VpnProtocol.WireGuard,
                    VpnProtocol.Smart,
            )
        }
    }

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val loginRobot = LoginRobot()
    private val connectionRobot = RealConnectionRobot()

    private lateinit var userDataHelper: UserDataHelper

    @Before
    fun setUp(){
        userDataHelper = UserDataHelper()
        userDataHelper.logoutUser()
        MockSwitch.mockedConnectionUsed = false
        MockSwitch.mockedServersUsed = false
        TestSetup.setCompletedOnboarding()

        ActivityScenario.launch(LoginActivity::class.java)
        ServerManagerHelper().serverManager.clearCache()
    }

    @Test
    //Don't run this test case individually, Junit has a bug https://github.com/android/android-test/issues/960
    fun realConnection() {
        runBlocking {
            userDataHelper.setProtocol(protocol)
            loginRobot.login(TestUser.getPlusUser())
            connectionRobot
                    .connectThroughQuickConnectRealConnection()
                    .verify {
                        checkIfConnectedAndCorrectIpAddressIsDisplayed()
                        checkProtocol(protocol)
                    }
            connectionRobot
                    .disconnectFromVPN()
                    .verify { checkIfDisconnected() }
        }
    }

    @After
    fun tearDown(){
        userDataHelper.logoutUser()
        ServiceTestHelper().connectionManager.disconnect()
        Storage.clearAllPreferences()
        ServerManagerHelper().serverManager.clearCache()
        MockSwitch.mockedConnectionUsed = true
        MockSwitch.mockedServersUsed = true
        userDataHelper.setProtocol(VpnProtocol.IKEv2)
    }
}