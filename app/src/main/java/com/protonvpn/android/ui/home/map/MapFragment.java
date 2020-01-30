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
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.lzyzsd.circleprogress.CircleProgress;
import com.protonvpn.android.R;
import com.protonvpn.android.bus.ConnectToServer;
import com.protonvpn.android.bus.EventBus;
import com.protonvpn.android.bus.VpnStateChanged;
import com.protonvpn.android.components.BaseFragment;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.Markable;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.models.vpn.TranslatedCoordinates;
import com.protonvpn.android.models.vpn.VpnCountry;
import com.protonvpn.android.utils.ServerManager;
import com.protonvpn.android.vpn.VpnStateMonitor;
import com.qozix.tileview.TileView;
import com.qozix.tileview.markers.MarkerLayout;
import com.qozix.tileview.paths.CompositePathView;
import com.qozix.tileview.widgets.ZoomPanLayout;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import androidx.core.content.ContextCompat;
import butterknife.BindView;

import static java.util.Collections.sort;

@ContentLayout(R.layout.fragment_map)
public class MapFragment extends BaseFragment implements MarkerLayout.MarkerTapListener {

    private static final double X_MIN_BOUND = 0;
    private static final double Y_MIN_BOUND = 0;
    private static final double X_MAX_BOUND = 4978;
    private static final double Y_MAX_BOUND = 2402;

    @BindView(R.id.mapView) TileView mapView;
    @Inject ServerManager serverManager;
    @Inject VpnStateMonitor stateMonitor;
    @Inject UserData userData;
    private List<CompositePathView.DrawablePath> paths = new ArrayList<>();
    private ImageView secureCoreMarker = null;

    public MapFragment() {
    }

    public static MapFragment newInstance() {
        return new MapFragment();
    }

    @Override
    public void onViewCreated() {
        initMap();
        registerForEvents();
    }

    @Subscribe
    public void onVpnStateChanged(VpnStateChanged change) {
        initMapState();
    }

    private void initMap() {
        mapView.setSize(4978, 2402);
        mapView.addDetailLevel(1.0000f, "1000/%d_%d.png", 256, 256);
        mapView.addDetailLevel(0.5000f, "500/%d_%d.png", 256, 256);
        mapView.addDetailLevel(0.2500f, "250/%d_%d.png", 256, 256);

        mapView.setMinimumScaleMode(ZoomPanLayout.MinimumScaleMode.FIT);
        mapView.setMarkerAnchorPoints(-0.5f, -0.5f);
        mapView.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.bgMap));
        mapView.defineBounds(X_MIN_BOUND, Y_MIN_BOUND, X_MAX_BOUND, Y_MAX_BOUND);

        mapView.getMarkerLayout().setMarkerTapListener(this);
        mapView.setScaleLimits(0f, 4.0f);
        mapView.setScale(0f);
        mapView.setViewportPadding(256);
        mapView.setShouldRenderWhilePanning(true);
        mapView.setAnimationDuration(1000);
        initMapState();
    }

    private void initMapState() {
        addPins(true, userData.isSecureCoreEnabled() ? serverManager.getSecureCoreEntryCountries() :
            serverManager.getVpnCountries());
        if (userData.isSecureCoreEnabled()) {
            if (stateMonitor.isConnected()) {
                addPins(false, Collections.singletonList(stateMonitor.getConnectionProfile() != null ?
                    stateMonitor.getConnectionProfile().getServer() : null));
                paintPaths(stateMonitor.getConnectionProfile().getServer().getEntryCountryCoordinates(),
                    Collections.singletonList(stateMonitor.getConnectionProfile().getServer()));
            }
            else {
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

            int selectedMarker = stateMonitor.isConnectedToAny(country.getConnectableServers()) ?
                R.drawable.ic_marker_colored :
                userData.hasAccessToAnyServer(country.getConnectableServers()) ?
                    R.drawable.ic_marker_available : R.drawable.ic_marker;

            marker.setContentDescription(country.getMarkerText());
            if (stateMonitor.isConnectedToAny(country.getConnectableServers())) {
                marker.setContentDescription(country.getMarkerText() + " Selected");
            }

            if ((country.equals(selectedCountry)) && userData.isSecureCoreEnabled()
                && country.isSecureCoreMarker()) {
                secureCoreMarker = marker;
            }

            if (secureCoreMarker != null) {
                marker.setSelected(secureCoreMarker.getTag() == marker.getTag() || (marker.getTag()
                    .toString()
                    .contains(((VpnCountry) secureCoreMarker.getTag()).getCountryName())));
                if (marker.isSelected()) {
                    marker.setContentDescription(country.getMarkerText() + " Selected");
                }
            }

            marker.setImageResource(
                userData.isSecureCoreEnabled() && country.isSecureCoreMarker() ? selectedResource :
                    selectedMarker);

            if (country.getCoordinates().hasValidCoordinates()) {
                mapView.addMarker(marker, coordinates.getPositionX(), coordinates.getPositionY(), -0.5f,
                    userData.isSecureCoreEnabled() && country.isSecureCoreMarker() ? -0.5f : -1.0f);
            }
        }
    }

    private <T extends Markable> List<T> sortPins(List<T> list) {
        List<T> modifiableList = new ArrayList<>(list);
        sort(modifiableList, (t, t1) -> t.getCoordinates().compareTo(t1.getCoordinates()));
        sort(modifiableList, (t, t1) -> {
            if (userData.hasAccessToAnyServer(t.getConnectableServers()) && !userData.hasAccessToAnyServer(
                t1.getConnectableServers())) {
                return 1;
            }
            if (!userData.hasAccessToAnyServer(t.getConnectableServers()) && userData.hasAccessToAnyServer(
                t1.getConnectableServers())) {
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
        paint.setColor(ContextCompat.getColor(getContext(), R.color.colorAccent));
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
        paint.setColor(ContextCompat.getColor(getContext(), R.color.transparentWhite));

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
            if (userData.isSecureCoreEnabled() && country.isSecureCoreCountry()) {
                removePaths();
                initMapState();
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

        mapView.slideToAndCenterWithScale(marker.getCoordinates().getPositionX(),
            marker.getCoordinates().getPositionY(), 4.0f);
        mapView.addCallout(initCalloutView(marker), marker.getCoordinates().getPositionX(),
            marker.getCoordinates().getPositionY(), -0.5f, -1.0f);
    }

    private View initCalloutView(final Markable country) {
        View calloutView = View.inflate(getContext(), R.layout.item_marker_callout, null);

        TextView textMarker = calloutView.findViewById(R.id.textMarker);
        textMarker.setText(country.getMarkerText());

        final boolean currentConnection = stateMonitor.isConnectedToAny(country.getConnectableServers());

        Button buttonConnect = calloutView.findViewById(R.id.buttonConnect);
        buttonConnect.setText(currentConnection ? R.string.mapCalloutDisconnect : R.string.mapCalloutConnect);
        buttonConnect.setOnClickListener(v -> {
            if (currentConnection) {
                stateMonitor.disconnect();
            }
            else {
                EventBus.post(
                    new ConnectToServer(serverManager.getBestScoreServer(country.getConnectableServers())));
            }
            initMapState();
            mapView.getCalloutLayout().removeAllViews();
        });

        CircleProgress progressLoad = calloutView.findViewById(R.id.progressLoad);
        // TODO should show load of country
        progressLoad.setProgress(15);
        return calloutView;
    }
}