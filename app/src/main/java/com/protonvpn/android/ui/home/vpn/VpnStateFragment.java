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

import android.animation.LayoutTransition;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.github.clans.fab.FloatingActionMenu;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.color.MaterialColors;
import com.protonvpn.android.R;
import com.protonvpn.android.api.ProtonApiRetroFit;
import com.protonvpn.android.appconfig.AppConfig;
import com.protonvpn.android.bus.ConnectToProfile;
import com.protonvpn.android.bus.ConnectedToServer;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.bus.TrafficUpdate;
import com.protonvpn.android.components.BaseFragment;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.NetShieldSwitch;
import com.protonvpn.android.components.VPNException;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.ui.home.ServerListUpdater;
import com.protonvpn.android.ui.ServerLoadColor;
import com.protonvpn.android.ui.onboarding.OnboardingDialogs;
import com.protonvpn.android.ui.onboarding.OnboardingPreferences;
import com.protonvpn.android.utils.ConnectionTools;
import com.protonvpn.android.utils.DebugUtils;
import com.protonvpn.android.utils.HtmlTools;
import com.protonvpn.android.utils.Log;
import com.protonvpn.android.utils.ProtonLogger;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.utils.TimeUtils;
import com.protonvpn.android.utils.TrafficMonitor;
import com.protonvpn.android.vpn.RetryInfo;
import com.protonvpn.android.vpn.VpnConnectionManager;
import com.protonvpn.android.vpn.VpnState;
import com.protonvpn.android.vpn.VpnStateMonitor;

import java.util.Timer;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.widget.ImageViewCompat;
import butterknife.BindView;
import butterknife.OnClick;

import static com.protonvpn.android.utils.AndroidUtilsKt.openProtonUrl;

@ContentLayout(R.layout.vpn_state_fragment)
public class VpnStateFragment extends BaseFragment {

    private static final String KEY_ERROR_CONNECTION_ID = "error_connection_id";
    private static final String KEY_DISMISSED_CONNECTION_ID = "dismissed_connection_id";

    @BindView(R.id.layoutStatusHeader) View layoutStatusHeader;
    @BindView(R.id.textConnectingTo) TextView textConnectingTo;
    @BindView(R.id.imageExpand) ImageView imageExpand;
    @BindView(R.id.dividerTop) View dividerTop;
    @BindView(R.id.layoutBottomSheet) View layoutBottomSheet;
    @BindView(R.id.progressBar) ProgressBar progressBar;

    @BindView(R.id.layoutError) View layoutError;
    @BindView(R.id.layoutConnecting) View layoutConnecting;
    @BindView(R.id.layoutNotConnected) View layoutNotConnected;
    @BindView(R.id.layoutConnected) View layoutConnected;

    @BindView(R.id.textCurrentIp) TextView textCurrentIp;
    @BindView(R.id.textServerName) TextView textServerName;
    @BindView(R.id.textServerIp) TextView textServerIp;
    @BindView(R.id.textDownloadSpeed) TextView textDownloadSpeed;
    @BindView(R.id.textUploadSpeed) TextView textUploadSpeed;
    @BindView(R.id.textDownloadVolume) TextView textDownloadVolume;
    @BindView(R.id.textUploadVolume) TextView textUploadVolume;
    @BindView(R.id.textProtocol) TextView textProtocol;
    @BindView(R.id.textSessionTime) TextView textSessionTime;
    @BindView(R.id.textError) TextView textError;
    @BindView(R.id.progressBarError) ProgressBar progressBarError;
    @BindView(R.id.textLoad) TextView textLoad;
    @BindView(R.id.imageLoad) ImageView imageLoad;
    @BindView(R.id.buttonCancel) Button buttonCancel;
    @BindView(R.id.netShieldSwitch) NetShieldSwitch switchNetShield;

    @Inject ProtonApiRetroFit api;
    @Inject ServerManager manager;
    @Inject UserData userData;
    @Inject AppConfig appConfig;
    @Inject VpnStateMonitor stateMonitor;
    @Inject VpnConnectionManager vpnConnectionManager;
    @Inject ServerListUpdater serverListUpdater;
    @Inject TrafficMonitor trafficMonitor;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private long errorConnectionID;
    private long dismissedConnectionID;
    private Timer graphUpdateTimer;

    @OnClick(R.id.buttonQuickConnect)
    public void buttonQuickConnect() {
        Profile defaultProfile = manager.getDefaultConnection();
        EventBus.post(new ConnectToProfile(defaultProfile));
    }

    @OnClick(R.id.buttonCancel)
    public void buttonCancel() {
        ProtonLogger.INSTANCE.log("Canceling connection");
        vpnConnectionManager.disconnect();
        changeBottomSheetState(false);
    }

    @OnClick(R.id.buttonCancelRetry)
    public void buttonCancelRetry() {
        vpnConnectionManager.disconnect();
    }

    @OnClick(R.id.buttonDisconnect)
    public void buttonDisconnect() {
        buttonCancel();
    }

