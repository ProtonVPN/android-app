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
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.protonvpn.android.BuildConfig;
import com.protonvpn.android.R;
import com.protonvpn.android.auth.usecase.CurrentUserKt;
import com.protonvpn.android.bus.ConnectToProfile;
import com.protonvpn.android.bus.ConnectToServer;
import com.protonvpn.android.bus.ConnectedToServer;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.bus.VpnStateChanged;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.LoaderUI;
import com.protonvpn.android.components.MinimizedNetworkLayout;
import com.protonvpn.android.components.NotificationHelper;
import com.protonvpn.android.components.ProtonActionMenu;
import com.protonvpn.android.components.ReversedList;
import com.protonvpn.android.components.SwitchEx;
import com.protonvpn.android.components.ViewPagerAdapter;
import com.protonvpn.android.logging.LogCategory;
import com.protonvpn.android.logging.LogEventsKt;
import com.protonvpn.android.logging.LogExtensionsKt;
import com.protonvpn.android.logging.ProtonLogger;
import com.protonvpn.android.models.config.Setting;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.ui.CommonDialogsKt;
import com.protonvpn.android.ui.drawer.AccountActivity;
import com.protonvpn.android.ui.drawer.LogActivity;
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity;
import com.protonvpn.android.ui.home.countries.CountryListFragment;
import com.protonvpn.android.ui.home.map.MapFragment;
import com.protonvpn.android.ui.home.profiles.HomeViewModel;
import com.protonvpn.android.ui.home.profiles.ProfilesFragment;
import com.protonvpn.android.ui.home.vpn.SwitchDialogActivity;
import com.protonvpn.android.ui.home.vpn.VpnStateFragment;
import com.protonvpn.android.ui.onboarding.OnboardingActivity;
import com.protonvpn.android.ui.onboarding.OnboardingDialogs;
import com.protonvpn.android.ui.onboarding.OnboardingPreferences;
import com.protonvpn.android.ui.onboarding.TooltipManager;
import com.protonvpn.android.ui.promooffers.PromoOfferNotificationHelper;
import com.protonvpn.android.ui.promooffers.PromoOfferNotificationViewModel;
import com.protonvpn.android.ui.settings.SettingsActivity;
import com.protonvpn.android.utils.AnimationTools;
import com.protonvpn.android.utils.Constants;
import com.protonvpn.android.utils.HtmlTools;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.utils.UserPlanManager;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.squareup.otto.Subscribe;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;
import butterknife.BindView;
import butterknife.OnCheckedChanged;
import butterknife.OnClick;
import dagger.hilt.android.AndroidEntryPoint;

import kotlin.Unit;

import static com.protonvpn.android.utils.AndroidUtilsKt.openProtonUrl;

@AndroidEntryPoint
@ContentLayout(R.layout.activity_home)
public class HomeActivity extends PoolingActivity {

    @BindView(R.id.viewPager) ViewPager viewPager;
    @BindView(R.id.tabs) TabLayout tabs;
    @BindView(R.id.fabQuickConnect) ProtonActionMenu fabQuickConnect;
    @BindView(R.id.coordinator) CoordinatorLayout coordinator;
    @BindView(R.id.textUserName) TextView textUser;
    @BindView(R.id.textUserInitials) TextView textUserInitials;
    @BindView(R.id.textUserEmail) TextView textUserEmail;
    @BindView(R.id.textVersion) TextView textVersion;
    @BindView(R.id.minimizedLoader) MinimizedNetworkLayout minimizedLoader;
    @BindView(R.id.imageNotification) ImageView imageNotification;
    @BindView(R.id.switchSecureCore) SwitchEx switchSecureCore;

    VpnStateFragment fragment;
    @Inject ServerManager serverManager;
    @Inject UserData userData;
    @Inject VpnStateMonitor vpnStateMonitor;
    @Inject ServerListUpdater serverListUpdater;
    @Inject NotificationHelper notificationHelper;

    private HomeViewModel viewModel;

    private final TooltipManager tooltipManager = new TooltipManager(this);

