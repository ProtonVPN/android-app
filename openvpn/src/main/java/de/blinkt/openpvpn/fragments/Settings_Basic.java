/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.R.id;
import de.blinkt.openpvpn.VpnProfile;
import de.blinkt.openpvpn.views.FileSelectLayout;

public class Settings_Basic extends KeyChainSettingsFragment implements OnItemSelectedListener, FileSelectLayout.FileSelectCallback {
    private static final int CHOOSE_FILE_OFFSET = 1000;

    private FileSelectLayout mClientCert;
    private FileSelectLayout mCaCert;
    private FileSelectLayout mClientKey;
    private CheckBox mUseLzo;
    private Spinner mType;
    private FileSelectLayout mpkcs12;
    private FileSelectLayout mCrlFile;
    private TextView mPKCS12Password;
    private EditText mUserName;
    private EditText mPassword;
    private View mView;
    private EditText mProfileName;
    private EditText mKeyPassword;

    private SparseArray<FileSelectLayout> fileselects = new SparseArray<>();
    private Spinner mAuthRetry;


    private void addFileSelectLayout(FileSelectLayout fsl, Utils.FileType type) {
        int i = fileselects.size() + CHOOSE_FILE_OFFSET;
        fileselects.put(i, fsl);
        fsl.setCaller(this, i, type);
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        mView = inflater.inflate(R.layout.basic_settings, container, false);

        mProfileName = mView.findViewById(id.profilename);
        mClientCert = mView.findViewById(id.certselect);
        mClientKey = mView.findViewById(id.keyselect);
        mCaCert = mView.findViewById(id.caselect);
        mpkcs12 = mView.findViewById(id.pkcs12select);
        mCrlFile = mView.findViewById(id.crlfile);
        mUseLzo = mView.findViewById(id.lzo);
        mType = mView.findViewById(id.type);
        mPKCS12Password = mView.findViewById(id.pkcs12password);

        mUserName = mView.findViewById(id.auth_username);
        mPassword = mView.findViewById(id.auth_password);
        mKeyPassword = mView.findViewById(id.key_password);
        mAuthRetry = mView.findViewById(id.auth_retry);

        addFileSelectLayout(mCaCert, Utils.FileType.CA_CERTIFICATE);
        addFileSelectLayout(mClientCert, Utils.FileType.CLIENT_CERTIFICATE);
        addFileSelectLayout(mClientKey, Utils.FileType.KEYFILE);
        addFileSelectLayout(mpkcs12, Utils.FileType.PKCS12);
        addFileSelectLayout(mCrlFile, Utils.FileType.CRL_FILE);
        mCaCert.setShowClear();
        mCrlFile.setShowClear();

        mType.setOnItemSelectedListener(this);
        mAuthRetry.setOnItemSelectedListener(this);

        initKeychainViews(mView);

        return mView;
    }


