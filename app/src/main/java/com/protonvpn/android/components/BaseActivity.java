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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.protonvpn.android.R;
import com.protonvpn.android.api.NetworkLoader;
import com.protonvpn.android.bus.EventBus;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import butterknife.BindView;
import butterknife.ButterKnife;
import dagger.android.AndroidInjection;
import dagger.android.support.DaggerAppCompatActivity;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

public abstract class BaseActivity extends DaggerAppCompatActivity implements NetworkLoader {

    @Nullable @BindView(R.id.loadingContainer) NetworkFrameLayout loadingContainer;
    @Nullable @BindView(R.id.layoutDrawer) protected DrawerLayout drawer;
    @Nullable @BindView(R.id.toolbar) protected Toolbar toolbar;
    @Nullable @BindView(R.id.navigationDrawer) View navigationDrawer;
    boolean isRegisteredForEvents = false;
    protected ActionBarDrawerToggle toggle;

    public void navigateTo(Class className) {
        Intent intent = new Intent(this, className);
        ActivityCompat.startActivity(this, intent, null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkOrientation();
        setContentView(AnnotationParser.getAnnotatedLayout(this));
        ButterKnife.bind(this);
        AndroidInjection.inject(this);

        if (isRegisteredForEvents) {
            EventBus.getInstance().register(this);
        }
    }

    public void checkOrientation() {
        setRequestedOrientation(getResources().getBoolean(R.bool.isTablet) ? SCREEN_ORIENTATION_FULL_USER :
            SCREEN_ORIENTATION_PORTRAIT);
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
        toggle =
            new ActionBarDrawerToggle(this, drawer, toolbar, R.string.hamburgerMenu, R.string.hamburgerMenu);
        getDrawer().addDrawerListener(toggle);
        toggle.syncState();
        setDrawerState(true, navigationDrawer);
    }

    public void closeDrawer() {
        getDrawer().closeDrawer(Gravity.START, false);
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
}