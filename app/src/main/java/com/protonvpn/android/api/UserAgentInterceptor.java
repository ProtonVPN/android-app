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
package com.protonvpn.android.api;

import android.os.Build;

import com.protonvpn.android.BuildConfig;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class UserAgentInterceptor implements Interceptor {

    public final String userAgent;

    public UserAgentInterceptor(String userAgent) {
        this.userAgent = userAgent;
    }

    public UserAgentInterceptor() {
        this(String.format(Locale.US, "%s/%s (Android %s; %s; %s %s; %s)", "ProtonVPN Android",
            BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, Build.MODEL, Build.BRAND, Build.DEVICE,
            Locale.getDefault().getLanguage()));
    }

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request userAgentRequest = chain.request().newBuilder().header("User-Agent", userAgent).build();
        return chain.proceed(userAgentRequest);
    }
}