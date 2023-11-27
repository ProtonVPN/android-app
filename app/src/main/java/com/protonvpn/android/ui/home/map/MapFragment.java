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
package com.protonvpn.android.ui.home.map;

import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import com.google.android.material.color.MaterialColors;
import com.protonvpn.android.R;
import com.protonvpn.android.appconfig.RestrictionsConfig;
import com.protonvpn.android.auth.usecase.CurrentUser;
import com.protonvpn.android.auth.data.VpnUser;
import com.protonvpn.android.auth.data.VpnUserKt;
import com.protonvpn.android.bus.ConnectToServer;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.components.BaseFragment;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.Markable;
import com.protonvpn.android.databinding.ItemMarkerCalloutBinding;
import com.protonvpn.android.logging.LogEventsKt;
import com.protonvpn.android.logging.ProtonLogger;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.models.vpn.TranslatedCoordinates;
import com.protonvpn.android.models.vpn.VpnCountry;
import com.protonvpn.android.settings.data.EffectiveCurrentUserSettingsCached;
import com.protonvpn.android.ui.planupgrade.UpgradeDialogActivity;
import com.protonvpn.android.utils.CountryTools;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.vpn.ConnectTrigger;
import com.protonvpn.android.vpn.DisconnectTrigger;
import com.protonvpn.android.vpn.VpnConnectionManager;
import com.protonvpn.android.vpn.VpnStatusProviderUI;
import com.qozix.tileview.TileView;
import com.qozix.tileview.markers.MarkerLayout;
import com.qozix.tileview.paths.CompositePathView;
import com.qozix.tileview.widgets.ZoomPanLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.ObjectsCompat;
import androidx.lifecycle.ViewModelProvider;

import butterknife.BindView;
import dagger.hilt.android.AndroidEntryPoint;

import static java.util.Collections.sort;

@AndroidEntryPoint
@ContentLayout(R.layout.fragment_map)
public class MapFragment extends BaseFragment implements MarkerLayout.MarkerTapListener {

    private static final double X_MIN_BOUND = 0;
    private static final double Y_MIN_BOUND = 0;
    private static final double X_MAX_BOUND = 4978;
    private static final double Y_MAX_BOUND = 2402;

    @BindView(R.id.mapView) TileView mapView;
    @Inject ServerManager serverManager;
    @Inject VpnStatusProviderUI vpnStatusProviderUI;
    @Inject VpnConnectionManager vpnConnectionManager;
    @Inject EffectiveCurrentUserSettingsCached userSettings;
    @Inject CurrentUser currentVpnUser;
    @Inject RestrictionsConfig restrictionsConfig;
    private List<CompositePathView.DrawablePath> paths = new ArrayList<>();
    private ImageView secureCoreMarker = null;
    private MapViewModel viewModel;

    public MapFragment() {
    }

    public static MapFragment newInstance() {
        return new MapFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = (new ViewModelProvider(this).get(MapViewModel.class));
    }

    @Override
    public void onViewCreated() {
        initMap();

        vpnStatusProviderUI.isConnectedOrDisconnectedLiveData().observe(
                getViewLifecycleOwner(), isConnected -> updateMapState());
        serverManager.getServerListVersionLiveData().observe(
                getViewLifecycleOwner(), v -> updateMapState());
        viewModel.getSecureCore().observe(
                getViewLifecycleOwner(), isSecureCore -> updateMapState());
    }

