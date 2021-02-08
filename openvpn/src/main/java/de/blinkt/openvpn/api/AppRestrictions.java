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
import de.blinkt.openvpn.VpnProfile;
import de.blinkt.openvpn.core.ConfigParser;
import de.blinkt.openvpn.core.Connection;
import de.blinkt.openvpn.core.ProfileManager;
import de.blinkt.openvpn.core.VpnStatus;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class AppRestrictions {
    public static final String PROFILE_CREATOR = "de.blinkt.openvpn.api.AppRestrictions";
    final static int CONFIG_VERSION = 1;
    static boolean alreadyChecked = false;
    private static AppRestrictions mInstance;
    private RestrictionsManager mRestrictionsMgr;
    private BroadcastReceiver mRestrictionsReceiver;

    private AppRestrictions(Context c) {

    }

    public static AppRestrictions getInstance(Context c) {
        if (mInstance == null)
            mInstance = new AppRestrictions(c);
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

    private String hashConfig(String config) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA1");
            byte utf8_bytes[] = config.getBytes();
            digest.update(utf8_bytes, 0, utf8_bytes.length);
            return new BigInteger(1, digest.digest()).toString(16);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void applyRestrictions(Context c) {
        mRestrictionsMgr = (RestrictionsManager) c.getSystemService(Context.RESTRICTIONS_SERVICE);
        if (mRestrictionsMgr == null)
            return;
        Bundle restrictions = mRestrictionsMgr.getApplicationRestrictions();
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
        Parcelable[] profileList = restrictions.getParcelableArray(("vpn_configuration_list"));
        if (profileList == null) {
            VpnStatus.logError("App restriction does not contain a profile list (vpn_configuration_list)");
            return;
        }

        Set<String> provisionedUuids = new HashSet<>();

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

            if (uuid == null || ovpn == null || name == null) {
                VpnStatus.logError("App restriction profile misses uuid, ovpn or name key");
                continue;
            }

            String ovpnHash = hashConfig(ovpn);

            provisionedUuids.add(uuid.toLowerCase(Locale.ENGLISH));
            // Check if the profile already exists
            VpnProfile vpnProfile = ProfileManager.get(c, uuid);


            if (vpnProfile != null) {
                // Profile exists, check if need to update it
                if (ovpnHash.equals(vpnProfile.importedProfileHash))
                    // not modified skip to next profile
                    continue;

            }
            addProfile(c, ovpn, uuid, name, vpnProfile);
        }

        Vector<VpnProfile> profilesToRemove = new Vector<>();
        // get List of all managed profiles
        for (VpnProfile vp: pm.getProfiles())
        {
            if (PROFILE_CREATOR.equals(vp.mProfileCreator)) {
                if (!provisionedUuids.contains(vp.getUUIDString()))
                    profilesToRemove.add(vp);
            }
        }
        for (VpnProfile vp: profilesToRemove) {
            VpnStatus.logInfo("Remove with uuid: %s and name: %s since it is no longer in the list of managed profiles");
            pm.removeProfile(c, vp);
        }

    }

    private String prepare(String config) {
        String newLine = System.getProperty("line.separator");
        if (!config.contains(newLine)&& !config.contains(" ")) {
            try {
                byte[] decoded = android.util.Base64.decode(config.getBytes(), android.util.Base64.DEFAULT);
                config  = new String(decoded);
                return config; 
            } catch(IllegalArgumentException e) {
               
            }
        }
        return config;
    };
    
    private void addProfile(Context c, String config, String uuid, String name, VpnProfile vpnProfile) {
        config  = prepare(config);
        ConfigParser cp = new ConfigParser();
        try {
            cp.parseConfig(new StringReader(config));
            VpnProfile vp = cp.convertProfile();
            vp.mProfileCreator = PROFILE_CREATOR;

            // We don't want provisioned profiles to be editable
            vp.mUserEditable = false;

            vp.mName = name;
            vp.setUUID(UUID.fromString(uuid));
            vp.importedProfileHash = hashConfig(config);

            ProfileManager pm = ProfileManager.getInstance(c);

            if (vpnProfile != null) {
                vp.mVersion = vpnProfile.mVersion + 1;
                vp.mAlias = vpnProfile.mAlias;
            }

            // The add method will replace any older profiles with the same UUID
            pm.addProfile(vp);
            pm.saveProfile(c, vp);
            pm.saveProfileList(c);

        } catch (ConfigParser.ConfigParseError | IOException | IllegalArgumentException e) {
            VpnStatus.logException("Error during import of managed profile", e);
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

    public void pauseCheckRestrictions(Context c)
    {
        removeChangesListener(c);
    }
}
