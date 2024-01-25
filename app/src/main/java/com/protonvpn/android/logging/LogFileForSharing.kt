/*
 * Copyright (c) 2024. Proton AG
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

package com.protonvpn.android.logging

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.protonvpn.android.BuildConfig
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

@Reusable
class LogFileForSharing @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    suspend operator fun invoke(): Intent? {
        val file = ProtonLogger.getLogFileForSharing()
        return file?.let { createIntent(it) }
    }

    private fun createIntent(file: File): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "*/*"

        val contentUri: Uri =
            FileProvider.getUriForFile(appContext, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        intent.putExtra(Intent.EXTRA_STREAM, contentUri)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.clipData = ClipData.newRawUri("", contentUri)
        return Intent.createChooser(intent, "Share log")
    }
}
