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

package com.protonvpn.test.shared

import com.protonvpn.android.appconfig.ApiNotification
import com.protonvpn.android.appconfig.ApiNotificationOffer
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.appconfig.ApiNotificationsResponse

object ApiNotificationTestHelper {

    fun mockOffer(id: String, start: Long = 0L, end: Long = 0L, label: String = "Offer") =
        ApiNotification(
            id, start, end, ApiNotificationTypes.TYPE_OFFER, ApiNotificationOffer(
                label, "https://protonvpn.com", "file:///android_asset/no_such_file.png"
            )
        )

    fun mockResponse(vararg items: ApiNotification) =
        ApiNotificationsResponse(arrayOf(*items))
}