    @Override
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result == Activity.RESULT_OK && request >= CHOOSE_FILE_OFFSET) {
            FileSelectLayout fsl = fileselects.get(request);
            fsl.parseResponse(data, getActivity());

            savePreferences();

            // Private key files may result in showing/hiding the private key password dialog
            if (fsl == mClientKey) {
                changeType(mType.getSelectedItemPosition());
            }
        }

    }


    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mType) {
            changeType(position);
        }
    }


    private void changeType(int type) {
        // hide everything
        mView.findViewById(R.id.pkcs12).setVisibility(View.GONE);
        mView.findViewById(R.id.certs).setVisibility(View.GONE);
        mView.findViewById(R.id.statickeys).setVisibility(View.GONE);
        mView.findViewById(R.id.keystore).setVisibility(View.GONE);
        mView.findViewById(R.id.cacert).setVisibility(View.GONE);
        ((FileSelectLayout) mView.findViewById(R.id.caselect)).setClearable(false);
        mView.findViewById(R.id.userpassword).setVisibility(View.GONE);
        mView.findViewById(R.id.key_password_layout).setVisibility(View.GONE);
        mView.findViewById(R.id.external_auth).setVisibility(View.GONE);

        // Fall through are by design
        switch (type) {
            case VpnProfile.TYPE_USERPASS_CERTIFICATES:
                mView.findViewById(R.id.userpassword).setVisibility(View.VISIBLE);
            case VpnProfile.TYPE_CERTIFICATES:
                mView.findViewById(R.id.certs).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.cacert).setVisibility(View.VISIBLE);
                if (mProfile.requireTLSKeyPassword())
                    mView.findViewById(R.id.key_password_layout).setVisibility(View.VISIBLE);
                break;

            case VpnProfile.TYPE_USERPASS_PKCS12:
                mView.findViewById(R.id.userpassword).setVisibility(View.VISIBLE);
            case VpnProfile.TYPE_PKCS12:
                mView.findViewById(R.id.pkcs12).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.cacert).setVisibility(View.VISIBLE);
                ((FileSelectLayout) mView.findViewById(R.id.caselect)).setClearable(true);
                break;

            case VpnProfile.TYPE_STATICKEYS:
                mView.findViewById(R.id.statickeys).setVisibility(View.VISIBLE);
                break;

            case VpnProfile.TYPE_USERPASS_KEYSTORE:
                mView.findViewById(R.id.userpassword).setVisibility(View.VISIBLE);
            case VpnProfile.TYPE_KEYSTORE:
                mView.findViewById(R.id.keystore).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.cacert).setVisibility(View.VISIBLE);
                ((FileSelectLayout) mView.findViewById(R.id.caselect)).setClearable(true);
                break;

            case VpnProfile.TYPE_USERPASS:
                mView.findViewById(R.id.userpassword).setVisibility(View.VISIBLE);
                mView.findViewById(R.id.cacert).setVisibility(View.VISIBLE);
                break;
            case VpnProfile.TYPE_EXTERNAL_APP:
                mView.findViewById(R.id.external_auth).setVisibility(View.VISIBLE);
                break;
        }


    }

    protected void loadPreferences() {
        super.loadPreferences();
        mProfileName.setText(mProfile.mName);
        mClientCert.setData(mProfile.mClientCertFilename, getActivity());
        mClientKey.setData(mProfile.mClientKeyFilename, getActivity());
        mCaCert.setData(mProfile.mCaFilename, getActivity());
        mCrlFile.setData(mProfile.mCrlFilename, getActivity());

        mUseLzo.setChecked(mProfile.mUseLzo);
        mType.setSelection(mProfile.mAuthenticationType);
        mpkcs12.setData(mProfile.mPKCS12Filename, getActivity());
        mPKCS12Password.setText(mProfile.mPKCS12Password);
        mUserName.setText(mProfile.mUsername);
        mPassword.setText(mProfile.mPassword);
        mKeyPassword.setText(mProfile.mKeyPassword);
        mAuthRetry.setSelection(mProfile.mAuthRetry);
    }

    protected void savePreferences() {
        super.savePreferences();
        mProfile.mName = mProfileName.getText().toString();
        mProfile.mCaFilename = mCaCert.getData();
        mProfile.mClientCertFilename = mClientCert.getData();
        mProfile.mClientKeyFilename = mClientKey.getData();
        mProfile.mCrlFilename = mCrlFile.getData();

        mProfile.mUseLzo = mUseLzo.isChecked();
        mProfile.mAuthenticationType = mType.getSelectedItemPosition();
        mProfile.mPKCS12Filename = mpkcs12.getData();
        mProfile.mPKCS12Password = mPKCS12Password.getText().toString();

        mProfile.mPassword = mPassword.getText().toString();
        mProfile.mUsername = mUserName.getText().toString();
        mProfile.mKeyPassword = mKeyPassword.getText().toString();
        mProfile.mAuthRetry = mAuthRetry.getSelectedItemPosition();

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        savePreferences();
        if (mProfile != null) {
            outState.putString(getActivity().getPackageName() + "profileUUID", mProfile.getUUID().toString());
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }


}
