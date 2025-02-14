/*
 * Copyright (c) 2012-2024 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core

import org.junit.Assert
import org.junit.Test

class TestLog {

    @Test
    fun testMarschTooLong()
    {
        /* generate a string that is 16k long */
        var longtsring = "";

        while (longtsring.length < 16384)
        {
            longtsring += "very very long string indeed"
        }
        val li = LogItem(VpnStatus.LogLevel.VERBOSE, longtsring)

        val libytes = li.marschaledBytes;

        Assert.assertTrue(libytes.size > 2000);
        Assert.assertTrue(libytes.size < 12000);


        val liback = LogItem(libytes, libytes.size)
        val msgback = liback.getString(null)
        Assert.assertTrue(msgback.endsWith("...[too long]"))
    }
}