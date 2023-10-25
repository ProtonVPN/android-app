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

import static com.protonvpn.android.logging.LogEventsKt.UiReconnect;
import static com.protonvpn.android.utils.AndroidUtilsKt.openProtonUrl;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

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
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.LoaderUI;
import com.protonvpn.android.components.MinimizedNetworkLayout;
import com.protonvpn.android.components.ProtonActionMenu;
import com.protonvpn.android.components.ReversedList;
import com.protonvpn.android.components.SwitchEx;
import com.protonvpn.android.components.ViewPagerAdapter;
import com.protonvpn.android.logging.LogCategory;
import com.protonvpn.android.logging.LogEventsKt;
import com.protonvpn.android.logging.LogExtensionsKt;
import com.protonvpn.android.logging.ProtonLogger;
import com.protonvpn.android.logging.Setting;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.notifications.NotificationHelper;
import com.protonvpn.android.search.SearchResultsFragment;
import com.protonvpn.android.search.SearchViewModel;
import com.protonvpn.android.ui.account.AccountActivity;
import com.protonvpn.android.ui.drawer.LogActivity;
import com.protonvpn.android.ui.drawer.bugreport.DynamicReportActivity;
import com.protonvpn.android.ui.home.countries.CountryListFragment;
import com.protonvpn.android.ui.home.map.MapFragment;
import com.protonvpn.android.ui.home.profiles.HomeViewModel;
import com.protonvpn.android.ui.home.profiles.ProfilesFragment;
import com.protonvpn.android.ui.home.vpn.SwitchDialogActivity;
import com.protonvpn.android.ui.home.vpn.VpnActivity;
import com.protonvpn.android.ui.home.vpn.VpnStateFragment;
import com.protonvpn.android.ui.onboarding.OnboardingActivity;
import com.protonvpn.android.ui.onboarding.OnboardingPreferences;
import com.protonvpn.android.ui.onboarding.WhatsNewFreeActivity;
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity;
import com.protonvpn.android.ui.planupgrade.UpgradeOnboardingDialogActivity;
import com.protonvpn.android.ui.promooffers.PromoOfferNotificationHelper;
import com.protonvpn.android.ui.promooffers.PromoOfferNotificationViewModel;
import com.protonvpn.android.ui.settings.SettingsActivity;
import com.protonvpn.android.userstorage.ProfileManager;
import com.protonvpn.android.utils.AnimationTools;
import com.protonvpn.android.utils.Constants;
import com.protonvpn.android.utils.HtmlTools;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.Storage;
import com.protonvpn.android.vpn.ConnectTrigger;
import com.protonvpn.android.vpn.DisconnectTrigger;
import com.protonvpn.android.vpn.VpnStatusProviderUI;
import com.squareup.otto.Subscribe;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.OnClick;
import dagger.hilt.android.AndroidEntryPoint;
import kotlin.Unit;

@AndroidEntryPoint
@ContentLayout(R.layout.activity_home)
public class HomeActivity extends VpnActivity {

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
    @BindView(R.id.fragmentSearchResults) FragmentContainerView fragmentSearchResults;
    private MenuItem searchMenuItem;

    VpnStateFragment fragment;
    @Inject ServerManager serverManager;
    @Inject ProfileManager profileManager;
    @Inject VpnStatusProviderUI vpnStatusProviderUI;
    @Inject ServerListUpdater serverListUpdater;
    @Inject NotificationHelper notificationHelper;
    private HomeViewModel viewModel;
    private SearchViewModel searchViewModel;

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
        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(HtmlTools.fromHtml(getString(R.string.toolbar_app_title)));
        initDrawer();
        initDrawerView();
        fragment = (VpnStateFragment) getSupportFragmentManager().findFragmentById(R.id.vpnStatusBar);
        initSecureCoreSwitch();
        initSnackbarHelper();
        initLayout();

        serverManager.getServerListVersionLiveData().observe(this, (ignored) -> {
            if (serverManager.getHaveLoadedServersAlready())
                getLoadingContainer().switchToEmpty();
            else
                getLoadingContainer().switchToLoading();

            checkForOnboarding();
            if (canShowPopups()) {
                notificationHelper.cancelInformationNotification(Constants.NOTIFICATION_GUESTHOLE_ID);
            }
        });

        profileManager.getProfilesLiveData().observe(this, (Unit) -> initQuickConnectFab());

        viewModel.getLogoutEvent().observe(this, account -> {
            // Result CANCELLED will close MobileMainActivity
            setResult(RESULT_OK);
            finish();
        });

