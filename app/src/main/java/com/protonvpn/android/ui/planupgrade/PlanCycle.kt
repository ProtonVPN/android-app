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

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import me.proton.android.payment.product.model.BillingCycle
import me.proton.android.payment.product.model.BillingRecurrence

@Serializable
data class PlanCycle(
    val paymentCycle: PaymentCycle,
    val recurrence: PaymentRecurrence,
)

@Serializable
sealed interface PaymentRecurrence {
    @Serializable
    object Infinite : PaymentRecurrence
    @Serializable
    data class Finite(val count: Int) : PaymentRecurrence
}


@Serializable
sealed interface PaymentCycle {
    val count: Int

    @Serializable
    data class Day(override val count: Int) : PaymentCycle
    @Serializable
    data class Week(override val count: Int) : PaymentCycle
    @Serializable
    data class Month(override val count: Int) : PaymentCycle
    @Serializable
    data class Year(override val count: Int) : PaymentCycle
}

// Used for comparisons, e.g. Week.unitOrder < Year.unitOrder.
// Don't rely on nor store the actual values.
fun PaymentCycle.unitOrder() = when(this) {
    is PaymentCycle.Day -> 1
    is PaymentCycle.Week -> 2
    is PaymentCycle.Month -> 3
    is PaymentCycle.Year -> 4
}

fun PaymentCycle.toISO8601() = when (this) {
    is PaymentCycle.Day -> "P${count}D"
    is PaymentCycle.Week -> "P${count}W"
    is PaymentCycle.Month -> "P${count}M"
    is PaymentCycle.Year -> "P${count}Y"
}

fun BillingCycle.toPaymentCycle(): PaymentCycle = when(this) {
    is BillingCycle.Day -> PaymentCycle.Day(count)
    is BillingCycle.Week -> PaymentCycle.Week(count)
    is BillingCycle.Month -> PaymentCycle.Month(count)
    is BillingCycle.Year -> PaymentCycle.Year(count)
}

// Very simple parsing with limited validation.
// No regex is used as this happens early in app start.
fun String.parsePaymentCycle(): PaymentCycle {
    val lowercased = lowercase()
    if (!(length >= 3 && lowercased.first() == 'p' && lowercased[1].isDigit()))
        throw SerializationException("Invalid PaymentCycle '$this'")

    val endNumberIndex = lowercased.substring(1).indexOfFirst { !it.isDigit() }
    if (endNumberIndex < 1)
        throw SerializationException("Invalid PaymentCycle '$this'")
    val count = lowercased.substring(1, endNumberIndex + 1).toInt()
    return when (val lastChar = lowercased.last()) {
        'd' -> PaymentCycle.Day(count)
        'w' -> PaymentCycle.Week(count)
        'm' -> PaymentCycle.Month(count)
        'y' -> PaymentCycle.Year(count)
        else -> throw SerializationException("Unknown period '$lastChar' in '$this'")
    }
}

fun BillingRecurrence.toPaymentRecurrence() = when (this) {
    is BillingRecurrence.Finite -> PaymentRecurrence.Finite(count)
    BillingRecurrence.Infinite -> PaymentRecurrence.Infinite
}
