/*
 * Copyright (c) 2021. Proton Technologies AG
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

package com.protonvpn.android.ui.promooffers

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.core.app.ComponentActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.protonvpn.android.R

class PromoOfferNotificationHelper(
    private val activity: ComponentActivity,
    private val imageNotification: ImageView,
    private val viewModel: PromoOfferNotificationViewModel
) {
    init {
        viewModel.offerNotification.asLiveData().observe(
            activity,
            Observer { notification -> this.updateOfferNotification(notification) }
        )
        viewModel.eventOpenPromoOffer.asLiveData().observe(
            activity, Observer { offerId -> this.openOfferActivity(offerId) }
        )
    }

    private fun updateOfferNotification(notification: PromoOfferNotificationViewModel.Notification?) {
        imageNotification.visibility = if (notification != null) View.VISIBLE else View.GONE
        if (notification != null) {
            // Clear the target to force Glide to reevaluate the request even if it is for the same URL,
            // especially when the "visited" state has changed.
            Glide.with(activity).clear(imageNotification)
            Glide.with(activity)
                .asDrawable()
                .load(notification.iconUrl)
                .error(R.drawable.ic_proton_gift)
                .into(object : DrawableImageViewTarget(imageNotification) {
                    override fun setDrawable(drawable: Drawable?) {
                        // setDrawable is called to set the error drawable.
                        super.setDrawable(getNotificationDrawable(drawable, notification.visited))
                    }

                    override fun setResource(drawable: Drawable?) {
                        // setResource is called to set the downloaded image.
                        super.setResource(getNotificationDrawable(drawable, notification.visited))
                    }
                })
            imageNotification.setOnClickListener { _ -> viewModel.onOpenOffer(notification) }
        }
    }

    private fun getNotificationDrawable(icon: Drawable?, isVisited: Boolean): Drawable? =
        if (isVisited || icon == null) icon else NotificationDotDrawableWrapper(activity, icon)


    private fun openOfferActivity(offer: PromoOfferNotificationViewModel.Notification) {
        if (offer.pictureUrlForPreload != null) {
            // The picture should already be on disk, start loading it to memory ASAP.
            PromoOfferActivity.preloadPicture(activity, offer.pictureUrlForPreload)
        }
        activity.startActivity(PromoOfferActivity.createIntent(activity, offer.id))
    }
}