        viewModel.getConnectEvent().observe(this, (input) -> {
            Profile profile = input.getFirst();
            ConnectTrigger trigger = input.getSecond();
            ProtonLogger.INSTANCE.log(UiReconnect, trigger.getDescription());
            onConnect(profile, trigger);
        });

        viewModel.shouldShowWhatsNew().observe(this, (showDialog) -> {
            if (showDialog) {
                startActivity(new Intent(this, WhatsNewFreeActivity.class));
                viewModel.onWhatsNewShown();
            }
        });

        new PromoOfferNotificationHelper(this, imageNotification,
            new ViewModelProvider(this).get(PromoOfferNotificationViewModel.class));

        searchViewModel.getEventCloseLiveData().observe(this, isOpen -> searchMenuItem.collapseActionView());
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (viewModel.getShowIKEv2Migration()) {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ikev2_removed_dialog_title)
                .setMessage(R.string.ikev2_removed_dialog_message)
                .setPositiveButton(R.string.ok, (dialog, button) -> viewModel.setShowIKEv2Migration(false))
                .show();
        }
    }

    private void checkForOnboarding() {
        viewModel.handleUserOnboarding(() -> {
            navigateTo(OnboardingActivity.class);
            return Unit.INSTANCE;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        initSearchView(menu.findItem(R.id.action_search));
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_info) {
            ActivityCompat.startActivity(this,
                    InformationActivity.createIntent(this, InformationActivity.InfoType.generic),
                    null);
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
        switchSecureCore.setSwitchClickInterceptor((switchView) -> {
            if (!switchView.isChecked() && !viewModel.hasAccessToSecureCore()) {
                UpgradeDialogActivity.launchSecureCore(this);
            } else if (!switchView.isChecked()) {
                secureCoreSpeedInfoDialog.launch(Unit.INSTANCE);
            } else {
                toggleSecureCore();
            }
            return true;
        });
        viewModel.getSecureCoreLiveData().observe(
                this, isEnabled -> switchSecureCore.setChecked(isEnabled));
    }

    private void toggleSecureCore() {
        LogExtensionsKt.logUiSettingChange(ProtonLogger.INSTANCE, Setting.SECURE_CORE, "main screen");
        viewModel.toggleSecureCore(!switchSecureCore.isChecked());
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
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                fragment.collapseBottomSheet();
            }
        });

        initStatusBar();
        initQuickConnectFab();
        initFullScreenNotification(getIntent());

        vpnStatusProviderUI.isConnectedOrDisconnectedLiveData().observe(this, isConnected -> initQuickConnectFab());
    }

    private void initFullScreenNotification(Intent newIntent) {
        if (newIntent.getSerializableExtra(SwitchDialogActivity.EXTRA_NOTIFICATION_DETAILS) != null) {
            Intent intent = new Intent(this, SwitchDialogActivity.class);
            intent.putExtras(newIntent);
            startActivity(intent);
        }
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

    private void initSearchView(@NonNull MenuItem menuItem) {
        searchMenuItem = menuItem;
        SearchView searchView = (SearchView) menuItem.getActionView();
        searchView.setQueryHint(getString(R.string.server_search_hint));

        searchView.setOnSearchClickListener((view) -> {
            int searchHintRes = viewModel.getSecureCore() ?
                R.string.server_search_hint_secure_core :
                R.string.server_search_hint;
            searchView.setQueryHint(getString(searchHintRes));
        });

        // Restore searchView state after Activity recreated.
        if (fragmentSearchResults.getFragment() != null) {
            menuItem.expandActionView();
            searchView.setIconified(false);
            searchView.setQuery(searchViewModel.getCurrentQuery(), false);
        }

        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragmentSearchResults, new SearchResultsFragment())
                        .commitNow();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                Fragment searchFragment =
                        getSupportFragmentManager().findFragmentById(R.id.fragmentSearchResults);
                if (searchFragment != null)
                    getSupportFragmentManager().beginTransaction()
                            .remove(searchFragment)
                            .commitNow();
                return true;
            }
        });

        searchViewModel.getQueryFromRecents().observe(this, (recentQuery) -> {
            searchView.setQuery(recentQuery, true);
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // Nothing, respond only to text change.
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                searchViewModel.setQuery(query);
                return true;
            }
        });
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
        if (vpnStatusProviderUI.isConnected()) {
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
            disconnect(connectTo.getDisconnectTrigger());
        } else {
            Server server = connectTo.getServer();
            onConnect(Profile.getTempProfile(server), connectTo.getConnectTrigger());
        }
    }

    @Subscribe
    public void onConnectToProfile(@NotNull ConnectToProfile event) {
        if (event.getProfile() == null) {
            disconnect(event.getDisconnectTrigger());
        } else {
            onConnect(event.getProfile(), event.getConnectTrigger());
        }
    }

    @Override
    protected Unit retryConnection(@NonNull Profile profile) {
        // This trigger isn't correct but connecting here should be a very rare occurrence.
        onConnect(profile, new ConnectTrigger.Auto("retry after missing vpn permission"));
        return Unit.INSTANCE;
    }

    private void initQuickConnectFab() {
        fabQuickConnect.removeAllMenuButtons();
        fabQuickConnect.setMenuButtonColorNormal(ContextCompat.getColor(this, R.color.shade_100));
        fabQuickConnect.setMenuButtonColorPressed(ContextCompat.getColor(this, R.color.shade_100));
        fabQuickConnect.setMenuButtonColorRipple(ContextCompat.getColor(this, R.color.fab_ripple));
        @DrawableRes
        int iconRes = vpnStatusProviderUI.isConnected() ? R.drawable.ic_vpn_icon_colorful : R.drawable.ic_vpn_icon_grayscale;
        fabQuickConnect.getMenuIconView().setImageDrawable(AppCompatResources.getDrawable(this, iconRes));
        AnimationTools.setScaleAnimationToMenuIcon(fabQuickConnect, () -> vpnStatusProviderUI.isConnected());
        fabQuickConnect.setOnMenuButtonClickListener(view -> {
            if (fabQuickConnect.isOpened()) {
                fabQuickConnect.close(true);
            }
            else {
                if (!vpnStatusProviderUI.isConnected()) {
                    connectToDefaultProfile();
                } else {
                    disconnect(new DisconnectTrigger.QuickConnect("quick connect"));
                    fragment.collapseBottomSheet();
                }

                if (!vpnStatusProviderUI.isConnected()) {
                    Storage.saveBoolean(OnboardingPreferences.FLOATING_BUTTON_USED, true);
                }
            }
        });
        fabQuickConnect.setOnMenuButtonLongClickListener(view -> {
            if (!viewModel.isQuickConnectRestricted() && !fabQuickConnect.isOpened()) {
                fabQuickConnect.open(true);
            }
            return true;
        });
        fabQuickConnect.setClosedOnTouchOutside(true);

        if (profileManager.getSavedProfiles().size() >= 6) {
            addActionButtonToFab(fabQuickConnect, null, null,
                getString(R.string.showAllProfiles), R.drawable.ic_proton_three_dots_horizontal, v -> {
                    viewPager.setCurrentItem(2);
                    fabQuickConnect.close(true);
                });
        }

        List<Profile> profileList = new ArrayList<>(profileManager.getSavedProfiles());
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
                    onConnectToProfile(
                            new ConnectToProfile(
                                    profile,
                                    new ConnectTrigger.QuickConnect("quick connect menu"),
                                    new DisconnectTrigger.QuickConnect("quick connect menu")));
                    fabQuickConnect.close(true);
                });
        }

        if (vpnStatusProviderUI.isConnected()) {
            addActionButtonToFab(
                fabQuickConnect,
                MaterialColors.getColor(fabQuickConnect, R.attr.strong_red_color),
                null,
                getString(R.string.disconnect),
                R.drawable.ic_proton_power_off,
                v -> {
                    disconnect(new DisconnectTrigger.QuickConnect("quick connect menu"));
                    fabQuickConnect.close(true);
                });
        } else {
            addActionButtonToFab(
                fabQuickConnect,
                null,
                null,
                getString(R.string.quickConnect),
                R.drawable.ic_proton_power_off,
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
        onConnect(profile, new ConnectTrigger.QuickConnect("quick connect"));
    }

    @Override
    public LoaderUI getNetworkFrameLayout() {
        if (serverManager.getHaveLoadedServersAlready()) {
            return minimizedLoader;
        }
        else {
            return getLoadingContainer();
        }
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

    private void disconnect(@NonNull DisconnectTrigger trigger) {
        ProtonLogger.INSTANCE.log(LogEventsKt.UiDisconnect, trigger.getDescription());
        vpnConnectionManager.disconnect(trigger);
    }

    private void onSecureCoreSpeedInfoDialogResult(boolean activateSc) {
        if (activateSc) toggleSecureCore();
    }

    @NonNull
    private String getInitials(@NonNull String login) {
        return login.substring(0, Math.min(1, login.length())).toUpperCase(Locale.US);
    }

    private boolean canShowPopups() {
        return serverManager.getHaveLoadedServersAlready();
    }
}
