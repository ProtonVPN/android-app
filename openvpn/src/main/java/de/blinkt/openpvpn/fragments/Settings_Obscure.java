/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.widget.Toast;

import java.util.Locale;

import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.VpnProfile;

public class Settings_Obscure extends OpenVpnPreferencesFragment implements OnPreferenceChangeListener {
	private CheckBoxPreference mUseRandomHostName;
	private CheckBoxPreference mUseFloat;
	private CheckBoxPreference mUseCustomConfig;
	private EditTextPreference mCustomConfig;
    private EditTextPreference mMssFixValue;
    private CheckBoxPreference mMssFixCheckBox;
    private CheckBoxPreference mPeerInfo;

    private CheckBoxPreference mPersistent;
    private ListPreference mConnectRetrymax;
    private EditTextPreference mConnectRetry;
    private EditTextPreference mConnectRetryMaxTime;
    private EditTextPreference mTunMtu;

    public void onCreateBehaviour(Bundle savedInstanceState) {

        mPersistent = (CheckBoxPreference) findPreference("usePersistTun");
        mConnectRetrymax = (ListPreference) findPreference("connectretrymax");
        mConnectRetry = (EditTextPreference) findPreference("connectretry");
        mConnectRetryMaxTime = (EditTextPreference) findPreference("connectretrymaxtime");

        mPeerInfo = (CheckBoxPreference) findPreference("peerInfo");

        mConnectRetrymax.setOnPreferenceChangeListener(this);
        mConnectRetrymax.setSummary("%s");

        mConnectRetry.setOnPreferenceChangeListener(this);
        mConnectRetryMaxTime.setOnPreferenceChangeListener(this);



    }

    protected void loadSettingsBehaviour() {
        mPersistent.setChecked(mProfile.mPersistTun);
        mPeerInfo.setChecked(mProfile.mPushPeerInfo);

        mConnectRetrymax.setValue(mProfile.mConnectRetryMax);
        onPreferenceChange(mConnectRetrymax, mProfile.mConnectRetryMax);

        mConnectRetry.setText(mProfile.mConnectRetry);
        onPreferenceChange(mConnectRetry, mProfile.mConnectRetry);

        mConnectRetryMaxTime.setText(mProfile.mConnectRetryMaxTime);
        onPreferenceChange(mConnectRetryMaxTime, mProfile.mConnectRetryMaxTime);

    }


    protected void saveSettingsBehaviour() {
        mProfile.mConnectRetryMax = mConnectRetrymax.getValue();
        mProfile.mPersistTun = mPersistent.isChecked();
        mProfile.mConnectRetry = mConnectRetry.getText();
        mProfile.mPushPeerInfo = mPeerInfo.isChecked();
        mProfile.mConnectRetryMaxTime = mConnectRetryMaxTime.getText();
    }


    public boolean onPreferenceChangeBehaviour(Preference preference, Object newValue) {
        if (preference == mConnectRetrymax) {
            if(newValue==null) {
                newValue="5";
            }
            mConnectRetrymax.setDefaultValue(newValue);

            for(int i=0;i< mConnectRetrymax.getEntryValues().length;i++){
                if(mConnectRetrymax.getEntryValues().equals(newValue))
                    mConnectRetrymax.setSummary(mConnectRetrymax.getEntries()[i]);
            }

        } else if (preference == mConnectRetry) {
            if(newValue==null || newValue=="")
                newValue="2";
            mConnectRetry.setSummary(String.format("%s s", newValue));
        } else if (preference == mConnectRetryMaxTime) {
            if(newValue==null || newValue=="")
                newValue="300";
            mConnectRetryMaxTime.setSummary(String.format("%s s", newValue));
        }


        return true;
    }



    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.vpn_obscure);
		
		mUseRandomHostName = (CheckBoxPreference) findPreference("useRandomHostname");
		mUseFloat = (CheckBoxPreference) findPreference("useFloat");
		mUseCustomConfig = (CheckBoxPreference) findPreference("enableCustomOptions");
		mCustomConfig = (EditTextPreference) findPreference("customOptions");
        mMssFixCheckBox = (CheckBoxPreference) findPreference("mssFix");
        mMssFixValue = (EditTextPreference) findPreference("mssFixValue");
        mMssFixValue.setOnPreferenceChangeListener(this);
        mTunMtu = (EditTextPreference) findPreference("tunmtu");
        mTunMtu.setOnPreferenceChangeListener(this);;

        onCreateBehaviour(savedInstanceState);
		loadSettings();

	}

    protected void loadSettings() {
        mUseRandomHostName.setChecked(mProfile.mUseRandomHostname);
        mUseFloat.setChecked(mProfile.mUseFloat);
        mUseCustomConfig.setChecked(mProfile.mUseCustomConfig);
        mCustomConfig.setText(mProfile.mCustomConfigOptions);

        if (mProfile.mMssFix == 0) {
            mMssFixValue.setText(String.valueOf(VpnProfile.DEFAULT_MSSFIX_SIZE));
            mMssFixCheckBox.setChecked(false);
            setMssSummary(VpnProfile.DEFAULT_MSSFIX_SIZE);
        } else {
            mMssFixValue.setText(String.valueOf(mProfile.mMssFix));
            mMssFixCheckBox.setChecked(true);
            setMssSummary(mProfile.mMssFix);
        }


        int tunmtu = mProfile.mTunMtu;
        if (mProfile.mTunMtu < 48)
            tunmtu = 1500;

        mTunMtu.setText(String.valueOf(tunmtu));
        setMtuSummary(tunmtu);


        loadSettingsBehaviour();

    }

    private void setMssSummary(int value) {
        mMssFixValue.setSummary(String.format(Locale.getDefault(),"Configured MSS value: %d", value));
    }

    private void setMtuSummary(int value) {
        if (value == 1500)
            mTunMtu.setSummary(String.format(Locale.getDefault(),"Using default (1500) MTU", value));
        else
            mTunMtu.setSummary(String.format(Locale.getDefault(),"Configured MTU value: %d", value));
    }

    protected void saveSettings() {
		mProfile.mUseRandomHostname = mUseRandomHostName.isChecked();
		mProfile.mUseFloat = mUseFloat.isChecked();
		mProfile.mUseCustomConfig = mUseCustomConfig.isChecked();
		mProfile.mCustomConfigOptions = mCustomConfig.getText();
        if (mMssFixCheckBox.isChecked())
            mProfile.mMssFix=Integer.parseInt(mMssFixValue.getText());
        else
            mProfile.mMssFix=0;

        mProfile.mTunMtu = Integer.parseInt(mTunMtu.getText());
        saveSettingsBehaviour();
	}

	
	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference.getKey().equals("mssFixValue"))
            try {
                int v = Integer.parseInt((String) newValue);
                if (v < 0 || v > 9000)
                    throw new NumberFormatException("mssfix value");
                setMssSummary(v);

            } catch(NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.mssfix_invalid_value, Toast.LENGTH_LONG).show();
                return false;
            }
        else if (preference.getKey().equals("tunmtu"))
            try {
                int v = Integer.parseInt((String) newValue);
                if (v < 48 || v > 9000)
                    throw new NumberFormatException("mtu value");
                setMtuSummary(v);

            } catch(NumberFormatException e) {
                Toast.makeText(getActivity(), R.string.mtu_invalid_value, Toast.LENGTH_LONG).show();
                return false;
            }
        return onPreferenceChangeBehaviour(preference, newValue);

	}

}