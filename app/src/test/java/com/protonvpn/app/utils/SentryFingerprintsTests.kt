/*
 * Copyright (c) 2021. Proton AG
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

package com.protonvpn.app.utils

import com.protonvpn.android.utils.SentryFingerprints
import org.junit.Assert.assertEquals
import org.junit.Test

class SentryFingerprintsTests {

    @Test
    fun removeServiceRecordId() {
        val inputMessage =
            "Context.startForegroundService() did not then call Service.startForeground(): ServiceRecord{d5bde1a u0 ch.protonvpn.android/com.protonvpn.android.vpn.OpenVPNWrapperService}"
        val cleanedMessage = SentryFingerprints.removeUniquesFromMessage(inputMessage)
        assertEquals(
            "Context.startForegroundService() did not then call Service.startForeground(): ServiceRecord{<id> u0 ch.protonvpn.android/com.protonvpn.android.vpn.OpenVPNWrapperService}",
            cleanedMessage
        )
    }

    @Test
    fun removeNumbersFromBadNotification() {
        val inputMessage =
            "Bad notification(tag=null, id=6) posted from package ch.protonvpn.android, crashing app(uid=10351, pid=13861): Couldn't inflate contentViewsjava.lang.ArrayIndexOutOfBoundsException: src.length=8 srcPos=0 dst.length=8 dstPos=2 length=8"
        val cleanedMessage = SentryFingerprints.removeUniquesFromMessage(inputMessage)
        assertEquals(
            "Bad notification(tag=<num>, id=<num>) posted from package ch.protonvpn.android, crashing app(uid=<num>, pid=<num>): Couldn't inflate contentViewsjava.lang.ArrayIndexOutOfBoundsException: src.length=<num> srcPos=<num> dst.length=<num> dstPos=<num> length=<num>",
            cleanedMessage
        )
    }
}
