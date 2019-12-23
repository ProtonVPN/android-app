/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.Pair;
import de.blinkt.openpvpn.activities.FileSelect;
import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.core.VpnStatus;
import de.blinkt.openpvpn.views.RemoteCNPreference;
import de.blinkt.openpvpn.VpnProfile;

import java.io.IOException;


public class Settings_Authentication extends OpenVpnPreferencesFragment implements OnPreferenceChangeListener, OnPreferenceClickListener {
	private static final int SELECT_TLS_FILE_LEGACY_DIALOG = 23223232;
    private static final int SELECT_TLS_FILE_KITKAT = SELECT_TLS_FILE_LEGACY_DIALOG +1;
    private CheckBoxPreference mExpectTLSCert;
	private CheckBoxPreference mCheckRemoteCN;
	private RemoteCNPreference mRemoteCN;
	private ListPreference mTLSAuthDirection;
	private Preference mTLSAuthFile;
	private SwitchPreference mUseTLSAuth;
	private EditTextPreference mCipher;
	private String mTlsAuthFileData;
	private EditTextPreference mAuth;
    private EditTextPreference mRemoteX509Name;

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.vpn_authentification);

		mExpectTLSCert = (CheckBoxPreference) findPreference("remoteServerTLS");
		mCheckRemoteCN = (CheckBoxPreference) findPreference("checkRemoteCN");
		mRemoteCN = (RemoteCNPreference) findPreference("remotecn");
		mRemoteCN.setOnPreferenceChangeListener(this);

		mRemoteX509Name = (EditTextPreference) findPreference("remotex509name");
        mRemoteX509Name.setOnPreferenceChangeListener(this);

		mUseTLSAuth = (SwitchPreference) findPreference("useTLSAuth" );
		mTLSAuthFile = findPreference("tlsAuthFile");
		mTLSAuthDirection = (ListPreference) findPreference("tls_direction");


		mTLSAuthFile.setOnPreferenceClickListener(this);		

		mCipher =(EditTextPreference) findPreference("cipher");
		mCipher.setOnPreferenceChangeListener(this);

		mAuth =(EditTextPreference) findPreference("auth");
		mAuth.setOnPreferenceChangeListener(this);

		loadSettings();

	}

	@Override
	protected void loadSettings() {

		mExpectTLSCert.setChecked(mProfile.mExpectTLSCert);
		mCheckRemoteCN.setChecked(mProfile.mCheckRemoteCN);
		mRemoteCN.setDN(mProfile.mRemoteCN);
		mRemoteCN.setAuthType(mProfile.mX509AuthType);
		onPreferenceChange(mRemoteCN,
				new Pair<Integer, String>(mProfile.mX509AuthType, mProfile.mRemoteCN));

        mRemoteX509Name.setText(mProfile.mx509UsernameField);
        onPreferenceChange(mRemoteX509Name, mProfile.mx509UsernameField);

		mUseTLSAuth.setChecked(mProfile.mUseTLSAuth);
		mTlsAuthFileData= mProfile.mTLSAuthFilename;
		setTlsAuthSummary(mTlsAuthFileData);
		mTLSAuthDirection.setValue(mProfile.mTLSAuthDirection);
		mCipher.setText(mProfile.mCipher);
		onPreferenceChange(mCipher, mProfile.mCipher);
		mAuth.setText(mProfile.mAuth);
		onPreferenceChange(mAuth, mProfile.mAuth);

        if (mProfile.mAuthenticationType == VpnProfile.TYPE_STATICKEYS) {
            mExpectTLSCert.setEnabled(false);
            mCheckRemoteCN.setEnabled(false);
            mUseTLSAuth.setChecked(true);
        } else {
            mExpectTLSCert.setEnabled(true);
            mCheckRemoteCN.setEnabled(true);

        }
	}

	@Override
	protected void saveSettings() {
		mProfile.mExpectTLSCert=mExpectTLSCert.isChecked();
		mProfile.mCheckRemoteCN=mCheckRemoteCN.isChecked();
		mProfile.mRemoteCN=mRemoteCN.getCNText();
		mProfile.mX509AuthType=mRemoteCN.getAuthtype();

		mProfile.mUseTLSAuth = mUseTLSAuth.isChecked();
		mProfile.mTLSAuthFilename = mTlsAuthFileData;
        mProfile.mx509UsernameField = mRemoteX509Name.getText();

		if(mTLSAuthDirection.getValue()==null)
			mProfile.mTLSAuthDirection=null;
		else
			mProfile.mTLSAuthDirection = mTLSAuthDirection.getValue();

		if(mCipher.getText()==null)
			mProfile.mCipher=null;
		else
			mProfile.mCipher = mCipher.getText();

		if(mAuth.getText()==null)
			mProfile.mAuth = null;
		else
			mProfile.mAuth = mAuth.getText();

	}



	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if(preference==mRemoteCN) {
			@SuppressWarnings("unchecked") 
			int authtype = ((Pair<Integer, String>) newValue).first;
			@SuppressWarnings("unchecked")
			String dn = ((Pair<Integer, String>) newValue).second;

			if ("".equals(dn)) {
                if (mProfile.mConnections.length > 0) {
                    preference.setSummary(getX509String(VpnProfile.X509_VERIFY_TLSREMOTE_RDN, mProfile.mConnections[0].mServerName));
                } else {
                    preference.setSummary(R.string.no_remote_defined);
                }
            } else {
                preference.setSummary(getX509String(authtype, dn));
            }

		} else if (preference == mCipher || preference == mAuth) {
			preference.setSummary((CharSequence) newValue);
		} else if (preference == mRemoteX509Name) {
            preference.setSummary(TextUtils.isEmpty((CharSequence) newValue) ? "CN (default)" : (CharSequence)newValue);
        }
		return true;
	}
	private CharSequence getX509String(int authtype, String dn) {
		String ret ="";
		switch (authtype) {
		case VpnProfile.X509_VERIFY_TLSREMOTE:
		case VpnProfile.X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING:
			ret+="tls-remote ";
			break;

		case VpnProfile.X509_VERIFY_TLSREMOTE_DN:
			ret="dn: ";
			break;

		case VpnProfile.X509_VERIFY_TLSREMOTE_RDN:
			ret="rdn: ";
			break;

		case VpnProfile.X509_VERIFY_TLSREMOTE_RDN_PREFIX:
			ret="rdn prefix: ";
			break;
		}
		return ret + dn;
	}

    void startFileDialog() {
        Intent startFC = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && ! Utils.alwaysUseOldFileChooser(getActivity())) {
            startFC = Utils.getFilePickerIntent(getActivity(), Utils.FileType.TLS_AUTH_FILE);
            startActivityForResult(startFC, SELECT_TLS_FILE_KITKAT);
        }

        if (startFC == null) {
            startFC = new Intent(getActivity(), FileSelect.class);
            startFC.putExtra(FileSelect.START_DATA, mTlsAuthFileData);
            startFC.putExtra(FileSelect.WINDOW_TITLE, R.string.tls_auth_file);
            startActivityForResult(startFC, SELECT_TLS_FILE_LEGACY_DIALOG);
        }
    }

    @Override
	public boolean onPreferenceClick(Preference preference) {
		startFileDialog();
		return true;

	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == SELECT_TLS_FILE_LEGACY_DIALOG && resultCode == Activity.RESULT_OK){
			String result = data.getStringExtra(FileSelect.RESULT_DATA);
			mTlsAuthFileData=result;
			setTlsAuthSummary(result);
		}  else if (requestCode == SELECT_TLS_FILE_KITKAT && resultCode == Activity.RESULT_OK) {
            try {
                mTlsAuthFileData= Utils.getFilePickerResult(Utils.FileType.TLS_AUTH_FILE,data,getActivity());
                setTlsAuthSummary(mTlsAuthFileData);
            } catch (IOException e) {
                VpnStatus.logException(e);
            } catch (SecurityException se) {
                VpnStatus.logException(se);
            }
        }
	}

	private void setTlsAuthSummary(String result) {
		if(result==null)
            result = getString(R.string.no_certificate);
		if(result.startsWith(VpnProfile.INLINE_TAG))
			mTLSAuthFile.setSummary(R.string.inline_file_data);
        else if (result.startsWith(VpnProfile.DISPLAYNAME_TAG))
			mTLSAuthFile.setSummary(getString(R.string.imported_from_file, VpnProfile.getDisplayName(result)));
		else
			mTLSAuthFile.setSummary(result);
	}
}