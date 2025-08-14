/*
 *
 *  * Copyright (c) 2023. Proton AG
 *  *
 *  * This file is part of ProtonVPN.
 *  *
 *  * ProtonVPN is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * ProtonVPN is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.protonvpn.android.release_tests.data

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object TestConstants {
    const val USERNAME = "automationPlusUser"
    const val TEST_PACKAGE = "ch.protonvpn.android.dev"
    val TWENTY_SECOND_TIMEOUT = 20000.milliseconds
    val TWO_MINUTES_TIMEOUT = 2.minutes

    val FIVE_SECONDS_TIMEOUT_MS = 5.seconds.inWholeMilliseconds
    val TWENTY_SECOND_TIMEOUT_MS = TWENTY_SECOND_TIMEOUT.inWholeMilliseconds
}
