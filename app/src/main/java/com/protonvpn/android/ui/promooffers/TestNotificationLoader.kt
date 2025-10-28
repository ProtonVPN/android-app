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
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.proton.core.util.kotlin.DispatcherProvider
import java.io.File
import javax.inject.Inject

private const val FILE_NAME = "test_notifications_response.json"

@Reusable
class TestNotificationLoader @Inject constructor(
    private val mainScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
    private val notificationManager: ApiNotificationManager
) {
    fun loadTestFile() {
        mainScope.launch {
            val jsonString = withContext(dispatcherProvider.Io) {
                val filePath = File(appContext.filesDir, FILE_NAME)
                if (filePath.exists()) {
                    ProtonLogger.logCustom(LogLevel.INFO, LogCategory.PROMO, "Found test notifications: $filePath")
                    filePath.readText()
                } else {
                    ProtonLogger.logCustom(LogLevel.INFO, LogCategory.PROMO, "No test notifications file: $filePath")
                    null
                }
            }

            if (jsonString != null) notificationManager.setTestNotificationsResponseJson(jsonString)
        }
    }
}
