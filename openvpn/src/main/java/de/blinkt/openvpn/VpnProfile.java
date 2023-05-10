/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.security.KeyChain;
import android.security.KeyChainException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.text.TextUtils;
import android.util.Base64;

import de.blinkt.openvpn.core.*;

import org.spongycastle.util.io.pem.PemObject;
import org.spongycastle.util.io.pem.PemWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.Vector;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class VpnProfile implements Serializable, Cloneable {
    // Note that this class cannot be moved to core where it belongs since
    // the profile loading depends on it being here
    // The Serializable documentation mentions that class name change are possible
    // but the how is unclear
    //
    transient public static final long MAX_EMBED_FILE_SIZE = 2048 * 1024; // 2048kB
    // Don't change this, not all parts of the program use this constant
    public static final String EXTRA_PROFILEUUID = "de.blinkt.openvpn.profileUUID";
    public static final String INLINE_TAG = "[[INLINE]]";
    public static final String DISPLAYNAME_TAG = "[[NAME]]";
    public static final int MAXLOGLEVEL = 4;
    public static final int CURRENT_PROFILE_VERSION = 10;
    public static final int DEFAULT_MSSFIX_SIZE = 1280;
    public static final int TYPE_CERTIFICATES = 0;
    public static final int TYPE_PKCS12 = 1;
    public static final int TYPE_KEYSTORE = 2;
    public static final int TYPE_USERPASS = 3;
    public static final int TYPE_STATICKEYS = 4;
    public static final int TYPE_USERPASS_CERTIFICATES = 5;
    public static final int TYPE_USERPASS_PKCS12 = 6;
    public static final int TYPE_USERPASS_KEYSTORE = 7;
    public static final int TYPE_EXTERNAL_APP = 8;
    public static final int X509_VERIFY_TLSREMOTE = 0;
    public static final int X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING = 1;
    public static final int X509_VERIFY_TLSREMOTE_DN = 2;
    public static final int X509_VERIFY_TLSREMOTE_RDN = 3;
    public static final int X509_VERIFY_TLSREMOTE_RDN_PREFIX = 4;
    public static final int AUTH_RETRY_NONE_FORGET = 0;
    public static final int AUTH_RETRY_NOINTERACT = 2;
    public static final boolean mIsOpenVPN22 = false;
    private static final long serialVersionUID = 7085688938959334563L;
    private static final int AUTH_RETRY_NONE_KEEP = 1;
    private static final int AUTH_RETRY_INTERACT = 3;
    private static final String EXTRA_RSA_PADDING_TYPE = "de.blinkt.openvpn.api.RSA_PADDING_TYPE";
    private static final String EXTRA_SALTLEN = "de.blinkt.openvpn.api.SALTLEN";
    private static final String EXTRA_NEEDS_DIGEST = "de.blinkt.openvpn.api.NEEDS_DIGEST";
    private static final String EXTRA_DIGEST = "de.blinkt.openvpn.api.DIGEST";
    public static String DEFAULT_DNS1 = "9.9.9.9";
    public static String DEFAULT_DNS2 = "2620:fe::fe";
    // variable named wrong and should haven beeen transient
    // but needs to keep wrong name to guarante loading of old
    // profiles
    public transient boolean profileDeleted = false;
    public int mAuthenticationType = TYPE_KEYSTORE;
    public String mName;
    public String mAlias;
    public String mClientCertFilename;
    public String mTLSAuthDirection = "";
    public String mTLSAuthFilename;
    public String mClientKeyFilename;
    public String mCaFilename;
    public boolean mUseLzo = false;
    public String mPKCS12Filename;
    public String mPKCS12Password;
    public boolean mUseTLSAuth = false;
    public String mDNS1 = DEFAULT_DNS1;
    public String mDNS2 = DEFAULT_DNS2;
    public String mIPv4Address;
    public String mIPv6Address;
    public boolean mOverrideDNS = false;
    public String mSearchDomain = "blinkt.de";
    public boolean mUseDefaultRoute = true;
    public boolean mUsePull = true;
    public String mCustomRoutes;
    public boolean mCheckRemoteCN = true;
    public boolean mExpectTLSCert = false;
    public String mRemoteCN = "";
    public String mPassword = "";
    public String mUsername = "";
    public boolean mRoutenopull = false;
    public boolean mUseRandomHostname = false;
    public boolean mUseFloat = false;
    public boolean mUseCustomConfig = false;
    public String mCustomConfigOptions = "";
    public String mVerb = "1";  //ignored
    public String mCipher = "";
    public boolean mNobind = true;
    public boolean mUseDefaultRoutev6 = true;
    public String mCustomRoutesv6 = "";
    public String mKeyPassword = "";
    public boolean mPersistTun = false;
    public String mConnectRetryMax = "-1";
    public String mConnectRetry = "2";
    public String mConnectRetryMaxTime = "300";
    public boolean mUserEditable = true;
    public String mAuth = "";
    public int mX509AuthType = X509_VERIFY_TLSREMOTE_RDN;
    public String mx509UsernameField = null;
    public boolean mAllowLocalLAN;
    public String mExcludedRoutes;
    public String mExcludedRoutesv6;
    public int mMssFix = 0; // -1 is default,
    public Connection[] mConnections = new Connection[0];
    public boolean mRemoteRandom = false;
    public HashSet<String> mAllowedAppsVpn = new HashSet<>();
    public boolean mAllowedAppsVpnAreDisallowed = true;
    public boolean mAllowAppVpnBypass = false;
    public String mCrlFilename;
    public String mProfileCreator;
    public String mExternalAuthenticator;
    public int mAuthRetry = AUTH_RETRY_NONE_FORGET;
    public int mTunMtu;
    public boolean mPushPeerInfo = false;
    public int mVersion = 0;
    // timestamp when the profile was last used
    public long mLastUsed;
    public String importedProfileHash;
    /* Options no longer used in new profiles */
    public String mServerName = "openvpn.example.com";
    public String mServerPort = "1194";
    public boolean mUseUdp = true;
    public boolean mTemporaryProfile = false;
    public String mDataCiphers = "";
    public boolean mBlockUnusedAddressFamilies = true;
    public boolean mCheckPeerFingerprint = false;
    public String mPeerFingerPrints = "";
    public int mCompatMode = 0;
    public boolean mUseLegacyProvider = false;
    public String mTlSCertProfile = "";

    private transient PrivateKey mPrivateKey;
    // Public attributes, since I got mad with getter/setter
    // set members to default values
    private UUID mUuid;
    private int mProfileVersion;

    public VpnProfile(String name) {
        mUuid = UUID.randomUUID();
        mName = name;
        mProfileVersion = CURRENT_PROFILE_VERSION;

        mConnections = new Connection[1];
        mConnections[0] = new Connection();
        mLastUsed = System.currentTimeMillis();
    }

    public static String openVpnEscape(String unescaped) {
        if (unescaped == null)
            return null;
        String escapedString = unescaped.replace("\\", "\\\\");
        escapedString = escapedString.replace("\"", "\\\"");
        escapedString = escapedString.replace("\n", "\\n");

        if (escapedString.equals(unescaped) && !escapedString.contains(" ") &&
                !escapedString.contains("#") && !escapedString.contains(";")
                && !escapedString.equals("")  && !escapedString.contains("'"))
            return unescaped;
        else
            return '"' + escapedString + '"';
    }

    public static boolean doUseOpenVPN3(Context c) {
        SharedPreferences prefs = Preferences.getDefaultSharedPreferences(c);
        boolean useOpenVPN3 = prefs.getBoolean("ovpn3", false);
        if (!BuildConfig.openvpn3)
            useOpenVPN3 = false;
        return useOpenVPN3;
    }

    //! Put inline data inline and other data as normal escaped filename
    public static String insertFileData(String cfgentry, String filedata) {
        if (filedata == null) {
            return String.format("%s %s\n", cfgentry, "file missing in config profile");
        } else if (isEmbedded(filedata)) {
            String dataWithOutHeader = getEmbeddedContent(filedata);
            return String.format(Locale.ENGLISH, "<%s>\n%s\n</%s>\n", cfgentry, dataWithOutHeader, cfgentry);
        } else {
            return String.format(Locale.ENGLISH, "%s %s\n", cfgentry, openVpnEscape(filedata));
        }
    }

    public static String getDisplayName(String embeddedFile) {
        int start = DISPLAYNAME_TAG.length();
        int end = embeddedFile.indexOf(INLINE_TAG);
        return embeddedFile.substring(start, end);
    }

    public static String getEmbeddedContent(String data) {
        if (!data.contains(INLINE_TAG))
            return data;

        int start = data.indexOf(INLINE_TAG) + INLINE_TAG.length();
        return data.substring(start);
    }

    public static boolean isEmbedded(String data) {
        if (data == null)
            return false;
        if (data.startsWith(INLINE_TAG) || data.startsWith(DISPLAYNAME_TAG))
            return true;
        else
            return false;
    }

    static public String getVersionEnvString(Context c) {
        String version = "unknown";
        try {
            PackageInfo packageinfo = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = packageinfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            VpnStatus.logException(e);
        }
        return String.format(Locale.US, "%s %s", c.getPackageName(), version);

    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VpnProfile) {
            VpnProfile vpnProfile = (VpnProfile) obj;
            return mUuid.equals(vpnProfile.mUuid);
        } else {
            return false;
        }
    }

    public void clearDefaults() {
        mServerName = "unknown";
        mUsePull = false;
        mUseLzo = false;
        mUseDefaultRoute = false;
        mUseDefaultRoutev6 = false;
        mExpectTLSCert = false;
        mCheckRemoteCN = false;
        mPersistTun = false;
        mAllowLocalLAN = true;
        mPushPeerInfo = false;
        mMssFix = 0;
        mNobind = false;
    }

    public UUID getUUID() {
        return mUuid;

    }

    // Only used for the special case of managed profiles
    public void setUUID(UUID uuid) {
        mUuid = uuid;
    }

    public String getName() {
        if (TextUtils.isEmpty(mName))
            return "No profile name";
        return mName;
    }

    public void upgradeProfile() {

        /* Fallthrough is intended here */
        switch (mProfileVersion) {
            case 0:
            case 1:
                /* default to the behaviour the OS used */
                mAllowLocalLAN = Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT;
            case 2:
            case 3:
                moveOptionsToConnection();
                mAllowedAppsVpnAreDisallowed = true;

                if (mAllowedAppsVpn == null)
                    mAllowedAppsVpn = new HashSet<>();

                if (mConnections == null)
                    mConnections = new Connection[0];
            case 4:
            case 5:

                if (TextUtils.isEmpty(mProfileCreator))
                    mUserEditable = true;
            case 6:
                for (Connection c : mConnections)
                    if (c.mProxyType == null)
                        c.mProxyType = Connection.ProxyType.NONE;
            case 7:
                if (mAllowAppVpnBypass)
                    mBlockUnusedAddressFamilies = false;
            case 8:
                if (!TextUtils.isEmpty(mCipher) && !mCipher.equals("AES-256-GCM") && !mCipher.equals("AES-128-GCM") && !mCipher.equals("CHACHA20-POLY1305")) {
                    mDataCiphers = "AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:" + mCipher;
                }
            case 9:
                if (!TextUtils.isEmpty(mDataCiphers) &&
                        mDataCiphers.toUpperCase(Locale.ROOT).contains("BF-CBC")) {
                    mUseLegacyProvider = true;
                }
            default:
        }

        mProfileVersion = CURRENT_PROFILE_VERSION;

    }

    private void moveOptionsToConnection() {
        mConnections = new Connection[1];
        Connection conn = new Connection();

        conn.mServerName = mServerName;
        conn.mServerPort = mServerPort;
        conn.mUseUdp = mUseUdp;
        conn.mCustomConfiguration = "";

        mConnections[0] = conn;

    }

    public String getConfigFile(Context context, boolean configForOvpn3) {

        File cacheDir = context.getCacheDir();
        StringBuilder cfg = new StringBuilder();

        if (!configForOvpn3) {
            // Enable management interface
            cfg.append("# Config for OpenVPN 2.x\n");
            cfg.append("# Enables connection to GUI\n");
            cfg.append("management ");

            cfg.append(cacheDir.getAbsolutePath()).append("/").append("mgmtsocket");
            cfg.append(" unix\n");
            cfg.append("management-client\n");
            // Not needed, see updated man page in 2.3
            //cfg += "management-signal\n";
            cfg.append("management-query-passwords\n");
            cfg.append("management-hold\n\n");

            cfg.append(String.format("setenv IV_GUI_VER %s \n", openVpnEscape(getVersionEnvString(context))));
            cfg.append("setenv IV_SSO openurl,webauth,crtext\n");
            String versionString = getPlatformVersionEnvString();
            cfg.append(String.format("setenv IV_PLAT_VER %s\n", openVpnEscape(versionString)));
            String hwaddr = NetworkUtils.getFakeMacAddrFromSAAID(context);
            if (hwaddr != null)
                cfg.append(String.format("setenv IV_HWADDR %s\n", hwaddr));

            if (mUseLegacyProvider)
                cfg.append("providers legacy default\n");

            if (!TextUtils.isEmpty(mTlSCertProfile) && mAuthenticationType != TYPE_STATICKEYS)
                cfg.append(String.format("tls-cert-profile %s\n", mTlSCertProfile));
        } else {
            cfg.append("# Config for OpenVPN 3 C++\n");
        }


        if (!configForOvpn3) {
            cfg.append("machine-readable-output\n");
            if (!mIsOpenVPN22)
                cfg.append("allow-recursive-routing\n");

            // Users are confused by warnings that are misleading...
            cfg.append("ifconfig-nowarn\n");
        }

        boolean useTLSClient = (mAuthenticationType != TYPE_STATICKEYS);

        if (useTLSClient && mUsePull)
            cfg.append("client\n");
        else if (mUsePull)
            cfg.append("pull\n");
        else if (useTLSClient)
            cfg.append("tls-client\n");


        //cfg += "verb " + mVerb + "\n";
        cfg.append("verb " + MAXLOGLEVEL + "\n");

        if (mConnectRetryMax == null) {
            mConnectRetryMax = "-1";
        }

        if (!mConnectRetryMax.equals("-1"))
            cfg.append("connect-retry-max ").append(mConnectRetryMax).append("\n");

        if (TextUtils.isEmpty(mConnectRetry))
            mConnectRetry = "2";

        if (TextUtils.isEmpty(mConnectRetryMaxTime))
            mConnectRetryMaxTime = "300";


        if (!mIsOpenVPN22)
            cfg.append("connect-retry ").append(mConnectRetry).append(" ").append(mConnectRetryMaxTime).append("\n");
        else if (mIsOpenVPN22 && !mUseUdp)
            cfg.append("connect-retry ").append(mConnectRetry).append("\n");


        cfg.append("resolv-retry 60\n");


        // We cannot use anything else than tun
        cfg.append("dev tun\n");


        boolean canUsePlainRemotes = true;

        if (mConnections.length == 1) {
            cfg.append(mConnections[0].getConnectionBlock(configForOvpn3));
        } else {
            for (Connection conn : mConnections) {
                canUsePlainRemotes = canUsePlainRemotes && conn.isOnlyRemote();
            }

            if (mRemoteRandom)
                cfg.append("remote-random\n");

            if (canUsePlainRemotes) {
                for (Connection conn : mConnections) {
                    if (conn.mEnabled) {
                        cfg.append(conn.getConnectionBlock(configForOvpn3));
                    }
                }
            }
        }


        switch (mAuthenticationType) {
            case VpnProfile.TYPE_USERPASS_CERTIFICATES:
                cfg.append("auth-user-pass\n");
            case VpnProfile.TYPE_CERTIFICATES:
                // Ca
                if (!TextUtils.isEmpty(mCaFilename)) {
                    cfg.append(insertFileData("ca", mCaFilename));
                }

                // Client Cert + Key
                cfg.append(insertFileData("key", mClientKeyFilename));
                cfg.append(insertFileData("cert", mClientCertFilename));

                break;
            case VpnProfile.TYPE_USERPASS_PKCS12:
                cfg.append("auth-user-pass\n");
            case VpnProfile.TYPE_PKCS12:
                cfg.append(insertFileData("pkcs12", mPKCS12Filename));

                if (!TextUtils.isEmpty(mCaFilename)) {
                    cfg.append(insertFileData("ca", mCaFilename));
                }
                break;

            case VpnProfile.TYPE_USERPASS_KEYSTORE:
                cfg.append("auth-user-pass\n");
            case VpnProfile.TYPE_KEYSTORE:
            case VpnProfile.TYPE_EXTERNAL_APP:
                if (!configForOvpn3) {
                    String[] ks = getExternalCertificates(context);
                    cfg.append("### From Keystore/ext auth app ####\n");
                    if (ks != null) {
                        if (!TextUtils.isEmpty(mCaFilename)) {
                            cfg.append(insertFileData("ca", mCaFilename));
                        } else if (!TextUtils.isEmpty(ks[0]) && !mCheckPeerFingerprint) {
                            /* if we have enabled peer-fingerprint verification the certificate from
                             * the keystore is more likely to screw things up than to fix anything
                             */
                            cfg.append("<ca>\n").append(ks[0]).append("\n</ca>\n");
                        }
                        if (!TextUtils.isEmpty(ks[1]))
                            cfg.append("<extra-certs>\n").append(ks[1]).append("\n</extra-certs>\n");
                        cfg.append("<cert>\n").append(ks[2]).append("\n</cert>\n");
                        cfg.append("management-external-key nopadding pkcs1 pss digest\n");
                    } else {
                        cfg.append(context.getString(R.string.keychain_access)).append("\n");
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN)
                            if (!mAlias.matches("^[a-zA-Z0-9]$"))
                                cfg.append(context.getString(R.string.jelly_keystore_alphanumeric_bug)).append("\n");
                    }
                }
                break;
            case VpnProfile.TYPE_USERPASS:
                cfg.append("auth-user-pass\n");
                if (!TextUtils.isEmpty(mCaFilename))
                    cfg.append(insertFileData("ca", mCaFilename));
                if (configForOvpn3) {
                    // OpenVPN 3 needs to be told that a client certificate is not required
                    cfg.append("client-cert-not-required\n");
                }
        }

        if (mCheckPeerFingerprint) {
            cfg.append("<peer-fingerprint>\n").append(mPeerFingerPrints).append("\n</peer-fingerprint>\n");
        }

        if (isUserPWAuth()) {
            if (mAuthRetry == AUTH_RETRY_NOINTERACT)
                cfg.append("auth-retry nointeract\n");
        }

        if (!TextUtils.isEmpty(mCrlFilename))
            cfg.append(insertFileData("crl-verify", mCrlFilename));

        if (mUseLzo) {
            cfg.append("comp-lzo\n");
        }

        if (mUseTLSAuth) {
            boolean useTlsCrypt = mTLSAuthDirection.equals("tls-crypt");
            boolean useTlsCrypt2 = mTLSAuthDirection.equals("tls-crypt-v2");

            if (mAuthenticationType == TYPE_STATICKEYS)
                cfg.append(insertFileData("secret", mTLSAuthFilename));
            else if (useTlsCrypt)
                cfg.append(insertFileData("tls-crypt", mTLSAuthFilename));
            else if (useTlsCrypt2)
                cfg.append(insertFileData("tls-crypt-v2", mTLSAuthFilename));
            else
                cfg.append(insertFileData("tls-auth", mTLSAuthFilename));

            if (!TextUtils.isEmpty(mTLSAuthDirection) && !useTlsCrypt && !useTlsCrypt2) {
                cfg.append("key-direction ");
                cfg.append(mTLSAuthDirection);
                cfg.append("\n");
            }

        }

        if (!mUsePull) {
            if (!TextUtils.isEmpty(mIPv4Address))
                cfg.append("ifconfig ").append(cidrToIPAndNetmask(mIPv4Address)).append("\n");

            if (!TextUtils.isEmpty(mIPv6Address)) {
                // Use our own ip as gateway since we ignore it anyway
                String fakegw = mIPv6Address.split("/", 2)[0];
                cfg.append("ifconfig-ipv6 ").append(mIPv6Address).append(" ").append(fakegw).append("\n");
            }

        }

        if (mUsePull && mRoutenopull)
            cfg.append("route-nopull\n");

        String routes = "";

        if (mUseDefaultRoute)
            routes += "route 0.0.0.0 0.0.0.0 vpn_gateway\n";
        else {
            for (String route : getCustomRoutes(mCustomRoutes)) {
                routes += "route " + route + " vpn_gateway\n";
            }

            for (String route : getCustomRoutes(mExcludedRoutes)) {
                routes += "route " + route + " net_gateway\n";
            }
        }


        if (mUseDefaultRoutev6)
            cfg.append("route-ipv6 ::/0\n");
        else
            for (String route : getCustomRoutesv6(mCustomRoutesv6)) {
                routes += "route-ipv6 " + route + "\n";
            }

        cfg.append(routes);

        if (mOverrideDNS || !mUsePull) {
            if (!TextUtils.isEmpty(mDNS1)) {
                cfg.append("dhcp-option DNS ").append(mDNS1).append("\n");
            }
            if (!TextUtils.isEmpty(mDNS2)) {
                cfg.append("dhcp-option DNS ").append(mDNS2).append("\n");
            }
            if (!TextUtils.isEmpty(mSearchDomain))
                cfg.append("dhcp-option DOMAIN ").append(mSearchDomain).append("\n");

        }

        if (mMssFix != 0) {
            if (mMssFix != 1450) {
                if (configForOvpn3)
                    cfg.append(String.format(Locale.US, "mssfix %d mtu\n", mMssFix));
                else
                    cfg.append(String.format(Locale.US, "mssfix %d\n", mMssFix));
            } else
                cfg.append("mssfix\n");
        }

        if (mTunMtu >= 48 && mTunMtu != 1500) {
            cfg.append(String.format(Locale.US, "tun-mtu %d\n", mTunMtu));
        }

        if (mNobind)
            cfg.append("nobind\n");


        // Authentication
        if (mAuthenticationType != TYPE_STATICKEYS) {
            if (mCheckRemoteCN) {
                if (mRemoteCN == null || mRemoteCN.equals(""))
                    cfg.append("verify-x509-name ").append(openVpnEscape(mConnections[0].mServerName)).append(" name\n");
                else
                    switch (mX509AuthType) {

                        // 2.2 style x509 checks
                        case X509_VERIFY_TLSREMOTE_COMPAT_NOREMAPPING:
                            cfg.append("compat-names no-remapping\n");
                        case X509_VERIFY_TLSREMOTE:
                            cfg.append("tls-remote ").append(openVpnEscape(mRemoteCN)).append("\n");
                            break;

                        case X509_VERIFY_TLSREMOTE_RDN:
                            cfg.append("verify-x509-name ").append(openVpnEscape(mRemoteCN)).append(" name\n");
                            break;

                        case X509_VERIFY_TLSREMOTE_RDN_PREFIX:
                            cfg.append("verify-x509-name ").append(openVpnEscape(mRemoteCN)).append(" name-prefix\n");
                            break;

                        case X509_VERIFY_TLSREMOTE_DN:
                            cfg.append("verify-x509-name ").append(openVpnEscape(mRemoteCN)).append("\n");
                            break;
                    }
                if (!TextUtils.isEmpty(mx509UsernameField))
                    cfg.append("x509-username-field ").append(openVpnEscape(mx509UsernameField)).append("\n");
            }
            if (mExpectTLSCert)
                cfg.append("remote-cert-tls server\n");
        }

        if (!TextUtils.isEmpty(mDataCiphers)) {
            cfg.append("data-ciphers ").append(mDataCiphers).append("\n");
        }

        if (mCompatMode > 0) {
            int major = mCompatMode / 10000;
            int minor = mCompatMode % 10000 / 100;
            int patch = mCompatMode % 100;
            cfg.append(String.format(Locale.US, "compat-mode %d.%d.%d\n", major, minor, patch));

        }

        if (!TextUtils.isEmpty(mCipher)) {
            cfg.append("cipher ").append(mCipher).append("\n");
        }

        if (!TextUtils.isEmpty(mAuth)) {
            cfg.append("auth ").append(mAuth).append("\n");
        }

        // Obscure Settings dialog
        if (mUseRandomHostname)
            cfg.append("#my favorite options :)\nremote-random-hostname\n");

        if (mUseFloat)
            cfg.append("float\n");

        if (mPersistTun) {
            cfg.append("persist-tun\n");
            cfg.append("# persist-tun also enables pre resolving to avoid DNS resolve problem\n");
            if (!mIsOpenVPN22)
                cfg.append("preresolve\n");
        }

        if (mPushPeerInfo)
            cfg.append("push-peer-info\n");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean usesystemproxy = prefs.getBoolean("usesystemproxy", true);
        if (usesystemproxy && !mIsOpenVPN22 && !configForOvpn3 && !usesExtraProxyOptions()) {
            cfg.append("# Use system proxy setting\n");
            cfg.append("management-query-proxy\n");
        }


        if (mUseCustomConfig) {
            cfg.append("# Custom configuration options\n");
            cfg.append("# You are on your on own here :)\n");
            cfg.append(mCustomConfigOptions);
            cfg.append("\n");

        }

        if (!canUsePlainRemotes) {
            cfg.append("# Connection Options are at the end to allow global options (and global custom options) to influence connection blocks\n");
            for (Connection conn : mConnections) {
                if (conn.mEnabled) {
                    cfg.append("<connection>\n");
                    cfg.append(conn.getConnectionBlock(configForOvpn3));
                    cfg.append("</connection>\n");
                }
            }
        }


        return cfg.toString();
    }

    public String getPlatformVersionEnvString() {
        return String.format(Locale.US, "%d %s %s %s %s %s", Build.VERSION.SDK_INT, Build.VERSION.RELEASE,
                NativeUtils.getNativeAPI(), Build.BRAND, Build.BOARD, Build.MODEL);
    }

    @NonNull
    private Collection<String> getCustomRoutes(String routes) {
        Vector<String> cidrRoutes = new Vector<>();
        if (routes == null) {
            // No routes set, return empty vector
            return cidrRoutes;
        }
        for (String route : routes.split("[\n \t]")) {
            if (!route.equals("")) {
                String cidrroute = cidrToIPAndNetmask(route);
                if (cidrroute == null)
                    return cidrRoutes;

                cidrRoutes.add(cidrroute);
            }
        }

        return cidrRoutes;
    }

    private Collection<String> getCustomRoutesv6(String routes) {
        Vector<String> cidrRoutes = new Vector<>();
        if (routes == null) {
            // No routes set, return empty vector
            return cidrRoutes;
        }
        for (String route : routes.split("[\n \t]")) {
            if (!route.equals("")) {
                cidrRoutes.add(route);
            }
        }

        return cidrRoutes;
    }

    private String cidrToIPAndNetmask(String route) {
        String[] parts = route.split("/");

        // No /xx, assume /32 as netmask
        if (parts.length == 1)
            parts = (route + "/32").split("/");

        if (parts.length != 2)
            return null;
        int len;
        try {
            len = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ne) {
            return null;
        }
        if (len < 0 || len > 32)
            return null;


        long nm = 0xffffffffL;
        nm = (nm << (32 - len)) & 0xffffffffL;

        String netmask = String.format(Locale.ENGLISH, "%d.%d.%d.%d", (nm & 0xff000000) >> 24, (nm & 0xff0000) >> 16, (nm & 0xff00) >> 8, nm & 0xff);
        return parts[0] + "  " + netmask;
    }

    public void writeConfigFileOutput(Context context, OutputStream out) throws IOException {
        OutputStreamWriter cfg = new OutputStreamWriter(out);
        cfg.write(getConfigFile(context, false));
        cfg.flush();
        cfg.close();
    }

    public Intent getStartServiceIntent(Context context, String startReason) {
        String prefix = context.getPackageName();

        Intent intent = new Intent(context, OpenVPNService.class);
        intent.putExtra(prefix + ".profileUUID", mUuid.toString());
        intent.putExtra(prefix + ".profileVersion", mVersion);
        if (startReason != null)
            intent.putExtra(prefix + ".startReason", startReason);
        return intent;
    }

    public void checkForRestart(final Context context) {
        /* This method is called when OpenVPNService is restarted */

        if ((mAuthenticationType == VpnProfile.TYPE_KEYSTORE || mAuthenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE)
                && mPrivateKey == null) {
            new Thread(() -> getExternalCertificates(context)).start();
        }
    }

    @Override
    protected VpnProfile clone() throws CloneNotSupportedException {
        VpnProfile copy = (VpnProfile) super.clone();
        copy.mUuid = UUID.randomUUID();
        copy.mConnections = new Connection[mConnections.length];
        int i = 0;
        for (Connection conn : mConnections) {
            copy.mConnections[i++] = conn.clone();
        }
        copy.mAllowedAppsVpn = (HashSet<String>) mAllowedAppsVpn.clone();
        return copy;
    }

    public VpnProfile copy(String name) {
        try {
            VpnProfile copy = clone();
            copy.mName = name;
            return copy;

        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void pwDidFail(Context c) {

    }

    private X509Certificate[] getKeyStoreCertificates(Context context) throws KeyChainException, InterruptedException {
        mPrivateKey = KeyChain.getPrivateKey(context, mAlias);


        X509Certificate[] caChain = KeyChain.getCertificateChain(context, mAlias);
        return caChain;
    }

    private X509Certificate[] getExtAppCertificates(Context context) throws KeyChainException {
        if (mExternalAuthenticator == null || mAlias == null)
            throw new KeyChainException("Alias or external auth provider name not set");
        return ExtAuthHelper.getCertificateChain(context, mExternalAuthenticator, mAlias);
    }

    public String[] getExternalCertificates(Context context) {
        return getExternalCertificates(context, 5);
    }


    synchronized String[] getExternalCertificates(Context context, int tries) {
        // Force application context- KeyChain methods will block long enough that by the time they
        // are finished and try to unbind, the original activity context might have been destroyed.
        context = context.getApplicationContext();

        try {
            String keystoreChain = null;

            X509Certificate caChain[];
            if (mAuthenticationType == TYPE_EXTERNAL_APP) {
                caChain = getExtAppCertificates(context);
            } else {
                caChain = getKeyStoreCertificates(context);
            }
            if (caChain == null)
                throw new NoCertReturnedException("No certificate returned from Keystore");

            if (caChain.length <= 1 && TextUtils.isEmpty(mCaFilename)) {
                VpnStatus.logMessage(VpnStatus.LogLevel.ERROR, "", context.getString(R.string.keychain_nocacert));
            } else {
                StringWriter ksStringWriter = new StringWriter();

                PemWriter pw = new PemWriter(ksStringWriter);
                for (int i = 1; i < caChain.length; i++) {
                    X509Certificate cert = caChain[i];
                    pw.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
                }
                pw.close();
                keystoreChain = ksStringWriter.toString();
            }


            String caout = null;
            if (!TextUtils.isEmpty(mCaFilename)) {
                try {
                    Certificate[] cacerts = X509Utils.getCertificatesFromFile(mCaFilename);
                    StringWriter caoutWriter = new StringWriter();
                    PemWriter pw = new PemWriter(caoutWriter);

                    for (Certificate cert : cacerts)
                        pw.writeObject(new PemObject("CERTIFICATE", cert.getEncoded()));
                    pw.close();
                    caout = caoutWriter.toString();

                } catch (Exception e) {
                    VpnStatus.logError("Could not read CA certificate" + e.getLocalizedMessage());
                }
            }


            StringWriter certout = new StringWriter();


            if (caChain.length >= 1) {
                X509Certificate usercert = caChain[0];

                PemWriter upw = new PemWriter(certout);
                upw.writeObject(new PemObject("CERTIFICATE", usercert.getEncoded()));
                upw.close();

            }
            String user = certout.toString();


            String ca, extra;
            if (caout == null) {
                ca = keystoreChain;
                extra = null;
            } else {
                ca = caout;
                extra = keystoreChain;
            }

            return new String[]{ca, extra, user};
        } catch (InterruptedException | IOException | KeyChainException | NoCertReturnedException | IllegalArgumentException
                | CertificateException e) {
            e.printStackTrace();
            VpnStatus.logError(R.string.keyChainAccessError, e.getLocalizedMessage());

            VpnStatus.logError(R.string.keychain_access);
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
                if (!mAlias.matches("^[a-zA-Z0-9]$")) {
                    VpnStatus.logError(R.string.jelly_keystore_alphanumeric_bug);
                }
            }
            return null;

        } catch (AssertionError e) {
            if (tries == 0)
                return null;
            VpnStatus.logError(String.format("Failure getting Keystore Keys (%s), retrying", e.getLocalizedMessage()));
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e1) {
                VpnStatus.logException(e1);
            }
            return getExternalCertificates(context, tries - 1);
        }

    }

    public int checkProfile(Context c) {
        return checkProfile(c, doUseOpenVPN3(c));
    }

    //! Return an error if something is wrong
    public int checkProfile(Context context, boolean useOpenVPN3) {
        if (mAuthenticationType == TYPE_KEYSTORE || mAuthenticationType == TYPE_USERPASS_KEYSTORE || mAuthenticationType == TYPE_EXTERNAL_APP) {
            if (mAlias == null)
                return R.string.no_keystore_cert_selected;
        } else if (mAuthenticationType == TYPE_CERTIFICATES || mAuthenticationType == TYPE_USERPASS_CERTIFICATES) {
            if (TextUtils.isEmpty(mCaFilename) && !mCheckPeerFingerprint)
                return R.string.no_ca_cert_selected;
        }

        if (mCheckRemoteCN && mX509AuthType == X509_VERIFY_TLSREMOTE)
            return R.string.deprecated_tls_remote;

        if (!mUsePull || mAuthenticationType == TYPE_STATICKEYS) {
            if (mIPv4Address == null || cidrToIPAndNetmask(mIPv4Address) == null)
                return R.string.ipv4_format_error;
        }
        if (!mUseDefaultRoute) {
            if (!TextUtils.isEmpty(mCustomRoutes) && getCustomRoutes(mCustomRoutes).size() == 0)
                return R.string.custom_route_format_error;

            if (!TextUtils.isEmpty(mExcludedRoutes) && getCustomRoutes(mExcludedRoutes).size() == 0)
                return R.string.custom_route_format_error;

        }

        if (mUseTLSAuth && TextUtils.isEmpty(mTLSAuthFilename))
            return R.string.missing_tlsauth;

        if ((mAuthenticationType == TYPE_USERPASS_CERTIFICATES || mAuthenticationType == TYPE_CERTIFICATES)
                && (TextUtils.isEmpty(mClientCertFilename) || TextUtils.isEmpty(mClientKeyFilename)))
            return R.string.missing_certificates;

        boolean noRemoteEnabled = true;
        for (Connection c : mConnections) {
            if (c.mEnabled)
                noRemoteEnabled = false;

        }
        if (noRemoteEnabled)
            return R.string.remote_no_server_selected;

        if (useOpenVPN3) {
            if (mAuthenticationType == TYPE_STATICKEYS) {
                return R.string.openvpn3_nostatickeys;
            }
            if (mAuthenticationType == TYPE_PKCS12 || mAuthenticationType == TYPE_USERPASS_PKCS12) {
                return R.string.openvpn3_pkcs12;
            }
            for (Connection conn : mConnections) {
                if (conn.mProxyType == Connection.ProxyType.ORBOT || conn.mProxyType == Connection.ProxyType.SOCKS5)
                    return R.string.openvpn3_socksproxy;
            }
        }
        for (Connection c : mConnections) {
            if (c.mProxyType == Connection.ProxyType.ORBOT) {
                if (usesExtraProxyOptions())
                    return R.string.error_orbot_and_proxy_options;
                if (!OrbotHelper.checkTorReceier(context))
                    return R.string.no_orbotfound;
            }
        }

        String dataciphers = "";
        if (!TextUtils.isEmpty(dataciphers))
            dataciphers = mDataCiphers.toUpperCase(Locale.ROOT);

        String cipher = "BF-CBC";
        if (!TextUtils.isEmpty(mCipher))
            cipher = mCipher.toUpperCase(Locale.ROOT);

        if (!mUseLegacyProvider &&
                (dataciphers.contains("BF-CBC")
                        || (mCompatMode > 0 && mCompatMode < 20500)
                        && cipher.equals("BF-CBC"))) {
            return R.string.bf_cbc_requires_legacy;
        }

        // Everything okay
        return R.string.no_error_found;

    }

    //! Openvpn asks for a "Private Key", this should be pkcs12 key
    //
    public String getPasswordPrivateKey() {
        String cachedPw = PasswordCache.getPKCS12orCertificatePassword(mUuid, true);
        if (cachedPw != null) {
            return cachedPw;
        }
        switch (mAuthenticationType) {
            case TYPE_PKCS12:
            case TYPE_USERPASS_PKCS12:
                return mPKCS12Password;

            case TYPE_CERTIFICATES:
            case TYPE_USERPASS_CERTIFICATES:
                return mKeyPassword;

            case TYPE_USERPASS:
            case TYPE_STATICKEYS:
            default:
                return null;
        }
    }

    public boolean isUserPWAuth() {
        switch (mAuthenticationType) {
            case TYPE_USERPASS:
            case TYPE_USERPASS_CERTIFICATES:
            case TYPE_USERPASS_KEYSTORE:
            case TYPE_USERPASS_PKCS12:
                return true;
            default:
                return false;

        }
    }

    public boolean requireTLSKeyPassword() {
        if (TextUtils.isEmpty(mClientKeyFilename))
            return false;

        String data = "";
        if (isEmbedded(mClientKeyFilename))
            data = mClientKeyFilename;
        else {
            char[] buf = new char[2048];
            FileReader fr;
            try {
                fr = new FileReader(mClientKeyFilename);
                int len = fr.read(buf);
                while (len > 0) {
                    data += new String(buf, 0, len);
                    len = fr.read(buf);
                }
                fr.close();
            } catch (FileNotFoundException e) {
                return false;
            } catch (IOException e) {
                return false;
            }

        }

        if (data.contains("Proc-Type: 4,ENCRYPTED"))
            return true;
        else if (data.contains("-----BEGIN ENCRYPTED PRIVATE KEY-----"))
            return true;
        else
            return false;
    }

    public int needUserPWInput(String transientCertOrPkcs12PW, String mTransientAuthPW) {
        if ((mAuthenticationType == TYPE_PKCS12 || mAuthenticationType == TYPE_USERPASS_PKCS12) &&
                (mPKCS12Password == null || mPKCS12Password.equals(""))) {
            if (transientCertOrPkcs12PW == null)
                return R.string.pkcs12_file_encryption_key;
        }

        if (mAuthenticationType == TYPE_CERTIFICATES || mAuthenticationType == TYPE_USERPASS_CERTIFICATES) {
            if (requireTLSKeyPassword() && TextUtils.isEmpty(mKeyPassword))
                if (transientCertOrPkcs12PW == null) {
                    return R.string.private_key_password;
                }
        }

        if (isUserPWAuth() &&
                (TextUtils.isEmpty(mUsername) ||
                        (TextUtils.isEmpty(mPassword) && mTransientAuthPW == null))) {
            return R.string.password;
        }
        return 0;
    }

    public String getPasswordAuth() {
        String cachedPw = PasswordCache.getAuthPassword(mUuid, true);
        if (cachedPw != null) {
            return cachedPw;
        } else {
            return mPassword;
        }
    }

    // Used by the Array Adapter
    @Override
    public String toString() {
        return mName;
    }

    public String getUUIDString() {
        return mUuid.toString().toLowerCase(Locale.ENGLISH);
    }

    public PrivateKey getKeystoreKey() {
        return mPrivateKey;
    }

    @Nullable
    public String getSignedData(Context c, String b64data, OpenVPNManagement.SignaturePadding padding, String saltlen, String hashalg, boolean needDigest) {
        byte[] data = Base64.decode(b64data, Base64.DEFAULT);
        byte[] signed_bytes;
        if (mAuthenticationType == TYPE_EXTERNAL_APP) {
            signed_bytes = getExtAppSignedData(c, data, padding, saltlen, hashalg, needDigest);
        } else {
            signed_bytes = getKeyChainSignedData(data, padding, saltlen, hashalg, needDigest);
        }

        if (signed_bytes != null)
            return Base64.encodeToString(signed_bytes, Base64.NO_WRAP);
        else
            return null;
    }

    private byte[] getExtAppSignedData(Context c, byte[] data, OpenVPNManagement.SignaturePadding padding, String saltlen, String hashalg, boolean needDigest)
    {

        Bundle extra = new Bundle();
        RsaPaddingType paddingType;
        switch (padding) {
            case RSA_PKCS1_PADDING:
                paddingType = RsaPaddingType.PKCS1_PADDING;
                break;
            case NO_PADDING:
                paddingType = RsaPaddingType.NO_PADDING;
                break;
            case RSA_PKCS1_PSS_PADDING:
                paddingType = RsaPaddingType.RSAPSS_PADDING;
                break;
            default:
                paddingType = RsaPaddingType.NO_PADDING;
        }

        extra.putInt(EXTRA_RSA_PADDING_TYPE, paddingType.ordinal());
        extra.putString(EXTRA_SALTLEN, saltlen);
        extra.putString(EXTRA_DIGEST, hashalg);
        extra.putBoolean(EXTRA_NEEDS_DIGEST, needDigest);

        if (TextUtils.isEmpty(mExternalAuthenticator))
            return null;
        try {
            return ExtAuthHelper.signData(c, mExternalAuthenticator, mAlias, data, extra);
        } catch (KeyChainException | InterruptedException e) {
            VpnStatus.logError(R.string.error_extapp_sign, mExternalAuthenticator, e.getClass().toString(), e.getLocalizedMessage());
            return null;
        }
    }

    private byte[] getKeyChainSignedData(byte[] data, OpenVPNManagement.SignaturePadding padding, String saltlen, String hashalg, boolean needDigest) {
        PrivateKey privkey = getKeystoreKey();


        try {
            @SuppressLint("GetInstance")
            String keyalgorithm = privkey.getAlgorithm();

            byte[] signed_bytes;
            if (needDigest || keyalgorithm.equals("EC")) {
                return doDigestSign(privkey, data, padding, hashalg, saltlen);
            } else {
             /* ECB is perfectly fine in this special case, since we are using it for
                the public/private part in the TLS exchange */
                Cipher signer = null;
                switch (padding) {
                    case RSA_PKCS1_PADDING:
                        signer = Cipher.getInstance("RSA/ECB/PKCS1PADDING");
                        break;
                    case NO_PADDING:
                        signer = Cipher.getInstance("RSA/ECB/NoPadding");
                        break;
                    case RSA_PKCS1_PSS_PADDING:
                        throw new NoSuchPaddingException("Cannot do PKCS1 PSS padding without also doing the digest");
                }

                signer.init(Cipher.ENCRYPT_MODE, privkey);

                signed_bytes = signer.doFinal(data);

                return signed_bytes;
            }
        } catch
        (NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchPaddingException | SignatureException | InvalidAlgorithmParameterException
                        e) {
            VpnStatus.logError(R.string.error_rsa_sign, e.getClass().toString(), e.getLocalizedMessage());
            return null;
        }
    }

    private byte[] addPSSPadding(PrivateKey privkey, String digest, byte[] data) throws NoSuchAlgorithmException {
        /* For < API 23, add padding ourselves */
        int hashtype = getHashtype(digest);

        MessageDigest msgDigest = MessageDigest.getInstance(digest);
        byte[] hash = msgDigest.digest(data);

        /*  MSBits = (BN_num_bits(rsa->n) - 1) & 0x7; */
        int numbits = ((RSAPrivateKey) privkey).getModulus().bitLength();

        int MSBits = (numbits - 1) & 0x7;

        return NativeUtils.addRssPssPadding(hashtype, MSBits, numbits/8, hash);
    }

    private int getHashtype(String digest) throws NoSuchAlgorithmException {
        int hashtype = 0;
        switch (digest) {
            case "SHA1":
                hashtype = 1;
                break;
            case "SHA224":
                hashtype = 2;
                break;
            case "SHA256":
                hashtype = 3;
                break;
            case "SHA384":
                hashtype = 4;
                break;
            case "SHA512":
                hashtype = 5;
                break;
            default:
                throw new NoSuchAlgorithmException("Unknown digest algorithm: " + digest);
        }
        return hashtype;
    }

    private byte[] doDigestSign(PrivateKey privkey, byte[] data, OpenVPNManagement.SignaturePadding padding, String hashalg, String saltlen) throws SignatureException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        /* RSA */
        Signature sig = null;

        if (privkey.getAlgorithm().equals("EC")) {
            if (hashalg.equals(""))
                hashalg = "NONE";
            /* e.g. SHA512withECDSA */
            hashalg = hashalg + "withECDSA";
            sig = Signature.getInstance(hashalg.toUpperCase(Locale.ROOT));
        } else if (padding == OpenVPNManagement.SignaturePadding.RSA_PKCS1_PSS_PADDING) {
            /* https://developer.android.com/training/articles/keystore#SupportedSignatures */
            if (!"digest".equals(saltlen))
                throw new SignatureException("PSS signing requires saltlen=digest");

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                data = addPSSPadding(privkey, hashalg, data);
                return getKeyChainSignedData(data, OpenVPNManagement.SignaturePadding.NO_PADDING, "none", "none", false);
            }

            sig = Signature.getInstance(hashalg + "withRSA/PSS");

            PSSParameterSpec pssspec = null;
            switch (hashalg) {
                case "SHA256":
                    pssspec = new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
                    break;
                case "SHA512":
                    pssspec = new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1);
                    break;
                case "SHA384":
                    pssspec = new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, 1);
                    break;
            }
            sig.setParameter(pssspec);
        } else if (padding == OpenVPNManagement.SignaturePadding.RSA_PKCS1_PADDING) {
            sig = Signature.getInstance(hashalg + "withRSA");
        }

        sig.initSign(privkey);
        sig.update(data);
        return sig.sign();
    }

    private boolean usesExtraProxyOptions() {
        if (mUseCustomConfig && mCustomConfigOptions != null && mCustomConfigOptions.contains("http-proxy-option "))
            return true;
        for (Connection c : mConnections)
            if (c.usesExtraProxyOptions())
                return true;

        return false;
    }

    /**
     * The order of elements is important!
     */
    private enum RsaPaddingType {
        NO_PADDING,
        PKCS1_PADDING,
        RSAPSS_PADDING
    }

    static class NoCertReturnedException extends Exception {
        public NoCertReturnedException(String msg) {
            super(msg);
        }
    }
}