    private void initMap() {
        mapView.setSize(4978, 2402);
        mapView.addDetailLevel(1.0000f, "1000/%d_%d.png", 256, 256);
        mapView.addDetailLevel(0.5000f, "500/%d_%d.png", 256, 256);
        mapView.addDetailLevel(0.2500f, "250/%d_%d.png", 256, 256);

        mapView.setMinimumScaleMode(ZoomPanLayout.MinimumScaleMode.FIT);
        mapView.setMarkerAnchorPoints(-0.5f, -0.5f);
        mapView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.background_norm));
        mapView.defineBounds(X_MIN_BOUND, Y_MIN_BOUND, X_MAX_BOUND, Y_MAX_BOUND);

        mapView.getMarkerLayout().setMarkerTapListener(this);
        mapView.setScaleLimits(0f, 4.0f);
        mapView.setScale(0f);
        mapView.setViewportPadding(256);
        mapView.setShouldRenderWhilePanning(true);
        mapView.setAnimationDuration(1000);
        updateMapState();
    }

    private void updateMapState() {
        addPins(true, getSecureCoreEnabled() ? serverManager.getSecureCoreEntryCountries() :
            serverManager.getVpnCountries());
        if (getSecureCoreEnabled()) {
            if (vpnStatusProviderUI.isConnected()) {
                Server connectedServer =
                    ObjectsCompat.requireNonNull(vpnStatusProviderUI.getConnectionParams().getServer());

                addPins(false, Collections.singletonList(connectedServer));
                paintPaths(connectedServer.getEntryCountryCoordinates(),
                        Collections.singletonList(connectedServer));
            } else {
                paintCorePaths();
            }
        }
    }

    private <T extends Markable> void addPins(boolean removeViews, List<T> countries) {
        addPins(removeViews, countries, null);
    }

    private <T extends Markable> void addPins(boolean removeViews, List<T> countries, T selectedCountry) {
        if (removeViews) {
            mapView.getMarkerLayout().removeAllViews();
            removePaths();
        }
        List<T> countriesCopy = sortPins(countries);

        for (Markable country : countriesCopy) {
            ImageView marker = new ImageView(getContext());
            marker.setTag(country);
            TranslatedCoordinates coordinates = country.getCoordinates();

            int selectedResource =
                country.equals(selectedCountry) ? R.drawable.ic_marker_secure_core_pressed :
                    R.drawable.ic_marker_secure_core;

            VpnUser user = currentVpnUser.vpnUserCached();
            int selectedMarker = vpnStatusProviderUI.isConnectedToAny(country.getConnectableServers()) ?
                R.drawable.ic_marker_colored :
                VpnUserKt.hasAccessToAnyServer(user, country.getConnectableServers()) && !restrictionsConfig.restrictMapSync() ?
                    R.drawable.ic_marker_available : R.drawable.ic_marker;

            if ((country.equals(selectedCountry)) && getSecureCoreEnabled()
                && country.isSecureCoreMarker()) {
                secureCoreMarker = marker;
            }

            if (secureCoreMarker != null) {
                marker.setSelected(secureCoreMarker.getTag() == marker.getTag() || (marker.getTag()
                    .toString()
                    .contains(((VpnCountry) secureCoreMarker.getTag()).getCountryName())));
            }

            marker.setContentDescription(
                markerContentDescription(
                    country,
                    vpnStatusProviderUI.isConnectedToAny(country.getConnectableServers()) || marker.isSelected()));

            marker.setImageResource(
                getSecureCoreEnabled() && country.isSecureCoreMarker() ? selectedResource :
                    selectedMarker);

            if (country.getCoordinates().hasValidCoordinates()) {
                mapView.addMarker(marker, coordinates.getPositionX(), coordinates.getPositionY(), -0.5f,
                    getSecureCoreEnabled() && country.isSecureCoreMarker() ? -0.5f : -1.0f);
            }
        }
    }

    private <T extends Markable> List<T> sortPins(List<T> list) {
        List<T> modifiableList = new ArrayList<>(list);
        sort(modifiableList, (t, t1) -> t.getCoordinates().compareTo(t1.getCoordinates()));
        sort(modifiableList, (t, t1) -> {
            VpnUser user = currentVpnUser.vpnUserCached();
            boolean haveAccess = VpnUserKt.hasAccessToAnyServer(user, t.getConnectableServers());
            boolean haveAccess1 = VpnUserKt.hasAccessToAnyServer(user, t1.getConnectableServers());
            if (haveAccess && !haveAccess1) {
                return 1;
            }
            if (!haveAccess && haveAccess1) {
                return -1;
            }
            return 0;
        });
        return modifiableList;
    }

    private void removePaths() {
        for (CompositePathView.DrawablePath path : paths) {
            mapView.getCompositePathView().removePath(path);
        }
        paths = new ArrayList<>();
    }

    private void paintCorePaths() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        Paint paint = mapView.getDefaultPathPaint();
        paint.setColor(MaterialColors.getColor(mapView, R.attr.colorAccent));
        paint.setStrokeWidth(2);
        paint.setPathEffect(
            new CornerPathEffect(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, metrics)));

        List<double[]> positionList = new ArrayList<>();
        for (VpnCountry vpnCountry : serverManager.getSecureCoreEntryCountries()) {
            positionList.add(vpnCountry.getTranslatedCoordinates().asCoreCoordinates());
        }
        List<VpnCountry> secureEntryCountries = serverManager.getSecureCoreEntryCountries();
        if (!secureEntryCountries.isEmpty()) {
            positionList.add(secureEntryCountries.get(0).getTranslatedCoordinates().asCoreCoordinates());
            paths.add(mapView.drawPath(positionList, null));
        }
    }

    private <T extends Markable> void paintPaths(TranslatedCoordinates entryCoordinates,
                                                 List<T> exitCountries) {

        Paint paint = mapView.getDefaultPathPaint();
        paint.setColor(ContextCompat.getColor(getContext(), R.color.mapPathColor));

        for (Markable vpnCountry : exitCountries) {
            if (vpnCountry.getCoordinates().hasValidCoordinates()) {
                paths.add(mapView.drawPath(Arrays.asList(entryCoordinates.asCoreCoordinates(),
                    vpnCountry.getCoordinates().asCoreCoordinates()), null));
            }
        }
    }

    @Override
    public void onMarkerTap(View view, int x, int y) {

        if (view.getTag() instanceof Server) {
            showCallout((Server) view.getTag());
        }
        if (view.getTag() instanceof VpnCountry) {
            VpnCountry country = (VpnCountry) view.getTag();
            if (getSecureCoreEnabled() && country.isSecureCoreMarker()) {
                removePaths();
                updateMapState();
                addPins(false, serverManager.getSecureCoreEntryCountries(), country);
                addPins(false, country.getServerList());
                paintPaths(country.getCoordinates(), country.getServerList());
            }
            else {
                showCallout(country);
            }
        }
    }

    private <T extends Markable> void showCallout(T marker) {
        TranslatedCoordinates coordinates = marker.getCoordinates();
        mapView.slideToAndCenterWithScale(coordinates.getPositionX(),
            coordinates.getPositionY(), 4.0f);
        mapView.addCallout(initCalloutView(marker), coordinates.getPositionX(),
            coordinates.getPositionY(), -0.5f, -1.0f);
    }

    private View initCalloutView(final Markable country) {
        ItemMarkerCalloutBinding binding =
            ItemMarkerCalloutBinding.inflate(LayoutInflater.from(requireContext()));

        List<Server> countryServers = country.getConnectableServers();
        VpnUser user = currentVpnUser.vpnUserCached();
        boolean hasAccess = VpnUserKt.hasAccessToAnyServer(user, countryServers) && !restrictionsConfig.restrictMapSync();

        binding.countryWithFlags.setCountry(country);
        binding.countryWithFlags.setEnabled(hasAccess);

        if (hasAccess) {
            binding.buttonConnect.setOnClickListener(v -> {
                EventBus.post(
                        new ConnectToServer(
                                serverManager.getBestScoreServer(country.getConnectableServers()),
                                ConnectTrigger.Map.INSTANCE,
                                DisconnectTrigger.Map.INSTANCE
                        ));
                updateMapState();
                mapView.getCalloutLayout().removeAllViews();
            });
            binding.buttonDisconnect.setOnClickListener(v -> {
                ProtonLogger.INSTANCE.log(LogEventsKt.UiDisconnect, "map marker");
                vpnConnectionManager.disconnect(DisconnectTrigger.Map.INSTANCE);
                updateMapState();
                mapView.getCalloutLayout().removeAllViews();
            });

            final boolean currentConnection = vpnStatusProviderUI.isConnectedToAny(countryServers);
            binding.buttonConnect.setVisibility(currentConnection ? View.INVISIBLE : View.VISIBLE);
            binding.buttonDisconnect.setVisibility(currentConnection ? View.VISIBLE : View.INVISIBLE);
            binding.buttonUpgrade.setVisibility(View.GONE);
            binding.imageMarker.setImageResource(
                currentConnection ? R.drawable.ic_marker_colored : R.drawable.ic_marker_available);
        } else {
            binding.buttonUpgrade.setOnClickListener(v ->
                    UpgradeDialogActivity.launchCountry(requireContext(), country.getMarkerCountryCode())
            );

            binding.buttonConnect.setVisibility(View.GONE);
            binding.buttonDisconnect.setVisibility(View.GONE);
            binding.buttonUpgrade.setVisibility(View.VISIBLE);
            binding.imageMarker.setImageResource(R.drawable.ic_marker);
        }

        return binding.getRoot();
    }

    @NonNull
    private String markerContentDescription(@NonNull Markable item, boolean isSelected) {
        String result = "";
        if (item.getMarkerEntryCountryCode() != null) {
            result += CountryTools.INSTANCE.getFullName(item.getMarkerEntryCountryCode());
            result += ' ' + Server.SECURE_CORE_SEPARATOR + ' ';
        }
        result += CountryTools.INSTANCE.getFullName(item.getMarkerCountryCode());
        if (isSelected) {
            result += " Selected";
        }
        return result;
    }

    private Boolean getSecureCoreEnabled() {
        return userSettings.getValue().getSecureCore();
    }
}