    private final ActivityResultLauncher<Unit> secureCoreSpeedInfoDialog =
            registerForActivityResult(
                    SecureCoreSpeedInfoActivity.createContract(),
                    this::onSecureCoreSpeedInfoDialogResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerForEvents();
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        getLifecycle().addObserver(viewModel);

        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(HtmlTools.fromHtml(getString(R.string.toolbar_app_title)));
        initDrawer();
        initDrawerView();
        fragment = (VpnStateFragment) getSupportFragmentManager().findFragmentById(R.id.vpnStatusBar);
        initSecureCoreSwitch();
        initSnackbarHelper();
        if (serverManager.isDownloadedAtLeastOnce() || serverManager.isOutdated()) {
            initLayout();
        }
        else {
            minimizedLoader.switchToEmpty();
        }
        if (canShowPopups()) {
            initOnboarding();
        }

        checkForOnboarding();
        serverManager.getServerListVersionLiveData().observe(this, (ignored) -> {
            checkForOnboarding();
            if (canShowPopups()) {
                initOnboarding();
                notificationHelper.cancelInformationNotification(Constants.NOTIFICATION_GUESTHOLE_ID);
                EventBus.post(new VpnStateChanged(userData.getSecureCoreEnabled()));
            }
            else {
                initLayout();
            }
        });

        serverManager.getProfilesLiveData().observe(this, (Unit) -> initQuickConnectFab());

        viewModel.getLogoutEvent().observe(this, account -> {
            // Result CANCELLED will close MobileMainActivity
            setResult(RESULT_OK);
            finish();
        });

        viewModel.collectPlanChange(this, changes -> {
            onPlanChanged(changes);
            return Unit.INSTANCE;
        });
        new PromoOfferNotificationHelper(this, imageNotification,
            new ViewModelProvider(this).get(PromoOfferNotificationViewModel.class));

        serverListUpdater.startSchedule(getLifecycle(), this);
    }

