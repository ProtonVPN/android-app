/*
 * Copyright (c) 2012-2023 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package de.blinkt.openvpn.core

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.api.AppRestrictions
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Spy
import org.robolectric.RobolectricTestRunner

interface createTestBundle

@RunWith(RobolectricTestRunner::class)
class TestRestrictions : createTestBundle {
    @Test
    fun testImportRestrictions() {

        val context: Context = ApplicationProvider.getApplicationContext();

        val appr = AppRestrictions.getInstance(context);
        val b: Bundle = createTestBundle()


        appr.parseRestrictionsBundle(context, b);

        val pm = ProfileManager.getInstance(context);

        Assert.assertEquals(pm.profiles.size, 1)

        val firstProfile: VpnProfile = pm.profiles.first()

        Assert.assertEquals(
            firstProfile.uuidString,
            "F8AEE125-2D7A-44E9-B9EB-82FB619D51CC".lowercase()
        )
        Assert.assertEquals(
            firstProfile.importedProfileHash,
            "4098294f8a8d25bb6e85cef5672dfba13ed63719"
        )

        /* Try to remove the imported profiles again */
    }

    private fun createTestBundle(): Bundle {
        val b: Bundle = Bundle();

        val miniconfig = "client\nremote test.blinkt.de\n";

        val testVPN = Bundle();
        testVPN.putString("name", "Unit Test VPN");
        testVPN.putString("ovpn", miniconfig)
        testVPN.putString("uuid", "F8AEE125-2D7A-44E9-B9EB-82FB619D51CC");

        val ba: Array<Bundle> = arrayOf(testVPN)

        b.putParcelableArray("vpn_configuration_list", ba)
        b.putString("defaultprofile", "F8AEE125-2D7A-44E9-B9EB-82FB619D51CC")
        b.putString("version", "1")
        b.putString("allowed_remote_access", "some.random.app")
        return b
    }

    private fun createTestBundleEmptyVPN(): Bundle {
        val b: Bundle = Bundle();
        val ba: Array<Bundle> = arrayOf()

        b.putParcelableArray("vpn_configuration_list", ba)
        b.putString("version", "1")
        b.putString("allowed_remote_access", "some.random.app");
        return b
    }

    @Test
    fun testImportRestrictionsDelete() {

        val context: Context = ApplicationProvider.getApplicationContext();

        val appr = AppRestrictions.getInstance(context);
        val b: Bundle = createTestBundle()

        appr.parseRestrictionsBundle(context, b)

        /* add another not restriction managed profile */
        val otherVP: VpnProfile = VpnProfile("another")

        val pm = ProfileManager.getInstance(context)
        pm.addProfile(otherVP)

        Assert.assertEquals(pm.profiles.size, 2)

        val bEmpty: Bundle = createTestBundleEmptyVPN()
        appr.parseRestrictionsBundle(context, bEmpty)

        Assert.assertEquals(pm.profiles.size, 1)

        val firstProfile: VpnProfile = pm.profiles.first()

        Assert.assertEquals(
            firstProfile.name,
            "another"
        )

    }

    @Test
    fun testImportRestrictionsDeleteEmptyProfileList() {

        val context: Context = ApplicationProvider.getApplicationContext();

        val appr = AppRestrictions.getInstance(context);
        val b: Bundle = createTestBundle()

        appr.parseRestrictionsBundle(context, b)

        /* add another not restriction managed profile */
        val otherVP: VpnProfile = VpnProfile("another")

        val pm = ProfileManager.getInstance(context)
        pm.addProfile(otherVP)

        Assert.assertEquals(pm.profiles.size, 2)

        val bNoVPNConfigList: Bundle = Bundle();

        bNoVPNConfigList.putString("version", "1")


        appr.parseRestrictionsBundle(context, bNoVPNConfigList)

        Assert.assertEquals(pm.profiles.size, 1)

        val firstProfile: VpnProfile = pm.profiles.first()

        Assert.assertEquals(
            firstProfile.name,
            "another"
        )

    }
}
