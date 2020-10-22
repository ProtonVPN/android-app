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
package com.protonvpn.android.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.protonvpn.android.tv.login.TvLoginViewModel
import com.protonvpn.android.ui.home.TvHomeViewModel
import com.protonvpn.android.ui.home.countries.CountryListViewModel
import com.protonvpn.android.ui.home.profiles.HomeViewModel
import com.protonvpn.android.ui.home.profiles.ProfileViewModel
import com.protonvpn.android.ui.home.profiles.ProfilesViewModel
import com.protonvpn.android.ui.login.LoginViewModel
import com.protonvpn.android.ui.login.TroubleshootViewModel
import com.protonvpn.android.utils.ViewModelFactory
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

@Module
abstract class ViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(CountryListViewModel::class)
    abstract fun bindsCountryListViewModel(yourViewModel: CountryListViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ProfilesViewModel::class)
    abstract fun bindsProfilesViewModel(profilesViewModel: ProfilesViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(LoginViewModel::class)
    abstract fun bindsLoginViewModel(loginViewModel: LoginViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ProfileViewModel::class)
    abstract fun bindsProfileViewModel(profileViewModel: ProfileViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(HomeViewModel::class)
    abstract fun bindsHomeViewModel(viewModel: HomeViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TvHomeViewModel::class)
    abstract fun bindsTvHomeViewModel(viewModel: TvHomeViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TvLoginViewModel::class)
    abstract fun bindsTvLoginViewModel(viewModel: TvLoginViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(TroubleshootViewModel::class)
    abstract fun bindsTroubleshootViewModel(viewModel: TroubleshootViewModel): ViewModel

    @Binds
    abstract fun bindViewModelFactory(vmFactory: ViewModelFactory): ViewModelProvider.Factory
}