    private void checkForOnboarding() {
        viewModel.handleUserOnboarding(() -> {
            navigateTo(OnboardingActivity.class);
            return Unit.INSTANCE;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_info) {
            navigateTo(InformationActivity.class);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        ProtonLogger.INSTANCE.logCustom(LogCategory.APP, "HomeActivity: onTrimMemory level " + level);
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

    private void initSecureCoreSwitch() {
        switchSecureCore.setChecked(userData.getSecureCoreEnabled());
        switchSecureCore.setSwitchClickInterceptor((switchView) -> {
            if (!switchView.isChecked() && !viewModel.hasAccessToSecureCore()) {
                showSecureCoreUpgradeDialog();
            } else if (!switchView.isChecked()) {
                secureCoreSpeedInfoDialog.launch(Unit.INSTANCE);
            } else {
                toggleSecureCore();
            }
            return true;
        });
    }

    private void toggleSecureCore() {
        LogExtensionsKt.logUiSettingChange(ProtonLogger.INSTANCE, Setting.SECURE_CORE, "main screen");
        if (vpnStateMonitor.isConnected()
                && vpnStateMonitor.isConnectingToSecureCore() == switchSecureCore.isChecked()) {
            switchSecureCore.toggle();
            postSecureCoreSwitched(switchSecureCore);
            viewModel.reconnectToSameCountry("user toggled SC switch", newProfile -> {
                onConnect(newProfile, "Secure Core switch");
                return Unit.INSTANCE;
            });
        } else {
            switchSecureCore.toggle();
            postSecureCoreSwitched(switchSecureCore);
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
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                fragment.collapseBottomSheet();
                if (tab.isSelected()) {
                    View tabView = ((LinearLayout) tabs.getChildAt(0)).getChildAt(tab.getPosition());
                    if (getString(R.string.tabsMap).equals(tab.getText().toString())
                            && !isBottomSheetExpanded()) {
                        OnboardingDialogs.showDialogOnView(tooltipManager, tabView, tabView, getString(R.string.tabsMap),
                                getString(R.string.onboardingDialogMapView), OnboardingPreferences.MAPVIEW_DIALOG);
                    }
                    if (getString(R.string.tabsProfiles).equals(tab.getText().toString())
                            && !isBottomSheetExpanded() && OnboardingPreferences.wasFloatingButtonUsed()) {
                        OnboardingDialogs.showDialogOnView(tooltipManager, tabView, tabView,
                                getString(R.string.tabsProfiles), getString(R.string.onboardingDialogProfiles),
                                OnboardingPreferences.PROFILES_DIALOG);
                    }
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                fragment.collapseBottomSheet();
            }
        });

        AnimationTools.addScaleAnimationToMenuIcon(fabQuickConnect);
        initStatusBar();
        fabQuickConnect.setVisibility(View.VISIBLE);
        initQuickConnectFab();
        initFullScreenNotification(getIntent());
    }

    private void initFullScreenNotification(Intent newIntent) {
        if (newIntent.getSerializableExtra(SwitchDialogActivity.EXTRA_NOTIFICATION_DETAILS) != null) {
            Intent intent = new Intent(this, SwitchDialogActivity.class);
            intent.putExtras(newIntent);
            startActivity(intent);
        }
    }

    private void initOnboarding() {
        OnboardingDialogs.showDialogOnFab(tooltipManager, fabQuickConnect, getString(R.string.onboardingFAB),
            getString(R.string.onboardingFABDescription), OnboardingPreferences.FLOATINGACTION_DIALOG);
    }

    private void initDrawerView() {
        textVersion.setText(getString(R.string.drawerAppVersion, BuildConfig.VERSION_NAME));
        viewModel.getUserLiveData().observe(this, (user) -> {
            if (user != null) {
                String userName = CurrentUserKt.uiName(user);
                textUser.setText(userName);
                textUserInitials.setText(getInitials(userName));
                textUserEmail.setText(user.getEmail());
            }
        });
    }

    private void initSnackbarHelper() {
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            if (fabQuickConnect.getVisibility() == View.VISIBLE) {
                getSnackbarHelper().setAnchorView(fabQuickConnect.getActionButton());
            } else {
                getSnackbarHelper().setAnchorView(null);
            }
        };
        fabQuickConnect.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        listener.onGlobalLayout();
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
        navigateTo(DynamicReportActivity.class);
    }

    @OnClick(R.id.drawerButtonAccount)
    public void drawerButtonAccount() {
        navigateTo(AccountActivity.class);
        closeDrawer();
    }

    @OnClick(R.id.drawerButtonHelp)
    public void drawerButtonHelp() {
        openProtonUrl(this, "https://protonvpn.com/support");
    }

    @OnClick(R.id.drawerButtonLogout)
    public void drawerButtonLogout() {
        if (vpnStateMonitor.isConnected()) {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logoutConfirmDialogTitle)
                .setMessage(R.string.logoutConfirmDialogMessage)
                .setPositiveButton(R.string.logoutConfirmDialogButton, (dialog, which) -> viewModel.logout())
                .setNegativeButton(R.string.cancel, null)
                .show();
        }
        else {
            viewModel.logout();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent.getBooleanExtra("OpenStatus", false)) {
            fragment.openBottomSheet();
        }
        initFullScreenNotification(intent);
        super.onNewIntent(intent);
    }

    @Subscribe
    public void onConnectToServer(ConnectToServer connectTo) {
        if (connectTo.getServer() == null) {
            disconnect(connectTo.getUiElement());
        } else {
            Server server = connectTo.getServer();
            onConnect(connectTo.getUiElement(), Profile.getTempProfile(server, serverManager));
        }
    }

    @Subscribe
    public void onConnectToProfile(@NotNull ConnectToProfile event) {
        if (event.getProfile() == null) {
            disconnect(event.getUiElement());
        } else {
            onConnect(event.getUiElement(), event.getProfile());
        }
    }

    @Override
    protected Unit retryConnection(@NonNull Profile profile) {
        onConnect(profile, "retry after missing vpn permission");
        return Unit.INSTANCE;
    }

