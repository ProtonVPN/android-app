/*
 * Copyright (c) 2023. Proton AG
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

package com.protonvpn.tests.promooffer

import com.protonvpn.android.ui.promooffers.CountDownState
import me.proton.test.fusion.ui.compose.FusionComposeTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(Parameterized::class)
class PromoOfferCountDownTests(
    private val durationMs: Long, // Can't use Duration because it's a value class.
    private val expectedText: String
) : FusionComposeTest() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: {1}")
        fun data() = listOf(
            // Params: duration, expected label text
            2.days + 1.hours to "2 days 1 hour left",
            2.days + 1.hours + 10.minutes to "2 days 1 hour left",
            3.hours + 5.minutes + 59.seconds to "3 hours 5 minutes left",
            1.hours + 3.minutes to "1 hour 3 minutes left",
            3.minutes + 5.seconds to "3 minutes 5 seconds left",
            59.seconds to "0 minutes 59 seconds left",
            (-5).seconds to "0 minutes 0 seconds left",
        ).map { (duration, expected) ->
            arrayOf(duration.inWholeMilliseconds, expected)
        }
    }

    @Test
    fun timeLeftText() {
        var text: String? = null
        composeRule.setContent {
            val countDownState = CountDownState(durationMs, { 0 })
            text = countDownState.timeLeftText
        }
        assertEquals(expectedText, text)
    }
}
