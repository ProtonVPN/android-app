/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.text.DecimalFormat;

public final class ConnectionTools {

    public static boolean networkAvailable = true;

    public static void setNetworkAvailability(boolean availability) {
        networkAvailable = availability;
    }

    public ConnectionTools() {

    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return networkAvailable && activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public static String bytesToSize(long sizeInBytes) {
        String readableSize;

        double b = sizeInBytes;
        double k = sizeInBytes / 1024.0;
        double m = ((sizeInBytes / 1024.0) / 1024.0);
        double g = (((sizeInBytes / 1024.0) / 1024.0) / 1024.0);
        double t = ((((sizeInBytes / 1024.0) / 1024.0) / 1024.0) / 1024.0);

        DecimalFormat dec = new DecimalFormat("0.00");

        if (t > 1) {
            readableSize = dec.format(t).concat(" TB");
        }
        else if (g > 1) {
            readableSize = dec.format(g).concat(" GB");
        }
        else if (m > 1) {
            readableSize = dec.format(m).concat(" MB");
        }
        else if (k > 1) {
            readableSize = dec.format(k).concat(" KB");
        }
        else {
            readableSize = dec.format(b).concat(" B");
        }

        return readableSize;
    }
}
