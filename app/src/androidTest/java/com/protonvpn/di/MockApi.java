/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.di;

import com.protonvpn.MockSwitch;
import com.protonvpn.android.api.NetworkLoader;
import com.protonvpn.android.api.NetworkResultCallback;
import com.protonvpn.android.api.ProtonApiRetroFit;
import com.protonvpn.android.components.BaseActivity;
import com.protonvpn.android.models.login.GenericResponse;
import com.protonvpn.android.models.login.SessionListResponse;
import com.protonvpn.android.models.login.VpnInfoResponse;
import com.protonvpn.android.models.vpn.ServerList;
import com.protonvpn.android.utils.Log;
import com.protonvpn.testsHelper.IdlingResourceHelper;
import com.protonvpn.testsHelper.MockedServers;

import java.util.ArrayList;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingResource;
import retrofit2.Call;

public class MockApi extends ProtonApiRetroFit {

    public MockApi() {
        super();
        IdlingResource resource = IdlingResourceHelper.create("OkHttp", getOkClient());
        Espresso.registerIdlingResources(resource);
    }

    @Override
    public void getVPNInfo(final BaseActivity activity,
                           final NetworkResultCallback<VpnInfoResponse> callback) {
        super.getVPNInfo(activity, callback);
        Log.e("stub call");
    }

    @Override
    public Call<SessionListResponse> getSession(NetworkResultCallback<SessionListResponse> callback) {
        Log.e("stub call");
        callback.onSuccess(new SessionListResponse(0, new ArrayList<>()));
        return null;
    }

    @Override
    public void logout(NetworkResultCallback<GenericResponse> callback) {
        callback.onSuccess(new GenericResponse(1000));
    }

    @Override
    public void getServerList(final NetworkLoader activity, final String ip,
                              final NetworkResultCallback<ServerList> callback) {
        if (MockSwitch.mockedServersUsed) {
            ServerList list = new ServerList(MockedServers.getServerList());
            callback.onSuccess(list);
        }
        else {
            super.getServerList(activity, ip, callback);
        }
    }
}
