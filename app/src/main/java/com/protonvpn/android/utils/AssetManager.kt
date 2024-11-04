/*
 * Copyright (c) 2022 Proton AG
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

package com.protonvpn.android.utils

import com.protonvpn.android.BuildConfig
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Wrapper for android AssetManager supporting overriding image assets
// for tests. See VPNAND-1114 for details.
object AssetManager {
    private val manager = ProtonApplication.getAppContext().assets

    fun openFile(path: String): InputStream =
        try {
            decodeImageAsset(path) ?: manager.open(path)
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.APP, "AssetManager error: ${e.message}")
            manager.open(path)
        }

    private fun decodeImageAsset(path: String): InputStream? {
        val digest = MessageDigest.getInstance("SHA-256")
        val pathDigest = digest.digest(path.toByteArray()).toHexString()
        if (!pathDigest.contains(BuildConfig.TEST_ASSET_OVERRIDE_SHA))
            return null

        manager.open("test_image_asset.irx").use { input ->
            val bytes = input.readBytes()
            // Use encryption to have support for images for future promotions
            // nosemgrep: kotlin.lang.security.gcm-detection.gcm-detection
            val decoder = Cipher.getInstance("AES/GCM/NoPadding")
            decoder.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(
                    BuildConfig.TEST_SUITE_ASSET_OVERRIDE_KEY.hexToByteArray()
                        .zip(BuildConfig.TEST_ASSET_OVERRIDE_KEY.hexToByteArray())
                        .flatMap { listOf(it.first, it.second) }.toByteArray(), "AES"),
                // nosemgrep: kotlin.lang.security.gcm-detection.gcm-detection
                GCMParameterSpec(128, ByteArray(12) { 0 }))
            val decoded = decoder.doFinal(bytes)
            // This should be PNG file or other supported image format
            return ByteArrayInputStream(decoded)
        }
    }
}
