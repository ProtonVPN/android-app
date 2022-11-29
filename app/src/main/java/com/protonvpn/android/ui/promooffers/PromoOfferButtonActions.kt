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

import com.protonvpn.android.appconfig.ApiNotificationOfferButton
import com.protonvpn.android.auth.usecase.AutoLoginUrlForWeb
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.LogLevel
import com.protonvpn.android.logging.ProtonLogger
import dagger.Reusable
import me.proton.core.util.kotlin.equalsNoCase
import javax.inject.Inject

private const val AUTOLOGIN_FLAG = "autologin"
private const val ACTION_OPENURL = "openurl"

// Handles "Action", "URL" and "With" fields.
// At the moment the only implemented action is OpenURL.
@Reusable
class PromoOfferButtonActions @Inject constructor(
    private val autoLoginUrlForWeb: AutoLoginUrlForWeb
) {
    suspend fun getButtonUrl(button: ApiNotificationOfferButton): String? =
        getButtonUrl(button.url, button.action, button.actionBehaviors)

    private suspend fun getButtonUrl(url: String?, action: String, behaviors: List<String>): String? =
        if (action.equalsNoCase(ACTION_OPENURL) && url != null) {
            if (hasAutologin(behaviors)) {
                autoLoginUrlForWeb(url)
            } else {
                url
            }
        } else {
            ProtonLogger.logCustom(
                LogLevel.ERROR, LogCategory.UI, "Unknown button action \"${action}\" or missing URL."
            )
            null
        }

    companion object {
        fun hasAutologin(behaviors: List<String>): Boolean =
            behaviors.any { it.equalsNoCase(AUTOLOGIN_FLAG) }
    }
}
