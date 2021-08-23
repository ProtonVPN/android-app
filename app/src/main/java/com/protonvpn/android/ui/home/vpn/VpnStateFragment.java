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

import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.color.MaterialColors;
import com.protonvpn.android.R;
import com.protonvpn.android.appconfig.AppConfig;
import com.protonvpn.android.bus.ConnectedToServer;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.components.BaseFragment;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.VPNException;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.ui.CenterImageSpan;
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

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import butterknife.BindView;
import butterknife.OnClick;

@ContentLayout(R.layout.vpn_state_fragment)
public class VpnStateFragment extends BaseFragment {

    @BindView(R.id.layoutStatusHeader) View layoutStatusHeader;
    @BindView(R.id.textConnectingTo) TextView textConnectingTo;
    @BindView(R.id.imageExpand) ImageView imageExpand;
    @BindView(R.id.dividerTop) View dividerTop;
    @BindView(R.id.layoutBottomSheet) View layoutBottomSheet;
    @BindView(R.id.progressBar) ProgressBar progressBar;

    @Inject ViewModelProvider.Factory viewModelFactory;
    @Inject ServerManager manager;
    @Inject UserData userData;
    @Inject AppConfig appConfig;
    @Inject VpnStateMonitor stateMonitor;
    @Inject VpnConnectionManager vpnConnectionManager;
    @Inject ServerListUpdater serverListUpdater;
    @Inject TrafficMonitor trafficMonitor;
    private BottomSheetBehavior<View> bottomSheetBehavior;

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

        stateMonitor.getStatusLiveData().observe(getViewLifecycleOwner(), state -> updateView(false, state));
        viewModel.getEventCollapseBottomSheetLV().observe(getViewLifecycleOwner(), ignored -> collapseBottomSheet());
    }

    public boolean isBottomSheetExpanded() {
        return bottomSheetBehavior != null
            && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED;
    }

    public void initStatusLayout(final FloatingActionMenu attachedButton) {
        bottomSheetBehavior = BottomSheetBehavior.from(layoutBottomSheet);
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                attachedButton.animate().alpha(1 - slideOffset).setDuration(0).start();
                attachedButton.setVisibility(slideOffset == 1 ? View.GONE : View.VISIBLE);
                if (imageExpand != null) {
                    imageExpand.animate().rotation(180 * slideOffset).setDuration(0).start();
                }
                if (dividerTop != null) {
                    dividerTop.setVisibility(slideOffset == 1f ? View.GONE : View.VISIBLE);
                }
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
        // isTest(): ugly but enables running UI tests on android 5/6 (which have a problem with this view)
        progressBar.setVisibility(DebugUtils.isTest(getActivity()) ? View.INVISIBLE : View.VISIBLE);

        layoutStatusHeader.setBackgroundColor(
            MaterialColors.getColor(layoutStatusHeader, R.attr.proton_background_secondary));
        textConnectingTo.setTextColor(
            MaterialColors.getColor(textConnectingTo, R.attr.proton_text_norm));

        changeStateFragment(VpnStateConnectingFragment.class);


        if (!fromSavedState) {
            changeBottomSheetState(true);
        }
    }

    private void updateNotConnectedView() {
        progressBar.setVisibility(View.GONE);

        layoutStatusHeader.setBackgroundColor(
            MaterialColors.getColor(layoutStatusHeader, R.attr.proton_background_secondary));
        imageExpand.setImageResource(R.drawable.ic_chevron_up);
        textConnectingTo.setTextColor(MaterialColors.getColor(textConnectingTo, R.attr.proton_text_norm));

        changeStateFragment(VpnStateNotConnectedFragment.class);
    }

    private void initConnectedStateView() {
        progressBar.setVisibility(View.GONE);

        textConnectingTo.setTextColor(MaterialColors.getColor(textConnectingTo, R.attr.proton_text_inverted));
        layoutStatusHeader.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.shade_100));

        changeStateFragment(VpnStateConnectedFragment.class);
    }

    private void changeBottomSheetState(boolean expand) {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(
                expand ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    public void updateView(boolean fromSavedState, @NonNull VpnStateMonitor.Status vpnState) {
        Profile profile = vpnState.getProfile();

        String serverName = "";
        if (profile != null) {
            serverName = (profile.isPreBakedProfile() || profile.getDisplayName(requireContext()).isEmpty())
                && stateMonitor.getConnectingToServer() != null ?
                stateMonitor.getConnectingToServer().getDisplayName() : profile.getDisplayName(requireContext());
        }
        if (isAdded()) {
            VpnState state = vpnState.getState();
            //TODO: migrate to kotlin to use "when" here
            if (state instanceof VpnState.Error) {
                reportError(((VpnState.Error) vpnState.getState()));
            }
            else if (VpnState.Disabled.INSTANCE.equals(state)) {
                checkDisconnectFromOutside();
                textConnectingTo.setText(R.string.loaderNotConnected);
                updateNotConnectedView();
            }
            else if (VpnState.CheckingAvailability.INSTANCE.equals(state)
                || VpnState.ScanningPorts.INSTANCE.equals(state)) {
                textConnectingTo.setText(R.string.loaderCheckingAvailability);
                initConnectingStateView(fromSavedState);
            }
            else if (VpnState.Connecting.INSTANCE.equals(state)) {
                textConnectingTo.setText(getStringWithServerName(R.string.loaderConnectingTo, serverName));
                initConnectingStateView(fromSavedState);
            }
            else if (VpnState.WaitingForNetwork.INSTANCE.equals(state)) {
                textConnectingTo.setText(R.string.loaderReconnectNoNetwork);
                initConnectingStateView(fromSavedState);
            }
            else if (VpnState.Connected.INSTANCE.equals(state)) {
                textConnectingTo.setText(getStringWithServerName(R.string.loaderConnectedTo, serverName));
                initConnectedStateView();
            }
            else if (VpnState.Disconnecting.INSTANCE.equals(state)) {
                textConnectingTo.setText(R.string.loaderDisconnecting);
                layoutStatusHeader.setBackgroundColor(
                    MaterialColors.getColor(layoutStatusHeader, R.attr.proton_background_secondary));
                textConnectingTo.setTextColor(
                    MaterialColors.getColor(textConnectingTo, R.attr.proton_text_norm));
            }
            else {
                updateNotConnectedView();
            }
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
        progressBar.setVisibility(View.GONE);
        changeStateFragment(VpnStateErrorFragment.class);

        RetryInfo retryInfo = vpnConnectionManager.getRetryInfo();
        if (retryInfo != null) {
            // TODO: Keep updating the text. This will require moving some logic from the fragment to the
            //  ViewModel.
            int retryIn = retryInfo.getRetryInSeconds();
            textConnectingTo.setText(getResources().getQuantityString(R.plurals.retry_in, retryIn, retryIn));
        } else {
            textConnectingTo.setText(R.string.loaderReconnecting);
        }
    }

    @NonNull
    private Spannable getStringWithServerName(@StringRes int textRes, @NonNull String serverName) {
        return addSecureCoreArrow(getString(textRes, serverName));
    }

    @NonNull
    private Spannable addSecureCoreArrow(@NonNull String text) {
        SpannableString spannable = new SpannableString(text);

        int index = text.indexOf(Server.SECURE_CORE_SEPARATOR);
        if (index > -1) {
            CenterImageSpan span =
                new CenterImageSpan(requireContext(), R.drawable.ic_secure_core_arrow_color);
            spannable.setSpan(span, index, index + Server.SECURE_CORE_SEPARATOR.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
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
}
