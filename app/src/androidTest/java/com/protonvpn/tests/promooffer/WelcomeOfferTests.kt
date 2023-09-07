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

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.protonvpn.android.ui.home.countries.PromoOfferBannerItem
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WelcomeOfferTests {

    private lateinit var appContext: Context

    @Before
    fun setup() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test
    fun timeLeftText() {
        assertEquals(
            "2 days 1 hour left",
            PromoOfferBannerItem.timeLeftText(appContext.resources, 2.days + 1.hours)
        )
        assertEquals(
            "2 days 1 hour left",
            PromoOfferBannerItem.timeLeftText(appContext.resources, 2.days + 1.hours + 10.minutes)
        )
        assertEquals(
            "3 hours 5 minutes left",
            PromoOfferBannerItem.timeLeftText(appContext.resources, 3.hours + 5.minutes + 59.seconds)
        )
        assertEquals(
            "1 hour 3 minutes left",
            PromoOfferBannerItem.timeLeftText(appContext.resources, 1.hours + 3.minutes)
        )
        assertEquals(
            "3 minutes 5 seconds left",
            PromoOfferBannerItem.timeLeftText(appContext.resources, 3.minutes + 5.seconds)
        )
        assertEquals(
            "0 minutes 59 seconds left",
            PromoOfferBannerItem.timeLeftText(appContext.resources, 59.seconds)
        )
        assertEquals(
            "0 minutes 0 seconds left",
            PromoOfferBannerItem.timeLeftText(appContext.resources, (-5).seconds)
        )
    }
}
