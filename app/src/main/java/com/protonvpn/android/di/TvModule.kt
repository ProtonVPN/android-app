package com.protonvpn.android.di

import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.TvLoginFragment
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.tv.TvMainFragment
import com.protonvpn.android.tv.TvStatusFragment
import com.protonvpn.android.tv.detailed.CountryDetailFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class TvModule {

    @ContributesAndroidInjector
    abstract fun bindTvLogin(): TvLoginActivity

    @ContributesAndroidInjector
    abstract fun bindTvMain(): TvMainActivity

    @ContributesAndroidInjector
    abstract fun provideLoginFragment(): TvLoginFragment

    @ContributesAndroidInjector
    abstract fun provideMainFragment(): TvMainFragment

    @ContributesAndroidInjector
    abstract fun provideStatusFragment(): TvStatusFragment

    @ContributesAndroidInjector
    abstract fun provideDetailsFragment(): CountryDetailFragment
}
