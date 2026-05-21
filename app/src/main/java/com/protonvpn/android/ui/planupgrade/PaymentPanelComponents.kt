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
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionWeak
import me.proton.core.compose.theme.defaultStrongNorm
import me.proton.core.network.presentation.util.getUserMessage
import me.proton.core.payment.domain.repository.BillingClientError
import me.proton.core.plan.presentation.entity.PlanCycle
import me.proton.core.payment.presentation.R as PaymentR

/**
 * Code in this file is independent of Material3 and Material3 TV and thus can be used in both.
 */

@Composable
fun renewInfoText(
    selectedCycleInfo: CommonUpgradeDialogViewModel.CycleViewInfo,
): String? {
    val priceInfo = selectedCycleInfo.priceInfo
    val price = priceInfo.formattedPrice
    val renewPrice = priceInfo.formattedRenewPrice
    return when (selectedCycleInfo.cycle) {
        PlanCycle.MONTHLY -> when {
            priceInfo.hasIntroPrice ->
                stringResource(R.string.payment_welcome_price_message_monthly, renewPrice)

            else ->
                stringResource(R.string.payment_auto_renew_message_monthly, price)
        }

        PlanCycle.YEARLY -> when {
            priceInfo.hasIntroPrice ->
                stringResource(R.string.payment_welcome_price_message_annual, renewPrice)

            else ->
                stringResource(R.string.payment_auto_renew_message_annual, price)
        }

        else -> null
    }
}

@Composable
fun PricingCycleInfo(
    formattedPrice: String,
    @StringRes perCycleResId: Int?,
    formattedPerMonthPrice: String?,
    modifier: Modifier = Modifier,
    text: @Composable (AnnotatedString, TextStyle) -> Unit,
) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        text(
            getPriceAndCycleString(formattedPrice, perCycleResId),
            ProtonTheme.typography.captionWeak
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
    content: @Composable () -> Unit
) {
    Box(modifier) {
        Box(
            modifier = Modifier
                .alpha(0f)
                .semantics { hideFromAccessibility() }
        ) {
            minHeightContent()
        }
        content()
    }
}

@Composable
private fun getPriceAndCycleString(formattedPrice: String, @StringRes cycleResId: Int?): AnnotatedString {
    val text = if (cycleResId != null) {
        stringResource(id = R.string.payment_price_with_period, formattedPrice, stringResource(cycleResId))
    } else {
        formattedPrice
    }
    val priceIndex = text.indexOf(formattedPrice)
    return AnnotatedString.Builder(text).apply {
        addStyle(
            ProtonTheme.typography.defaultStrongNorm.toSpanStyle(),
            priceIndex,
            priceIndex + formattedPrice.length
        )
    }.toAnnotatedString()
}

fun CommonUpgradeDialogViewModel.Error.getPaymentErrorString(context: Context): String =
    message
        ?: messageRes?.let { context.getString(messageRes) }
        ?: when (throwable) {
            is BillingClientError -> null
            else -> throwable?.getUserMessage(context.resources)
        }
        ?: context.getString(PaymentR.string.payments_general_error)