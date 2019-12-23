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

import android.app.Activity;

import com.protonvpn.android.ui.home.HomeActivity;
import com.protonvpn.android.ui.home.countries.CountryListFragment;
import com.protonvpn.android.ui.home.map.MapFragment;
import com.protonvpn.android.ui.home.profiles.ProfilesFragment;
import com.protonvpn.android.ui.onboarding.WelcomeDialog;
import com.protonvpn.android.vpn.VpnStateFragment;

import dagger.Binds;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class HomeActivityModule {

    @Binds
    abstract Activity provideActivity(HomeActivity activity);

    @ContributesAndroidInjector
    abstract VpnStateFragment provideStateFragment();

    @ContributesAndroidInjector
    abstract MapFragment provideMapFragment();

    @ContributesAndroidInjector
    abstract CountryListFragment provideCountryListFragment();

    @ContributesAndroidInjector
    abstract ProfilesFragment provideProfileFragment();

    @ContributesAndroidInjector
    abstract WelcomeDialog provideWelcomeDialog();
}
