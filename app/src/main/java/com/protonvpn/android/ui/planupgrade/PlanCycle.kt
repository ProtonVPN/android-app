/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.ui.planupgrade

private const val MONTHS_YEAR = 12
private const val MONTHS_TWO_YEARS = 24
private const val MONTHS_UNKNOWN = Int.MIN_VALUE

enum class PlanCycle(private val value: Int) {
    MONTHLY(1), YEARLY(MONTHS_YEAR), TWO_YEARS(MONTHS_TWO_YEARS), OTHER(MONTHS_UNKNOWN);

    val cycleDurationMonths: Int = value
}
