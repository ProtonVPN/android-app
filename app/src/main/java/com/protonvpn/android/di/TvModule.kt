package com.protonvpn.android.di

import com.protonvpn.android.tv.TvGenericDialogActivity
import com.protonvpn.android.tv.TvTrialDialogActivity
import com.protonvpn.android.tv.TvLoginActivity
import com.protonvpn.android.tv.main.TvMainActivity
import com.protonvpn.android.tv.TvMainFragment
import com.protonvpn.android.tv.TvStatusFragment
import com.protonvpn.android.tv.TvUpgradeActivity
import com.protonvpn.android.tv.detailed.CountryDetailFragment
import com.protonvpn.android.tv.detailed.TvServerListFragment
import com.protonvpn.android.tv.detailed.TvServerListScreenFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class TvModule {

    @ContributesAndroidInjector
    abstract fun bindTvLogin(): TvLoginActivity

    @ContributesAndroidInjector
    abstract fun bindTvMain(): TvMainActivity

    @ContributesAndroidInjector
    abstract fun bindUpgradeActivity(): TvUpgradeActivity

    @ContributesAndroidInjector
    abstract fun provideMainFragment(): TvMainFragment

    @ContributesAndroidInjector
    abstract fun provideStatusFragment(): TvStatusFragment

    @ContributesAndroidInjector
    abstract fun provideDetailsFragment(): CountryDetailFragment

    @ContributesAndroidInjector
    abstract fun provideServerListFragment(): TvServerListFragment

    @ContributesAndroidInjector
    abstract fun provideServerListScreenFragment(): TvServerListScreenFragment

    @ContributesAndroidInjector
    abstract fun bindTvTrialDialogActivity(): TvTrialDialogActivity

    @ContributesAndroidInjector
    abstract fun bindTvGenericActivity(): TvGenericDialogActivity
}
