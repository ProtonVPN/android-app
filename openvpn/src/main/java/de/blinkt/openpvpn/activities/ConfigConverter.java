
/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import androidx.annotation.NonNull;
import de.blinkt.openpvpn.R;
import de.blinkt.openpvpn.VpnProfile;
import de.blinkt.openpvpn.core.ConfigParser;
import de.blinkt.openpvpn.core.ConfigParser.ConfigParseError;
import de.blinkt.openpvpn.core.ProfileManager;
import de.blinkt.openpvpn.fragments.Utils;
import de.blinkt.openpvpn.views.FileSelectLayout;

import static de.blinkt.openpvpn.views.FileSelectLayout.FileSelectCallback;

public class ConfigConverter extends BaseActivity implements FileSelectCallback, View.OnClickListener {

    public static final String IMPORT_PROFILE = "de.blinkt.openpvpn.IMPORT_PROFILE";
    private static final int RESULT_INSTALLPKCS12 = 7;
    private static final int CHOOSE_FILE_OFFSET = 1000;
    public static final String VPNPROFILE = "vpnProfile";
    private static final int PERMISSION_REQUEST_EMBED_FILES = 37231;
    private static final int PERMISSION_REQUEST_READ_URL = PERMISSION_REQUEST_EMBED_FILES + 1;

    private VpnProfile mResult;

    private transient List<String> mPathsegments;

    private String mAliasName = null;


