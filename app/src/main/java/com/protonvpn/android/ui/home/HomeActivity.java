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
package com.protonvpn.android.ui.home;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.material.tabs.TabLayout;
import com.jakewharton.rxbinding2.support.design.widget.RxTabLayout;
import com.protonvpn.android.BuildConfig;
import com.protonvpn.android.R;
import com.protonvpn.android.api.ProtonApiRetroFit;
import com.protonvpn.android.bus.ConnectToProfile;
import com.protonvpn.android.bus.ConnectToServer;
import com.protonvpn.android.bus.ConnectedToServer;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.bus.ForcedLogout;
import com.protonvpn.android.bus.VpnStateChanged;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.LoaderUI;
import com.protonvpn.android.components.MinimizedNetworkLayout;
import com.protonvpn.android.components.ProtonActionMenu;
import com.protonvpn.android.components.ReversedList;
import com.protonvpn.android.components.SecureCoreCallback;
import com.protonvpn.android.components.ViewPagerAdapter;
import com.protonvpn.android.migration.NewAppMigrator;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.login.VpnInfoResponse;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.ui.login.LoginActivity;
import com.protonvpn.android.ui.drawer.AccountActivity;
import com.protonvpn.android.ui.drawer.ReportBugActivity;
import com.protonvpn.android.ui.drawer.SettingsActivity;
import com.protonvpn.android.ui.home.countries.CountryListFragment;
import com.protonvpn.android.ui.home.map.MapFragment;
import com.protonvpn.android.ui.home.profiles.ProfilesFragment;
import com.protonvpn.android.ui.onboarding.OnboardingDialogs;
import com.protonvpn.android.ui.onboarding.OnboardingPreferences;
import com.protonvpn.android.utils.AndroidUtils;
import com.protonvpn.android.utils.AnimationTools;
import com.protonvpn.android.utils.HtmlTools;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.vpn.LogActivity;
import com.protonvpn.android.vpn.VpnStateFragment;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.squareup.otto.Subscribe;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import javax.inject.Inject;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.viewpager.widget.ViewPager;
import butterknife.BindView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import kotlin.Unit;

@ContentLayout(R.layout.activity_home)
public class HomeActivity extends PoolingActivity implements SecureCoreCallback {

    @BindView(R.id.viewPager) ViewPager viewPager;
    @BindView(R.id.tabs) TabLayout tabs;
    @BindView(R.id.fabQuickConnect) ProtonActionMenu fabQuickConnect;
    @BindView(R.id.coordinator) CoordinatorLayout coordinator;
    @BindView(R.id.textUser) TextView textUser;
    @BindView(R.id.textTier) TextView textTier;
    @BindView(R.id.textVersion) TextView textVersion;
    @BindView(R.id.minimizedLoader) MinimizedNetworkLayout minimizedLoader;
    VpnStateFragment fragment;
    public @BindView(R.id.switchSecureCore) SwitchCompat switchSecureCore;
    boolean doubleBackToExitPressedOnce = false;

    @Inject ProtonApiRetroFit api;
    @Inject ServerManager serverManager;
    @Inject UserData userData;
    @Inject VpnStateMonitor vpnStateMonitor;
    @Inject ServerListUpdater serverListUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerForEvents();
        super.onCreate(savedInstanceState);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(HtmlTools.fromHtml(getString(R.string.toolbar_app_title)));
        initDrawer();
        initDrawerView();
        fragment = (VpnStateFragment) getSupportFragmentManager().findFragmentById(R.id.vpnStatusBar);
        switchSecureCore.setChecked(userData.isSecureCoreEnabled());
        Sentry.getContext().setUser(new UserBuilder().setUsername(userData.getUser()).build());
        checkForUpdate();
        if (serverManager.isDownloadedAtLeastOnce() || serverManager.isOutdated()) {
            initLayout();
        }
        else {
            minimizedLoader.switchToEmpty();
        }
        if (serverManager.isDownloadedAtLeastOnce()) {
            initOnboarding();
        }

