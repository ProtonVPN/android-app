/*
 * Copyright (c) 2012-2018 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.VpnProfile;
import de.blinkt.openpvpn.core.ExtAuthHelper;
import de.blinkt.openpvpn.core.X509Utils;

import java.security.cert.X509Certificate;

abstract class KeyChainSettingsFragment extends Settings_Fragment implements View.OnClickListener, Handler.Callback {
    private static final int UPDATE_ALIAS = 20;
    private static final int UPDATEE_EXT_ALIAS = 210;


    private TextView mAliasCertificate;
    private TextView mAliasName;
    private Handler mHandler;
    private TextView mExtAliasName;
    private Spinner mExtAuthSpinner;


    private void setKeyStoreAlias() {
        if (mProfile.mAlias == null) {
            mAliasName.setText(R.string.client_no_certificate);
            mAliasCertificate.setText("");
        } else {
            mAliasCertificate.setText("Loading certificate from Keystore...");
            mAliasName.setText(mProfile.mAlias);
            setCertificate(false);
        }
    }

    private void setExtAlias() {
        if (mProfile.mAlias == null) {
            mExtAliasName.setText(R.string.extauth_not_configured);
            mAliasCertificate.setText("");
        } else {
            mAliasCertificate.setText("Querying certificate from external provider...");
            mExtAliasName.setText("");
            setCertificate(true);
        }
    }

    private void fetchExtCertificateMetaData() {
        new Thread() {
            @Override
            public void run() {
                try {
                    Bundle b = ExtAuthHelper.getCertificateMetaData(getActivity(), mProfile.mExternalAuthenticator, mProfile.mAlias);
                    mProfile.mAlias = b.getString(ExtAuthHelper.EXTRA_ALIAS);
                    getActivity().runOnUiThread(() -> setAlias());
                } catch (KeyChainException e) {
                    e.printStackTrace();
                }

            }
        }.start();
    }


    protected void setCertificate(boolean external) {
        new Thread() {
            public void run() {
                String certstr = "";
                Bundle metadata= null;
                try {
                    X509Certificate cert;

                    if (external) {
                        if (!TextUtils.isEmpty(mProfile.mExternalAuthenticator) && !TextUtils.isEmpty(mProfile.mAlias)) {
                            cert = ExtAuthHelper.getCertificateChain(getActivity(), mProfile.mExternalAuthenticator, mProfile.mAlias)[0];
                            metadata = ExtAuthHelper.getCertificateMetaData(getActivity(), mProfile.mExternalAuthenticator, mProfile.mAlias);
                        } else {
                            cert = null;
                            certstr = getString(R.string.extauth_not_configured);
                        }
                    } else {
                        cert = KeyChain.getCertificateChain(getActivity().getApplicationContext(), mProfile.mAlias)[0];
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            {
                                if (isInHardwareKeystore())
                                    certstr += getString(R.string.hwkeychain);
                            }
                        }
                    }
                    if (cert!=null) {
                        certstr += X509Utils.getCertificateValidityString(cert, getResources());
                        certstr += X509Utils.getCertificateFriendlyName(cert);
                    }



                } catch (Exception e) {
                    certstr = "Could not get certificate from Keystore: " + e.getLocalizedMessage();
                }

                final String certStringCopy = certstr;
                Bundle finalMetadata = metadata;
                getActivity().runOnUiThread(() -> {
                    mAliasCertificate.setText(certStringCopy);
                    if (finalMetadata!=null)
                        mExtAliasName.setText(finalMetadata.getString(ExtAuthHelper.EXTRA_DESCRIPTION));

                });

            }
        }.start();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean isInHardwareKeystore() throws KeyChainException, InterruptedException {
        String algorithm = KeyChain.getPrivateKey(getActivity().getApplicationContext(), mProfile.mAlias).getAlgorithm();
        return KeyChain.isBoundKeyAlgorithm(algorithm);
    }

    protected void initKeychainViews(View v) {
        v.findViewById(R.id.select_keystore_button).setOnClickListener(this);
        v.findViewById(R.id.configure_extauth_button).setOnClickListener(this);
        mAliasCertificate = v.findViewById(R.id.alias_certificate);
        mExtAuthSpinner = v.findViewById(R.id.extauth_spinner);
        mExtAuthSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ExtAuthHelper.ExternalAuthProvider selectedProvider = (ExtAuthHelper.ExternalAuthProvider) parent.getItemAtPosition(position);
                if (!selectedProvider.packageName.equals(mProfile.mExternalAuthenticator)) {
                    mProfile.mAlias = "";
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        mExtAliasName = v.findViewById(R.id.extauth_detail);
        mAliasName = v.findViewById(R.id.aliasname);
        if (mHandler == null) {
            mHandler = new Handler(this);
        }
        ExtAuthHelper.setExternalAuthProviderSpinnerList(mExtAuthSpinner, mProfile.mExternalAuthenticator);
    }

    @Override
    public void onClick(View v) {
        if (v == v.findViewById(R.id.select_keystore_button)) {
            showCertDialog();
        } else if (v == v.findViewById(R.id.configure_extauth_button)) {
            startExternalAuthConfig();
        }
    }

    private void startExternalAuthConfig() {
        ExtAuthHelper.ExternalAuthProvider eAuth = (ExtAuthHelper.ExternalAuthProvider) mExtAuthSpinner.getSelectedItem();
        mProfile.mExternalAuthenticator = eAuth.packageName;
        if (!eAuth.configurable) {
            fetchExtCertificateMetaData();
            return;
        }
        Intent extauth = new Intent(ExtAuthHelper.ACTION_CERT_CONFIGURATION);
        extauth.setPackage(eAuth.packageName);
        extauth.putExtra(ExtAuthHelper.EXTRA_ALIAS, mProfile.mAlias);
        startActivityForResult(extauth, UPDATEE_EXT_ALIAS);
    }

    @Override
    protected void savePreferences() {

    }

    @Override
    public void onStart() {
        super.onStart();
        loadPreferences();
    }

    @SuppressWarnings("WrongConstant")
    public void showCertDialog() {
        try {
            KeyChain.choosePrivateKeyAlias(getActivity(),
                    alias -> {
                        // Credential alias selected.  Remember the alias selection for future use.
                        mProfile.mAlias = alias;
                        mHandler.sendEmptyMessage(UPDATE_ALIAS);
                    },
                    new String[]{"RSA"}, // List of acceptable key types. null for any
                    null,                        // issuer, null for any
                    mProfile.mServerName,      // host name of server requesting the cert, null if unavailable
                    -1,                         // port of server requesting the cert, -1 if unavailable
                    mProfile.mAlias);                       // alias to preselect, null if unavailable
        } catch (ActivityNotFoundException anf) {
            AlertDialog.Builder ab = new AlertDialog.Builder(getActivity());
            ab.setTitle(R.string.broken_image_cert_title);
            ab.setMessage(R.string.broken_image_cert);
            ab.setPositiveButton(android.R.string.ok, null);
            ab.show();
        }
    }

    protected void loadPreferences() {
        setAlias();

    }

    private void setAlias() {
        if (mProfile.mAuthenticationType == VpnProfile.TYPE_EXTERNAL_APP)
            setExtAlias();
        else
            setKeyStoreAlias();
    }

    @Override
    public boolean handleMessage(Message msg) {
        setAlias();
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == UPDATEE_EXT_ALIAS && resultCode == Activity.RESULT_OK) {
            mProfile.mAlias = data.getStringExtra(ExtAuthHelper.EXTRA_ALIAS);
            mExtAliasName.setText(data.getStringExtra(ExtAuthHelper.EXTRA_DESCRIPTION));
        }
    }
}
