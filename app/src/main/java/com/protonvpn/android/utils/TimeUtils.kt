/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.utils

import org.joda.time.Period
import org.joda.time.format.PeriodFormatterBuilder

object TimeUtils {

    private val periodFormatter = PeriodFormatterBuilder()
            .minimumPrintedDigits(1)
            .appendDays()
            .appendSuffix("d")
            .minimumPrintedDigits(1)
            .appendSeparator(" ")
            .appendHours()
            .appendSuffix("h")
            .minimumPrintedDigits(1)
            .appendSeparator(" ")
            .appendMinutes()
            .appendSuffix("m")
            .minimumPrintedDigits(1)
            .appendSeparator(" ")
            .appendSeconds()
            .appendSuffix("s")
            .minimumPrintedDigits(1)
            .toFormatter()

    @JvmStatic
    fun getFormattedTimeFromSeconds(seconds: Int): String =
            periodFormatter.print(Period.seconds(seconds).normalizedStandard())
}
