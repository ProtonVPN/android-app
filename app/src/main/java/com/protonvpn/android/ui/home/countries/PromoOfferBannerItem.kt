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

package com.protonvpn.android.ui.home.countries

import android.content.res.Resources
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.protonvpn.android.R
import com.protonvpn.android.databinding.ItemPromoOfferBannerBinding
import com.protonvpn.android.utils.BindableItemEx
import com.protonvpn.android.utils.tickFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PromoOfferBannerItem(
    private val imageUrl: String,
    private val alternativeText: String,
    private val endTimestamp: Long?,
    private val action: suspend () -> Unit,
    private val parentLifecycleOwner: LifecycleOwner
) : BindableItemEx<ItemPromoOfferBannerBinding>() {

    private var tickerJob: Job? = null

    override fun bind(viewBinding: ItemPromoOfferBannerBinding, position: Int) {
        super.bind(viewBinding, position)
        with(viewBinding) {
            Glide.with(imageBanner)
                .load(imageUrl)
                .into(imageBanner)
            imageBanner.contentDescription = alternativeText

            textTimeLeft.isVisible = endTimestamp != null
            if (endTimestamp != null) {
                // Cancelled in clear()
                tickerJob = tickFlow(1.seconds, System::currentTimeMillis)
                    .flowWithLifecycle(parentLifecycleOwner.lifecycle)
                    .map { now -> timeLeftText(viewBinding.root.resources, (endTimestamp - now).milliseconds) }
                    // Don't update the UI if not needed to avoid accessibility service reading the same value again.
                    .distinctUntilChanged()
                    .onEach { text -> textTimeLeft.text = text }
                    .launchIn(parentLifecycleOwner.lifecycleScope)
            }

            root.setOnClickListener(suspendingClickAction(action))
            root.alpha = 1f
            root.isEnabled = true
        }
    }

    override fun clear() {
        Glide.with(binding.imageBanner).clear(binding.imageBanner)
        tickerJob?.cancel()
    }

    private fun suspendingClickAction(action: suspend () -> Unit) = View.OnClickListener { clickedView ->
        // Performing the action may take a moment, especially if autologin needs to make a request.
        // Disable the button until the action is finished.
        clickedView.isEnabled = false
        clickedView.animate().setStartDelay(300).alpha(0.5f)
        parentLifecycleOwner.lifecycleScope.launch {
            try {
                action()
            } finally {
                clickedView.animate().alpha(1f)
                clickedView.isEnabled = true
            }
        }
    }

    override fun getLayout(): Int = R.layout.item_promo_offer_banner

    override fun initializeViewBinding(v: View) = ItemPromoOfferBannerBinding.bind(v)

    companion object {
        @VisibleForTesting
        fun timeLeftText(res: Resources, rawTimeLeft: Duration): String {
            val timeLeft = rawTimeLeft.coerceAtLeast(0.milliseconds)
            val days = timeLeft.inWholeDays.toInt()
            val hours = (timeLeft - days.days).inWholeHours.toInt()
            val minutes = (timeLeft - days.days - hours.hours).inWholeMinutes.toInt()
            val seconds = (timeLeft - days.days - hours.hours - minutes.minutes).inWholeSeconds.toInt()
            val (largeUnits, smallUnits) = when {
                days > 0 -> Pair(
                    res.getQuantityString(R.plurals.time_left_days, days, days),
                    res.getQuantityString(R.plurals.time_left_hours, hours, hours)
                )
                hours > 0 -> Pair(
                    res.getQuantityString(R.plurals.time_left_hours, hours, hours),
                    res.getQuantityString(R.plurals.time_left_minutes, minutes, minutes)
                )
                else -> Pair(
                    res.getQuantityString(R.plurals.time_left_minutes, minutes, minutes),
                    res.getQuantityString(R.plurals.time_left_seconds, seconds, seconds)
                )
            }
            return res.getString(R.string.offer_time_left, largeUnits, smallUnits)
        }
    }
}
