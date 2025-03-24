/*
 * Copyright (c) 2019 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.utils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.tozny.crypto.android.AesCbcWithIntegrity;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import androidx.annotation.Nullable;

public class ProtonPreferences implements SharedPreferences {

    private static final int ORIGINAL_ITERATION_COUNT = 10000;

    private SharedPreferences sharedPreferences;

    private AesCbcWithIntegrity.SecretKeys keys;

    private String salt;

    private static boolean sLoggingEnabled = false;

    private static final String TAG = ProtonPreferences.class.getName();

    @Nullable private String sharedPrefFilename;

    public ProtonPreferences(Context context) {
        this(context, "", null);
    }

    public ProtonPreferences(Context context, String salt, String password, final String sharedPrefFilename) {
        this(context, null, password, salt, sharedPrefFilename, ORIGINAL_ITERATION_COUNT);
    }

    public ProtonPreferences(Context context, int iterationCount) {
        this(context, "", null, iterationCount);
    }

    public ProtonPreferences(Context context, final String password, final String sharedPrefFilename) {
        this(context, password, null, sharedPrefFilename, ORIGINAL_ITERATION_COUNT);
    }

    public ProtonPreferences(Context context, final String password, final String sharedPrefFilename,
                             int iterationCount) {
        this(context, null, password, null, sharedPrefFilename, iterationCount);
    }

    public ProtonPreferences(Context context, final AesCbcWithIntegrity.SecretKeys secretKey,
                             final String sharedPrefFilename) {
        this(context, secretKey, null, null, sharedPrefFilename, 0);
    }

    public ProtonPreferences(Context context, final String password, final String salt,
                             final String sharedPrefFilename, int iterationCount) {
        this(context, null, password, salt, sharedPrefFilename, iterationCount);
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    private ProtonPreferences(Context context, final AesCbcWithIntegrity.SecretKeys secretKey,
                              final String password, final String salt, final String sharedPrefFilename,
                              int iterationCount) {
        if (sharedPreferences == null) {
            sharedPreferences = getSharedPreferenceFile(context, sharedPrefFilename);
        }

        this.salt = salt;

        if (secretKey != null) {
            keys = secretKey;
        }
        else if (TextUtils.isEmpty(password)) {
            try {
                final String key = generateAesKeyName(context, iterationCount);

                String keyAsString = sharedPreferences.getString(key, null);
                if (keyAsString == null) {
                    keys = AesCbcWithIntegrity.generateKey();
                    //saving new key
                    boolean committed = sharedPreferences.edit().putString(key, keys.toString()).commit();
                    if (!committed) {
                        Log.w(TAG, "Key not committed to prefs");
                    }
                }
                else {
                    keys = AesCbcWithIntegrity.keys(keyAsString);
                }

                if (keys == null) {
                    throw new GeneralSecurityException("Problem generating Key");
                }

            }
            catch (GeneralSecurityException e) {
                if (sLoggingEnabled) {
                    Log.e(TAG, "Error init:" + e.getMessage());
                }
                throw new IllegalStateException(e);
            }
        }
        else {
            //use the password to generate the key
            try {
                final byte[] saltBytes = getSalt(context).getBytes();
                keys = AesCbcWithIntegrity.generateKeyFromPassword(password, saltBytes, iterationCount);

                if (keys == null) {
                    throw new GeneralSecurityException("Problem generating Key From Password");
                }
            }
            catch (GeneralSecurityException e) {
                if (sLoggingEnabled) {
                    Log.e(TAG, "Error init using user password:" + e.getMessage());
                }
                throw new IllegalStateException(e);
            }
        }
    }

    private SharedPreferences getSharedPreferenceFile(Context context, String prefFilename) {
        this.sharedPrefFilename = prefFilename;

        if (TextUtils.isEmpty(prefFilename)) {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }
        else {
            return context.getSharedPreferences(prefFilename, Context.MODE_PRIVATE);
        }
    }

    public void destroyKeys() {
        keys = null;
    }

    public boolean isEmpty() {
        Map<String, String> stringMap = getAll();
        return stringMap.containsValue(null);
    }

    private String generateAesKeyName(Context context, int iterationCount) throws GeneralSecurityException {
        final String password = context.getPackageName();
        final byte[] salt = getSalt(context).getBytes();
        AesCbcWithIntegrity.SecretKeys generatedKeyName =
            AesCbcWithIntegrity.generateKeyFromPassword(password, salt, iterationCount);

        return hashPrefKey(generatedKeyName.toString());
    }

    /**
     * Gets the hardware serial number of this device.
     *
     * @return serial number or Settings.Secure.ANDROID_ID if not available.
     */
    @SuppressLint("HardwareIds")
    public static String getDeviceSerialNumber(Context context) {
        // We're using the Reflection API because Build.SERIAL is only available
        // since API Level 9 (Gingerbread, Android 2.3).
        try {
            String deviceSerial = (String) Build.class.getField("SERIAL").get(null);
            if (TextUtils.isEmpty(deviceSerial)) {
                return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            }
            else {
                return deviceSerial;
            }
        }
        catch (Exception ignored) {
            return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        }
    }

    private String getSalt(Context context) {
        if (TextUtils.isEmpty(this.salt)) {
            return getDeviceSerialNumber(context);
        }
        else {
            return this.salt;
        }
    }

    @Nullable
    public static String hashPrefKey(String prefKey) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = prefKey.getBytes("UTF-8");
            digest.update(bytes, 0, bytes.length);

            return Base64.encodeToString(digest.digest(), AesCbcWithIntegrity.BASE64_FLAGS);

        }
        catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            if (sLoggingEnabled) {
                Log.w(TAG, "Problem generating hash", e);
            }
        }
        return null;
    }

    private String encrypt(String cleartext) {
        if (TextUtils.isEmpty(cleartext)) {
            return cleartext;
        }
        try {
            return AesCbcWithIntegrity.encrypt(cleartext, keys).toString();
        }
        catch (GeneralSecurityException e) {
            if (sLoggingEnabled) {
                Log.w(TAG, "encrypt", e);
            }
            return null;
        }
        catch (UnsupportedEncodingException e) {
            if (sLoggingEnabled) {
                Log.w(TAG, "encrypt", e);
            }
        }
        return null;
    }

    @Nullable
    private String decrypt(final String ciphertext) {
        if (TextUtils.isEmpty(ciphertext)) {
            return ciphertext;
        }
        try {
            AesCbcWithIntegrity.CipherTextIvMac cipherTextIvMac =
                new AesCbcWithIntegrity.CipherTextIvMac(ciphertext);

            return AesCbcWithIntegrity.decryptString(cipherTextIvMac, keys);
        }
        catch (GeneralSecurityException | UnsupportedEncodingException e) {
            if (sLoggingEnabled) {
                Log.w(TAG, "decrypt", e);
            }
        }
        return null;
    }

    @Override
    public Map<String, String> getAll() {
        final Map<String, ?> encryptedMap = sharedPreferences.getAll();
        final Map<String, String> decryptedMap = new HashMap<String, String>(encryptedMap.size());
        for (Entry<String, ?> entry : encryptedMap.entrySet()) {
            try {
                Object cipherText = entry.getValue();
                if (cipherText != null && !cipherText.equals(keys.toString())) {
                    decryptedMap.put(entry.getKey(), decrypt(cipherText.toString()));
                }
            }
            catch (Exception e) {
                if (sLoggingEnabled) {
                    Log.w(TAG, "error during getAll", e);
                }
                // Ignore issues that unencrypted values and use instead raw cipher text string
                decryptedMap.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return decryptedMap;
    }

    @Override
    public String getString(String key, String defaultValue) {
        final String encryptedValue = sharedPreferences.getString(ProtonPreferences.hashPrefKey(key), null);

        String decryptedValue = decrypt(encryptedValue);
        if (encryptedValue != null && decryptedValue != null) {
            return decryptedValue;
        }
        else {
            return defaultValue;
        }
    }

    public String getEncryptedString(String key, String defaultValue) {
        final String encryptedValue = sharedPreferences.getString(ProtonPreferences.hashPrefKey(key), null);
        return (encryptedValue != null) ? encryptedValue : defaultValue;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public Set<String> getStringSet(String key, Set<String> defaultValues) {
        final Set<String> encryptedSet =
            sharedPreferences.getStringSet(ProtonPreferences.hashPrefKey(key), null);
        if (encryptedSet == null) {
            return defaultValues;
        }
        final Set<String> decryptedSet = new HashSet<String>(encryptedSet.size());
        for (String encryptedValue : encryptedSet) {
            decryptedSet.add(decrypt(encryptedValue));
        }
        return decryptedSet;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        final String encryptedValue = sharedPreferences.getString(ProtonPreferences.hashPrefKey(key), null);
        if (encryptedValue == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(decrypt(encryptedValue));
        }
        catch (NumberFormatException e) {
            throw new ClassCastException(e.getMessage());
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        final String encryptedValue = sharedPreferences.getString(ProtonPreferences.hashPrefKey(key), null);
        if (encryptedValue == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(decrypt(encryptedValue));
        }
        catch (NumberFormatException e) {
            throw new ClassCastException(e.getMessage());
        }
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        final String encryptedValue = sharedPreferences.getString(ProtonPreferences.hashPrefKey(key), null);
        if (encryptedValue == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(decrypt(encryptedValue));
        }
        catch (NumberFormatException e) {
            throw new ClassCastException(e.getMessage());
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        final String encryptedValue = sharedPreferences.getString(ProtonPreferences.hashPrefKey(key), null);
        if (encryptedValue == null) {
            return defaultValue;
        }
        try {
            return Boolean.parseBoolean(decrypt(encryptedValue));
        }
        catch (NumberFormatException e) {
            throw new ClassCastException(e.getMessage());
        }
    }

    @Override
    public boolean contains(String key) {
        return sharedPreferences.contains(ProtonPreferences.hashPrefKey(key));
    }

    /**
     * Cycle through the unencrypt all the current prefs to mem cache, clear, then encrypt with key
     * generated from new password.
     * This method can be used if switching from the generated key to a key derived from user password
     * <p>
     * Note: the pref keys will remain the same as they are SHA256 hashes.
     *
     * @param newPassword
     * @param context        should be ApplicationContext not Activity
     * @param iterationCount The iteration count for the keys generation
     */
    @SuppressLint("CommitPrefEdits")
    public void handlePasswordChange(String newPassword, Context context,
                                     int iterationCount) throws GeneralSecurityException {

        final byte[] salt = getSalt(context).getBytes();
        AesCbcWithIntegrity.SecretKeys newKey =
            AesCbcWithIntegrity.generateKeyFromPassword(newPassword, salt, iterationCount);

        Map<String, ?> allOfThePrefs = sharedPreferences.getAll();
        Map<String, String> unencryptedPrefs = new HashMap<>(allOfThePrefs.size());
        for (String prefKey : allOfThePrefs.keySet()) {
            Object prefValue = allOfThePrefs.get(prefKey);
            if (prefValue instanceof String) {
                final String prefValueString = (String) prefValue;
                final String plainTextPrefValue = decrypt(prefValueString);
                unencryptedPrefs.put(prefKey, plainTextPrefValue);
            }
        }

        destroyKeys();

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.commit();

        //refresh the sharedPreferences object ref: I found it was retaining old ref/values
        sharedPreferences = null;
        sharedPreferences = getSharedPreferenceFile(context, sharedPrefFilename);

        //assign new key
        this.keys = newKey;

        SharedPreferences.Editor updatedEditor = sharedPreferences.edit();

        //iterate through the unencryptedPrefs encrypting each one with new key
        Iterator<String> unencryptedPrefsKeys = unencryptedPrefs.keySet().iterator();
        while (unencryptedPrefsKeys.hasNext()) {
            String prefKey = unencryptedPrefsKeys.next();
            String prefPlainText = unencryptedPrefs.get(prefKey);
            updatedEditor.putString(prefKey, encrypt(prefPlainText));
        }
        updatedEditor.commit();
    }

    public void handlePasswordChange(String newPassword, Context context) throws GeneralSecurityException {
        handlePasswordChange(newPassword, context, ORIGINAL_ITERATION_COUNT);
    }

    @Override
    public Editor edit() {
        return new Editor();
    }

    public final class Editor implements SharedPreferences.Editor {

        private SharedPreferences.Editor mEditor;

        private Editor() {
            mEditor = sharedPreferences.edit();
        }

        @Override
        public SharedPreferences.Editor putString(String key, String value) {
            mEditor.putString(ProtonPreferences.hashPrefKey(key), encrypt(value));
            return this;
        }

        public SharedPreferences.Editor putUnencryptedString(String key, String value) {
            mEditor.putString(ProtonPreferences.hashPrefKey(key), value);
            return this;
        }

        @Override
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        public SharedPreferences.Editor putStringSet(String key, Set<String> values) {
            final Set<String> encryptedValues = new HashSet<String>(values.size());
            for (String value : values) {
                encryptedValues.add(encrypt(value));
            }
            mEditor.putStringSet(ProtonPreferences.hashPrefKey(key), encryptedValues);
            return this;
        }

        @Override
        public SharedPreferences.Editor putInt(String key, int value) {
            mEditor.putString(ProtonPreferences.hashPrefKey(key), encrypt(Integer.toString(value)));
            return this;
        }

        @Override
        public SharedPreferences.Editor putLong(String key, long value) {
            mEditor.putString(ProtonPreferences.hashPrefKey(key), encrypt(Long.toString(value)));
            return this;
        }

        @Override
        public SharedPreferences.Editor putFloat(String key, float value) {
            mEditor.putString(ProtonPreferences.hashPrefKey(key), encrypt(Float.toString(value)));
            return this;
        }

        @Override
        public SharedPreferences.Editor putBoolean(String key, boolean value) {
            mEditor.putString(ProtonPreferences.hashPrefKey(key), encrypt(Boolean.toString(value)));
            return this;
        }

        @Override
        public SharedPreferences.Editor remove(String key) {
            mEditor.remove(ProtonPreferences.hashPrefKey(key));
            return this;
        }

        @Override
        public SharedPreferences.Editor clear() {
            mEditor.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return mEditor.commit();
        }

        @Override
        @TargetApi(Build.VERSION_CODES.GINGERBREAD)
        public void apply() {
            mEditor.apply();
        }
    }

    public static boolean isLoggingEnabled() {
        return sLoggingEnabled;
    }

    public static void setLoggingEnabled(boolean loggingEnabled) {
        sLoggingEnabled = loggingEnabled;
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    /**
     * @param listener    OnSharedPreferenceChangeListener
     * @param decryptKeys Callbacks receive the "key" parameter decrypted
     */
    public void registerOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener,
                                                         boolean decryptKeys) {

        if (!decryptKeys) {
            registerOnSharedPreferenceChangeListener(listener);
        }
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
