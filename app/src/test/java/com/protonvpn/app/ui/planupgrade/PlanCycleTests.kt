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

package com.protonvpn.app.ui.planupgrade

import com.protonvpn.android.ui.planupgrade.PaymentCycle
import com.protonvpn.android.ui.planupgrade.parsePaymentCycle
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanCycleTests {

    @Test
    fun `parse_payment_cycle`() {
        val cases = listOf(
            "p1M" to PaymentCycle.Month(1),
            "P2W" to PaymentCycle.Week(2),
            "P14d" to PaymentCycle.Day(14),
            "p1y" to PaymentCycle.Year(1),
        )
        cases.forEach { (iso, cycle) ->
            assertEquals(cycle, iso.parsePaymentCycle())
        }
    }
}
