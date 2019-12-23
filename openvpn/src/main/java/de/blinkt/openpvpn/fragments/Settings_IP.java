/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.VpnProfile;

public class Settings_IP extends OpenVpnPreferencesFragment implements OnPreferenceChangeListener {
		private EditTextPreference mIPv4;
		private EditTextPreference mIPv6;
		private SwitchPreference mUsePull;
		private CheckBoxPreference mOverrideDNS;
		private EditTextPreference mSearchdomain;
		private EditTextPreference mDNS1;
		private EditTextPreference mDNS2;
		private CheckBoxPreference mNobind;

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			
		
			// Make sure default values are applied.  In a real app, you would
			// want this in a shared function that is used to retrieve the
			// SharedPreferences wherever they are needed.
			PreferenceManager.setDefaultValues(getActivity(),
					R.xml.vpn_ipsettings, false);
			
			// Load the preferences from an XML resource
			addPreferencesFromResource(R.xml.vpn_ipsettings);
			mIPv4 = (EditTextPreference) findPreference("ipv4_address");
			mIPv6 = (EditTextPreference) findPreference("ipv6_address");
			mUsePull = (SwitchPreference) findPreference("usePull");
			mOverrideDNS = (CheckBoxPreference) findPreference("overrideDNS");
			mSearchdomain =(EditTextPreference) findPreference("searchdomain");
			mDNS1 = (EditTextPreference) findPreference("dns1");
			mDNS2 = (EditTextPreference) findPreference("dns2");
			mNobind = (CheckBoxPreference) findPreference("nobind");

			mIPv4.setOnPreferenceChangeListener(this);
			mIPv6.setOnPreferenceChangeListener(this);
			mDNS1.setOnPreferenceChangeListener(this);
			mDNS2.setOnPreferenceChangeListener(this);
			mUsePull.setOnPreferenceChangeListener(this);
			mOverrideDNS.setOnPreferenceChangeListener(this);
			mSearchdomain.setOnPreferenceChangeListener(this);
						
			loadSettings();
		}

		@Override
		protected void loadSettings() {

			mUsePull.setChecked(mProfile.mUsePull);
			mIPv4.setText(mProfile.mIPv4Address);
			mIPv6.setText(mProfile.mIPv6Address);
			mDNS1.setText(mProfile.mDNS1);
			mDNS2.setText(mProfile.mDNS2);
			mOverrideDNS.setChecked(mProfile.mOverrideDNS);
			mSearchdomain.setText(mProfile.mSearchDomain);
			mNobind.setChecked(mProfile.mNobind);
            if (mProfile.mAuthenticationType == VpnProfile.TYPE_STATICKEYS)
                mUsePull.setChecked(false);

			mUsePull.setEnabled(mProfile.mAuthenticationType != VpnProfile.TYPE_STATICKEYS);

			// Sets Summary
			onPreferenceChange(mIPv4, mIPv4.getText());
			onPreferenceChange(mIPv6, mIPv6.getText());
			onPreferenceChange(mDNS1, mDNS1.getText());
			onPreferenceChange(mDNS2, mDNS2.getText());
			onPreferenceChange(mSearchdomain, mSearchdomain.getText());

			setDNSState();
		}
		
		
		@Override
		protected void saveSettings() {
			mProfile.mUsePull = mUsePull.isChecked();
			mProfile.mIPv4Address = mIPv4.getText();
			mProfile.mIPv6Address = mIPv6.getText();
			mProfile.mDNS1 = mDNS1.getText();
			mProfile.mDNS2 = mDNS2.getText();
			mProfile.mOverrideDNS = mOverrideDNS.isChecked();
			mProfile.mSearchDomain = mSearchdomain.getText();
			mProfile.mNobind = mNobind.isChecked();
			
		}
		
		@Override
		public boolean onPreferenceChange(Preference preference,
				Object newValue) {
			if(preference==mIPv4 || preference == mIPv6 
					 || preference==mDNS1 || preference == mDNS2
					 || preference == mSearchdomain 
					)
			
				preference.setSummary((String)newValue);

			if(preference== mUsePull || preference == mOverrideDNS)
				if(preference==mOverrideDNS) { 
					// Set so the function gets the right value
					mOverrideDNS.setChecked((Boolean) newValue);
				}
				setDNSState();
			
			saveSettings();
			return true;
		}

		private void setDNSState() {
			boolean enabled;
			mOverrideDNS.setEnabled(mUsePull.isChecked());
			if(!mUsePull.isChecked())
				enabled =true;
			else
                enabled = mOverrideDNS.isChecked();

			mDNS1.setEnabled(enabled);
			mDNS2.setEnabled(enabled);
			mSearchdomain.setEnabled(enabled);
			

		}


	}