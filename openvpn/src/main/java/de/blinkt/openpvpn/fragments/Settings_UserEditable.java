/*
 * Copyright (c) 2012-2015 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.VpnProfile;
import de.blinkt.openpvpn.api.AppRestrictions;

public class Settings_UserEditable extends KeyChainSettingsFragment implements View.OnClickListener {

    private View mView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.settings_usereditable, container, false);
        TextView messageView = (TextView) mView.findViewById(R.id.messageUserEdit);
        messageView.setText(getString(R.string.message_no_user_edit, getPackageString(mProfile.mProfileCreator)));
        initKeychainViews(this.mView);
        return mView;
    }


    private String getPackageString(String packageName) {

        if (AppRestrictions.PROFILE_CREATOR.equals(packageName))
            return "Android Enterprise Management";

        final PackageManager pm = getActivity().getPackageManager();
        ApplicationInfo ai;
        try {
            ai = pm.getApplicationInfo(packageName, 0);
        } catch (final PackageManager.NameNotFoundException e) {
            ai = null;
        }
        final String applicationName = (String) (ai != null ? pm.getApplicationLabel(ai) : "(unknown)");
        return String.format("%s (%s)", applicationName, packageName);
    }

    @Override
    protected void savePreferences() {

    }

    @Override
    public void onResume() {
        super.onResume();
        mView.findViewById(R.id.keystore).setVisibility(View.GONE);
        if (mProfile.mAuthenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE ||
                mProfile.mAuthenticationType == VpnProfile.TYPE_KEYSTORE)
            mView.findViewById(R.id.keystore).setVisibility(View.VISIBLE);
    }
}
