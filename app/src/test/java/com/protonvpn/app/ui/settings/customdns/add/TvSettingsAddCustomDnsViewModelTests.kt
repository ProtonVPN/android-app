package com.protonvpn.app.ui.settings.customdns.add

import app.cash.turbine.test
import com.protonvpn.android.redesign.settings.ui.customdns.AddDnsError
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettingsStoreProvider
import com.protonvpn.android.tv.settings.customdns.add.TvSettingsAddCustomDnsViewModel
import com.protonvpn.test.shared.InMemoryDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.receiveAsFlow
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
class TvSettingsAddCustomDnsViewModelTests {

    private lateinit var testScope: TestScope

    private lateinit var userSettingsManager: CurrentUserLocalSettingsManager

    private lateinit var viewModel: TvSettingsAddCustomDnsViewModel

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

        viewModel = TvSettingsAddCustomDnsViewModel(
            mainScope = testScope,
            userSettingsManager = userSettingsManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `WHEN observing view state THEN initial view state is emitted`() = testScope.runTest {
        val expectedViewState = TvSettingsAddCustomDnsViewModel.ViewState(error = null)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN empty custom DNS WHEN observing view state THEN view state error is EmptyInput`() = testScope.runTest {
        val customDns = ""
        val expectedViewState = TvSettingsAddCustomDnsViewModel.ViewState(error = AddDnsError.EmptyInput)

        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN invalid custom DNS WHEN observing view state THEN view state error is InvalidInput`() = testScope.runTest {
        val customDns = "invalid DNS"
        val expectedViewState = TvSettingsAddCustomDnsViewModel.ViewState(error = AddDnsError.InvalidInput)

        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN custom DNS already exists WHEN observing view state THEN view state error is DuplicateInput`() = testScope.runTest {
        val customDns = "1.1.1.1"
        val expectedViewState = TvSettingsAddCustomDnsViewModel.ViewState(error = AddDnsError.DuplicateInput)
        userSettingsManager.updateCustomDnsList(newDnsList = listOf(customDns))

        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN custom DNS changes WHEN observing view state THEN view state error is reset`() = testScope.runTest {
        val customDns = ""
        val expectedViewState = TvSettingsAddCustomDnsViewModel.ViewState(error = null)
        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.onCustomDnsChanged()

        viewModel.viewStateFlow.test {
            val viewState = awaitItem()

            assertEquals(expectedViewState, viewState)
        }
    }

    @Test
    fun `GIVEN valid custom DNS WHEN adding the custom DNS THEN custom DNS is added`() = testScope.runTest {
        val customDns = "2001:db8:3333:4444:5555:6666:7777:8888"
        val expectedCustomDnsList = listOf(customDns)
        viewModel.onAddCustomDns(newCustomDns = customDns)

        userSettingsManager.rawCurrentUserSettingsFlow.test {
            val customDnsList = awaitItem().customDns.rawDnsList

            assertEquals(expectedCustomDnsList, customDnsList)
        }
    }

    @Test
    fun `GIVEN valid custom DNS WHEN adding the custom DNS THEN notifies custom DNS is added`() = testScope.runTest {
        val customDns = "16.0.254.1"
        val expectedEvent = TvSettingsAddCustomDnsViewModel.Event.OnCustomDnsAdded
        viewModel.onAddCustomDns(newCustomDns = customDns)

        viewModel.eventChannelReceiver.receiveAsFlow().test {
            val event = awaitItem()

            assertEquals(expectedEvent, event)
        }
    }

}
