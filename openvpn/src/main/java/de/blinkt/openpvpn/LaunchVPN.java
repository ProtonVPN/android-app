/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import java.io.IOException;

import de.blinkt.openpvpn.activities.LogWindow;
import de.blinkt.openpvpn.api.ExternalAppDatabase;
import de.blinkt.openpvpn.core.Connection;
import de.blinkt.openpvpn.core.ConnectionStatus;
import de.blinkt.openpvpn.core.IServiceStatus;
import de.blinkt.openpvpn.core.OpenVPNStatusService;
import de.blinkt.openpvpn.core.PasswordCache;
import de.blinkt.openpvpn.core.Preferences;
import de.blinkt.openpvpn.core.ProfileManager;
import de.blinkt.openpvpn.core.VPNLaunchHelper;
import de.blinkt.openpvpn.core.VpnStatus;

/**
 * This Activity actually handles two stages of a launcher shortcut's life cycle.
 * <p/>
 * 1. Your application offers to provide shortcuts to the launcher.  When
 * the user installs a shortcut, an activity within your application
 * generates the actual shortcut and returns it to the launcher, where it
 * is shown to the user as an icon.
 * <p/>
 * 2. Any time the user clicks on an installed shortcut, an intent is sent.
 * Typically this would then be handled as necessary by an activity within
 * your application.
 * <p/>
 * We handle stage 1 (creating a shortcut) by simply sending back the information (in the form
 * of an {@link android.content.Intent} that the launcher will use to create the shortcut.
 * <p/>
 * You can also implement this in an interactive way, by having your activity actually present
 * UI for the user to select the specific nature of the shortcut, such as a contact, picture, URL,
 * media item, or action.
 * <p/>
 * We handle stage 2 (responding to a shortcut) in this sample by simply displaying the contents
 * of the incoming {@link android.content.Intent}.
 * <p/>
 * In a real application, you would probably use the shortcut intent to display specific content
 * or start a particular operation.
 */
public class LaunchVPN extends Activity {

    public static final String EXTRA_KEY = "de.blinkt.openpvpn.shortcutProfileUUID";
    public static final String EXTRA_NAME = "de.blinkt.openpvpn.shortcutProfileName";
    public static final String EXTRA_HIDELOG = "de.blinkt.openpvpn.showNoLogWindow";
    public static final String CLEARLOG = "clearlogconnect";


    private static final int START_VPN_PROFILE = 70;


    private VpnProfile mSelectedProfile;
    private boolean mhideLog = false;

