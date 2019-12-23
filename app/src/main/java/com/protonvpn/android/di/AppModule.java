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
package com.protonvpn.android.di;

import com.google.gson.Gson;
import com.protonvpn.android.api.ProtonApiRetroFit;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.ui.home.ServerListUpdater;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.vpn.ProtonVpnBackendProvider;
import com.protonvpn.android.vpn.VpnBackendProvider;
import com.protonvpn.android.vpn.VpnStateMonitor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;

@Module
public class AppModule {

    @Singleton
    @Provides
    public ServerManager provideServerManager(UserData userData) {
        return new ServerManager(userData);
    }

    @Singleton
    @Provides
    public ServerListUpdater provideServerListUpdater(ProtonApiRetroFit api, ServerManager serverManager,
                                                      UserData userData) {
        CoroutineContext coroutineContext =
            GlobalScope.INSTANCE.getCoroutineContext().plus(Dispatchers.getMain());
        return new ServerListUpdater(coroutineContext, api, serverManager, userData);
    }

    @Singleton
    @Provides
    public ProtonApiRetroFit provideAPI() {
        return new ProtonApiRetroFit();
    }

    @Singleton
    @Provides
    public Gson provideGson() {
        return new Gson();
    }

    @Singleton
    @Provides
    public UserData provideUserPrefs() {
        return Storage.load(UserData.class, new UserData());
    }

    @Singleton
    @Provides
    public VpnStateMonitor provideVpnStateMonitor(UserData userData, ProtonApiRetroFit api,
                                                  VpnBackendProvider backendManager,
                                                  ServerListUpdater serverListUpdater) {
        return new VpnStateMonitor(userData, api, backendManager, serverListUpdater);
    }

    @Singleton
    @Provides
    public VpnBackendProvider provideVpnBackendManager() {
        return new ProtonVpnBackendProvider();
    }
}
