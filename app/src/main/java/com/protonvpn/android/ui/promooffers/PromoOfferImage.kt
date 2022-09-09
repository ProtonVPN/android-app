/*
 * Copyright (c) 2022. Proton AG
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

import android.content.Context
import android.content.res.Configuration
import com.bumptech.glide.Glide
import com.protonvpn.android.R
import com.protonvpn.android.appconfig.ApiNotificationOfferFullScreenImage
import com.protonvpn.android.utils.AndroidUtils.isChromeOS

object PromoOfferImage {

    // Only PNG and LOTTIE are mentioned in the spec but since Glide supports also other formats let's have them too.
    enum class SupportedFormats {
        PNG, WEBP, JPEG, LOTTIE, GIF
    }

    fun preloadPicture(context: Context, pictureUrl: String) {
        val maxSize = getPictureMaxSize(context)
        // Use the same dimensions for preload as for displaying the image.
        Glide.with(context).load(pictureUrl).preload(maxSize.width, maxSize.height)
    }

    fun getPictureMaxSize(context: Context) = with(context.resources) {
        Size(
            displayMetrics.widthPixels - 2 * getDimensionPixelSize(R.dimen.offer_panel_picture_horizontal_margin),
            getDimensionPixelSize(R.dimen.offer_panel_picture_max_height)
        )
    }

    fun getFullScreenImageUrl(
        context: Context, fullScreenImage: ApiNotificationOfferFullScreenImage
    ): String? = getFullScreenImageUrl(getFullScreenImageMaxSizePx(context).width, fullScreenImage)

    fun getFullScreenImageUrl(
        pixelWidth: Int, fullScreenImage: ApiNotificationOfferFullScreenImage
    ): String? {
        val supportedFormats = SupportedFormats.values().map { it.toString() }
        val firstSupported = fullScreenImage.source
            .firstOrNull { it.type.uppercase() in supportedFormats }
        val imageSpec = if (firstSupported?.width != null) {
            val sortedByWidth = fullScreenImage.source
                .filter { it.type.uppercase() == firstSupported.type.uppercase() }
                .sortedBy { it.width }
            sortedByWidth
                .firstOrNull { it.width == null || it.width >= pixelWidth }
                ?: sortedByWidth.lastOrNull()
        } else {
            firstSupported
        }
        return imageSpec?.url
    }

    fun getFullScreenImageMaxSizePx(context: Context) = with(context.resources) {
        val portraitScreenSizePx =
            if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT || context.isChromeOS()) {
                Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
            } else {
                Size(displayMetrics.heightPixels, displayMetrics.widthPixels)
            }
        Size(
            minOf(
                portraitScreenSizePx.width,
                getDimensionPixelSize(R.dimen.offer_panel_fullscreen_image_max_width)
            ),
            portraitScreenSizePx.height
        )
    }

    // Avoid android.util.Size for the sake of unit tests.
    data class Size(val width: Int, val height: Int)
}
