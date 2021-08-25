/*
 * Copyright (c) 2021 Proton Technologies AG
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
package com.protonvpn.android.ui.home.vpn;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.protonvpn.android.R;
import com.protonvpn.android.appconfig.AppConfig;
import com.protonvpn.android.bus.ConnectedToServer;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.bus.TrafficUpdate;
import com.protonvpn.android.components.BaseFragment;
import com.protonvpn.android.components.CountryWithFlagsView;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.VPNException;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.ui.home.ServerListUpdater;
import com.protonvpn.android.utils.DebugUtils;
import com.protonvpn.android.utils.HtmlTools;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.TrafficMonitor;
import com.protonvpn.android.vpn.RetryInfo;
import com.protonvpn.android.vpn.VpnConnectionManager;
import com.protonvpn.android.vpn.VpnState;
import com.protonvpn.android.vpn.VpnStateMonitor;

import org.joda.time.Duration;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import butterknife.BindView;
import butterknife.OnClick;

@ContentLayout(R.layout.vpn_state_fragment)
public class VpnStateFragment extends BaseFragment {

    @BindView(R.id.layoutStatusHeader) View layoutStatusHeader;
    @BindView(R.id.imageExpand) ImageView imageExpand;
    @BindView(R.id.dividerTop) View dividerTop;
    @BindView(R.id.layoutBottomSheet) View layoutBottomSheet;

    @BindView(R.id.textNotConnectedStatus) TextView textNotConnectedStatus;
    @BindView(R.id.textNotConnectedSuggestion) TextView textNotConnectedSuggestion;
    @BindView(R.id.textConnectedTo) TextView textConnectedTo;
    @BindView(R.id.countryWithFlags) CountryWithFlagsView countryFlags;
    @BindView(R.id.textProfile) TextView textProfile;
    @BindView(R.id.textSessionTime) TextView textSessionTime;

    private View fab;

    @Inject ViewModelProvider.Factory viewModelFactory;
    @Inject ServerManager manager;
    @Inject UserData userData;
    @Inject AppConfig appConfig;
    @Inject VpnStateMonitor stateMonitor;
    @Inject VpnConnectionManager vpnConnectionManager;
    @Inject ServerListUpdater serverListUpdater;
    @Inject TrafficMonitor trafficMonitor;
    private BottomSheetBehavior<View> bottomSheetBehavior;

    @NonNull
    private final PeriodFormatter sessionTimeFormatter = createSessionTimeFormatter();
    @NonNull
    private final Observer<TrafficUpdate> sessionTimeObserver = this::updateSessionTime;

    // Some fragments handle more than one state.
    // Keep track of the currently attached fragment's class to avoid replacing the fragment with an identical
    // one when not necessary.
    @Nullable
    private Class<? extends Fragment> currentStateFragmentClass;

    private VpnStateViewModel viewModel;

    @OnClick(R.id.layoutStatusHeader)
    public void layoutCollapsedStatus() {
        if (bottomSheetBehavior != null) {
            changeBottomSheetState(bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = (new ViewModelProvider(this, viewModelFactory)).get(VpnStateViewModel.class);
    }

    @Override
    public void onViewCreated() {
        registerForEvents();
        initPeekHeightLayoutListener();

        stateMonitor.getStatusLiveData().observe(getViewLifecycleOwner(), state -> updateView(false, state));
        viewModel.getEventCollapseBottomSheetLV().observe(getViewLifecycleOwner(), ignored -> collapseBottomSheet());
    }

    public boolean isBottomSheetExpanded() {
        return bottomSheetBehavior != null
            && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED;
    }

    private boolean isBottomSheetCollapsed() {
        return bottomSheetBehavior != null
            && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED;
    }

    public void initStatusLayout(final FloatingActionMenu floatingButton) {
        fab = floatingButton;
        bottomSheetBehavior = BottomSheetBehavior.from(layoutBottomSheet);
        bottomSheetBehavior.setHideable(false);
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (isAdded()) {
                    updateSessionTimeObserver(stateMonitor.isConnected());
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                updateFabVisibility(stateMonitor.isConnected(), slideOffset);
                if (imageExpand != null) {
                    imageExpand.animate().rotation(180 * slideOffset).setDuration(0).start();
                }
                if (dividerTop != null) {
                    dividerTop.setVisibility(slideOffset == 1f ? View.GONE : View.VISIBLE);
                }
                textSessionTime.setAlpha(slideOffset);
            }
        });
    }

    public boolean collapseBottomSheet() {
        if (bottomSheetBehavior != null
            && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            changeBottomSheetState(false);
            return true;
        }
        return false;
    }

    public boolean openBottomSheet() {
        if (bottomSheetBehavior != null
            && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            changeBottomSheetState(true);
            return true;
        }
        return false;
    }

    private void initConnectingStateView(boolean fromSavedState) {
        changeStateFragment(VpnStateConnectingFragment.class);

        if (!fromSavedState) {
            changeBottomSheetState(true);
        }
    }

    private void updateNotConnectedView() {
        changeStateFragment(VpnStateNotConnectedFragment.class);
    }

    private void initConnectedStateView() {
        changeStateFragment(VpnStateConnectedFragment.class);
    }

    private void showNotConnectedHeaderState(@StringRes int statusText, @StringRes int suggestionText) {
        showNotConnectedHeaderState(getString(statusText), suggestionText);
    }

    private void showNotConnectedHeaderState(
           @NonNull CharSequence statusText, @StringRes int suggestionText) {
        textNotConnectedStatus.setText(statusText);
        if (suggestionText != 0)
            textNotConnectedSuggestion.setText(suggestionText);
        else
            textNotConnectedSuggestion.setText(null);

        textNotConnectedStatus.setVisibility(View.VISIBLE);
        textNotConnectedSuggestion.setVisibility(View.VISIBLE);
        // Set them to INVISIBLE to avoid header changing size.
        textConnectedTo.setVisibility(View.INVISIBLE);
        countryFlags.setVisibility(View.INVISIBLE);
        textProfile.setVisibility(View.INVISIBLE);
    }

    private void showConnectedOrConnectingHeaderState(
            @StringRes int statusText, @Nullable Server country, @NonNull Profile profile) {
        textConnectedTo.setText(statusText);
        if (country != null) {
            countryFlags.setVisibility(View.VISIBLE);
            countryFlags.setCountry(country);
        } else {
            textProfile.setVisibility(View.VISIBLE);
            textProfile.setText(profile.getDisplayName(requireContext()));
            Drawable profileDot = null;
            if (profile.getProfileColor() != null) {
                profileDot = ContextCompat.getDrawable(requireContext(), R.drawable.ic_profile_custom_small);
                profileDot.setTint(ContextCompat.getColor(requireContext(), profile.getProfileColor().getColorRes()));
            } else {
                DebugUtils.INSTANCE.debugAssert("Profile with no color", () -> true);
            }
            textProfile.setCompoundDrawablesRelativeWithIntrinsicBounds(profileDot, null, null, null);
        }

        textConnectedTo.setVisibility(View.VISIBLE);
        textNotConnectedStatus.setVisibility(View.INVISIBLE);
        textNotConnectedSuggestion.setVisibility(View.INVISIBLE);
    }

    private void changeBottomSheetState(boolean expand) {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(
                expand ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private void updateView(boolean fromSavedState, @NonNull VpnStateMonitor.Status vpnState) {
        Profile profile = vpnState.getProfile();

        Server server = null;
        if (profile == null || profile.isPreBakedProfile() || profile.getDisplayName(requireContext()).isEmpty()) {
            server = stateMonitor.getConnectingToServer();
        }
        if (isAdded()) {
            VpnState state = vpnState.getState();
            //TODO: migrate to kotlin to use "when" here
            if (state instanceof VpnState.Error) {
                reportError(((VpnState.Error) vpnState.getState()));
            }
            else if (VpnState.Disabled.INSTANCE.equals(state)) {
                checkDisconnectFromOutside();
                showNotConnectedHeaderState(R.string.loaderNotConnected, R.string.loaderNotConnectedSuggestion);
                updateNotConnectedView();
            }
            else if (VpnState.CheckingAvailability.INSTANCE.equals(state)
                || VpnState.ScanningPorts.INSTANCE.equals(state)) {
                showNotConnectedHeaderState(
                    R.string.loaderCheckingAvailability, R.string.loaderCheckingAvailabilitySuggestion);
                initConnectingStateView(fromSavedState);
            }
            else if (VpnState.Connecting.INSTANCE.equals(state)) {
                showConnectedOrConnectingHeaderState(R.string.loaderConnectingToLabel, server, profile);
                initConnectingStateView(fromSavedState);
            }
            else if (VpnState.WaitingForNetwork.INSTANCE.equals(state)) {
                showNotConnectedHeaderState(
                    R.string.loaderReconnectNoNetwork, R.string.loaderReconnectNoNetworkSuggestion);
                initConnectingStateView(fromSavedState);
            }
            else if (VpnState.Connected.INSTANCE.equals(state)) {
                showConnectedOrConnectingHeaderState(R.string.loaderConnectedToLabel, server, profile);
                initConnectedStateView();
            }
            else if (VpnState.Disconnecting.INSTANCE.equals(state)) {
                showNotConnectedHeaderState(
                    R.string.loaderDisconnecting, R.string.loaderDisconnectingSuggestion);
            }
            else {
                updateNotConnectedView();
            }

            boolean isConnected = VpnState.Connected.INSTANCE.equals(state);
            updateFabVisibility(isConnected);
            updateSessionTimeObserver(isConnected);
        }
    }

    private void checkDisconnectFromOutside() {
        if (stateMonitor.isConnected()) {
            EventBus.getInstance().post(new ConnectedToServer(null));
        }
    }

    private boolean reportError(VpnState.Error error) {
        Log.e("report error: " + error.toString());
        switch (error.getType()) {
            case AUTH_FAILED:
                showAuthError(R.string.error_auth_failed);
                break;
            case PEER_AUTH_FAILED:
            case UNREACHABLE:
                showErrorState();
                break;
            case MAX_SESSIONS:
                Log.exception(new VPNException("Maximum number of sessions used"));
                break;
            case UNPAID:
                showAuthError(HtmlTools.fromHtml(getString(R.string.errorUserDelinquent)));
                Log.exception(new VPNException("Overdue payment"));
                break;
            case MULTI_USER_PERMISSION:
                vpnConnectionManager.disconnect();
                showAuthError(R.string.errorTunMultiUserPermission);
                Log.exception(new VPNException("Dual-apps permission error"));
                break;
            case LOCAL_AGENT_ERROR:
                vpnConnectionManager.disconnect();
                showAuthError(getString(R.string.errorWireguardWithPlaceholder, error.getDescription()));
                Log.exception(new VPNException("Wireguard error: " + error.getDescription()));
                break;
            default:
                showErrorState();
                break;
        }

        return true;
    }

    private void showAuthError(@StringRes int stringRes) {
        showAuthError(getString(stringRes));
    }

    private void showAuthError(CharSequence content) {
        new MaterialDialog.Builder(requireActivity()).theme(Theme.DARK)
            .title(R.string.dialogTitleAttention)
            .content(content)
            .cancelable(false)
            .negativeText(R.string.close)
            .onNegative((dialog, which) -> vpnConnectionManager.disconnect())
            .show();
    }

    private void showErrorState() {
        changeStateFragment(VpnStateErrorFragment.class);

        RetryInfo retryInfo = vpnConnectionManager.getRetryInfo();
        if (retryInfo != null) {
            // TODO: Keep updating the text. This will require moving some logic from the fragment to the
            //  ViewModel.
            int retryIn = retryInfo.getRetryInSeconds();
            showNotConnectedHeaderState(
                getResources().getQuantityString(R.plurals.retry_in, retryIn, retryIn),
                R.string.loaderReconnectingSuggestion);
        } else {
            showNotConnectedHeaderState(R.string.loaderReconnecting, R.string.loaderNotConnectedSuggestion);
        }
    }

    private void updateFabVisibility(boolean isConnected) {
        if (isBottomSheetExpanded()) updateFabVisibility(isConnected, 1f);
        else if (isBottomSheetCollapsed()) updateFabVisibility(isConnected, 0f);
        // Otherwise leave it, the BottomSheetCallback.onSlide will update it soon.
    }

    private void updateFabVisibility(boolean isConnected, float slideOffset) {
        if (isConnected) {
            fab.setAlpha(1);
            fab.setVisibility(View.VISIBLE);
        } else {
            fab.setAlpha(1 - slideOffset);
            fab.setVisibility(slideOffset == 1 ? View.GONE : View.VISIBLE);
        }
    }

    private void updateSessionTimeObserver(boolean isConnected) {
        if (isConnected && !isBottomSheetCollapsed()) {
            viewModel.getTrafficStatus().observe(getViewLifecycleOwner(), sessionTimeObserver);
            textSessionTime.setVisibility(View.VISIBLE);
        } else {
            viewModel.getTrafficStatus().removeObserver(sessionTimeObserver);
            textSessionTime.setVisibility(View.GONE);
        }
    }

    private void updateSessionTime(@Nullable TrafficUpdate trafficUpdate) {
        final Period period = (trafficUpdate != null)
            ? Duration.standardSeconds(trafficUpdate.getSessionTimeSeconds()).toPeriod()
            : Period.ZERO;
        textSessionTime.setText(sessionTimeFormatter.print(period));
    }

    private void changeStateFragment(@NonNull Class<? extends Fragment> fragmentClass) {
        if (!fragmentClass.equals(currentStateFragmentClass)) {
            currentStateFragmentClass = fragmentClass;
            try {
                getChildFragmentManager().beginTransaction()
                    .replace(R.id.fragmentState, fragmentClass.newInstance())
                    .commitNow();
            } catch (IllegalAccessException | java.lang.InstantiationException e) {
                throw new IllegalStateException("Unable to create fragment", e);
            }
        }
    }

    private void initPeekHeightLayoutListener() {
        layoutStatusHeader.addOnLayoutChangeListener(
            (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                int oldHeight = oldBottom - oldTop;
                int height = bottom - top;
                if (bottomSheetBehavior != null && height > 0 && oldHeight != height) {
                    bottomSheetBehavior.setPeekHeight(height, oldHeight != 0);
                }
            });
    }

    @NonNull
    private PeriodFormatter createSessionTimeFormatter() {
        return (new PeriodFormatterBuilder())
            .printZeroAlways()
            .minimumPrintedDigits(2)
            .appendHours()
            .appendLiteral(":")
            .appendMinutes()
            .appendLiteral(":")
            .appendSeconds()
            .toFormatter();
    }
}
