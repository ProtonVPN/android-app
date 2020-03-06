/*
 * Copyright (c) 2019 Proton Technologies AG
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

import com.protonvpn.android.BuildConfig;

public class Constants {

    public static final long MAX_LOG_SIZE = 200 * 1024;
    public static final int NOTIFICATION_ID = 6;
    public static final String SIGNUP_URL = "https://account.protonvpn.com/signup?from=mobile";
    public static final String PRIMARY_VPN_API_URL = "https://" + BuildConfig.API_DOMAIN + "/";
    public static final String ALTERNATIVE_ROUTING_LEARN_URL = "https://protonmail.com/blog/anti-censorship-alternative-routing";
}
