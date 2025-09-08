package com.protonvpn.app.telemetry.settings

import com.protonvpn.android.redesign.recents.data.DefaultConnection
import com.protonvpn.android.redesign.recents.usecases.ObserveDefaultConnection
import com.protonvpn.android.settings.data.CustomDnsSettings
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettings
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.telemetry.CommonDimensions
import com.protonvpn.android.telemetry.settings.GetSettingsTelemetrySnapshotDimensions
import com.protonvpn.android.theme.ThemeType
import com.protonvpn.android.tv.settings.FakeIsTvAutoConnectFeatureFlagEnabled
import com.protonvpn.android.ui.settings.AppIconManager
import com.protonvpn.android.ui.settings.CustomAppIconData
import com.protonvpn.android.vpn.ConnectivityMonitor
import com.protonvpn.android.vpn.usecases.FakeServerListTruncationEnabled
import com.protonvpn.android.widget.WidgetType
import com.protonvpn.android.widget.data.WidgetTracker
import com.protonvpn.mocks.FakeCommonDimensions
import com.protonvpn.mocks.FakeGetTruncationMustHaveIDs
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class GetSettingsTelemetrySnapshotDimensionsTests {

    @MockK
    private lateinit var mockAppIconManager: AppIconManager

    @MockK
    private lateinit var mockConnectivityMonitor: ConnectivityMonitor

    @MockK
    private lateinit var mockObserveDefaultConnection: ObserveDefaultConnection

    @MockK
    private lateinit var mockWidgetTracker: WidgetTracker

    private lateinit var getSettingsTelemetrySnapshotDimensions: GetSettingsTelemetrySnapshotDimensions

    private lateinit var getTruncationMustHaveIDs: FakeGetTruncationMustHaveIDs

    private lateinit var isServerListTruncationEnabledFlow: MutableStateFlow<Boolean>

    private lateinit var localUserSettingsFlow: MutableStateFlow<LocalUserSettings>

    private lateinit var testScope: TestScope

    private lateinit var testDispatcher: CoroutineDispatcher

    private val userTier = "paid"

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        testDispatcher = UnconfinedTestDispatcher()

        // Required setting main dispatcher due to usage of: EffectiveCurrentUserSettings
        Dispatchers.setMain(testDispatcher)

        testScope = TestScope(testDispatcher)

        getTruncationMustHaveIDs = FakeGetTruncationMustHaveIDs()

        isServerListTruncationEnabledFlow = MutableStateFlow(value = true)

        localUserSettingsFlow = MutableStateFlow(value = LocalUserSettings())

        getSettingsTelemetrySnapshotDimensions = GetSettingsTelemetrySnapshotDimensions(
            appIconManager = mockAppIconManager,
            connectivityMonitor = mockConnectivityMonitor,
            commonDimensions = FakeCommonDimensions(dimensions = mapOf("user_tier" to userTier)),
            effectiveCurrentUserSettings = EffectiveCurrentUserSettings(testScope.backgroundScope, localUserSettingsFlow),
            getTruncationMustHaveIDs = getTruncationMustHaveIDs,
            isServerListTruncationEnabled = FakeServerListTruncationEnabled(isServerListTruncationEnabledFlow),
            observeDefaultConnection = mockObserveDefaultConnection,
            widgetTracker = mockWidgetTracker,
            isTvAutoConnectFeatureFlagEnabled = FakeIsTvAutoConnectFeatureFlagEnabled(true)
        )

        every { mockAppIconManager.getCurrentIconData() } returns CustomAppIconData.DEFAULT
        every { mockConnectivityMonitor.isPrivateDnsActive } returns MutableStateFlow(null)
        every { mockObserveDefaultConnection() } returns MutableStateFlow(DefaultConnection.FastestConnection)
        every { mockWidgetTracker.widgetCount } returns MutableStateFlow(0)
        coEvery { mockWidgetTracker.firstWidgetType() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN appIcon options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "app_icon"

        listOf(
            CustomAppIconData.DEFAULT to "default",
            CustomAppIconData.DARK to "dark",
            CustomAppIconData.RETRO to "retro",
            CustomAppIconData.WEATHER to "weather",
            CustomAppIconData.NOTES to "notes",
            CustomAppIconData.CALCULATOR to "calculator",
        ).forEach { (appIcon, expectedDimensionValue) ->
            every { mockAppIconManager.getCurrentIconData() } returns appIcon

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN defaultConnection options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "default_connection_type"

        listOf(
            DefaultConnection.FastestConnection to "fastest",
            DefaultConnection.LastConnection to "last_connection",
            DefaultConnection.Recent(recentId = 1L) to "recent",
        ).forEach { (defaultConnection, expectedDimensionValue) ->
            every { mockObserveDefaultConnection() } returns MutableStateFlow(value = defaultConnection)

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN isPrivateDnsActive options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "is_system_custom_dns_enabled"

        listOf(
            null to "n/a",
            true to "true",
            false to "false",
        ).forEach { (isPrivateDnsActive, expectedDimensionValue) ->
            every { mockConnectivityMonitor.isPrivateDnsActive } returns MutableStateFlow(isPrivateDnsActive)

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN widgetCount options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "widget_count"

        listOf(
            0 to "0",
            1 to "1",
            2 to "2-4",
            3 to "2-4",
            4 to "2-4",
            5 to ">=5",
            6 to ">=5",
        ).forEach { (widgetCount, expectedDimensionValue) ->
            every { mockWidgetTracker.widgetCount } returns MutableStateFlow(value = widgetCount)

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN firstWidgetSize options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "first_widget_size"

        listOf(
            null to null,
            WidgetType.SMALL to "small",
            WidgetType.SMALL_MATERIAL to "small",
            WidgetType.BIG to "large",
            WidgetType.BIG_MATERIAL to "large",
        ).forEach { (widgetType, expectedDimensionValue) ->
            coEvery { mockWidgetTracker.firstWidgetType() } returns widgetType

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN firstWidgetTheme options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "first_widget_theme"

        listOf(
            null to null,
            WidgetType.SMALL to "proton",
            WidgetType.BIG to "proton",
            WidgetType.SMALL_MATERIAL to "material",
            WidgetType.BIG_MATERIAL to "material",
        ).forEach { (widgetType, expectedDimensionValue) ->
            coEvery { mockWidgetTracker.firstWidgetType() } returns widgetType

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN ServerListTruncation feature is disabled WHEN providing dimensions THEN dimension is excluded`() = testScope.runTest {
        val dimension = "server_list_truncation_protected_ids_count"
        isServerListTruncationEnabledFlow.value = false

        val dimensions = getSettingsTelemetrySnapshotDimensions()

        assertNull(dimensions[dimension])
    }

    @Test
    fun `GIVEN ServerListTruncation feature is enabled WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "server_list_truncation_protected_ids_count"
        isServerListTruncationEnabledFlow.value = true

        listOf(
            emptySet<String>() to "0",
            (1..1).map(Int::toString).toSet() to "1",
            (1..2).map(Int::toString).toSet() to "2-10",
            (1..10).map(Int::toString).toSet() to "2-10",
            (1..11).map(Int::toString).toSet() to "11-50",
            (1..50).map(Int::toString).toSet() to "11-50",
            (1..51).map(Int::toString).toSet() to ">=51",
        ).forEach { (truncatedIds, expectedDimensionValue) ->
            getTruncationMustHaveIDs.set(newTruncationIds = truncatedIds)

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN ThemeType options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "ui_theme"

        listOf(
            ThemeType.Dark to "dark",
            ThemeType.Light to "light",
            ThemeType.System to "system",
        ).forEach { (themeType, expectedDimensionValue) ->
            localUserSettingsFlow.value = LocalUserSettings(theme = themeType)

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN LAN mode options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "lan_mode"

        listOf(
            Pair(false, false) to "off",
            Pair(false, true) to "off",
            Pair(true, false) to "standard",
            Pair(true, true) to "direct",
        ).forEach { (lanMode, expectedDimensionValue) ->
            localUserSettingsFlow.value = LocalUserSettings(
                lanConnections = lanMode.first,
                lanConnectionsAllowDirect = lanMode.second,
            )

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN is IPV6 enabled options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "is_ipv6_enabled"

        listOf(
            true to "true",
            false to "false",
        ).forEach { (isIPV6Enabled, expectedDimensionValue) ->
            localUserSettingsFlow.value = LocalUserSettings(ipV6Enabled = isIPV6Enabled)

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN isCustomDnsEnabled options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "is_custom_dns_enabled"
        val rawDnsList = listOf("dns.vpn.test")

        listOf(
            Pair(true, emptyList<String>()) to "false",
            Pair(true, rawDnsList) to "true",
            Pair(false, emptyList<String>()) to "false",
            Pair(false, rawDnsList) to "false",
        ).forEach { (customDns, expectedDimensionValue) ->
            localUserSettingsFlow.value = LocalUserSettings(
                customDns = CustomDnsSettings(
                    toggleEnabled = customDns.first,
                    rawDnsList = customDns.second,
                )
            )

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN rawDnsList length options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "custom_dns_count"

        listOf(
            emptyList<String>() to "0",
            (1..1).map(Int::toString).toList() to "1",
            (1..2).map(Int::toString).toList() to "2-4",
            (1..4).map(Int::toString).toList() to "2-4",
            (1..5).map(Int::toString).toList() to ">=5",
        ).forEach { (rawDnsList, expectedDimensionValue) ->
            localUserSettingsFlow.value = LocalUserSettings(
                customDns = CustomDnsSettings(rawDnsList = rawDnsList)
            )

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `GIVEN rawDnsList address family options WHEN providing dimensions THEN dimension is set`() = testScope.runTest {
        val dimension = "first_custom_dns_address_family"

        listOf(
            emptyList<String>() to "n/a",
            listOf("2001:db8:85a3::8a2e:370:7334") to "ipv6",
            listOf("192.0.2.1") to "ipv4",
        ).forEach { (rawDnsList, expectedDimensionValue) ->
            localUserSettingsFlow.value = LocalUserSettings(
                customDns = CustomDnsSettings(rawDnsList = rawDnsList)
            )

            val dimensions = getSettingsTelemetrySnapshotDimensions()

            assertEquals(expectedDimensionValue, dimensions[dimension])
        }
    }

    @Test
    fun `WHEN providing dimensions THEN common dimensions are added`() = testScope.runTest {
        val dimensions = getSettingsTelemetrySnapshotDimensions()

        assertEquals(userTier, dimensions[CommonDimensions.Key.USER_TIER.reportedName])
    }

    @Test
    fun `WHEN TV auto connect FF is enabled THEN auto_connect dimension is added`() = testScope.runTest {
        listOf(
            true to "true",
            false to "false"
        ).forEach { (isEnabled, expectedValue) ->
            localUserSettingsFlow.value = LocalUserSettings(tvAutoConnectOnBoot = isEnabled)

            val dimensions = getSettingsTelemetrySnapshotDimensions()
            assertEquals(expectedValue, dimensions["is_auto_connect_enabled"])
        }
    }

}
