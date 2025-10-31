/*
 * Copyright (c) 2024. Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.app.update

import com.protonvpn.android.appconfig.AppFeaturesPrefs
import com.protonvpn.android.update.AppUpdateInfo
import com.protonvpn.android.update.AppUpdateManager
import com.protonvpn.android.update.IsUpdatePromptForStaleVersionEnabled
import com.protonvpn.android.update.UpdatePromptForStaleVersion
import com.protonvpn.test.shared.MockSharedPreferencesProvider
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private const val UPDATE_STALENESS_THRESHOLD = 45

class PromptUpdateForStaleVersionTests {

    @MockK
    private lateinit var mockAppUpdateManager: AppUpdateManager
    @MockK
    private lateinit var mockIsFeatureEnabled: IsUpdatePromptForStaleVersionEnabled

    private var clockTime: Duration = Duration.ZERO
    private lateinit var prefs: AppFeaturesPrefs

    private lateinit var updatePrompt: UpdatePromptForStaleVersion

    @Before
    fun setup() {
        MockKAnnotations.init(this)

        coEvery { mockIsFeatureEnabled.invoke() } returns true
        coEvery { mockAppUpdateManager.launchUpdateFlow(any(), any()) } just Runs

        clockTime = 1.minutes
        prefs = AppFeaturesPrefs(MockSharedPreferencesProvider())
        updatePrompt = UpdatePromptForStaleVersion(
            mockAppUpdateManager,
            prefs,
            mockIsFeatureEnabled,
            { clockTime.inWholeMilliseconds }
        )
    }

    @Test
    fun `when there is no update then there is no prompt`() = runTest {
        coEvery { mockAppUpdateManager.checkForUpdate() } returns null

        assertNull(updatePrompt.getUpdatePrompt())
    }

    @Test
    fun `when there is an update but feature is disabled there is no prompt`() = runTest {
        coEvery { mockIsFeatureEnabled.invoke() } returns false
        coEvery { mockAppUpdateManager.checkForUpdate() } returns AppUpdateInfo(UPDATE_STALENESS_THRESHOLD, 0)

        assertNull(updatePrompt.getUpdatePrompt())
    }

    @Test
    fun `when there is a fresh update then there is no prompt`() = runTest {
        coEvery { mockAppUpdateManager.checkForUpdate() } returns AppUpdateInfo(1, 0)

        assertNull(updatePrompt.getUpdatePrompt())
    }

    @Test
    fun `when there is a stale update then there is a prompt`() = runTest {
        coEvery { mockAppUpdateManager.checkForUpdate() } returns AppUpdateInfo(UPDATE_STALENESS_THRESHOLD, 0)

        assertNotNull(updatePrompt.getUpdatePrompt())
    }

    @Test
    fun `when an update is launched the prompts are shown according to schedule`() = runTest {
        mockStaleUpdateWithClock(UPDATE_STALENESS_THRESHOLD)
        assertNotNull(updatePrompt.getUpdatePrompt())
        updatePrompt.launchUpdateFlow(mockk(), mockk())
        assertNull(updatePrompt.getUpdatePrompt())

        val scheduledIntervals = listOf(21, 13, 8, 5, 3, 2, 2, 2)
        scheduledIntervals.forEachIndexed { index, interval ->
            clockTime += interval.days - 1.minutes
            assertNull("no prompt just before interval $index: $interval", updatePrompt.getUpdatePrompt())

            clockTime += 1.minutes
            assertNotNull("prompt on before interval $index: $interval", updatePrompt.getUpdatePrompt())
            updatePrompt.launchUpdateFlow(mockk(), mockk())
        }
    }

    @Test
    fun `when an update is launched the prompts are shown according to schedule with respect to previous prompt`() = runTest {
        mockStaleUpdateWithClock(UPDATE_STALENESS_THRESHOLD)
        updatePrompt.launchUpdateFlow(mockk(), mockk())

        val scheduledIntervals = listOf(21, 13, 8, 5, 3, 2, 2, 2)
        scheduledIntervals.forEachIndexed { index, interval ->
            clockTime += interval.days - 1.minutes
            assertNull("no prompt just before interval $index: $interval", updatePrompt.getUpdatePrompt())

            // Longer delay:
            clockTime += 10.days
            assertNotNull("prompt on before interval $index: $interval", updatePrompt.getUpdatePrompt())
            updatePrompt.launchUpdateFlow(mockk(), mockk())
        }
    }

    @Test
    fun `after update prompt intervals start from the first`() = runTest {
        mockStaleUpdateWithClock(UPDATE_STALENESS_THRESHOLD)
        updatePrompt.launchUpdateFlow(mockk(), mockk())
        clockTime += 1.minutes

        mockStaleUpdateWithClock(0)
        clockTime += UPDATE_STALENESS_THRESHOLD.days
        assertNotNull(updatePrompt.getUpdatePrompt())

        updatePrompt.launchUpdateFlow(mockk(), mockk())

        clockTime += 20.days
        assertNull(updatePrompt.getUpdatePrompt())

        clockTime += 1.days
        assertNotNull(updatePrompt.getUpdatePrompt())
    }

    @Test
    fun `when update is pending and time moves backwards things don't explode`() = runTest {
        clockTime += 100.days
        mockStaleUpdateWithClock(UPDATE_STALENESS_THRESHOLD)
        updatePrompt.launchUpdateFlow(mockk(), mockk())

        clockTime -= 20.days

        assertNull(updatePrompt.getUpdatePrompt())
        clockTime -= UPDATE_STALENESS_THRESHOLD.days

        assertNull(updatePrompt.getUpdatePrompt())

        clockTime += 200.days
        assertNotNull(updatePrompt.getUpdatePrompt())
    }

    private fun mockStaleUpdateWithClock(stalenessDays: Int) {
        val startTime = clockTime
        coEvery { mockAppUpdateManager.checkForUpdate() } answers {
            val elapsedDays = (clockTime - startTime).inWholeDays
            AppUpdateInfo(stalenessDays + elapsedDays.toInt(), 0)
        }
    }
}
