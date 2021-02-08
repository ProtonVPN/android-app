/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import de.blinkt.openvpn.R
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.StringReader
import java.util.*

/**
 * Created by arne on 03.10.16.
 */

const val miniconfig = "client\nremote test.blinkt.de\n"
const val fakeCerts = "<ca>\n" +
        "-----BEGIN CERTIFICATE-----\n" +
        "\n" +
        "-----END CERTIFICATE-----\n" +
        "\n" +
        "</ca>\n" +
        "\n" +
        "<cert>\n" +
        "-----BEGIN CERTIFICATE-----\n" +
        "\n" +
        "-----END CERTIFICATE-----\n" +
        "\n" +
        "</cert>\n" +
        "\n" +
        "<key>\n" +
        "-----BEGIN PRIVATE KEY-----\n" +
        "\n" +
        "-----END PRIVATE KEY-----\n" +
        "\n" +
        "</key>"


@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class TestConfigParser {
    @Test
    @Throws(IOException::class, ConfigParser.ConfigParseError::class)
    fun testHttpProxyPass() {
        val httpproxypass = "<http-proxy-user-pass>\n" +
                "foo\n" +
                "bar\n" +
                "</http-proxy-user-pass>\n"

        val cp = ConfigParser()
        cp.parseConfig(StringReader(miniconfig + httpproxypass))
        val p = cp.convertProfile()
        Assert.assertFalse(p.mCustomConfigOptions.contains(httpproxypass))


    }

    @Test
    @Throws(IOException::class, ConfigParser.ConfigParseError::class)
    fun cleanReImport() {
        var cp = ConfigParser()
        cp.parseConfig(StringReader(miniconfig + fakeCerts))
        val vp = cp.convertProfile()

        val outConfig = vp.getConfigFile(ApplicationProvider.getApplicationContext(), false)

        cp = ConfigParser()
        cp.parseConfig(StringReader(outConfig))
        val vp2 = cp.convertProfile()

        val outConfig2 = vp2.getConfigFile(ApplicationProvider.getApplicationContext(), false)

        Assert.assertEquals(outConfig, outConfig2)
        Assert.assertFalse(vp.mUseCustomConfig)
        Assert.assertFalse(vp2.mUseCustomConfig)

    }

    @Test
    @Throws(IOException::class, ConfigParser.ConfigParseError::class)
    fun testCommonOptionsImport() {
        val config = ("client\n"
                + "tun-mtu 1234\n" +
                "<connection>\n" +
                "remote foo.bar\n" +
                "tun-mtu 1222\n" +
                "</connection>\n" +
                "route 8.8.8.8 255.255.255.255 net_gateway\n")

        val cp = ConfigParser()
        cp.parseConfig(StringReader(config))
        val vp = cp.convertProfile()

        Assert.assertEquals(1234, vp.mTunMtu.toLong())
        Assert.assertTrue(vp.mConnections[0].mCustomConfiguration.contains("tun-mtu 1222"))
        Assert.assertTrue(vp.mConnections[0].mUseCustomConfig)
        Assert.assertEquals(vp.mExcludedRoutes.trim(), "8.8.8.8/32");
    }


    @Test
    fun testCipherImport() {
        val config = ("client\n"
                + "tun-mtu 1234\n" +
                "<connection>\n" +
                "remote foo.bar\n" +
                "tun-mtu 1222\n" +
                "</connection>\n" +
                "route 8.8.8.8 255.255.255.255 net_gateway\n")


        val config1 = config + "cipher AES-128-GCM\n"

        val cp = ConfigParser()
        cp.parseConfig(StringReader(config1))
        val vp = cp.convertProfile()

        Assert.assertEquals("", vp.mDataCiphers)
        Assert.assertEquals("AES-128-GCM", vp.mCipher)

        val config2 = config + "cipher AES-128-GCM\ndata-ciphers AES-128-GCM:AES-256-GCM:BF-CBC\n"

        cp.parseConfig(StringReader(config2))
        val vp2 = cp.convertProfile()

        Assert.assertEquals("AES-128-GCM:AES-256-GCM:BF-CBC", vp2.mDataCiphers)

        val config3 = config + "cipher AES-128-GCM\n"

        cp.parseConfig(StringReader(config3))
        val vp3 = cp.convertProfile()

        Assert.assertEquals(vp3.mDataCiphers, "")

        val config4 = config + "cipher BF-CBC\nncp-ciphers AES-128-GCM:AES-256-GCM:CHACHA20-POLY1305\n"
        cp.parseConfig(StringReader(config4))
        val vp4 = cp.convertProfile()

        Assert.assertEquals("AES-128-GCM:AES-256-GCM:CHACHA20-POLY1305:BF-CBC", vp4.mDataCiphers)



    }


    @Test
    @Throws(IOException::class, ConfigParser.ConfigParseError::class)
    fun testSockProxyImport() {
        val proxy = "ca baz\n" +
                "key foo\n" +
                "cert bar\n" +
                "client\n" +
                "<connection>\n" +
                "socks-proxy 13.23.3.2\n" +
                "remote foo.bar\n" +
                "</connection>\n" +
                "\n" +
                "<connection>\n" +
                "socks-proxy 1.2.3.4 1234\n" +
                "remote foo.bar\n" +
                "</connection>\n" +
                "\n" +
                "<connection>\n" +
                "http-proxy 1.2.3.7 8080\n" +
                "remote foo.bar\n" +
                "</connection>"

        val cp = ConfigParser()
        cp.parseConfig(StringReader(proxy))
        val vp = cp.convertProfile()
        Assert.assertEquals(3, vp.mConnections.size.toLong())

        Assert.assertEquals("13.23.3.2", vp.mConnections[0].mProxyName)
        Assert.assertEquals("1080", vp.mConnections[0].mProxyPort)
        Assert.assertEquals(Connection.ProxyType.SOCKS5, vp.mConnections[0].mProxyType)

        Assert.assertEquals("1.2.3.4", vp.mConnections[1].mProxyName)
        Assert.assertEquals("1234", vp.mConnections[1].mProxyPort)
        Assert.assertEquals(Connection.ProxyType.SOCKS5, vp.mConnections[0].mProxyType)

        Assert.assertEquals("1.2.3.7", vp.mConnections[2].mProxyName)
        Assert.assertEquals("8080", vp.mConnections[2].mProxyPort)
        Assert.assertEquals(Connection.ProxyType.HTTP, vp.mConnections[2].mProxyType)

        val c:Context = ApplicationProvider.getApplicationContext()
        val err = vp.checkProfile(c, false)
        Assert.assertTrue("Failed with " + c.getString(err), err == R.string.no_error_found)
    }

    @Test
    @Throws(IOException::class, ConfigParser.ConfigParseError::class)
    fun testHttpUserPassAuth() {
        val proxy = "client\n" +
                "dev tun\n" +
                "proto tcp\n" +
                "remote 1.2.3.4 443\n" +
                "resolv-retry infinite\n" +
                "nobind\n" +
                "persist-key\n" +
                "persist-tun\n" +
                "auth-user-pass\n" +
                "verb 3\n" +
                "cipher AES-128-CBC\n" +
                "pull\n" +
                "route-delay 2\n" +
                "redirect-gateway\n" +
                "remote-cert-tls server\n" +
                "ns-cert-type server\n" +
                "comp-lzo no\n" +
                "http-proxy 1.2.3.4 1234\n" +
                "<http-proxy-user-pass>\n" +
                "username12\n" +
                "password34\n" +
                "</http-proxy-user-pass>\n" +
                "<ca>\n" +
                "foo\n" +
                "</ca>\n" +
                "<cert>\n" +
                "bar\n" +
                "</cert>\n" +
                "<key>\n" +
                "baz\n" +
                "</key>\n"
        val cp = ConfigParser()
        cp.parseConfig(StringReader(proxy))
        val vp = cp.convertProfile()
        var config = vp.getConfigFile(ApplicationProvider.getApplicationContext(), true)
        Assert.assertTrue(config.contains("username12"))
        Assert.assertTrue(config.contains("http-proxy 1.2.3.4"))

        config = vp.getConfigFile(ApplicationProvider.getApplicationContext(), false)

        Assert.assertFalse(config.contains("username12"))
        Assert.assertFalse(config.contains("http-proxy 1.2.3.4"))

        Assert.assertTrue(vp.mConnections[0].mUseProxyAuth)
        Assert.assertEquals(vp.mConnections[0].mProxyAuthUser, "username12")
        Assert.assertEquals(vp.mConnections[0].mProxyAuthPassword, "password34")
    }

    @Test
    @Throws(IOException::class, ConfigParser.ConfigParseError::class)
    fun testConfigWithHttpProxyOptions() {
        val proxyconf = "pull\n" +
                "dev tun\n" +
                "proto tcp-client\n" +
                "cipher AES-128-CBC\n" +
                "auth SHA1\n" +
                "reneg-sec 0\n" +
                "remote-cert-tls server\n" +
                "tls-version-min 1.2 or-highest\n" +
                "persist-tun\n" +
                "nobind\n" +
                "connect-retry 2 2\n" +
                "dhcp-option DNS 1.1.1.1\n" +
                "dhcp-option DNS 84.200.69.80\n" +
                "auth-user-pass\n" +
                "\n" +
                "remote xx.xx.xx.xx 1194\n" +
                "http-proxy 1.2.3.4 8080\n" +
                "http-proxy-option VERSION 1.1\n" +
                "http-proxy-option CUSTOM-HEADER \"Connection: Upgrade\"\n" +
                "http-proxy-option CUSTOM-HEADER \"X-Forwarded-Proto: https\"\n" +
                "http-proxy-option CUSTOM-HEADER \"Upgrade-Insecure-Requests: 1\"\n" +
                "http-proxy-option CUSTOM-HEADER \"DNT: 1\"\n" +
                "http-proxy-option CUSTOM-HEADER \"Tk: N\"\n" +
                "\n" +
                fakeCerts

        val cp = ConfigParser()
        cp.parseConfig(StringReader(proxyconf))
        val vp = cp.convertProfile()
        var config = vp.getConfigFile(ApplicationProvider.getApplicationContext(), true)


        Assert.assertEquals(vp.checkProfile(ApplicationProvider.getApplicationContext(), true).toLong(), R.string.no_error_found.toLong())
        Assert.assertEquals(vp.checkProfile(ApplicationProvider.getApplicationContext(), false).toLong(), R.string.no_error_found.toLong())

        config = vp.getConfigFile(ApplicationProvider.getApplicationContext(), false)

        Assert.assertTrue(config.contains("http-proxy 1.2.3.4"))
        Assert.assertFalse(config.contains("management-query-proxy"))


        Assert.assertTrue(config.contains("http-proxy-option CUSTOM-HEADER"))

        vp.mConnections = Arrays.copyOf(vp.mConnections, vp.mConnections.size + 1)
        vp.mConnections[vp.mConnections.size - 1] = Connection()

        vp.mConnections[vp.mConnections.size - 1].mProxyType = Connection.ProxyType.ORBOT

        Assert.assertEquals(vp.checkProfile(ApplicationProvider.getApplicationContext(), false).toLong(), R.string.error_orbot_and_proxy_options.toLong())

    }


    @Test
    fun testTlscryptV2Import()
    {
        val conf = """<ca>
Here
</ca>

<cert>
no
</cert>

cipher AES-256-GCM
client
compress
dev-type tun
explicit-exit-notify
<key>
useful
</key>
nobind
persist-key
persist-tun
remote home.evil.cloud 65443 udp
remote home.evil.cloud 65443 tcp-client
remote-cert-tls server
<tls-crypt-v2>
content
</tls-crypt-v2>
topology subnet
verb 4
verify-x509-name homevpn.evil.cloud name

""";
        val cp = ConfigParser()
        cp.parseConfig(StringReader(conf))

        val vp = cp.convertProfile()

        val config = vp.getConfigFile(ApplicationProvider.getApplicationContext(), true)

        Assert.assertEquals("tls-crypt-v2", vp.mTLSAuthDirection)

        Assert.assertFalse(config.contains("key-direction"))
    }

}