    @OnClick(R.id.buttonSaveToProfile)
    public void buttonSaveToProfile() {
        Profile currentProfile = stateMonitor.getConnectionProfile();
        for (Profile profile : manager.getSavedProfiles()) {
            if (profile.getServer().getDomain().equals(currentProfile.getServer().getDomain())) {
                Toast.makeText(getActivity(), R.string.saveProfileAlreadySaved, Toast.LENGTH_LONG).show();
                return;
            }
        }
        manager.addToProfileList(currentProfile.getServer().getServerName(),
            Profile.Companion.getRandomProfileColor(requireContext()), currentProfile.getServer());
        Toast.makeText(getActivity(), R.string.toastProfileSaved, Toast.LENGTH_LONG).show();
    }

    @OnClick(R.id.layoutStatusHeader)
    public void layoutCollapsedStatus() {
        if (bottomSheetBehavior != null) {
            changeBottomSheetState(bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    @OnClick(R.id.buttonRetry)
    public void buttonRetry() {
        vpnConnectionManager.reconnect(requireContext());
    }

    @Override
    public void onViewCreated() {
        registerForEvents();
        updateNotConnectedView();
        forceAnimeNestedLayouts();
        serverListUpdater.getIpAddress()
            .observe(getViewLifecycleOwner(), (ip) -> textCurrentIp.setText(textCurrentIp.getContext()
                .getString(R.string.notConnectedCurrentIp,
                    ip.isEmpty() ? getString(R.string.stateFragmentUnknownIp) : ip)));
        switchNetShield.init(userData.getNetShieldProtocol(), appConfig, getViewLifecycleOwner(), userData,
            stateMonitor, vpnConnectionManager, s -> {
                userData.setNetShieldProtocol(s);
                return null;
            });
        userData.getNetShieldLiveData().observe(getViewLifecycleOwner(), state -> {
            if (state != null) {
                switchNetShield.setNetShieldValue(state);
            }
        });

        stateMonitor.getStatusLiveData().observe(getViewLifecycleOwner(), state -> updateView(false, state));
        trafficMonitor
            .getTrafficStatus()
            .observe(getViewLifecycleOwner(), this::onTrafficUpdate);
    }

    private void forceAnimeNestedLayouts() {
        LayoutTransition layoutTransition = ((ViewGroup) layoutConnected).getLayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
    }

    public boolean isBottomSheetExpanded() {
        return bottomSheetBehavior != null
            && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        errorConnectionID = 0;
        dismissedConnectionID = 0;
        if (savedInstanceState != null && savedInstanceState.containsKey(KEY_ERROR_CONNECTION_ID)) {
            errorConnectionID = (Long) savedInstanceState.getSerializable(KEY_ERROR_CONNECTION_ID);
            dismissedConnectionID = (Long) savedInstanceState.getSerializable(KEY_DISMISSED_CONNECTION_ID);
        }
    }

    public void initStatusLayout(final FloatingActionMenu attachedButton) {
        bottomSheetBehavior = BottomSheetBehavior.from(layoutBottomSheet);
        bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED && appConfig.getFeatureFlags().getNetShieldEnabled()
                    && switchNetShield.isSwitchVisible()) {
                    OnboardingDialogs.showDialogOnView(getContext(), switchNetShield,
                        getString(R.string.netshield), getString(R.string.onboardingNetshield),
                        OnboardingPreferences.NETSHIELD_DIALOG);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                attachedButton.animate().alpha(1 - slideOffset).setDuration(0).start();
                attachedButton.setVisibility(slideOffset == 1 ? View.GONE : View.VISIBLE);
                if (imageExpand != null) {
                    imageExpand.animate().rotation(180 * slideOffset).setDuration(0).start();
                }
                dividerTop.setVisibility(slideOffset == 1f ? View.GONE : View.VISIBLE);
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(KEY_ERROR_CONNECTION_ID, errorConnectionID);
        outState.putSerializable(KEY_DISMISSED_CONNECTION_ID, dismissedConnectionID);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (graphUpdateTimer != null) {
            graphUpdateTimer.cancel();
            graphUpdateTimer = null;
        }
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
        layoutConnected.setVisibility(View.GONE);
        layoutNotConnected.setVisibility(View.GONE);
        layoutConnecting.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);

        // isTest(): ugly but enables running UI tests on android 5/6 (which have a problem with this view)
        progressBar.setVisibility(DebugUtils.isTest(getActivity()) ? View.INVISIBLE : View.VISIBLE);

        layoutStatusHeader.setBackgroundColor(
            MaterialColors.getColor(layoutStatusHeader, R.attr.proton_background_secondary));
        textConnectingTo.setTextColor(
            MaterialColors.getColor(textConnectingTo, R.attr.proton_text_norm));

        if (!fromSavedState) {
            changeBottomSheetState(true);
        }
    }

    private void onTrafficUpdate(final @Nullable TrafficUpdate update) {
        if (getActivity() != null && update != null) {
            textSessionTime.setText(TimeUtils.getFormattedTimeFromSeconds(update.getSessionTimeSeconds()));
            textUploadSpeed.setText(update.getUploadSpeedString());
            textDownloadSpeed.setText(update.getDownloadSpeedString());
            textUploadVolume.setText(ConnectionTools.bytesToSize(update.getSessionUpload()));
            textDownloadVolume.setText(ConnectionTools.bytesToSize(update.getSessionDownload()));
        }
    }

    private void clearConnectedStatus() {
        if (graphUpdateTimer != null) {
            graphUpdateTimer.cancel();
            graphUpdateTimer = null;
        }
        onTrafficUpdate(new TrafficUpdate(0, 0, 0, 0, 0));
    }

    private void updateNotConnectedView() {
        layoutConnected.setVisibility(View.GONE);
        layoutNotConnected.setVisibility(View.VISIBLE);
        layoutConnecting.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);

        layoutStatusHeader.setBackgroundColor(
            MaterialColors.getColor(layoutStatusHeader, R.attr.proton_background_secondary));
        imageExpand.setImageResource(R.drawable.ic_chevron_up);
        textConnectingTo.setTextColor(MaterialColors.getColor(textConnectingTo, R.attr.proton_text_norm));
    }

    private void initConnectedStateView(Server server) {
        layoutConnected.setVisibility(View.VISIBLE);
        layoutNotConnected.setVisibility(View.GONE);
        layoutConnecting.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        textConnectingTo.setTextColor(MaterialColors.getColor(textConnectingTo, R.attr.proton_text_inverted));
        layoutStatusHeader.setBackgroundColor(
            MaterialColors.getColor(layoutStatusHeader, R.attr.proton_background_inverted));

        textServerName.setText(server.getServerName());
        textServerIp.setText(stateMonitor.getExitIP());
        textProtocol.setText(stateMonitor.getConnectionProtocol().displayName());
        int load = (int) server.getLoad();
        textLoad.setText(textLoad.getContext().getString(R.string.serverLoad, String.valueOf(load)));
        ImageViewCompat.setImageTintList(imageLoad,
            ColorStateList.valueOf(ServerLoadColor.getColor(imageLoad, server.getLoadState())));
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
        Server connectedServer = null;
        if (profile != null) {
            serverName = (profile.isPreBakedProfile() || profile.getDisplayName(requireContext()).isEmpty())
                && stateMonitor.getConnectingToServer() != null ?
                stateMonitor.getConnectingToServer().getDisplayName() : profile.getDisplayName(requireContext());
            connectedServer = vpnState.getServer();
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
                textConnectingTo.setText(getString(R.string.loaderConnectingTo, serverName));
                initConnectingStateView(fromSavedState);
            }
            else if (VpnState.WaitingForNetwork.INSTANCE.equals(state)) {
                textConnectingTo.setText(R.string.loaderReconnectNoNetwork);
                initConnectingStateView(fromSavedState);
            }
            else if (VpnState.Connected.INSTANCE.equals(state)) {
                textConnectingTo.setText(getString(R.string.loaderConnectedTo, serverName));
                initConnectedStateView(connectedServer);
            }
            else if (VpnState.Disconnecting.INSTANCE.equals(state)) {
                textConnectingTo.setText(R.string.loaderDisconnecting);
                layoutStatusHeader.setBackgroundColor(
                    MaterialColors.getColor(layoutStatusHeader, R.attr.proton_background_secondary));
                textConnectingTo.setTextColor(
                    MaterialColors.getColor(textConnectingTo, R.attr.proton_text_norm));
                clearConnectedStatus();
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
                showErrorDialog(R.string.error_peer_auth_failed);
                Log.exception(new VPNException("Peer Auth: Verifying gateway authentication failed"));
                break;
            case UNREACHABLE:
                showErrorDialog(R.string.error_server_unreachable);
                Log.exception(new VPNException("Gateway is unreachable"));
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
                showErrorDialog(R.string.error_generic);
                Log.exception(new VPNException("Unspecified failure while connecting"));
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

    private void showErrorDialog(@StringRes int textId) {
        layoutConnected.setVisibility(View.GONE);
        layoutNotConnected.setVisibility(View.GONE);
        layoutConnecting.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        textError.setText(textId);

        RetryInfo retryInfo = vpnConnectionManager.getRetryInfo();
        progressBarError.setVisibility(retryInfo != null ? View.VISIBLE : View.INVISIBLE);
        if (retryInfo != null) {
            progressBarError.setMax(retryInfo.getTimeoutSeconds());
            progressBarError.setProgress(retryInfo.getRetryInSeconds());
            int retryIn = retryInfo.getRetryInSeconds();
            textConnectingTo.setText(getResources().getQuantityString(R.plurals.retry_in, retryIn, retryIn));
        }
        else {
            textConnectingTo.setText(R.string.loaderReconnecting);
        }
    }
}
