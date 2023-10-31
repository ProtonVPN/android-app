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
package com.protonvpn.android.components;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import com.protonvpn.android.R;
import com.protonvpn.android.api.NetworkLoader;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.tv.IsTvCheck;
import com.protonvpn.android.ui.snackbar.DelegatedSnackManager;
import com.protonvpn.android.ui.snackbar.DelegatedSnackbarHelper;
import com.protonvpn.android.ui.snackbar.SnackbarHelper;
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegate;
import com.protonvpn.android.ui.vpn.VpnUiActivityDelegateMobile;
import com.protonvpn.android.utils.AndroidUtils;
import com.protonvpn.android.utils.ServerManager;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import butterknife.BindView;
import butterknife.ButterKnife;
import kotlin.Unit;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

public abstract class BaseActivity extends AppCompatActivity
        implements NetworkLoader, VpnUiDelegateProvider {

    @Nullable @BindView(R.id.loadingContainer) NetworkFrameLayout loadingContainer;
    @Nullable @BindView(R.id.layoutDrawer) protected DrawerLayout drawer;
    @Nullable @BindView(R.id.toolbar) protected Toolbar toolbar;
    @Nullable @BindView(R.id.navigationDrawer) View navigationDrawer;
    boolean isRegisteredForEvents = false;

    private VpnUiActivityDelegate vpnUiDelegate;

    private DelegatedSnackbarHelper snackbarHelper;
    @Inject public DelegatedSnackManager delegatedSnackManager;
    @Inject public IsTvCheck isTv;

    public void navigateTo(Class<? extends AppCompatActivity> className) {
        Intent intent = new Intent(this, className);
        ActivityCompat.startActivity(this, intent, null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkOrientation();

        vpnUiDelegate = new VpnUiActivityDelegateMobile(this, this::retryConnection);

        setContentView(AnnotationParser.getAnnotatedLayout(this));
        ButterKnife.bind(this);

        if (isRegisteredForEvents) {
            EventBus.getInstance().register(this);
        }
        snackbarHelper = new DelegatedSnackbarHelper(this, getContentView(), delegatedSnackManager);
    }

    public void checkOrientation() {
        setRequestedOrientation(
            getResources().getBoolean(R.bool.isTablet) || isTv.invoke(false) ?
                SCREEN_ORIENTATION_FULL_USER : SCREEN_ORIENTATION_PORTRAIT);
    }

    public NetworkFrameLayout getLoadingContainer() {
        if (loadingContainer == null) {
            throw new RuntimeException("No loadingContainer found");
        }
        return loadingContainer;
    }

    public void initToolbarWithUpEnabled() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    public void initDrawer() {
        toolbar.setNavigationIcon(R.drawable.ic_proton_hamburger);
        toolbar.setNavigationContentDescription(R.string.hamburgerMenu);
        toolbar.setNavigationOnClickListener(view -> toggleDrawer());
        setDrawerState(true, navigationDrawer);
    }

    public void closeDrawer() {
        getDrawer().closeDrawer(GravityCompat.START, false);
    }

    public void setDrawerState(boolean isEnabled, View view) {
        if (isEnabled) {
            getDrawer().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, view);
        }
        else {
            getDrawer().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, view);
        }
    }

    public DrawerLayout getDrawer() {
        if (drawer == null) {
            throw new RuntimeException("No drawerLayout found in this layout");
        }
        else {
            return drawer;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRegisteredForEvents) {
            EventBus.getInstance().unregister(this);
        }
    }

    public void registerForEvents() {
        isRegisteredForEvents = true;
    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public LoaderUI getNetworkFrameLayout() {
        return getLoadingContainer();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected Unit retryConnection(@NonNull Profile profile) {
        return Unit.INSTANCE;
    }

    public SnackbarHelper getSnackbarHelper() {
        return snackbarHelper;
    }

    private View getContentView() {
        return findViewById(android.R.id.content);
    }

    private void toggleDrawer() {
        if (getDrawer().isDrawerOpen(GravityCompat.START)) {
            getDrawer().closeDrawer(GravityCompat.START, true);
        } else {
            getDrawer().openDrawer(GravityCompat.START, true);
        }
    }

    public VpnUiActivityDelegate getVpnUiDelegate() {
        return vpnUiDelegate;
    }
}
