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

package com.protonvpn.android.ui.planupgrade.usecase

import me.proton.android.payment.common.exception.PaymentException
import me.proton.android.payment.common.exception.PaymentExceptionCode

fun shouldReportToSentry(throwable: Throwable?): Boolean = when(throwable) {
    null -> false
    is PaymentException -> {
        val ignored = arrayOf(
            PaymentExceptionCode.CORE_DISABLED_VENDOR,
            PaymentExceptionCode.CORE_LOGGING,
            PaymentExceptionCode.CORE_UNSUPPORTED_PLATFORM,

            PaymentExceptionCode.NETWORK_REQUEST_TIMEOUT,
            PaymentExceptionCode.NETWORK_TOO_MANY_REQUESTS,
            PaymentExceptionCode.NETWORK_UNREACHABLE,

            PaymentExceptionCode.STORE_CANCELED,
            PaymentExceptionCode.STORE_FETCH_PRODUCTS,
            PaymentExceptionCode.STORE_UNAVAILABLE,

        )
        throwable.code !in ignored
    }

    else -> true
}