    private Map<Utils.FileType, FileSelectLayout> fileSelectMap = new HashMap<>();
    private String mEmbeddedPwFile;
    private Vector<String> mLogEntries = new Vector<>();
    private Uri mSourceUri;
    private EditText mProfilename;
    private AsyncTask<Void, Void, Integer> mImportTask;
    private LinearLayout mLogLayout;
    private TextView mProfilenameLabel;

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.fab_save)
            userActionSaveProfile();
        if (v.getId() == R.id.permssion_hint && Build.VERSION.SDK_INT == Build.VERSION_CODES.M)
            doRequestSDCardPermission(PERMISSION_REQUEST_EMBED_FILES);

    }

    @TargetApi(Build.VERSION_CODES.M)
    private void doRequestSDCardPermission(int requestCode) {
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, requestCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Permission declined, do nothing
        if (grantResults.length == 0 || grantResults[0] == PackageManager.PERMISSION_DENIED)
            return;

        // Reset file select dialogs
        findViewById(R.id.files_missing_hint).setVisibility(View.GONE);
        findViewById(R.id.permssion_hint).setVisibility(View.GONE);
        LinearLayout fileroot = (LinearLayout) findViewById(R.id.config_convert_root);
        for (int i = 0; i < fileroot.getChildCount(); ) {
            if (fileroot.getChildAt(i) instanceof FileSelectLayout)
                fileroot.removeViewAt(i);
            else
                i++;
        }

        if (requestCode == PERMISSION_REQUEST_EMBED_FILES)
            embedFiles(null);

        else if (requestCode == PERMISSION_REQUEST_READ_URL) {
            if (mSourceUri != null)
                doImportUri(mSourceUri);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.cancel) {
            setResult(Activity.RESULT_CANCELED);
            finish();
        } else if (item.getItemId() == R.id.ok) {
            return userActionSaveProfile();
        }

        return super.onOptionsItemSelected(item);

    }

    private boolean userActionSaveProfile() {
        if (mResult == null) {
            log(R.string.import_config_error);
            Toast.makeText(this, R.string.import_config_error, Toast.LENGTH_LONG).show();
            return true;
        }

        mResult.mName = mProfilename.getText().toString();
        ProfileManager vpl = ProfileManager.getInstance(this);
        if (vpl.getProfileByName(mResult.mName) != null) {
            mProfilename.setError(getString(R.string.duplicate_profile_name));
            return true;
        }

        Intent in = installPKCS12();

        if (in != null)
            startActivityForResult(in, RESULT_INSTALLPKCS12);
        else
            saveProfile();

        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mResult != null)
            outState.putSerializable(VPNPROFILE, mResult);
        outState.putString("mAliasName", mAliasName);


        String[] logentries = mLogEntries.toArray(new String[mLogEntries.size()]);

        outState.putStringArray("logentries", logentries);

        int[] fileselects = new int[fileSelectMap.size()];
        int k = 0;
        for (Utils.FileType key : fileSelectMap.keySet()) {
            fileselects[k] = key.getValue();
            k++;
        }
        outState.putIntArray("fileselects", fileselects);
        outState.putString("pwfile", mEmbeddedPwFile);
        outState.putParcelable("mSourceUri", mSourceUri);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (requestCode == RESULT_INSTALLPKCS12 && resultCode == Activity.RESULT_OK) {
            showCertDialog();
        }

        if (resultCode == Activity.RESULT_OK && requestCode >= CHOOSE_FILE_OFFSET) {
            Utils.FileType type = Utils.FileType.getFileTypeByValue(requestCode - CHOOSE_FILE_OFFSET);


            FileSelectLayout fs = fileSelectMap.get(type);
            fs.parseResponse(result, this);

            String data = fs.getData();

            switch (type) {
                case USERPW_FILE:
                    mEmbeddedPwFile = data;
                    break;
                case PKCS12:
                    mResult.mPKCS12Filename = data;
                    break;
                case TLS_AUTH_FILE:
                    mResult.mTLSAuthFilename = data;
                    break;
                case CA_CERTIFICATE:
                    mResult.mCaFilename = data;
                    break;
                case CLIENT_CERTIFICATE:
                    mResult.mClientCertFilename = data;
                    break;
                case KEYFILE:
                    mResult.mClientKeyFilename = data;
                    break;
                case CRL_FILE:
                    mResult.mCrlFilename = data;
                    break;
                default:
                    throw new RuntimeException("Type is wrong somehow?");
            }
        }

        super.onActivityResult(requestCode, resultCode, result);
    }

    private void saveProfile() {
        Intent result = new Intent();
        ProfileManager vpl = ProfileManager.getInstance(this);

        if (!TextUtils.isEmpty(mEmbeddedPwFile))
            ConfigParser.useEmbbedUserAuth(mResult, mEmbeddedPwFile);

        vpl.addProfile(mResult);
        vpl.saveProfile(this, mResult);
        vpl.saveProfileList(this);
        result.putExtra(VpnProfile.EXTRA_PROFILEUUID, mResult.getUUID().toString());
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    public void showCertDialog() {
        try {
            //noinspection WrongConstant
            KeyChain.choosePrivateKeyAlias(this,
                    new KeyChainAliasCallback() {

                        public void alias(String alias) {
                            // Credential alias selected.  Remember the alias selection for future use.
                            mResult.mAlias = alias;
                            saveProfile();
                        }


                    },
                    new String[]{"RSA"}, // List of acceptable key types. null for any
                    null,                        // issuer, null for any
                    mResult.mServerName,      // host name of server requesting the cert, null if unavailable
                    -1,                         // port of server requesting the cert, -1 if unavailable
                    mAliasName);                       // alias to preselect, null if unavailable
        } catch (ActivityNotFoundException anf) {
            Builder ab = new AlertDialog.Builder(this);
            ab.setTitle(R.string.broken_image_cert_title);
            ab.setMessage(R.string.broken_image_cert);
            ab.setPositiveButton(android.R.string.ok, null);
            ab.show();
        }
    }


    private Intent installPKCS12() {

        if (!((CheckBox) findViewById(R.id.importpkcs12)).isChecked()) {
            setAuthTypeToEmbeddedPKCS12();
            return null;

        }
        String pkcs12datastr = mResult.mPKCS12Filename;
        if (VpnProfile.isEmbedded(pkcs12datastr)) {
            Intent inkeyIntent = KeyChain.createInstallIntent();

            pkcs12datastr = VpnProfile.getEmbeddedContent(pkcs12datastr);


            byte[] pkcs12data = Base64.decode(pkcs12datastr, Base64.DEFAULT);


            inkeyIntent.putExtra(KeyChain.EXTRA_PKCS12, pkcs12data);

            if (mAliasName.equals(""))
                mAliasName = null;

            if (mAliasName != null) {
                inkeyIntent.putExtra(KeyChain.EXTRA_NAME, mAliasName);
            }
            return inkeyIntent;

        }
        return null;
    }


    private void setAuthTypeToEmbeddedPKCS12() {
        if (VpnProfile.isEmbedded(mResult.mPKCS12Filename)) {
            if (mResult.mAuthenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE)
                mResult.mAuthenticationType = VpnProfile.TYPE_USERPASS_PKCS12;

            if (mResult.mAuthenticationType == VpnProfile.TYPE_KEYSTORE)
                mResult.mAuthenticationType = VpnProfile.TYPE_PKCS12;

        }
    }


    private String getUniqueProfileName(String possibleName) {

        int i = 0;

        ProfileManager vpl = ProfileManager.getInstance(this);

        String newname = possibleName;

        // 	Default to
        if (mResult.mName != null && !ConfigParser.CONVERTED_PROFILE.equals(mResult.mName))
            newname = mResult.mName;

        while (newname == null || vpl.getProfileByName(newname) != null) {
            i++;
            if (i == 1)
                newname = getString(R.string.converted_profile);
            else
                newname = getString(R.string.converted_profile_i, i);
        }

        return newname;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.import_menu, menu);
        return true;
    }

    private String embedFile(String filename, Utils.FileType type, boolean onlyFindFileAndNullonNotFound) {
        if (filename == null)
            return null;

        // Already embedded, nothing to do
        if (VpnProfile.isEmbedded(filename))
            return filename;

        File possibleFile = findFile(filename, type);
        if (possibleFile == null)
            if (onlyFindFileAndNullonNotFound)
                return null;
            else
                return filename;
        else if (onlyFindFileAndNullonNotFound)
            return possibleFile.getAbsolutePath();
        else
            return readFileContent(possibleFile, type == Utils.FileType.PKCS12);

    }


    private Pair<Integer, String> getFileDialogInfo(Utils.FileType type) {
        int titleRes = 0;
        String value = null;
        switch (type) {
            case KEYFILE:
                titleRes = R.string.client_key_title;
                if (mResult != null)
                    value = mResult.mClientKeyFilename;
                break;
            case CLIENT_CERTIFICATE:
                titleRes = R.string.client_certificate_title;
                if (mResult != null)
                    value = mResult.mClientCertFilename;
                break;
            case CA_CERTIFICATE:
                titleRes = R.string.ca_title;
                if (mResult != null)
                    value = mResult.mCaFilename;
                break;
            case TLS_AUTH_FILE:
                titleRes = R.string.tls_auth_file;
                if (mResult != null)
                    value = mResult.mTLSAuthFilename;
                break;
            case PKCS12:
                titleRes = R.string.client_pkcs12_title;
                if (mResult != null)
                    value = mResult.mPKCS12Filename;
                break;

            case USERPW_FILE:
                titleRes = R.string.userpw_file;
                value = mEmbeddedPwFile;
                break;

            case CRL_FILE:
                titleRes = R.string.crl_file;
                value = mResult.mCrlFilename;
                break;
        }

        return Pair.create(titleRes, value);

    }

    private File findFile(String filename, Utils.FileType fileType) {
        File foundfile = findFileRaw(filename);

        if (foundfile == null && filename != null && !filename.equals("")) {
            log(R.string.import_could_not_open, filename);
        }
        fileSelectMap.put(fileType, null);

        return foundfile;
    }

    private void addMissingFileDialogs()
    {
        for (Map.Entry<Utils.FileType, FileSelectLayout> item: fileSelectMap.entrySet()) {
            if (item.getValue()==null)
                addFileSelectDialog(item.getKey());
        }
    }

    private void addFileSelectDialog(Utils.FileType type) {

        Pair<Integer, String> fileDialogInfo = getFileDialogInfo(type);

        boolean isCert = type == Utils.FileType.CA_CERTIFICATE || type == Utils.FileType.CLIENT_CERTIFICATE;
        FileSelectLayout fl = new FileSelectLayout(this, getString(fileDialogInfo.first), isCert, false);
        fileSelectMap.put(type, fl);
        fl.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ((LinearLayout) findViewById(R.id.config_convert_root)).addView(fl, 2);
        findViewById(R.id.files_missing_hint).setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M)
            checkPermission();

        fl.setData(fileDialogInfo.second, this);
        int i = getFileLayoutOffset(type);
        fl.setCaller(this, i, type);

    }

    @TargetApi(Build.VERSION_CODES.M)
    private void checkPermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            findViewById(R.id.permssion_hint).setVisibility(View.VISIBLE);
            findViewById(R.id.permssion_hint).setOnClickListener(this);
        }
    }

    private int getFileLayoutOffset(Utils.FileType type) {
        return CHOOSE_FILE_OFFSET + type.getValue();
    }


    private File findFileRaw(String filename) {
        if (filename == null || filename.equals(""))
            return null;

        // Try diffent path relative to /mnt/sdcard
        File sdcard = Environment.getExternalStorageDirectory();
        File root = new File("/");

        HashSet<File> dirlist = new HashSet<>();

        for (int i = mPathsegments.size() - 1; i >= 0; i--) {
            String path = "";
            for (int j = 0; j <= i; j++) {
                path += "/" + mPathsegments.get(j);
            }
            // Do a little hackish dance for the Android File Importer
            // /document/primary:ovpn/openvpn-imt.conf


            if (path.indexOf(':') != -1 && path.lastIndexOf('/') > path.indexOf(':')) {
                String possibleDir = path.substring(path.indexOf(':') + 1, path.length());
                // Unquote chars in the  path
                try {
                    possibleDir = URLDecoder.decode(possibleDir, "UTF-8");
                } catch (UnsupportedEncodingException ignored) {}

                possibleDir = possibleDir.substring(0, possibleDir.lastIndexOf('/'));




                dirlist.add(new File(sdcard, possibleDir));

            }
            dirlist.add(new File(path));


        }
        dirlist.add(sdcard);
        dirlist.add(root);


        String[] fileparts = filename.split("/");
        for (File rootdir : dirlist) {
            String suffix = "";
            for (int i = fileparts.length - 1; i >= 0; i--) {
                if (i == fileparts.length - 1)
                    suffix = fileparts[i];
                else
                    suffix = fileparts[i] + "/" + suffix;

                File possibleFile = new File(rootdir, suffix);
                if (possibleFile.canRead())
                    return possibleFile;

            }
        }
        return null;
    }

    String readFileContent(File possibleFile, boolean base64encode) {
        byte[] filedata;
        try {
            filedata = readBytesFromFile(possibleFile);
        } catch (IOException e) {
            log(e.getLocalizedMessage());
            return null;
        }

        String data;
        if (base64encode) {
            data = Base64.encodeToString(filedata, Base64.DEFAULT);
        } else {
            data = new String(filedata);

        }

        return VpnProfile.DISPLAYNAME_TAG + possibleFile.getName() + VpnProfile.INLINE_TAG + data;

    }


    private byte[] readBytesFromFile(File file) throws IOException {
        InputStream input = new FileInputStream(file);

        long len = file.length();
        if (len > VpnProfile.MAX_EMBED_FILE_SIZE)
            throw new IOException("File size of file to import too large.");

        // Create the byte array to hold the data
        byte[] bytes = new byte[(int) len];

        // Read in the bytes
        int offset = 0;
        int bytesRead;
        while (offset < bytes.length
                && (bytesRead = input.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += bytesRead;
        }

        input.close();
        return bytes;
    }

    void embedFiles(ConfigParser cp) {
        // This where I would like to have a c++ style
        // void embedFile(std::string & option)

        if (mResult.mPKCS12Filename != null) {
            File pkcs12file = findFileRaw(mResult.mPKCS12Filename);
            if (pkcs12file != null) {
                mAliasName = pkcs12file.getName().replace(".p12", "");
            } else {
                mAliasName = "Imported PKCS12";
            }
        }


        mResult.mCaFilename = embedFile(mResult.mCaFilename, Utils.FileType.CA_CERTIFICATE, false);
        mResult.mClientCertFilename = embedFile(mResult.mClientCertFilename, Utils.FileType.CLIENT_CERTIFICATE, false);
        mResult.mClientKeyFilename = embedFile(mResult.mClientKeyFilename, Utils.FileType.KEYFILE, false);
        mResult.mTLSAuthFilename = embedFile(mResult.mTLSAuthFilename, Utils.FileType.TLS_AUTH_FILE, false);
        mResult.mPKCS12Filename = embedFile(mResult.mPKCS12Filename, Utils.FileType.PKCS12, false);
        mResult.mCrlFilename = embedFile(mResult.mCrlFilename, Utils.FileType.CRL_FILE, true);
        if (cp != null) {
            mEmbeddedPwFile = cp.getAuthUserPassFile();
            mEmbeddedPwFile = embedFile(cp.getAuthUserPassFile(), Utils.FileType.USERPW_FILE, false);
        }

    }

    private void updateFileSelectDialogs() {
        for (Map.Entry<Utils.FileType, FileSelectLayout> fl : fileSelectMap.entrySet()) {
            fl.getValue().setData(getFileDialogInfo(fl.getKey()).second, this);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.config_converter);

        ImageButton fab_button = (ImageButton) findViewById(R.id.fab_save);
        if (fab_button != null) {
            fab_button.setOnClickListener(this);
            findViewById(R.id.fab_footerspace).setVisibility(View.VISIBLE);
        }

        mLogLayout = (LinearLayout) findViewById(R.id.config_convert_root);


        mProfilename = (EditText) findViewById(R.id.profilename);
        mProfilenameLabel = (TextView) findViewById(R.id.profilename_label);

        if (savedInstanceState != null && savedInstanceState.containsKey(VPNPROFILE)) {
            mResult = (VpnProfile) savedInstanceState.getSerializable(VPNPROFILE);
            mAliasName = savedInstanceState.getString("mAliasName");
            mEmbeddedPwFile = savedInstanceState.getString("pwfile");
            mSourceUri = savedInstanceState.getParcelable("mSourceUri");
            mProfilename.setText(mResult.mName);

            if (savedInstanceState.containsKey("logentries")) {
                //noinspection ConstantConditions
                for (String logItem : savedInstanceState.getStringArray("logentries"))
                    log(logItem);
            }
            if (savedInstanceState.containsKey("fileselects")) {
                //noinspection ConstantConditions
                for (int k : savedInstanceState.getIntArray("fileselects")) {
                    addFileSelectDialog(Utils.FileType.getFileTypeByValue(k));
                }
            }
            return;
        }


        final android.content.Intent intent = getIntent();

        if (intent != null) {
            doImportIntent(intent);

            // We parsed the intent, relay on saved instance for restoring
            setIntent(null);
        }


    }

    private void doImportIntent(Intent intent) {
        final Uri data = intent.getData();
        if (data != null) {
            mSourceUri = data;
            doImportUri(data);
        }
    }

    private void doImportUri(Uri data) {
        //log(R.string.import_experimental);
        log(R.string.importing_config, data.toString());
        String possibleName = null;
        if ((data.getScheme() != null && data.getScheme().equals("file")) ||
                (data.getLastPathSegment() != null &&
                        (data.getLastPathSegment().endsWith(".ovpn") ||
                                data.getLastPathSegment().endsWith(".conf")))
                ) {
            possibleName = data.getLastPathSegment();
            if (possibleName.lastIndexOf('/') != -1)
                possibleName = possibleName.substring(possibleName.lastIndexOf('/') + 1);

        }

        mPathsegments = data.getPathSegments();

        Cursor cursor = getContentResolver().query(data, null, null, null, null);

        try {

            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                if (columnIndex != -1) {
                    String displayName = cursor.getString(columnIndex);
                    if (displayName != null)
                        possibleName = displayName;
                }
                columnIndex = cursor.getColumnIndex("mime_type");
                if (columnIndex != -1) {
                    log("Mime type: " + cursor.getString(columnIndex));
                }
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        if (possibleName != null) {
            possibleName = possibleName.replace(".ovpn", "");
            possibleName = possibleName.replace(".conf", "");
        }

        startImportTask(data, possibleName);


    }

    private void startImportTask(final Uri data, final String possibleName) {
        mImportTask = new AsyncTask<Void, Void, Integer>() {
            private ProgressBar mProgress;

            @Override
            protected void onPreExecute() {
                mProgress = new ProgressBar(ConfigConverter.this);
                addViewToLog(mProgress);
            }

            @Override
            protected Integer doInBackground(Void... params) {
                try {
                    InputStream is = getContentResolver().openInputStream(data);

                    doImport(is);
                    is.close();
                    if (mResult==null)
                        return -3;
                } catch (IOException| SecurityException se)

                {
                    log(R.string.import_content_resolve_error + ":" + se.getLocalizedMessage());
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        checkMarschmallowFileImportError(data);
                    return -2;
                }

                return 0;
            }

            @Override
            protected void onPostExecute(Integer errorCode) {
                mLogLayout.removeView(mProgress);
                addMissingFileDialogs();
                updateFileSelectDialogs();

                if (errorCode == 0) {
                    displayWarnings();
                    mResult.mName = getUniqueProfileName(possibleName);
                    mProfilename.setVisibility(View.VISIBLE);
                    mProfilenameLabel.setVisibility(View.VISIBLE);
                    mProfilename.setText(mResult.getName());

                    log(R.string.import_done);
                }
            }
        }.execute();
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void checkMarschmallowFileImportError(Uri data) {
        // Permission already granted, not the source of the error
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
            return;

        // We got a file:/// URL and have no permission to read it. Technically an error of the calling app since
        // it makes an assumption about other apps being able to read the url but well ...
        if (data != null && "file".equals(data.getScheme()))
            doRequestSDCardPermission(PERMISSION_REQUEST_READ_URL);

    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    private void log(final String logmessage) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView tv = new TextView(ConfigConverter.this);
                mLogEntries.add(logmessage);
                tv.setText(logmessage);

                addViewToLog(tv);
            }
        });
    }

    private void addViewToLog(View view) {
        mLogLayout.addView(view, mLogLayout.getChildCount() - 1);
    }

    private void doImport(InputStream is) {
        ConfigParser cp = new ConfigParser();
        try {
            InputStreamReader isr = new InputStreamReader(is);

            cp.parseConfig(isr);
            mResult = cp.convertProfile();
            embedFiles(cp);
            return;

        } catch (IOException | ConfigParseError e) {
            log(R.string.error_reading_config_file);
            log(e.getLocalizedMessage());
        }
        mResult = null;

    }

    private void displayWarnings() {
        if (mResult.mUseCustomConfig) {
            log(R.string.import_warning_custom_options);
            String copt = mResult.mCustomConfigOptions;
            if (copt.startsWith("#")) {
                int until = copt.indexOf('\n');
                copt = copt.substring(until + 1);
            }

            log(copt);
        }

        if (mResult.mAuthenticationType == VpnProfile.TYPE_KEYSTORE ||
                mResult.mAuthenticationType == VpnProfile.TYPE_USERPASS_KEYSTORE) {
            findViewById(R.id.importpkcs12).setVisibility(View.VISIBLE);
        }

    }

    private void log(int ressourceId, Object... formatArgs) {
        log(getString(ressourceId, formatArgs));
    }

}
