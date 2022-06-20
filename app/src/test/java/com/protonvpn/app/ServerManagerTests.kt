package com.protonvpn.app

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.auth.data.VpnUser
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.mockVpnUser
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.builtins.ListSerializer
import me.proton.core.util.kotlin.deserialize
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

class ServerManagerTests {

    private lateinit var manager: ServerManager

    @RelaxedMockK private lateinit var userData: UserData
    @RelaxedMockK private lateinit var currentUser: CurrentUser
    @RelaxedMockK private lateinit var vpnUser: VpnUser

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        mockkObject(CountryTools)
        currentUser.mockVpnUser { vpnUser }
        every { vpnUser.userTier } returns 2
        every { CountryTools.getPreferredLocale() } returns Locale.US
        manager = ServerManager(userData, currentUser) { 0L }
        val serversFile = File(javaClass.getResource("/Servers.json")?.path)
        val list = serversFile.readText().deserialize(ListSerializer(Server.serializer()))

        manager.setServers(list, null)
    }

    @Test
    fun doNotChooseOfflineServerFromCountry() {
        val country = manager.getVpnExitCountry("CA", false)
        val countryBestServer = manager.getBestScoreServer(country!!)
        Assert.assertEquals("CA#2", countryBestServer!!.serverName)
    }

    @Test
    fun doNotChooseOfflineServerFromAll() {
        Assert.assertEquals(
            "DE#1", manager.getBestScoreServer(userData.secureCoreEnabled)!!.serverName
        )
    }

    @Test
    fun testFilterForProtocol() {
        every { userData.selectedProtocol } returns VpnProtocol.WireGuard
        val filtered = manager.filterForProtocol(manager.getVpnCountries())
        Assert.assertEquals(listOf("CA#1", "DE#1"), filtered.flatMap { it.serverList.map { it.serverName } })
        val canada = filtered.first { it.flag == "CA" }
        Assert.assertEquals(1, canada.serverList.size)
        Assert.assertEquals(1, canada.serverList.first().connectingDomains.size)
    }

    @Test
    fun testGetServerById() {
        val server = manager.getServerById(
            "1H8EGg3J1QpSDL6K8hGsTvwmHXdtQvnxplUMePE7Hruen5JsRXvaQ75-sXptu03f0TCO-he3ymk0uhrHx6nnGQ=="
        )
        Assert.assertNotNull(server)
        Assert.assertEquals("CA#2", server?.serverName)
    }
}