    private void initQuickConnectFab() {
        fabQuickConnect.removeAllMenuButtons();
        ImageView menuIcon = fabQuickConnect.getMenuIconView();
        menuIcon.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_proton));
        updateFabColors(fabQuickConnect, vpnStateMonitor.isConnected());
        fabQuickConnect.setOnMenuButtonClickListener(view -> {
            if (fabQuickConnect.isOpened()) {
                fabQuickConnect.close(true);
            }
            else {
                if (!vpnStateMonitor.isConnected()) {
                    connectToDefaultProfile();
                } else {
                    disconnect("quick connect");
                    fragment.collapseBottomSheet();
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
            }
            return true;
        });
        fabQuickConnect.setOpenListener(new ProtonActionMenu.Listener() {
            @Override
            public void onOpening() {
                updateFabColors(fabQuickConnect, false);
            }

            @Override
            public void onClosing() {
                updateFabColors(fabQuickConnect, vpnStateMonitor.isConnected());
            }
        });
        fabQuickConnect.setClosedOnTouchOutside(true);

        if (serverManager.getSavedProfiles().size() >= 6) {
            addActionButtonToFab(fabQuickConnect, null, null,
                getString(R.string.showAllProfiles), R.drawable.ic_zoom_out, v -> {
                    viewPager.setCurrentItem(2);
                    fabQuickConnect.close(true);
                });
        }

        List<Profile> profileList = new ArrayList<>(serverManager.getSavedProfiles());
        for (final Profile profile : ReversedList.reverse(
            profileList.subList(0, profileList.size() >= 6 ? 6 : profileList.size()))) {
            addActionButtonToFab(
                fabQuickConnect,
                null,
                profile.getProfileColor() != null
                    ? ContextCompat.getColor(getContext(), profile.getProfileColor().getColorRes())
                    : null,
                profile.getDisplayName(getContext()),
                profile.getProfileSpecialIcon() != null ? profile.getProfileSpecialIcon() : R.drawable.ic_profile_custom_fab,
                v -> {
                    onConnectToProfile(new ConnectToProfile("quick connect menu", profile));
                    fabQuickConnect.close(true);
                });
        }

        if (vpnStateMonitor.isConnected()) {
            addActionButtonToFab(
                fabQuickConnect,
                MaterialColors.getColor(fabQuickConnect, R.attr.proton_notification_error),
                MaterialColors.getColor(fabQuickConnect, R.attr.colorOnPrimary),
                getString(R.string.disconnect),
                R.drawable.ic_power_off,
                v -> {
                    disconnect("quick connect menu");
                    fabQuickConnect.close(true);
                });
        } else {
            addActionButtonToFab(
                fabQuickConnect,
                MaterialColors.getColor(fabQuickConnect, R.attr.brand_norm),
                MaterialColors.getColor(fabQuickConnect, R.attr.colorOnPrimary),
                getString(R.string.quickConnect),
                R.drawable.ic_power_off,
                v -> {
                    connectToDefaultProfile();
                    fabQuickConnect.close(true);
                });
        }
        fabQuickConnect.onboardingAnimation();
    }

    private void addActionButtonToFab(
            @NonNull FloatingActionMenu actionsMenu, @Nullable Integer bgColorOverride,
            @Nullable Integer iconColorOverride, @NonNull String name, @DrawableRes int icon,
            @NonNull View.OnClickListener listener) {
        FloatingActionButton button = new FloatingActionButton(getContext());
        int buttonColor = bgColorOverride == null
            ? MaterialColors.getColor(button, R.attr.proton_interaction_weak)
            : bgColorOverride;
        button.setColorNormal(buttonColor);
        button.setColorPressed(buttonColor);
        button.setButtonSize(1);
        int iconColor = iconColorOverride == null
            ? MaterialColors.getColor(button, R.attr.proton_icon_norm)
            : iconColorOverride;
        // FloatingActionButton is an ImageView but has custom drawing implementation that breaks image
        // tinting.
        Drawable iconDrawable = ContextCompat.getDrawable(getContext(), icon);
        if (iconDrawable != null) {
            iconDrawable = iconDrawable.mutate();
            iconDrawable.setTint(iconColor);
            button.setImageDrawable(iconDrawable);
        }
        button.setLabelText(name);
        button.setOnClickListener(listener);
        actionsMenu.addMenuButton(button);
    }

    private void connectToDefaultProfile() {
        Profile profile = serverManager.getDefaultConnection();
        onConnectToProfile(new ConnectToProfile("quick connect", profile));
    }

    private void updateFabColors(@NonNull FloatingActionMenu fab, boolean accented) {
        @ColorInt
        int bgColor = accented
            ? MaterialColors.getColor(fab, R.attr.brand_norm)
            : ContextCompat.getColor(this, R.color.shade_100);
        @ColorRes
        int rippleColorRes = accented ? R.color.mtrl_btn_ripple_color : R.color.fab_disconnected_ripple;
        int iconColorAttr = accented ? R.attr.colorOnPrimary : R.attr.proton_icon_inverted;
        fab.setMenuButtonColorNormal(bgColor);
        fab.setMenuButtonColorPressed(bgColor);
        fab.setMenuButtonColorRipple(ContextCompat.getColor(this, rippleColorRes));
        fab.getMenuIconView().setColorFilter(MaterialColors.getColor(fab, iconColorAttr));
    }

    @OnCheckedChanged(R.id.switchSecureCore)
    public void switchSecureCore(final SwitchCompat switchCompat, final boolean isChecked) {
        LogExtensionsKt.logUiSettingChange(ProtonLogger.INSTANCE, Setting.SECURE_CORE, "main screen");
        postSecureCoreSwitched(switchCompat);
    }

    private void onPlanChanged(UserPlanManager.InfoChange.PlanChange change) {
        switchSecureCore.setChecked(userData.getSecureCoreEnabled());
        if (change == UserPlanManager.InfoChange.PlanChange.TrialEnded.INSTANCE)
            showExpiredDialog();
        EventBus.post(new VpnStateChanged(userData.getSecureCoreEnabled()));
    }

    private void postSecureCoreSwitched(final SwitchCompat switchCompat) {
        OnboardingDialogs.showDialogOnView(tooltipManager, switchCompat, switchCompat,
            getString(R.string.onboardingDialogSecureCoreTitle),
            getString(R.string.onboardingDialogSecureCoreDescription),
            OnboardingPreferences.SECURECORE_DIALOG);
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
        EventBus.post(new VpnStateChanged(userData.getSecureCoreEnabled()));
        switchSecureCore.setChecked(userData.getSecureCoreEnabled());
        initQuickConnectFab();
    }

    @Subscribe
    public void onVpnStateChange(VpnStateChanged change) {
        switchSecureCore.setChecked(change.isSecureCoreEnabled());
    }

    public TooltipManager getTooltips() {
        return tooltipManager;
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
            super.onBackPressed();
        }
    }

    @Override
    public void onConnect(@NotNull Profile profile, @NonNull String connectionCauseLog) {
        boolean secureCoreServer = profile.getServer() != null && profile.getServer().isSecureCoreServer();
        if (secureCoreServer && !viewModel.hasAccessToSecureCore()) {
            showSecureCoreUpgradeDialog();
        } else {
            super.onConnect(profile, connectionCauseLog);
        }
    }

    private void disconnect(@NonNull String uiElement) {
        ProtonLogger.INSTANCE.log(LogEventsKt.UiDisconnect, uiElement);
        vpnConnectionManager.disconnect("user via " + uiElement);
    }

    private void onSecureCoreSpeedInfoDialogResult(boolean activateSc) {
        if (activateSc) toggleSecureCore();
    }

    @NonNull
    private String getInitials(@NonNull String login) {
        return login.substring(0, Math.min(1, login.length())).toUpperCase(Locale.US);
    }

    private boolean canShowPopups() {
        return serverManager.isDownloadedAtLeastOnce();
    }
}
