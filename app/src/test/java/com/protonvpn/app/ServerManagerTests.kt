package com.protonvpn.app

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.app.mocks.MockSharedPreference
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.mockkObject
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

    @get:Rule
    var rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        Storage.setPreferences(MockSharedPreference())
        val contextMock = mockk<Context>(relaxed = true)
        mockkObject(CountryTools)
        ProtonApplication.setAppContextForTest(contextMock)
        every { userData.hasAccessToServer(any()) } returns true
        every { userData.hasAccessToAnyServer(any()) } returns true
        every { CountryTools.getPreferredLocale(any()) } returns Locale.US
        manager = ServerManager(contextMock, userData)
        val serversFile = File(javaClass.getResource("/Servers.json")?.path)
        val list = serversFile.readText().deserialize(ListSerializer(Server.serializer()))

        manager.setServers(list)
    }

    @Test
    fun doNotChooseOfflineServerFromCountry() {
        val country = manager.getVpnExitCountry("CA", false)
        val countryBestServer = manager.getBestScoreServer(country!!)
        Assert.assertEquals("CA#2", countryBestServer!!.serverName)
    }

    @Test
    fun doNotChooseOfflineServerFromAll() {
        Assert.assertEquals("DE#1", manager.getBestScoreServer()!!.serverName)
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
}
