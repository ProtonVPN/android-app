/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.views;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.VpnProfile;

public class RemoteCNPreference extends DialogPreference {


	private Spinner mSpinner;
	private EditText mEditText;
	private int mDNType;
	private String mDn;
	private TextView mRemoteTLSNote;
	//private ScrollView mScrollView;

	public RemoteCNPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		setDialogLayoutResource(R.layout.tlsremote);

	}

	@Override
	protected void onBindDialogView(View view) {

		super.onBindDialogView(view);

		mEditText = (EditText) view.findViewById(R.id.tlsremotecn);
		mSpinner = (Spinner) view.findViewById(R.id.x509verifytype);
		mRemoteTLSNote = (TextView) view.findViewById(R.id.tlsremotenote);
		//mScrollView = (ScrollView) view.findViewById(R.id.tlsremotescroll);
		if(mDn!=null)
			mEditText.setText(mDn);

		populateSpinner();

	}



	public String getCNText() {
		return mDn;
	}

	public int getAuthtype() {
		return mDNType;
	}

	public void setDN(String dn) {
		mDn = dn;
		if(mEditText!=null)
			mEditText.setText(dn);
	}

	public void setAuthType(int x509authtype) {
		mDNType = x509authtype;
		if (mSpinner!=null)
			populateSpinner();
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);

		if (positiveResult) {
			String dn = mEditText.getText().toString();
			int authtype = getAuthTypeFromSpinner();
			if (callChangeListener(new Pair<Integer, String>(authtype, dn))) {
				mDn = dn;
				mDNType = authtype;
			}
		}
	}
	
	private void populateSpinner() {
		ArrayAdapter<String> authtypes = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_item);
		authtypes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		authtypes.add(getContext().getString(R.string.complete_dn));
		authtypes.add(getContext().getString(R.string.rdn));
		authtypes.add(getContext().getString(R.string.rdn_prefix));
		if ((mDNType == VpnProfile.X509_VERIFY_TLSREMOTE || mDNType == VpnProfile.X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING)
				&& !(mDn==null || "".equals(mDn))) {
			authtypes.add(getContext().getString(R.string.tls_remote_deprecated));
			mRemoteTLSNote.setVisibility(View.VISIBLE);
		} else {
			mRemoteTLSNote.setVisibility(View.GONE);
		}
		mSpinner.setAdapter(authtypes);
		mSpinner.setSelection(getSpinnerPositionFromAuthTYPE());
	}
	
	private int getSpinnerPositionFromAuthTYPE() {
		switch (mDNType) {
		case VpnProfile.X509_VERIFY_TLSREMOTE_DN:
			return 0;
		case VpnProfile.X509_VERIFY_TLSREMOTE_RDN:
			return 1;
		case VpnProfile.X509_VERIFY_TLSREMOTE_RDN_PREFIX:
			return 2;
		case VpnProfile.X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING:
		case VpnProfile.X509_VERIFY_TLSREMOTE:
			if (mDn==null || "".equals(mDn))
				return 1;
			else
				return 3;


		default:
			return 0;
		}
	}
	
	private int getAuthTypeFromSpinner() {
		int pos = mSpinner.getSelectedItemPosition();
		switch (pos) {
		case 0:
			return VpnProfile.X509_VERIFY_TLSREMOTE_DN;
		case 1:
			return VpnProfile.X509_VERIFY_TLSREMOTE_RDN;
		case 2:
			return VpnProfile.X509_VERIFY_TLSREMOTE_RDN_PREFIX;
		case 3:
			// This is the tls-remote entry, only visible if mDntype is a
			// tls-remote type
			return mDNType;
		default:
			return VpnProfile.X509_VERIFY_TLSREMOTE;
		}
	}

}
