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

import com.protonvpn.TestApplication;
import com.protonvpn.android.di.ActivityBuilder;
import com.protonvpn.android.di.AppComponent;
import com.protonvpn.android.di.ViewModelModule;
import com.protonvpn.testsHelper.ServerManagerHelper;
import com.protonvpn.testsHelper.UserDataHelper;

import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;
import dagger.android.support.AndroidSupportInjectionModule;

@Singleton
@Component(modules = {AndroidSupportInjectionModule.class, ActivityBuilder.class, MockAppModule.class,
    ViewModelModule.class})
public interface TestAppComponent extends AppComponent {

    @Component.Builder
    interface Builder {

        @BindsInstance
        Builder application(TestApplication application);

        TestAppComponent build();
    }

    void provideServerManager(ServerManagerHelper serverManagerHelper);

    void provideUserPrefs(UserDataHelper userDataHelper);
}