    private boolean mCmfixed = false;
    private String mTransientAuthPW;
    private String mTransientCertOrPCKS12PW;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.launchvpn);
        startVpnFromIntent();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder binder) {
            IServiceStatus service = IServiceStatus.Stub.asInterface(binder);
            try {
                if (mTransientAuthPW != null)

                    service.setCachedPassword(mSelectedProfile.getUUIDString(), PasswordCache.AUTHPASSWORD, mTransientAuthPW);
                if (mTransientCertOrPCKS12PW != null)
                    service.setCachedPassword(mSelectedProfile.getUUIDString(), PasswordCache.PCKS12ORCERTPASSWORD, mTransientCertOrPCKS12PW);

                onActivityResult(START_VPN_PROFILE, Activity.RESULT_OK, null);

            } catch (RemoteException e) {
                e.printStackTrace();
            }

            unbindService(this);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    protected void startVpnFromIntent() {
        // Resolve the intent

        final Intent intent = getIntent();
        final String action = intent.getAction();

        // If the intent is a request to create a shortcut, we'll do that and exit


        if (Intent.ACTION_MAIN.equals(action)) {
            // Check if we need to clear the log
            if (Preferences.getDefaultSharedPreferences(this).getBoolean(CLEARLOG, true))
                VpnStatus.clearLog();

            // we got called to be the starting point, most likely a shortcut
            String shortcutUUID = intent.getStringExtra(EXTRA_KEY);
            String shortcutName = intent.getStringExtra(EXTRA_NAME);
            mhideLog = intent.getBooleanExtra(EXTRA_HIDELOG, false);

        //    VpnProfile profileToConnect = ProfileManager.get(this, shortcutUUID);
            VpnProfile profileToConnect = new VpnProfile("dk.protonvpn.com");
            profileToConnect.mAuthenticationType = VpnProfile.TYPE_USERPASS;
            profileToConnect.mCaFilename = "[[INLINE]]-----BEGIN CERTIFICATE-----\n" +
                    "MIIFozCCA4ugAwIBAgIBATANBgkqhkiG9w0BAQ0FADBAMQswCQYDVQQGEwJDSDEV\n" +
                    "MBMGA1UEChMMUHJvdG9uVlBOIEFHMRowGAYDVQQDExFQcm90b25WUE4gUm9vdCBD\n" +
                    "QTAeFw0xNzAyMTUxNDM4MDBaFw0yNzAyMTUxNDM4MDBaMEAxCzAJBgNVBAYTAkNI\n" +
                    "MRUwEwYDVQQKEwxQcm90b25WUE4gQUcxGjAYBgNVBAMTEVByb3RvblZQTiBSb290\n" +
                    "IENBMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt+BsSsZg7+AuqTq7\n" +
                    "vDbPzfygtl9f8fLJqO4amsyOXlI7pquL5IsEZhpWyJIIvYybqS4s1/T7BbvHPLVE\n" +
                    "wlrq8A5DBIXcfuXrBbKoYkmpICGc2u1KYVGOZ9A+PH9z4Tr6OXFfXRnsbZToie8t\n" +
                    "2Xjv/dZDdUDAqeW89I/mXg3k5x08m2nfGCQDm4gCanN1r5MT7ge56z0MkY3FFGCO\n" +
                    "qRwspIEUzu1ZqGSTkG1eQiOYIrdOF5cc7n2APyvBIcfvp/W3cpTOEmEBJ7/14RnX\n" +
                    "nHo0fcx61Inx/6ZxzKkW8BMdGGQF3tF6u2M0FjVN0lLH9S0ul1TgoOS56yEJ34hr\n" +
                    "JSRTqHuar3t/xdCbKFZjyXFZFNsXVvgJu34CNLrHHTGJj9jiUfFnxWQYMo9UNUd4\n" +
                    "a3PPG1HnbG7LAjlvj5JlJ5aqO5gshdnqb9uIQeR2CdzcCJgklwRGCyDT1pm7eoiv\n" +
                    "WV19YBd81vKulLzgPavu3kRRe83yl29It2hwQ9FMs5w6ZV/X6ciTKo3etkX9nBD9\n" +
                    "ZzJPsGQsBUy7CzO1jK4W01+u3ItmQS+1s4xtcFxdFY8o/q1zoqBlxpe5MQIWN6Qa\n" +
                    "lryiET74gMHE/S5WrPlsq/gehxsdgc6GDUXG4dk8vn6OUMa6wb5wRO3VXGEc67IY\n" +
                    "m4mDFTYiPvLaFOxtndlUWuCruKcCAwEAAaOBpzCBpDAMBgNVHRMEBTADAQH/MB0G\n" +
                    "A1UdDgQWBBSDkIaYhLVZTwyLNTetNB2qV0gkVDBoBgNVHSMEYTBfgBSDkIaYhLVZ\n" +
                    "TwyLNTetNB2qV0gkVKFEpEIwQDELMAkGA1UEBhMCQ0gxFTATBgNVBAoTDFByb3Rv\n" +
                    "blZQTiBBRzEaMBgGA1UEAxMRUHJvdG9uVlBOIFJvb3QgQ0GCAQEwCwYDVR0PBAQD\n" +
                    "AgEGMA0GCSqGSIb3DQEBDQUAA4ICAQCYr7LpvnfZXBCxVIVc2ea1fjxQ6vkTj0zM\n" +
                    "htFs3qfeXpMRf+g1NAh4vv1UIwLsczilMt87SjpJ25pZPyS3O+/VlI9ceZMvtGXd\n" +
                    "MGfXhTDp//zRoL1cbzSHee9tQlmEm1tKFxB0wfWd/inGRjZxpJCTQh8oc7CTziHZ\n" +
                    "ufS+Jkfpc4Rasr31fl7mHhJahF1j/ka/OOWmFbiHBNjzmNWPQInJm+0ygFqij5qs\n" +
                    "51OEvubR8yh5Mdq4TNuWhFuTxpqoJ87VKaSOx/Aefca44Etwcj4gHb7LThidw/ky\n" +
                    "zysZiWjyrbfX/31RX7QanKiMk2RDtgZaWi/lMfsl5O+6E2lJ1vo4xv9pW8225B5X\n" +
                    "eAeXHCfjV/vrrCFqeCprNF6a3Tn/LX6VNy3jbeC+167QagBOaoDA01XPOx7Odhsb\n" +
                    "Gd7cJ5VkgyycZgLnT9zrChgwjx59JQosFEG1DsaAgHfpEl/N3YPJh68N7fwN41Cj\n" +
                    "zsk39v6iZdfuet/sP7oiP5/gLmA/CIPNhdIYxaojbLjFPkftVjVPn49RqwqzJJPR\n" +
                    "N8BOyb94yhQ7KO4F3IcLT/y/dsWitY0ZH4lCnAVV/v2YjWAWS3OWyC8BFx/Jmc3W\n" +
                    "DK/yPwECUcPgHIeXiRjHnJt0Zcm23O2Q3RphpU+1SO3XixsXpOVOYP6rJIXW9bMZ\n" +
                    "A1gTTlpi7A==\n" +
                    "-----END CERTIFICATE-----\n" +
                    "-----BEGIN CERTIFICATE-----\n" +
                    "MIIFszCCA5ugAwIBAgIBBjANBgkqhkiG9w0BAQ0FADBAMQswCQYDVQQGEwJDSDEV\n" +
                    "MBMGA1UEChMMUHJvdG9uVlBOIEFHMRowGAYDVQQDExFQcm90b25WUE4gUm9vdCBD\n" +
                    "QTAeFw0xNzAyMTUxNTE3MDBaFw0yNzAyMTUxNDM4MDBaMEoxCzAJBgNVBAYTAkNI\n" +
                    "MRUwEwYDVQQKEwxQcm90b25WUE4gQUcxJDAiBgNVBAMTG1Byb3RvblZQTiBJbnRl\n" +
                    "cm1lZGlhdGUgQ0EgMTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCCAgoCggIBANv3\n" +
                    "uwQMFjYOx74taxadhczLbjCTuT73jMz09EqFNv7O7UesXfYJ6kQgYV9YyE86znP4\n" +
                    "xbsswNUZYh+XdZUpOoP6Zu3tR/iiYiuzi6jVYrJ66G89nPqS2mm5dn8Fbb8CRWkJ\n" +
                    "ygm8AdlYkDwYNldhDUrERlQdCRDGsYYg/98dded+5pXnSG8Y/+iuLM6/YYhkUVQe\n" +
                    "Cfq1L6XguSwu8CuvJjIjjE1PptUHa3Hc3tGziVydltKynxWlqb1dJqinGKiBZvYn\n" +
                    "oiV4motpFYwhc3Wd09JLPzeobhD2IAZ2evSatikMWDingEv1EJXpI+V/E2AK3xHK\n" +
                    "Skhw+YZx99tNxCiOu3U5BFAreZR3j2YnZzX1nEv9p02IGaWzzYJPNED0zSO2w07u\n" +
                    "thSmKcxA39VTvs91lptbcV7VTxoJY0SErHIeVS3Scrnr7WvoOTuu3M3SCRqe6oI9\n" +
                    "oJZMOdfNsceBdvG+qlpOFICoBjO53W4BK8KahzTd/PWlBRiVJ3UVv8xXwUDA+o98\n" +
                    "34DXVAobaAHXQtM9jNobqT98FXhZktjOQEA2UORL581ZPxfKeHLRcgWJ5dmPsDBG\n" +
                    "y/L6/qW/yrm6DUDAdN5+q41+gSNEjNBjLBJQFUmDk3l6Qxiu0uEDQ98oFvGHk5US\n" +
                    "2Kbj0OAq1RpiDjHci/536yua9rTC+cxekTM2asdXAgMBAAGjga0wgaowEgYDVR0T\n" +
                    "AQH/BAgwBgEB/wIBADAdBgNVHQ4EFgQUJbaTWcIB4t5ETvvhUy5/yQqqGjMwaAYD\n" +
                    "VR0jBGEwX4AUg5CGmIS1WU8MizU3rTQdqldIJFShRKRCMEAxCzAJBgNVBAYTAkNI\n" +
                    "MRUwEwYDVQQKEwxQcm90b25WUE4gQUcxGjAYBgNVBAMTEVByb3RvblZQTiBSb290\n" +
                    "IENBggEBMAsGA1UdDwQEAwIBBjANBgkqhkiG9w0BAQ0FAAOCAgEAAgZ/BIVl+DcK\n" +
                    "OTVJJBy+RZ1E8os11gFaMKy12lAT1XEXDqLAnitvVyQgG5lPZKFQ2wzUR/TCrYKT\n" +
                    "SUZWdYaJIXkRWAU0aCDZ2I81T0OMpg9aS7xdxgHCGWOwwes8GhjtvQad9GJ8mUZH\n" +
                    "GyzfMaGG6fAZrgHnlOb4OIoqhBWYla6D2bpvbKgGkMo5NLAaX/7+U0HcxjjSS9vm\n" +
                    "/3XHTZU4q77pn+lhPWncajnVyMtm1mIZxMioyckR4+scyZse0mYJS6xli/7crH7j\n" +
                    "qScX7c5sWcaN4J63a3+x3uGvzOXjCyoDl9IaeqnxQpi8yc0nsWxIyDalR3uRQ9tJ\n" +
                    "7l/eRxJZ/1Pzz2LRHSQZuqN2ZReWVNTqJ42af8cWWH0fDOEt2468GLeSm08Hvyz0\n" +
                    "lRjn7Tf5hxOJSw4/3oGihvzuTdquJMOi62kThbp7DS3mMaZsfbmDoU3oNDv91bvL\n" +
                    "57z8wm7yRcGEoMsUNnrOZ4SU8dG/souvJM1BDStMLprFEgUbHEY5MjSR4/PLR6j9\n" +
                    "3NZgocfnfk80nBvNtgWVHxW019nuT93WL0/5L5g4UVm0Ay1V6pNkGZCmgNUBaRY4\n" +
                    "2JLzyY8p48OKapR5GnedLTJXJVbdd9GUNzIzm4iVITDH3p/u1g69dITCNXTO9EO5\n" +
                    "sGEYLNPbV49XBnVAm1tUWuoByZAjoWs=\n" +
                    "-----END CERTIFICATE-----";

            profileToConnect.mTLSAuthFilename = "[[INLINE]]# 2048 bit OpenVPN static key\n" +
                    "-----BEGIN OpenVPN Static key V1-----\n" +
                    "6acef03f62675b4b1bbd03e53b187727\n" +
                    "423cea742242106cb2916a8a4c829756\n" +
                    "3d22c7e5cef430b1103c6f66eb1fc5b3\n" +
                    "75a672f158e2e2e936c3faa48b035a6d\n" +
                    "e17beaac23b5f03b10b868d53d03521d\n" +
                    "8ba115059da777a60cbfd7b2c9c57472\n" +
                    "78a15b8f6e68a3ef7fd583ec9f398c8b\n" +
                    "d4735dab40cbd1e3c62a822e97489186\n" +
                    "c30a0b48c7c38ea32ceb056d3fa5a710\n" +
                    "e10ccc7a0ddb363b08c3d2777a3395e1\n" +
                    "0c0b6080f56309192ab5aacd4b45f55d\n" +
                    "a61fc77af39bd81a19218a79762c3386\n" +
                    "2df55785075f37d8c71dc8a42097ee43\n" +
                    "344739a0dd48d03025b0450cf1fb5e8c\n" +
                    "aeb893d9a96d1f15519bb3c4dcb40ee3\n" +
                    "16672ea16c012664f8a9f11255518deb\n" +
                    "-----END OpenVPN Static key V1-----";
            profileToConnect.mTLSAuthDirection = "1";
            profileToConnect.mAuth = "SHA512";
            profileToConnect.mCipher = "AES-256-CBC";
            profileToConnect.mUsername = "vpntest3ovpn";
            profileToConnect.mUseTLSAuth = true;
            Connection conn = new Connection();

            conn.mServerName = "test-exit.protonvpn.com";
            conn.mServerPort = "443";
            conn.mUseUdp = false;
            conn.mCustomConfiguration = "";
            profileToConnect.mConnections[0] = conn;
            profileToConnect.mPassword = "vpntest3ovpnpw";
            if (shortcutName != null && profileToConnect == null) {
                profileToConnect = ProfileManager.getInstance(this).getProfileByName(shortcutName);
                if (!(new ExternalAppDatabase(this).checkRemoteActionPermission(this, getCallingPackage()))) {
                    finish();
                    return;
                }
            }


            if (profileToConnect == null) {
                VpnStatus.logError(R.string.shortcut_profile_notfound);
                // show Log window to display error
                showLogWindow();
                finish();
            } else {
                mSelectedProfile = profileToConnect;
                launchVPN();
            }
        }
    }

    private void askForPW(final int type) {

        final EditText entry = new EditText(this);

        entry.setSingleLine();
        entry.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        entry.setTransformationMethod(new PasswordTransformationMethod());

        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(getString(R.string.pw_request_dialog_title, getString(type)));
        dialog.setMessage(getString(R.string.pw_request_dialog_prompt, mSelectedProfile.mName));


        @SuppressLint("InflateParams") final View userpwlayout = getLayoutInflater().inflate(R.layout.userpass, null, false);

        if (type == R.string.password) {
            ((EditText) userpwlayout.findViewById(R.id.username)).setText(mSelectedProfile.mUsername);
            ((EditText) userpwlayout.findViewById(R.id.password)).setText(mSelectedProfile.mPassword);
            ((CheckBox) userpwlayout.findViewById(R.id.save_password)).setChecked(!TextUtils.isEmpty(mSelectedProfile.mPassword));
            ((CheckBox) userpwlayout.findViewById(R.id.show_password)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked)
                        ((EditText) userpwlayout.findViewById(R.id.password)).setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    else
                        ((EditText) userpwlayout.findViewById(R.id.password)).setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
            });

            dialog.setView(userpwlayout);
        } else {
            dialog.setView(entry);
        }

        AlertDialog.Builder builder = dialog.setPositiveButton(android.R.string.ok,
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        if (type == R.string.password) {
                            mSelectedProfile.mUsername = ((EditText) userpwlayout.findViewById(R.id.username)).getText().toString();

                            String pw = ((EditText) userpwlayout.findViewById(R.id.password)).getText().toString();
                            if (((CheckBox) userpwlayout.findViewById(R.id.save_password)).isChecked()) {
                                mSelectedProfile.mPassword = pw;
                            } else {
                                mSelectedProfile.mPassword = null;
                                mTransientAuthPW = pw;
                            }
                        } else {
                            mTransientCertOrPCKS12PW = entry.getText().toString();
                        }
                        Intent intent = new Intent(LaunchVPN.this, OpenVPNStatusService.class);
                        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
                    }

                });
        dialog.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        VpnStatus.updateStateString("USER_VPN_PASSWORD_CANCELLED", "", R.string.state_user_vpn_password_cancelled,
                                ConnectionStatus.LEVEL_NOTCONNECTED);
                        finish();
                    }
                });

        dialog.create().show();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == START_VPN_PROFILE) {
            if (resultCode == Activity.RESULT_OK) {
                int needpw = mSelectedProfile.needUserPWInput(mTransientCertOrPCKS12PW, mTransientAuthPW);
                if (needpw != 0) {
                    VpnStatus.updateStateString("USER_VPN_PASSWORD", "", R.string.state_user_vpn_password,
                            ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT);
                    askForPW(needpw);
                } else {
                    SharedPreferences prefs = Preferences.getDefaultSharedPreferences(this);
                    boolean showLogWindow = prefs.getBoolean("showlogwindow", true);

                    if (!mhideLog && showLogWindow)
                        showLogWindow();
                    ProfileManager.updateLRU(this, mSelectedProfile);
                    VPNLaunchHelper.startOpenVpn(mSelectedProfile, getBaseContext());
                    finish();
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // User does not want us to start, so we just vanish
                VpnStatus.updateStateString("USER_VPN_PERMISSION_CANCELLED", "", R.string.state_user_vpn_permission_cancelled,
                        ConnectionStatus.LEVEL_NOTCONNECTED);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    VpnStatus.logError(R.string.nought_alwayson_warning);

                finish();
            }
        }
    }

    void showLogWindow() {

        Intent startLW = new Intent(getBaseContext(), LogWindow.class);
        startLW.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(startLW);

    }

    void showConfigErrorDialog(int vpnok) {
        AlertDialog.Builder d = new AlertDialog.Builder(this);
        d.setTitle(R.string.config_error_found);
        d.setMessage(vpnok);
        d.setPositiveButton(android.R.string.ok, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();

            }
        });
        d.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
            setOnDismissListener(d);
        d.show();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void setOnDismissListener(AlertDialog.Builder d) {
        d.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });
    }

    void launchVPN() {
     /*   int vpnok = mSelectedProfile.checkProfile(this);
        if (vpnok != R.string.no_error_found) {
            showConfigErrorDialog(vpnok);
            return;
        }
        ProfileManager.getInstance(this).addProfile(mSelectedProfile);
        ProfileManager.setTemporaryProfile(this, mSelectedProfile);
        Intent intent = VpnService.prepare(this);
        // Check if we want to fix /dev/tun
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(this);
        boolean usecm9fix = prefs.getBoolean("useCM9Fix", false);
        boolean loadTunModule = prefs.getBoolean("loadTunModule", false);

        if (loadTunModule)
            execeuteSUcmd("insmod /system/lib/modules/tun.ko");

        if (usecm9fix && !mCmfixed) {
            execeuteSUcmd("chown system /dev/tun");
        }

        if (intent != null) {
            VpnStatus.updateStateString("USER_VPN_PERMISSION", "", R.string.state_user_vpn_permission,
                    ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT);
            // Start the query
            try {
                startActivityForResult(intent, START_VPN_PROFILE);

            } catch (ActivityNotFoundException ane) {
                // Shame on you Sony! At least one user reported that
                // an official Sony Xperia Arc S image triggers this exception
                VpnStatus.logError(R.string.no_vpn_support_image);
                showLogWindow();
            }
        } else {
            onActivityResult(START_VPN_PROFILE, Activity.RESULT_OK, null);
        }
 */
    }

    private void execeuteSUcmd(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", command);
            Process p = pb.start();
            int ret = p.waitFor();
            if (ret == 0)
                mCmfixed = true;
        } catch (InterruptedException | IOException e) {
            VpnStatus.logException("SU command", e);
        }
    }
}
