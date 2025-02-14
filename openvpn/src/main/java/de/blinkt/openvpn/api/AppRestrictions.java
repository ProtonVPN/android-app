/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.api;

import android.annotation.TargetApi;
import android.content.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;

import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.Preferences;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class AppRestrictions {
    public static final String PROFILE_CREATOR = "de.blinkt.openvpn.api.AppRestrictions";
    final static int CONFIG_VERSION = 1;
    static boolean alreadyChecked = false;
    private static AppRestrictions mInstance;
    private BroadcastReceiver mRestrictionsReceiver;

    private AppRestrictions() {

    }

    public static AppRestrictions getInstance(Context c) {
        if (mInstance == null)
            mInstance = new AppRestrictions();
        return mInstance;
    }

    private void addChangesListener(Context c) {
        IntentFilter restrictionsFilter =
                new IntentFilter(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED);
        mRestrictionsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                applyRestrictions(context);
            }
        };
        c.registerReceiver(mRestrictionsReceiver, restrictionsFilter);
    }

    private void removeChangesListener(Context c) {
        c.unregisterReceiver(mRestrictionsReceiver);
    }

    private String hashConfig(String rawconfig, String allowedApps) {
        String config = prepare(rawconfig);
        MessageDigest digest;

        if (allowedApps == null)
            allowedApps = "";



        try {
            digest = MessageDigest.getInstance("SHA1");
            byte[] utf8_bytes = config.getBytes(StandardCharsets.UTF_8);
            digest.update(utf8_bytes, 0, utf8_bytes.length);

            byte[] apps_bytes = allowedApps.getBytes(StandardCharsets.UTF_8);
            digest.update(apps_bytes, 0, apps_bytes.length);
            return new BigInteger(1, digest.digest()).toString(16);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void applyRestrictions(Context c) {
        RestrictionsManager restrictionsMgr = (RestrictionsManager) c.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (restrictionsMgr == null)
            return;
        Bundle restrictions = restrictionsMgr.getApplicationRestrictions();
        parseRestrictionsBundle(c, restrictions);
    }
    public void parseRestrictionsBundle(Context c, Bundle restrictions)
    {
        if (restrictions == null)
            return;

        String configVersion = restrictions.getString("version", "(not set)");
        try {
            if (Integer.parseInt(configVersion) != CONFIG_VERSION)
                throw new NumberFormatException("Wrong version");
        } catch (NumberFormatException nex) {
            if ("(not set)".equals(configVersion))
                // Ignore error if no version present
                return;
            VpnStatus.logError(String.format(Locale.US, "App restriction version %s does not match expected version %d", configVersion, CONFIG_VERSION));
            return;
        }
        Parcelable[] profileList = restrictions.getParcelableArray("vpn_configuration_list");
        if (profileList == null) {
            VpnStatus.logInfo("App restriction does not contain a profile list. Removing previously added profiles. (vpn_configuration_list)");
            profileList = new Parcelable[]{};
        }

        importVPNProfiles(c, restrictions, profileList);
        setAllowedRemoteControl(c, restrictions);

        setMiscSettings(c, restrictions);
    }

    private void setAllowedRemoteControl(Context c, Bundle restrictions) {
        String allowedApps = restrictions.getString("allowed_remote_access", null);
        ExternalAppDatabase extapps = new ExternalAppDatabase(c);

        if (allowedApps == null)
        {
            extapps.setFlagManagedConfiguration(false);
            return;
        }

        HashSet<String> restrictionApps = new HashSet<>();

        for (String package_name:allowedApps.split("[, \n\r]")) {
            if (!TextUtils.isEmpty(package_name)) {
                restrictionApps.add(package_name);
            }
        }
        extapps.setFlagManagedConfiguration(true);
        extapps.clearAllApiApps();

        if(!extapps.getExtAppList().equals(restrictionApps))
        {
            extapps.setAllowedApps(restrictionApps);
        }
    }

    private static void setMiscSettings(Context c, Bundle restrictions) {
        SharedPreferences defaultPrefs = Preferences.getDefaultSharedPreferences(c);

        if(restrictions.containsKey("screenoffpausevpn"))
        {
            boolean pauseVPN = restrictions.getBoolean("screenoffpausevpn");
            SharedPreferences.Editor editor = defaultPrefs.edit();
            editor.putBoolean("screenoff", pauseVPN);
            editor.apply();
        }
        if(restrictions.containsKey("ignorenetworkstate"))
        {
            boolean ignoreNetworkState = restrictions.getBoolean("ignorenetworkstate");
            SharedPreferences.Editor editor = defaultPrefs.edit();
            editor.putBoolean("ignorenetstate", ignoreNetworkState);
            editor.apply();
        }
        if (restrictions.containsKey("restartvpnonboot"))
        {
            boolean restartVPNonBoot = restrictions.getBoolean("restartvpnonboot");
            SharedPreferences.Editor editor = defaultPrefs.edit();
            editor.putBoolean("restartvpnonboot", restartVPNonBoot);
            editor.apply();
        }
    }

    private void importVPNProfiles(Context c, Bundle restrictions, Parcelable[] profileList) {
        Set<String> provisionedUuids = new HashSet<>();

        String defaultprofile = restrictions.getString("defaultprofile", null);
        boolean defaultprofileProvisioned = false;


        ProfileManager pm = ProfileManager.getInstance(c);
        for (Parcelable profile : profileList) {
            if (!(profile instanceof Bundle)) {
                VpnStatus.logError("App restriction profile has wrong type");
                continue;
            }
            Bundle p = (Bundle) profile;

            String uuid = p.getString("uuid");
            String ovpn = p.getString("ovpn");
            String name = p.getString("name");
            String certAlias = p.getString("certificate_alias");
            String allowedApps = p.getString("allowed_apps");

            if (TextUtils.isEmpty(uuid) || TextUtils.isEmpty(ovpn) || TextUtils.isEmpty(name)) {
                VpnStatus.logError("App restriction profile misses uuid, ovpn or name key");
                continue;
            }

            /* we always use lower case uuid since Android UUID class will use present
             * them that way */
            uuid = uuid.toLowerCase(Locale.US);
            if (defaultprofile != null)
                defaultprofile = defaultprofile.toLowerCase(Locale.US);

            if (uuid.equals(defaultprofile))
                defaultprofileProvisioned = true;

            String ovpnHash = hashConfig(ovpn, allowedApps);

            provisionedUuids.add(uuid.toLowerCase(Locale.ENGLISH));
            // Check if the profile already exists
            VpnProfile vpnProfile = ProfileManager.get(c, uuid);

            HashSet<String> oldAllowedPackages = null;
            if (vpnProfile != null) {
                // Profile exists, check if need to update it
                if (ovpnHash.equals(vpnProfile.importedProfileHash)) {
                    addCertificateAlias(vpnProfile, certAlias, c);

                    // not modified skip to next profile
                    continue;
                }
                oldAllowedPackages = vpnProfile.mAllowedAppsVpn;
            }
            vpnProfile = addProfile(c, ovpn, uuid, name, vpnProfile, ovpnHash);
            if (vpnProfile == null)
            {
                continue;
            }

            addCertificateAlias(vpnProfile, certAlias, c);
            HashSet<String> allowedAppsSet = new HashSet<>();
            if (allowedApps != null && vpnProfile != null){
                for (String app:allowedApps.split("[,: \n\r]")){
                    if (!TextUtils.isEmpty(app))
                        allowedAppsSet.add(app);
                }
                if (!allowedAppsSet.equals(vpnProfile.mAllowedAppsVpn))
                {
                    vpnProfile.mAllowedAppsVpn = allowedAppsSet;
                    vpnProfile.mAllowedAppsVpnAreDisallowed = false;
                    vpnProfile.addChangeLogEntry("app restrictions updated allowed apps");
                    pm.saveProfile(c, vpnProfile);
                }

            }
            if (TextUtils.isEmpty(allowedApps) && oldAllowedPackages != null)
            {
                vpnProfile.addChangeLogEntry("app restrictions kept old allowed app (new ones empty)");
                vpnProfile.mAllowedAppsVpn = oldAllowedPackages;
                pm.saveProfile(c, vpnProfile);
            }

        }

        Vector<VpnProfile> profilesToRemove = new Vector<>();
        // get List of all managed profiles
        for (VpnProfile vp : pm.getProfiles()) {
            if (PROFILE_CREATOR.equals(vp.mProfileCreator)) {
                if (!provisionedUuids.contains(vp.getUUIDString()))
                    profilesToRemove.add(vp);
            }
        }
        for (VpnProfile vp : profilesToRemove) {
            VpnStatus.logInfo("Remove with uuid: %s and name: %s since it is no longer in the list of managed profiles");
            pm.removeProfile(c, vp);
        }

        SharedPreferences defaultPrefs = Preferences.getDefaultSharedPreferences(c);

        if (!TextUtils.isEmpty(defaultprofile)) {
            if (!defaultprofileProvisioned) {
                VpnStatus.logError("App restrictions: Setting a default profile UUID without providing a profile with that UUID");
            } else {
                String uuid = defaultPrefs.getString("alwaysOnVpn", null);
                if (!defaultprofile.equals(uuid))
                {
                    SharedPreferences.Editor editor = defaultPrefs.edit();
                    editor.putString("alwaysOnVpn", defaultprofile);
                    editor.apply();

                }
            }
        }
    }

    /**
     * If certAlias is non-null will modify the profile type to use the keystore variant of
     * the authentication method and will also set the keystore alias
     */
    private void addCertificateAlias(VpnProfile vpnProfile, String certAlias, Context c) {
        if (vpnProfile == null)
            return;

        if (certAlias == null)
            certAlias = "";

        int oldType = vpnProfile.mAuthenticationType;
        String oldAlias = vpnProfile.mAlias;

        if (!TextUtils.isEmpty(certAlias)) {
            switch (vpnProfile.mAuthenticationType)
            {
                case VpnProfile.TYPE_PKCS12:
                case VpnProfile.TYPE_CERTIFICATES:
                    vpnProfile.mAuthenticationType = VpnProfile.TYPE_KEYSTORE;
                    break;
                case VpnProfile.TYPE_USERPASS_CERTIFICATES:
                case VpnProfile.TYPE_USERPASS_PKCS12:
                    vpnProfile.mAuthenticationType = VpnProfile.TYPE_USERPASS_KEYSTORE;
                    break;
            }

        } else
        {
            /* Alias is null, return to non keystore method */
            boolean pkcs12present = !TextUtils.isEmpty(vpnProfile.mPKCS12Filename);
            switch (vpnProfile.mAuthenticationType) {
                case VpnProfile.TYPE_USERPASS_KEYSTORE:
                    if (pkcs12present)
                        vpnProfile.mAuthenticationType = VpnProfile.TYPE_USERPASS_PKCS12;
                    else
                        vpnProfile.mAuthenticationType = VpnProfile.TYPE_USERPASS_CERTIFICATES;
                    break;
                case VpnProfile.TYPE_KEYSTORE:
                    if (pkcs12present)
                        vpnProfile.mAuthenticationType = VpnProfile.TYPE_PKCS12;
                    else
                        vpnProfile.mAuthenticationType = VpnProfile.TYPE_CERTIFICATES;
                    break;
             }
        }
        vpnProfile.mAlias = certAlias;

        if (!certAlias.equals(oldAlias) || oldType != vpnProfile.mAuthenticationType)
        {
            vpnProfile.addChangeLogEntry("app restrictions updated certificate alias");
            ProfileManager pm = ProfileManager.getInstance(c);
            pm.saveProfile(c, vpnProfile);
        }
    }

    private String prepare(String config) {
        String newLine = System.getProperty("line.separator");
        if (!config.contains(newLine) && !config.contains(" ")) {
            try {
                byte[] decoded = android.util.Base64.decode(config.getBytes(), android.util.Base64.DEFAULT);
                config = new String(decoded);
                return config;
            } catch (IllegalArgumentException e) {

            }
        }
        return config;
    }

    ;

    VpnProfile addProfile(Context c, String config, String uuid, String name, VpnProfile vpnProfile, String ovpnHash) {
        config = prepare(config);
        ConfigParser cp = new ConfigParser();
        try {
            cp.parseConfig(new StringReader(config));
            VpnProfile vp = cp.convertProfile();
            vp.mProfileCreator = PROFILE_CREATOR;

            // We don't want provisioned profiles to be editable
            vp.mUserEditable = false;

            vp.mName = name;
            vp.setUUID(UUID.fromString(uuid));
            vp.importedProfileHash = ovpnHash;

            ProfileManager pm = ProfileManager.getInstance(c);

            if (vpnProfile != null) {
                vp.mVersion = vpnProfile.mVersion + 1;
                vp.mAlias = vpnProfile.mAlias;
                vp.addChangeLogEntry("App restriction with hash " + ovpnHash);
            }

            // The add method will replace any older profiles with the same UUID
            pm.addProfile(vp);
            vp.addChangeLogEntry("app restrictions created profile");
            pm.saveProfile(c, vp);
            pm.saveProfileList(c);
            return vp;

        } catch (ConfigParser.ConfigParseError | IOException | IllegalArgumentException e) {
            VpnStatus.logException("Error during import of managed profile", e);
            return null;
        }
    }

    public void checkRestrictions(Context c) {
        if (alreadyChecked) {
            return;
        }
        alreadyChecked = true;
        addChangesListener(c);
        applyRestrictions(c);
    }

    public void pauseCheckRestrictions(Context c) {
        removeChangesListener(c);
    }
}
