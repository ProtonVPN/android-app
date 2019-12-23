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
package com.protonvpn.android.api;

import com.protonvpn.android.models.login.GenericResponse;
import com.protonvpn.android.models.login.LoginBody;
import com.protonvpn.android.models.login.LoginInfoBody;
import com.protonvpn.android.models.login.LoginInfoResponse;
import com.protonvpn.android.models.login.LoginResponse;
import com.protonvpn.android.models.login.RefreshBody;
import com.protonvpn.android.models.login.SessionListResponse;
import com.protonvpn.android.models.login.VpnInfoResponse;
import com.protonvpn.android.models.vpn.OpenVPNConfigResponse;
import com.protonvpn.android.models.vpn.ServerList;
import com.protonvpn.android.models.vpn.UserLocation;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ProtonVPNRetrofit {

    @GET("vpn/logicals")
    Call<ServerList> getServers(@Query("IP") String ip);

    @POST("auth/info")
    Call<LoginInfoResponse> postLoginInfo(@Body LoginInfoBody body);

    @POST("auth")
    Call<LoginResponse> postLogin(@Body LoginBody body);

    @POST("auth/refresh")
    Call<LoginResponse> postRefresh(@Body RefreshBody body);

    @DELETE("auth")
    Call<GenericResponse> postLogout();

    @GET("vpn")
    Call<VpnInfoResponse> getVPNInfo();

    @GET("vpn/sessions")
    Call<SessionListResponse> getSession();

    @GET("vpn/location")
    Call<UserLocation> getLocation();

    @POST("reports/bug")
    Call<GenericResponse> postBugReport(@Body RequestBody params);

    @GET("/vpn/clientconfig")
    Call<OpenVPNConfigResponse> getOpenVPNConfig();
}
