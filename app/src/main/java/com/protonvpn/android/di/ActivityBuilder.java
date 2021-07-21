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

import com.protonvpn.android.components.BootReceiver;
import com.protonvpn.android.components.QuickTileService;
import com.protonvpn.android.ui.ProtocolSelectionActivity;
import com.protonvpn.android.ui.drawer.SettingsDefaultProfileActivity;
import com.protonvpn.android.ui.drawer.SettingsExcludeAppsActivity;
import com.protonvpn.android.ui.drawer.SettingsExcludeIpsActivity;
import com.protonvpn.android.ui.drawer.SettingsMtuActivity;
import com.protonvpn.android.ui.home.InformationActivity;
import com.protonvpn.android.ui.home.profiles.CountrySelectionActivity;
import com.protonvpn.android.ui.home.profiles.ServerSelectionActivity;
import com.protonvpn.android.ui.home.vpn.SwitchDialogActivity;
import com.protonvpn.android.ui.login.LoginActivity;
import com.protonvpn.android.ui.drawer.AccountActivity;
import com.protonvpn.android.ui.drawer.AlwaysOnSettingsActivity;
import com.protonvpn.android.ui.drawer.OssLicensesActivity;
import com.protonvpn.android.ui.drawer.ReportBugActivity;
import com.protonvpn.android.ui.drawer.SettingsActivity;
import com.protonvpn.android.ui.home.HomeActivity;
import com.protonvpn.android.ui.home.profiles.ProfileActivity;
import com.protonvpn.android.ui.login.TroubleshootActivity;
import com.protonvpn.android.ui.onboarding.OnboardingActivity;
import com.protonvpn.android.ui.drawer.LogActivity;
import com.protonvpn.android.vpn.openvpn.OpenVPNWrapperService;
import com.protonvpn.android.vpn.ikev2.ProtonCharonVpnService;
import com.protonvpn.android.vpn.wireguard.WireguardWrapperService;

import androidx.annotation.RequiresApi;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;

import static android.os.Build.VERSION_CODES.N;

@Module
public abstract class ActivityBuilder {

    @ContributesAndroidInjector(modules = {HomeActivityModule.class})
    abstract HomeActivity bindMainActivity();

    @ContributesAndroidInjector(modules = {HomeActivityModule.class})
    abstract SwitchDialogActivity bindSwitchActivity();

    @ContributesAndroidInjector(modules = {LoginModule.class})
    abstract LoginActivity bindDetailActivity();

    @ContributesAndroidInjector(modules = {OnboardingModule.class})
    abstract OnboardingActivity bindOnboardingActivity();

    @ContributesAndroidInjector(modules = {LogActivityModule.class})
    abstract LogActivity bindLogActivity();

    @ContributesAndroidInjector(modules = {SettingsModule.class})
    abstract SettingsActivity bindSettingsActivity();

    @ContributesAndroidInjector(modules = {SettingsModule.class})
    abstract SettingsDefaultProfileActivity bindSettingsDefaultProfileActivity();

    @ContributesAndroidInjector(modules = {SettingsModule.class})
    abstract SettingsExcludeIpsActivity bindSettingsExcludeIpsActivity();

    @ContributesAndroidInjector(modules = {SettingsModule.class})
    abstract SettingsExcludeAppsActivity bindSettingsExcludeAppsActivity();

    @ContributesAndroidInjector(modules = {SettingsModule.class})
    abstract AlwaysOnSettingsActivity bindAlwaysOnSettingsActivity();

    @ContributesAndroidInjector(modules = {SettingsModule.class})
    abstract SettingsMtuActivity bindSettingsMtuActivity();

    @ContributesAndroidInjector(modules = {ProfileActivityModule.class})
    abstract ProfileActivity bindProfileActivity();

    @ContributesAndroidInjector(modules = {CountrySelectionActivityModule.class})
    abstract CountrySelectionActivity bindCountrySelectionActivity();

    @ContributesAndroidInjector(modules = {ServerSelectionActivityModule.class})
    abstract ServerSelectionActivity bindServerSelectionActivity();

    @ContributesAndroidInjector(modules = {ProtocolSelectionActivityModule.class})
    abstract ProtocolSelectionActivity bindProtocolSelectionActivity();

    @ContributesAndroidInjector(modules = {ReportBugModule.class})
    abstract ReportBugActivity bindReportBugActivity();

    @ContributesAndroidInjector(modules = {AccountModule.class})
    abstract AccountActivity bindAccountActivity();

    @ContributesAndroidInjector(modules = {OssLicensesModule.class})
    abstract OssLicensesActivity bindOssLicensesActivity();

    @ContributesAndroidInjector(modules = {InformationModule.class})
    abstract InformationActivity bindInformationActivity();

    @ContributesAndroidInjector(modules = {TroubleshootActivityModule.class})
    abstract TroubleshootActivity bindTroubleshootActivity();

    @ContributesAndroidInjector(modules = {CharonModule.class})
    abstract ProtonCharonVpnService bindCharon();

    @ContributesAndroidInjector(modules = {OpenVPNWrapperModule.class})
    abstract OpenVPNWrapperService bindOpenVPN();

    @ContributesAndroidInjector(modules = {WireguardWrapperModule.class})
    abstract WireguardWrapperService bindWireguardWrapper();

    @ContributesAndroidInjector(modules = {BootUpModule.class})
    abstract BootReceiver bindBootReceiver();

    @RequiresApi(N)
    @ContributesAndroidInjector(modules = {QuickTileModule.class})
    abstract QuickTileService bindQuickTile();

}
