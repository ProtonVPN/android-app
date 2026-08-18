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

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import com.protonvpn.android.R
import me.proton.android.payment.common.exception.PaymentException
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import me.proton.core.network.presentation.util.getUserMessage
import me.proton.core.payment.domain.repository.BillingClientError
import me.proton.core.payment.presentation.R as PaymentR

/**
 * Code in this file is independent of Material3 and Material3 TV and thus can be used in both.
 */

@Composable
fun renewInfoText(
    selectedCycleInfo: UpgradeDialogViewModel.CycleViewInfo,
): String? {
    val priceInfo = selectedCycleInfo.priceInfo
    val initialPrice = priceInfo.formattedPrice
    val renewPrice = priceInfo.formattedRenewPrice

    val cycle = selectedCycleInfo.cycle.paymentCycle
    return when (val discountRecurrence = selectedCycleInfo.cycle.recurrence) {
        is PaymentRecurrence.Finite if discountRecurrence.count == 1 -> {
            val messageRes = when (cycle) {
                is PaymentCycle.Day -> R.plurals.payment_discount_renewal_price_message_daily
                is PaymentCycle.Week -> R.plurals.payment_discount_renewal_price_message_weekly
                is PaymentCycle.Month -> R.plurals.payment_discount_renewal_price_message_monthly
                is PaymentCycle.Year -> R.plurals.payment_discount_renewal_price_message_yearly
            }
            pluralStringResource(messageRes, cycle.count, renewPrice, cycle.count)
        }
        is PaymentRecurrence.Infinite -> {
            val messageRes = when (cycle) {
                is PaymentCycle.Day -> R.plurals.payment_renewal_price_message_daily
                is PaymentCycle.Week -> R.plurals.payment_renewal_price_message_weekly
                is PaymentCycle.Month -> R.plurals.payment_renewal_price_message_monthly
                is PaymentCycle.Year -> R.plurals.payment_renewal_price_message_yearly
            }
            pluralStringResource(messageRes, cycle.count, renewPrice, cycle.count)
        }
        is PaymentRecurrence.Finite -> {
            val recurrence = discountRecurrence.count
            val discountPriceRes = when (cycle) {
                is PaymentCycle.Day -> R.plurals.payment_multi_renewal_discount_daily
                is PaymentCycle.Week -> R.plurals.payment_multi_renewal_discount_weekly
                is PaymentCycle.Month -> R.plurals.payment_multi_renewal_discount_monthly
                is PaymentCycle.Year -> R.plurals.payment_multi_renewal_discount_yearly
            }
            val discountForRes = when (cycle) {
                is PaymentCycle.Day -> R.plurals.payment_multi_renewal_discount_for_days
                is PaymentCycle.Week -> R.plurals.payment_multi_renewal_discount_for_weeks
                is PaymentCycle.Month -> R.plurals.payment_multi_renewal_discount_for_months
                is PaymentCycle.Year -> R.plurals.payment_multi_renewal_discount_for_years
            }
            val secondPartRes = when (cycle) {
                is PaymentCycle.Day -> R.plurals.payment_multi_renewal_rest_daily
                is PaymentCycle.Week -> R.plurals.payment_multi_renewal_rest_weekly
                is PaymentCycle.Month -> R.plurals.payment_multi_renewal_rest_monthly
                is PaymentCycle.Year -> R.plurals.payment_multi_renewal_rest_yearly
            }
            val forFirstCycleCount = cycle.count * recurrence
            stringResource(
                R.string.payment_multi_renewal_template,
                pluralStringResource(discountPriceRes, cycle.count, initialPrice, cycle.count),
                pluralStringResource(discountForRes,  forFirstCycleCount,  forFirstCycleCount),
                pluralStringResource(secondPartRes, cycle.count, renewPrice, cycle.count)
            )
        }
    }
}

@Composable
fun PricingCycleInfo(
    formattedPrice: String,
    formattedPerMonthPrice: String?,
    modifier: Modifier = Modifier,
    text: @Composable (AnnotatedString, TextStyle) -> Unit,
) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        text(
            AnnotatedString(formattedPrice),
            ProtonTheme.typography.body1Bold,
        )
        if (formattedPerMonthPrice != null) {
            val perMonth = stringResource(R.string.payment_price_per_month)
            text(
                AnnotatedString(stringResource(id = R.string.payment_price_with_period, formattedPerMonthPrice, perMonth)),
                ProtonTheme.typography.captionWeak
            )
        }
    }
}

@Composable
fun WithMinHeightOf(
    minHeightContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier) {
        content()
        Box(
            modifier = Modifier
                .alpha(0f)
                .semantics { hideFromAccessibility() }
        ) {
            minHeightContent()
        }
    }
}


@Composable
fun cycleLabelStringResource(cycle: PaymentCycle): String {
    val pluralsId = when (cycle) {
        is PaymentCycle.Day -> R.plurals.payment_price_cycle_label_day
        is PaymentCycle.Week -> R.plurals.payment_price_cycle_label_week
        is PaymentCycle.Month -> R.plurals.payment_price_cycle_label_month
        is PaymentCycle.Year -> R.plurals.payment_price_cycle_label_year
    }
    return pluralStringResource(pluralsId, cycle.count, cycle.count)
}

fun UpgradeDialogViewModel.Error.getPaymentErrorString(context: Context): String =
    messageRes?.let { context.getString(messageRes) }
        ?: when (throwable) {
            is BillingClientError -> null
            is PaymentException -> throwable.message
            else -> throwable?.getUserMessage(context.resources)
        }
        ?: context.getString(PaymentR.string.payments_general_error)