        serverManager.getUpdateEvent().observe(this, () -> {
            if (serverManager.isDownloadedAtLeastOnce()) {
                initOnboarding();
                EventBus.post(new VpnStateChanged(userData.isSecureCoreEnabled()));
            }
            else {
                initLayout();
            }
            return Unit.INSTANCE;
        });

        serverManager.getProfilesUpdateEvent().observe(this, () -> {
            initQuickConnectFab();
            return Unit.INSTANCE;
        });

        serverListUpdater.onHomeActivityCreated(this);

        if (Storage.getBoolean(NewAppMigrator.PREFS_MIGRATED_FROM_OLD)) {
            if (AndroidUtils.INSTANCE.isPackageInstalled(this, NewAppMigrator.OLD_APP_ID)) {
                showMigrationDialog();
            }
            Storage.saveBoolean(NewAppMigrator.PREFS_MIGRATED_FROM_OLD, false);
        }
    }

    private void showMigrationDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setMessage(R.string.successful_migration_message);
        Intent oldAppIntent = AndroidUtils.INSTANCE.playMarketIntentFor(NewAppMigrator.OLD_APP_ID);
        dialog.setPositiveButton(R.string.successful_migration_uninstall,
            (dialogInterface, button) -> startActivity(oldAppIntent));
        dialog.setNegativeButton(R.string.ok, null);
        dialog.create().show();
    }

    @Override
    public void onTrialEnded() {
        vpnStateMonitor.disconnect();
        switchSecureCore.setChecked(false);
    }

    private void checkForUpdate() {
        int versionCode = Storage.getInt("VERSION_CODE");
        Storage.saveInt("VERSION_CODE", BuildConfig.VERSION_CODE);
    }

    public boolean isBottomSheetExpanded() {
        return fragment.isBottomSheetExpanded();
    }

    private void initStatusBar() {
        fragment.initStatusLayout(fabQuickConnect);
        if (getIntent().getBooleanExtra("OpenStatus", false)) {
            fragment.openBottomSheet();
        }
    }

    @SuppressLint("CheckResult")
    private void initLayout() {
        tabs.setVisibility(View.VISIBLE);
        final ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFrag(new CountryListFragment(), getString(R.string.tabsCountries));
        adapter.addFrag(MapFragment.newInstance(), getString(R.string.tabsMap));
        adapter.addFrag(new ProfilesFragment(), getString(R.string.tabsProfiles));
        viewPager.setAdapter(adapter);

        tabs.setupWithViewPager(viewPager);
        RxTabLayout.selectionEvents(tabs)
            .subscribe(tabLayoutSelectionEvent -> fragment.collapseBottomSheet());
        RxTabLayout.selections(tabs).subscribe(tab -> {
            if (tab.isSelected()) {
                View tabView = ((LinearLayout) tabs.getChildAt(0)).getChildAt(tab.getPosition());
                if (getString(R.string.tabsMap).equals(tab.getText().toString())
                    && !isBottomSheetExpanded()) {
                    OnboardingDialogs.showDialogOnView(getContext(), tabView, getString(R.string.tabsMap),
                        getString(R.string.onboardingDialogMapView), OnboardingPreferences.MAPVIEW_DIALOG);
                }
                if (getString(R.string.tabsProfiles).equals(tab.getText().toString())
                    && !isBottomSheetExpanded() && OnboardingPreferences.wasFloatingButtonUsed()) {
                    OnboardingDialogs.showDialogOnView(getContext(), tabView,
                        getString(R.string.tabsProfiles), getString(R.string.onboardingDialogProfiles),
                        OnboardingPreferences.PROFILES_DIALOG);
                }
                if (getString(R.string.tabsCountries).equals(tab.getText().toString())
                    && OnboardingPreferences.wasFloatingButtonUsed() && !vpnStateMonitor.isConnected()
                    && !isBottomSheetExpanded()) {
                    OnboardingDialogs.showDialogOnView(getContext(), tabView,
                        getString(R.string.tabsCountries), getString(R.string.onboardingListDescription),
                        OnboardingPreferences.COUNTRY_DIALOG);
                }
            }
        });

        AnimationTools.addScaleAnimationToMenuIcon(fabQuickConnect);
        initStatusBar();
        fabQuickConnect.setVisibility(View.VISIBLE);
        initQuickConnectFab();
    }

    private void initOnboarding() {
        OnboardingDialogs.showDialogOnView(this, fabQuickConnect.getActionButton(),
            getString(R.string.onboardingFAB), getString(R.string.onboardingFABDescription),
            OnboardingPreferences.FLOATINGACTION_DIALOG, Gravity.TOP);
    }

    private void initDrawerView() {
        textTier.setText(userData.getVpnInfoResponse().getUserTierName());
        textUser.setText(userData.getUser());
        textVersion.setText(getString(R.string.drawerAppVersion, BuildConfig.VERSION_NAME));
    }

    @OnClick(R.id.layoutUserInfo)
    public void onUserInfoClick() {
        navigateTo(AccountActivity.class);
        closeDrawer();
    }

    @OnClick(R.id.drawerButtonSettings)
    public void drawerButtonSettings() {
        closeDrawer();
        navigateTo(SettingsActivity.class);
    }

    @OnClick(R.id.drawerButtonShowLog)
    public void drawerButtonShowLog() {
        navigateTo(LogActivity.class);
    }

    @OnClick(R.id.drawerButtonReportBug)
    public void drawerButtonReportBug() {
        closeDrawer();
        navigateTo(ReportBugActivity.class);
    }

    @OnClick(R.id.drawerButtonAccount)
    public void drawerButtonAccount() {
        navigateTo(AccountActivity.class);
        closeDrawer();
    }

    @OnClick(R.id.drawerButtonHelp)
    public void drawerButtonHelp() {
        openUrl("https://protonvpn.com/support");
    }

    @OnClick(R.id.drawerButtonLogout)
    public void drawerButtonLogout() {
        if (vpnStateMonitor.isConnected()) {
            new MaterialDialog.Builder(this).theme(Theme.DARK)
                .title(R.string.warning)
                .content(R.string.logoutDescription)
                .positiveText(R.string.ok)
                .onPositive((dialog, which) -> logout())
                .negativeText(R.string.cancel)
                .show();
        }
        else {
            logout();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent.getBooleanExtra("OpenStatus", false)) {
            fragment.openBottomSheet();
        }
        super.onNewIntent(intent);
    }

    @Subscribe
    public void onForcedLogout(ForcedLogout event) {
        logout();
    }

    @Subscribe
    public void onConnectToServer(ConnectToServer server) {
        if (server.getServer() == null) {
            vpnStateMonitor.disconnect();
        }
        else {
            onConnect(Profile.getTemptProfile(server.getServer(), serverManager));
        }
    }

    @Subscribe
    public void onConnectToProfile(@NotNull ConnectToProfile profile) {
        onConnect(profile.getProfile());
    }

    public void logout() {
        userData.logout();
        serverManager.clearCache();
        api.logout(result ->
            Log.d(result.isSuccess() ? "Logout successful" : "Logout api call failed"));
        vpnStateMonitor.disconnect();
        finish();
        navigateTo(LoginActivity.class);
    }

    private void initQuickConnectFab() {
        fabQuickConnect.removeAllMenuButtons();
        fabQuickConnect.setMenuButtonColorNormalResId(
            vpnStateMonitor.isConnected() ? R.color.colorAccent : R.color.darkGrey);
        fabQuickConnect.getMenuIconView().setImageResource(R.drawable.ic_proton);
        fabQuickConnect.setOnMenuButtonClickListener(view -> {
            if (fabQuickConnect.isOpened()) {
                fabQuickConnect.close(true);
                fabQuickConnect.setMenuButtonColorNormalResId(
                    vpnStateMonitor.isConnected() ? R.color.colorAccent : R.color.darkGrey);
            }
            else {
                if (!vpnStateMonitor.isConnected()) {
                    Profile profile = serverManager.getDefaultConnection();
                    if (profile != null) {
                        onConnectToProfile(new ConnectToProfile(profile));
                    }
                }
                else {
                    vpnStateMonitor.disconnect();
                }

                if (!vpnStateMonitor.isConnected()) {
                    Storage.saveBoolean(OnboardingPreferences.FLOATING_BUTTON_USED, true);
                    Storage.saveBoolean(OnboardingPreferences.FLOATINGACTION_DIALOG, true);
                }
            }
        });
        fabQuickConnect.setOnMenuButtonLongClickListener(view -> {
            if (!fabQuickConnect.isOpened() && OnboardingPreferences.wasFloatingButtonUsed()) {
                fabQuickConnect.open(true);
                fabQuickConnect.setMenuButtonColorNormalResId(R.color.darkGrey);
            }
            return true;
        });
        fabQuickConnect.setClosedOnTouchOutside(true);

        if (serverManager.getSavedProfiles().size() >= 6) {
            addActionButtonToFab(fabQuickConnect, Color.parseColor("#27272c"),
                getString(R.string.showAllProfiles), R.drawable.ic_zoom_out, v -> {
                    viewPager.setCurrentItem(2);
                    fabQuickConnect.close(true);
                });
        }

        List<Profile> profileList = serverManager.getSavedProfiles();
        for (final Profile profile : ReversedList.reverse(
            profileList.subList(0, profileList.size() >= 6 ? 6 : profileList.size()))) {
            addActionButtonToFab(fabQuickConnect, Color.parseColor(profile.getColor()), profile.getDisplayName(getContext()),
                profile.getProfileIcon(), v -> {
                    onConnectToProfile(new ConnectToProfile(profile));
                    fabQuickConnect.close(true);
                });
        }

        if (vpnStateMonitor.isConnected()) {
            addActionButtonToFab(fabQuickConnect, Color.RED, getString(R.string.disconnect),
                R.drawable.ic_notification_disconnected, v -> {
                    vpnStateMonitor.disconnect();
                    fabQuickConnect.close(true);
                });
        }
        fabQuickConnect.onboardingAnimation();
    }

    private void addActionButtonToFab(FloatingActionMenu actionsMenu, int color, String name,
                                      @DrawableRes int icon, View.OnClickListener listener) {
        FloatingActionButton button = new FloatingActionButton(getContext());
        button.setColorNormal(color);
        button.setColorPressed(ContextCompat.getColor(getContext(), R.color.darkGrey));
        button.setButtonSize(1);
        button.setImageResource(icon);
        button.setLabelText(name);
        button.setOnClickListener(listener);
        actionsMenu.addMenuButton(button);
    }

    @OnCheckedChanged(R.id.switchSecureCore)
    public void switchSecureCore(final SwitchCompat switchCompat, final boolean isChecked) {
        if (vpnStateMonitor.isConnected() && (!vpnStateMonitor.isConnectingToSecureCore() && isChecked) || (
            vpnStateMonitor.isConnectingToSecureCore() && !isChecked)) {
            new MaterialDialog.Builder(getContext()).title(R.string.warning)
                .theme(Theme.DARK)
                .content(R.string.disconnectDialogDescription)
                .cancelable(false)
                .positiveText(R.string.yes)
                .negativeText(R.string.no)
                .negativeColor(ContextCompat.getColor(this, R.color.white))
                .onPositive((dialog, which) -> {
                    postSecureCoreSwitched(switchCompat);
                    vpnStateMonitor.disconnect();
                })
                .onNegative((materialDialog, dialogAction) -> switchCompat.setChecked(!isChecked))
                .show();
        }
        else {
            postSecureCoreSwitched(switchCompat);
        }
    }

    // FIXME: API needs to inform app of changes, not other way
    @Override
    public void onResume() {
        super.onResume();
        if (!userData.wasVpnInfoRecentlyUpdated(3)) {
            api.getVPNInfo(getNetworkFrameLayout(), this::checkTrialChange);
        }
    }

    private void checkTrialChange(VpnInfoResponse result) {
        if (!result.equals(userData.getVpnInfoResponse())) {
            serverListUpdater.getServersList(this);
            initDrawerView();
            shouldExpiredDialog(result);
            userData.setVpnInfoResponse(result);
            EventBus.post(new VpnStateChanged(userData.isSecureCoreEnabled()));
        }
    }

    private void postSecureCoreSwitched(final SwitchCompat switchCompat) {
        OnboardingDialogs.showDialogOnView(getContext(), switchCompat,
            getString(R.string.onboardingDialogSecureCoreTitle),
            getString(R.string.onboardingDialogSecureCoreDescription),
            OnboardingPreferences.SECURECORE_DIALOG);
        switchCompat.setBackgroundColor(ContextCompat.getColor(getContext(),
            switchCompat.isChecked() ? R.color.colorAccent : R.color.grey));
        userData.setSecureCoreEnabled(switchCompat.isChecked());
        EventBus.post(new VpnStateChanged(switchCompat.isChecked()));
    }

    @Override
    public LoaderUI getNetworkFrameLayout() {
        if (serverManager.isDownloadedAtLeastOnce()) {
            return minimizedLoader;
        }
        else {
            return getLoadingContainer();
        }
    }

    @Subscribe
    public void onConnectedToServer(@NonNull ConnectedToServer server) {
        if (server.getServer() != null) {
            userData.setSecureCoreEnabled(server.getServer().isSecureCoreServer());
        }
        EventBus.post(new VpnStateChanged(userData.isSecureCoreEnabled()));
        switchSecureCore.setChecked(userData.isSecureCoreEnabled());
        initQuickConnectFab();
    }

    @Subscribe
    public void onVpnStateChange(VpnStateChanged change) {
        switchSecureCore.setChecked(change.isSecureCoreEnabled());
    }

    private boolean shouldCloseFab() {
        if (fabQuickConnect.isOpened()) {
            fabQuickConnect.close(true);
            return true;
        }
        return false;
    }

    private boolean shouldCloseDrawer() {
        if (getDrawer().isDrawerOpen(GravityCompat.START)) {
            getDrawer().closeDrawer(GravityCompat.START, true);
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (!shouldCloseDrawer() && !fragment.collapseBottomSheet() && !shouldCloseFab()) {
            if (doubleBackToExitPressedOnce) {
                this.moveTaskToBack(true);
                return;
            }

            this.doubleBackToExitPressedOnce = true;
            Toast.makeText(getContext(), R.string.clickBackAgainLogout, Toast.LENGTH_LONG).show();

            new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
        }
    }

    @Override
    public void onConnect(@NotNull Profile profile) {
        boolean secureCoreServer = profile.getServer() != null && profile.getServer().isSecureCoreServer();
        boolean secureCoreOn = userData.isSecureCoreEnabled();
        if ((secureCoreServer && !secureCoreOn) || (!secureCoreServer && secureCoreOn)) {
            showSecureCoreChangeDialog(profile);
        }
        else {
            super.onConnect(profile);
        }
    }

    private void showSecureCoreChangeDialog(Profile profileToConnect) {
        String disconnect =
            vpnStateMonitor.isConnected() ? getString(R.string.currentConnectionWillBeLost) : ".";
        boolean isSecureCoreServer = profileToConnect.getServer().isSecureCoreServer();
        new MaterialDialog.Builder(this).title(R.string.warning)
            .theme(Theme.DARK)
            .content(HtmlTools.fromHtml(
                getString(isSecureCoreServer ? R.string.secureCoreSwitchOn : R.string.secureCoreSwitchOff,
                    disconnect)))
            .cancelable(false)
            .positiveText(R.string.yes)
            .negativeText(R.string.no)
            .negativeColor(ContextCompat.getColor(this, R.color.white))
            .onPositive((dialog, which) -> super.onConnect(profileToConnect))
            .show();
    }
}