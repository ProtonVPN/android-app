/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.app.mmp.referrer

import com.protonvpn.android.mmp.referrer.MmpReferrer

object TestMmpReferrer {

    fun create(
        asid: String = "bf00d374-db9b-4b9c-9cfd-037e16e82053",
        referrerLink: String = "utm_source=google-play&utm_medium=organic",
        sessionStartTimestamp: Long? = null,
    ): MmpReferrer = MmpReferrer(
        asid = asid,
        referrerLink = referrerLink,
        sessionStartTimestamp = sessionStartTimestamp,
    )

}
