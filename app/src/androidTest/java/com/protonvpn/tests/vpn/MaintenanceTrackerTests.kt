package com.protonvpn.tests.vpn

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SdkSuppress
import com.protonvpn.android.api.ProtonApiRetroFit
import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.models.vpn.ConnectingDomain
import com.protonvpn.android.models.vpn.ConnectingDomainResponse
import com.protonvpn.android.ui.home.ServerListUpdater
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.vpn.MaintenanceTracker
import com.protonvpn.android.vpn.VpnStateMonitor
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import me.proton.core.network.domain.ApiResult
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// These tests use mocking of final classes that's not available on API < 28
@SdkSuppress(minSdkVersion = 28)
class MaintenanceTrackerTests {

    private lateinit var maintenanceTracker: MaintenanceTracker

    @MockK private lateinit var apiRetroFit: ProtonApiRetroFit

    @RelaxedMockK private lateinit var vpnStateMonitor: VpnStateMonitor

    @RelaxedMockK private lateinit var serverListUpdater: ServerListUpdater

    @RelaxedMockK private lateinit var serverManager: ServerManager

    @RelaxedMockK private lateinit var appConfig: AppConfig

    @RelaxedMockK private lateinit var scope: CoroutineScope

    @get:Rule var rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        maintenanceTracker =
            MaintenanceTracker(scope, apiRetroFit, serverManager, serverListUpdater, appConfig)
        maintenanceTracker.initWithStateMonitor(vpnStateMonitor)
    }

    @Test
    fun featureFlagDisablesMaintenanceChecking() = runBlocking {
        every { appConfig.isMaintenanceTrackerEnabled() } returns false
        Assert.assertEquals(false, maintenanceTracker.checkMaintenanceReconnect())
    }

    @Test
    fun reconnectHappensIfServerIsOffline() = runBlocking {
        every { appConfig.isMaintenanceTrackerEnabled() } returns true
        val mockedDomain = mockk<ConnectingDomain>(relaxed = true)
        every { mockedDomain.isOnline } returns false
        coEvery { apiRetroFit.getConnectingDomain(any()) } returns ApiResult.Success(
            ConnectingDomainResponse(
                mockedDomain
            )
        )

        Assert.assertEquals(true, maintenanceTracker.checkMaintenanceReconnect())
        verify { vpnStateMonitor.connect(any(), any()) }
    }
}
