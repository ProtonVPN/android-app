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
package com.protonvpn.android.utils;

import android.content.Context;

import com.protonvpn.android.BuildConfig;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.models.profiles.Profile;
import com.protonvpn.android.models.profiles.SavedProfilesV3;
import com.protonvpn.android.models.profiles.ServerDeliver;
import com.protonvpn.android.models.profiles.ServerWrapper;
import com.protonvpn.android.models.vpn.Server;
import com.protonvpn.android.models.vpn.VpnCountry;

import org.jetbrains.annotations.TestOnly;
import org.joda.time.DateTime;
import org.joda.time.Minutes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ServerManager implements Serializable, ServerDeliver {

    private final List<VpnCountry> vpnCountries = new ArrayList<>();
    private final List<VpnCountry> secureCoreEntryCountries = new ArrayList<>();
    private final List<VpnCountry> secureCoreExitCountries = new ArrayList<>();
    private final SavedProfilesV3 savedProfiles;
    private DateTime updatedAt;

    private transient Context appContext;

    private transient LiveEvent updateEvent = new LiveEvent();
    private transient LiveEvent profilesUpdateEvent = new LiveEvent();

    private transient UserData userData;

    private ServerManager(Context appContext) {
        this.appContext = appContext.getApplicationContext();
        savedProfiles = Storage.load(SavedProfilesV3.class, SavedProfilesV3.defaultProfiles(appContext, this));
    }

    @Override
    public String toString() {
        return "vpnCountries: " + vpnCountries.size() + " entry: " + secureCoreEntryCountries.size()
            + " exit: " + secureCoreExitCountries.size() + " saved: " + (savedProfiles == null ?
            "null saved profiles" : (savedProfiles.getProfileList() == null ? "null profile list" :
            savedProfiles.getProfileList().size())) + " ServerManager Updated: " + updatedAt
            + " ";
    }

    @Inject
    public ServerManager(Context appContext, UserData userData) {
        this.appContext = appContext.getApplicationContext();
        ServerManager oldManager = Storage.load(ServerManager.class);
        if (oldManager != null) {
            vpnCountries.addAll(oldManager.getVpnCountries());
            secureCoreExitCountries.addAll(oldManager.getSecureCoreExitCountries());
            secureCoreEntryCountries.addAll(oldManager.getSecureCoreEntryCountries());
            updatedAt = oldManager.getUpdatedAt();
        }
        this.userData = userData;
        savedProfiles = Storage.load(SavedProfilesV3.class, SavedProfilesV3.defaultProfiles(appContext, this));
        reInitProfiles();
    }

    // TODO Remove this logic.
    // Whole profile providing should be moved to separate class outside of ServerManager
    private void reInitProfiles() {
        for (Profile profile : savedProfiles.getProfileList()) {
            profile.getServerWrapper().setDeliverer(this);
        }
        for (VpnCountry country : vpnCountries) {
            country.setDeliverer(this);
        }
        for (VpnCountry country : secureCoreExitCountries) {
            country.setDeliverer(this);
        }
        for (VpnCountry country : secureCoreEntryCountries) {
            country.setDeliverer(this);
        }
    }

    public LiveEvent getUpdateEvent() {
        return updateEvent;
    }

    public LiveEvent getProfilesUpdateEvent() {
        return profilesUpdateEvent;
    }

    public boolean isDownloadedAtLeastOnce() {
        return updatedAt != null && !vpnCountries.isEmpty();
    }

    public boolean isOutdated() {
        return updatedAt == null || vpnCountries.isEmpty()
            || Minutes.minutesBetween(updatedAt, new DateTime()).getMinutes() >= (BuildConfig.DEBUG ? 1 : 15);
    }

    public void clearCache() {
        updatedAt = null;
        Storage.delete(ServerManager.class);
    }

    public void setServers(List<Server> serverList) {
        vpnCountries.clear();
        secureCoreEntryCountries.clear();
        secureCoreExitCountries.clear();
        HashSet<String> countries = new HashSet<>();
        for (Server server : serverList) {
            countries.add(server.getFlag());
        }

        for (final String country : countries) {
            List<Server> servers = filterServers(serverList,
                server -> !server.isSecureCoreServer() && server.getFlag().equals(country));
            VpnCountry vpnCountry = new VpnCountry(country, servers, this, getBestScoreServer(servers));
            vpnCountries.add(vpnCountry);
        }

        for (final String country : countries) {
            if (country.equals("IS") || country.equals("SE") || country.equals("CH")) {
                List<Server> servers = filterServers(serverList,
                    server -> server.isSecureCoreServer() && server.getEntryCountry()
                        .equalsIgnoreCase(country));

                VpnCountry vpnCountry = new VpnCountry(country, servers, this, getBestScoreServer(servers));
                vpnCountry.addBestConnectionToList(false);
                secureCoreEntryCountries.add(vpnCountry);
            }
        }

        for (final String country : countries) {
            List<Server> servers = filterServers(serverList,
                server -> server.isSecureCoreServer() && server.getExitCountry().equalsIgnoreCase(country));
            VpnCountry vpnCountry = new VpnCountry(country, servers, this, getBestScoreServer(servers));
            if (!vpnCountry.getServerList().isEmpty()) {
                vpnCountry.addBestConnectionToList(false);
                secureCoreExitCountries.add(vpnCountry);
            }
        }

        sortVpnCountries(vpnCountries);
        sortVpnCountries(secureCoreExitCountries);

        updatedAt = new DateTime();
        Storage.save(this);
        updateEvent.emit();
        profilesUpdateEvent.emit();
    }

    private void sortVpnCountries(List<VpnCountry> list) {
        Collections.sort(list, (lhs, rhs) -> {
            if (userData.isFreeUser()) {
                if (lhs.hasAccessibleServer(userData)) {
                    return rhs.hasAccessibleServer(userData) ?
                        rhs.getCountryName().compareTo(lhs.getCountryName()) : -1;
                }
                if (rhs.hasAccessibleServer(userData)) {
                    return 1;
                }
                return lhs.getCountryName().compareTo(rhs.getCountryName());
            }
            return lhs.getCountryName().compareTo(rhs.getCountryName());
        });
    }

    public List<VpnCountry> getVpnCountries() {
        return vpnCountries;
    }

    @Nullable
    public Profile getDefaultConnection() {
        Profile profile = userData.getDefaultConnection() == null ? getSavedProfiles().get(0) :
            userData.getDefaultConnection();
        profile.getServerWrapper().setDeliverer(this);
        return profile;
    }

    public List<VpnCountry> getSecureCoreEntryCountries() {
        return secureCoreEntryCountries;
    }

    private List<Server> filterServers(List<Server> serverList, CollectionTools.Predicate<Server> predicate) {
        List<Server> filtered = new ArrayList<>();
        for (Server server : serverList) {
            if (predicate.contains(server)) {
                filtered.add(server);
            }
        }
        return filtered;
    }

    @Nullable
    public Server getServerById(String serverId) {
        for (VpnCountry vpnCountry : getVpnCountries()) {
            for (Server server : vpnCountry.getServerList()) {
                if (server.getServerId().equals(serverId)) {
                    return server;
                }
            }
        }
        for (VpnCountry vpnCountry : getSecureCoreEntryCountries()) {
            for (Server server : vpnCountry.getServerList()) {
                if (server.getServerId().equals(serverId)) {
                    return server;
                }
            }
        }
        return null;
    }

    @Nullable
    public VpnCountry getVpnCountry(String country, boolean secureCoreCountry) {
        for (VpnCountry vpnCountry : secureCoreCountry ? getSecureCoreEntryCountries() : getVpnCountries()) {
            if (vpnCountry.getFlag().equals(country)) {
                return vpnCountry;
            }
        }
        return null;
    }

    @Nullable
    public VpnCountry getVpnExitCountry(String country, boolean secureCoreCountry) {
        for (VpnCountry vpnCountry : secureCoreCountry ? getSecureCoreExitCountries() : getVpnCountries()) {
            if (vpnCountry.getFlag().equals(country)) {
                return vpnCountry;
            }
        }
        return null;
    }

    public Server getBestScoreServer(VpnCountry country) {
        return getBestScoreServer(country.getConnectableServers());
    }

    @Nullable
    public Server getBestScoreServer(List<Server> serverList) {
        Server bestScore = null;
        boolean hasAccessAtAll = userData.hasAccessToAnyServer(serverList);
        for (Server server : serverList) {
            if (!server.getKeywords().contains("tor") && server.isOnline()) {
                if (bestScore == null) {
                    bestScore = server;
                }
                else if (
                    (bestScore.getScore() > server.getScore() || !userData.hasAccessToServer(bestScore)) && (
                        !hasAccessAtAll || userData.hasAccessToServer(server))
                        || server.isSecureCoreServer()) {
                    bestScore = server;
                }
            }
        }
        return bestScore;
    }

    @Nullable
    public Server getRandomServer() {
        List<VpnCountry> list =
            userData.isSecureCoreEnabled() ? getSecureCoreExitCountries() : getVpnCountries();
        if (!userData.isSecureCoreEnabled()) {
            list = CollectionTools.findAll(list, item -> item.hasAccessibleServer(userData));
        }
        return list.isEmpty() ? null : getRandomServerForCountry(list.get(new Random().nextInt(list.size())));
    }

    @NonNull
    public Server getRandomServerForCountry(VpnCountry country) {
        List<Server> serverList = country.getServerList();
        if (!userData.isSecureCoreEnabled()) {
            serverList = CollectionTools.findAll(serverList, userData::hasAccessToServer);
        }
        if (serverList.size() == 0) {
            serverList = country.getServerList();
        }
        return serverList.get(new Random().nextInt(serverList.size()));
    }

    public Server getRandomServerForCountryExcluded(VpnCountry country, Server excludedServer) {
        List<Server> serverList = country.getServerList();
        if (!userData.isSecureCoreEnabled()) {
            serverList = CollectionTools.findAll(serverList, item -> userData.hasAccessToServer(item) && !item
                .getServerName()
                .equals(excludedServer.getServerName()));
        }
        if (serverList.size() == 0) {
            serverList = country.getServerList();
        }
        return serverList.get(new Random().nextInt(serverList.size()));
    }

    public Server getBestScoreServerFromAll() {
        Server bestScore = null;
        for (VpnCountry country : userData.isSecureCoreEnabled() ? getSecureCoreExitCountries() :
            getVpnCountries()) {
            if (country.hasAccessibleServer(userData) || userData.isSecureCoreEnabled()) {
                Server countryBestServer = getBestScoreServer(country);
                if (bestScore == null) {
                    bestScore = countryBestServer;
                }
                else if (countryBestServer != null && (bestScore.getScore() > countryBestServer.getScore())) {
                    bestScore = countryBestServer;
                }
            }
        }
        return bestScore;
    }

    public List<Profile> getSavedProfiles() {
        return savedProfiles.getProfileList();
    }

    public void deleteSavedProfiles() {
        List<Profile> defaultProfiles = SavedProfilesV3.defaultProfiles(appContext, this).getProfileList();
        for (Profile profile : getSavedProfiles()) {
            if (!defaultProfiles.contains(profile)) {
                deleteProfile(profile);
            }
        }
    }

    public void addToProfileList(String serverName, String color, Server server) {
        Profile newProfile = new Profile(serverName, color, ServerWrapper.makeWithServer(server, this));
        newProfile.getServerWrapper().setSecureCoreCountry(userData.isSecureCoreEnabled());
        addToProfileList(newProfile);
    }

    public boolean addToProfileList(Profile profileToSave) {
        if (!savedProfiles.getProfileList().contains(profileToSave)) {
            savedProfiles.getProfileList().add(profileToSave);
            Storage.save(savedProfiles);
            profilesUpdateEvent.emit();
            return true;
        }
        return false;
    }

    public void editProfile(Profile oldProfile, Profile profileToSave) {
        if (oldProfile.equals(getDefaultConnection())) {
            userData.setDefaultConnection(profileToSave);
        }
        savedProfiles.getProfileList().set(savedProfiles.getProfileList().indexOf(oldProfile), profileToSave);
        Storage.save(savedProfiles);
        profilesUpdateEvent.emit();
    }

    public void deleteProfile(Profile profileToSave) {
        savedProfiles.getProfileList().remove(profileToSave);
        Storage.save(savedProfiles);
        profilesUpdateEvent.emit();
    }

    public List<VpnCountry> getSecureCoreExitCountries() {
        return secureCoreExitCountries;
    }

    public DateTime getUpdatedAt() {
        return updatedAt;
    }

    @Nullable
    public Server getServerFromWrap(ServerWrapper wrapper) {
        switch (wrapper.type) {
            case FASTEST:
                return getBestScoreServerFromAll();
            case RANDOM:
                return getRandomServer();
            case RANDOM_IN_COUNTRY:
                VpnCountry country = wrapper.secureCoreCountry ? getVpnExitCountry(wrapper.country, true) :
                    getVpnCountry(wrapper.country, false);
                return country != null ? getRandomServerForCountry(country) : null;
            case FASTEST_IN_COUNTRY:
                VpnCountry vpnCountry = wrapper.secureCoreCountry ? getVpnExitCountry(wrapper.country, true) :
                    getVpnCountry(wrapper.country, false);
                return vpnCountry != null ? getBestScoreServer(vpnCountry) : null;
            case DIRECT:
                return getServerById(wrapper.serverId);
        }
        throw new RuntimeException("Incorrect server type in profile");
    }

    @Nullable
    @Override
    public Server getServer(ServerWrapper wrapper) {
        return getServerFromWrap(wrapper);
    }

    @Override
    public boolean hasAccessToServer(Server server) {
        return userData.hasAccessToServer(server);
    }

    @TestOnly
    public VpnCountry getFirstNotAccessibleVpnCountry() {
        List<VpnCountry> countries = getVpnCountries();

        for (VpnCountry country : countries) {
            if (!country.hasAccessibleServer(userData)) {
                return country;
            }
        }
        throw new UnsupportedOperationException("Should only use this method on free tiers");
    }
}
