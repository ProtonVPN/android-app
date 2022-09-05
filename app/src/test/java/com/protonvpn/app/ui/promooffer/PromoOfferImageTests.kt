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

package com.protonvpn.app.ui.promooffer

import com.protonvpn.android.appconfig.ApiNotificationOfferFullScreenImage
import com.protonvpn.android.ui.promooffers.PromoOfferImage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PromoOfferImageTests {

    @Test
    fun `take smallest image wider than requested width`() {
        val fullScreenImage = Json.decodeFromString<ApiNotificationOfferFullScreenImage>("""
            {
                "Source": [
                  { "Width": 200, "URL": "200", "Type": "PNG" },
                  { "Width": 300, "URL": "300", "Type": "PNG" },
                  { "Width": 100, "URL": "100", "Type": "PNG" }
                ]
            }
        """.trimIndent())
        val selectedImage = PromoOfferImage.getFullScreenImageUrl(150, fullScreenImage)
        assertEquals("200", selectedImage)
    }

    @Test
    fun `when no image is wide enough take the widest`() {
        val fullScreenImage = Json.decodeFromString<ApiNotificationOfferFullScreenImage>("""
            {
                "Source": [
                  { "Width": 100, "URL": "100", "Type": "PNG" },
                  { "Width": 200, "URL": "200", "Type": "PNG" }
                ]
            }
        """.trimIndent())
        val selectedImage = PromoOfferImage.getFullScreenImageUrl(300, fullScreenImage)
        assertEquals("200", selectedImage)
    }

    @Test
    fun `when multiple types are available only the first type is considered`() {
        val fullScreenImage = Json.decodeFromString<ApiNotificationOfferFullScreenImage>("""
            {
                "Source": [
                  { "Width": 100, "URL": "100.png", "Type": "PNG" },
                  { "Width": 100, "URL": "100.webp", "Type": "WEBP" },
                  { "Width": 200, "URL": "200.png", "Type": "PNG" },
                  { "Width": 300, "URL": "300.webp", "Type": "WEBP" }
                ]
            }
        """.trimIndent())
        val selectedImage = PromoOfferImage.getFullScreenImageUrl(300, fullScreenImage)
        assertEquals("200.png", selectedImage)
    }

    @Test
    fun `image width is optional`() {
        val fullScreenImage = Json.decodeFromString<ApiNotificationOfferFullScreenImage>("""
            {
                "Source": [
                  { "URL": "image.lottie", "Type": "LOTTIE" },
                  { "Width": 300, "URL": "300.png", "Type": "png" }
                ]
            }
        """.trimIndent())
        val selectedImage = PromoOfferImage.getFullScreenImageUrl(300, fullScreenImage)
        assertEquals("image.lottie", selectedImage)
    }
}
