package com.protonvpn.app.ui.settings.customdns

import app.cash.turbine.test
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.tv.settings.customdns.TvSettingsCustomDnsViewModel
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TvSettingsCustomDnsViewModelTests {

    private lateinit var testScope: TestScope

    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    private lateinit var viewModel: TvSettingsCustomDnsViewModel

    @Before
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher(scheduler = TestCoroutineScheduler())
        testScope = TestScope(context = testDispatcher)

        Dispatchers.setMain(dispatcher = testDispatcher)

        userSettingsManager = CurrentUserLocalSettingsManager(
            userSettingsStoreProvider = LocalUserSettingsStoreProvider(
                factory = InMemoryDataStoreFactory(),
            )
        )

        viewModel = TvSettingsCustomDnsViewModel(
            mainScope = testScope,
            userSettingsManager = userSettingsManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `GIVEN user has no custom DNS WHEN observing view state THEN view state is Empty`() = testScope.runTest {
        val customDnsList = emptyList<String>()
        val expectedViewState = TvSettingsCustomDnsViewModel.ViewState.Empty
        userSettingsManager.updateCustomDnsList(customDnsList)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN user has custom DNS WHEN observing view state THEN view state is CustomDns`() = testScope.runTest {
        val customDnsList = listOf("1.1.1.1")
        val expectedViewState = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            isCustomDnsEnabled = false,
            customDnsList = customDnsList,
        )
        userSettingsManager.updateCustomDnsList(customDnsList)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN user has custom DNS WHEN toggle custom DNS THEN view state is updated`() = testScope.runTest {
        val customDnsList = listOf("2001:db8:3333:4444:5555:6666:7777:8888")
        val expectedViewStateDisabled = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            isCustomDnsEnabled = false,
            customDnsList = customDnsList,
        )
        val expectedViewStateEnabled = TvSettingsCustomDnsViewModel.ViewState.CustomDns(
            isCustomDnsEnabled = true,
            customDnsList = customDnsList,
        )
        userSettingsManager.updateCustomDnsList(customDnsList)

        viewModel.viewStateFlow.test {
            val viewStateDisabled = awaitItem()
            assertEquals(expectedViewStateDisabled, viewStateDisabled)

            viewModel.onToggleIsCustomDnsEnabled()

            val viewStateEnabled = awaitItem()
            assertEquals(expectedViewStateEnabled, viewStateEnabled)
        }
    }

}
