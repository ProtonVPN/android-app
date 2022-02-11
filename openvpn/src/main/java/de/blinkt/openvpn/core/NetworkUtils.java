/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.net.*;
import android.os.Build;
import android.text.TextUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Vector;

public class NetworkUtils {

    public static Vector<String> getLocalNetworks(Context c, boolean ipv6) {
        Vector<String> nets = new Vector<>();
        ConnectivityManager conn = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Network[] networks = conn.getAllNetworks();
            for (Network network : networks) {
                NetworkInfo ni = conn.getNetworkInfo(network);
                LinkProperties li = conn.getLinkProperties(network);

                NetworkCapabilities nc = conn.getNetworkCapabilities(network);

                // Skip VPN networks like ourselves
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                    continue;

                // Also skip mobile networks
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                    continue;


                for (LinkAddress la : li.getLinkAddresses()) {
                    if ((la.getAddress() instanceof Inet4Address && !ipv6) ||
                            (la.getAddress() instanceof Inet6Address && ipv6))
                        nets.add(la.toString());
                }
            }
        } else {
            // Old Android Version, use native utils via ifconfig instead
            // Add local network interfaces
            if (ipv6)
                return nets;

            String[] localRoutes = NativeUtils.getIfconfig();

            // The format of mLocalRoutes is kind of broken because I don't really like JNI
            for (int i = 0; i < localRoutes.length; i += 3) {
                String intf = localRoutes[i];
                String ipAddr = localRoutes[i + 1];
                String netMask = localRoutes[i + 2];

                if (intf == null || intf.equals("lo") ||
                        intf.startsWith("tun") || intf.startsWith("rmnet"))
                    continue;

                if (ipAddr == null || netMask == null) {
                    VpnStatus.logError("Local routes are broken?! (Report to author) " + TextUtils.join("|", localRoutes));
                    continue;
                }
                nets.add(ipAddr + "/" + CIDRIP.calculateLenFromMask(netMask));

            }

        }
        return nets;
    }

}