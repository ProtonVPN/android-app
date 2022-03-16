/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.blinkt.openvpn.api;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import de.blinkt.openvpn.R;
import de.blinkt.openvpn.core.IOpenVPNServiceInternal;
import de.blinkt.openvpn.core.OpenVPNService;


public class ConfirmDialog extends Activity implements
        CompoundButton.OnCheckedChangeListener, DialogInterface.OnClickListener {
    private static final String TAG = "OpenVPNVpnConfirm";

    public static final String EXTRA_PACKAGE_NAME = "android.intent.extra.PACKAGE_NAME";

    public static final String ANONYMOUS_PACKAGE = "de.blinkt.openvpn.ANYPACKAGE";

    private String mPackage;

    private Button mButton;

    private AlertDialog mAlert;

    private IOpenVPNServiceInternal mService;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            mService = IOpenVPNServiceInternal.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
        }

    };

    @Override
    protected void onResume() {
        super.onResume();

        Intent serviceintent = new Intent(this, OpenVPNService.class);
        serviceintent.setAction(OpenVPNService.START_SERVICE);
        bindService(serviceintent, mConnection, Context.BIND_AUTO_CREATE);

        Intent intent = getIntent();
        if (intent.getStringExtra(EXTRA_PACKAGE_NAME) != null) {
            mPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME);
        } else {
            mPackage = getCallingPackage();
            if (mPackage == null) {
                finish();
                return;
            }
        }

        try {
            View view = View.inflate(this, R.layout.api_confirm, null);
            CharSequence appString;
            if (mPackage.equals(ANONYMOUS_PACKAGE)) {
                appString = getString(R.string.all_app_prompt, getString(R.string.app));
            } else {
                PackageManager pm = getPackageManager();
                ApplicationInfo app = pm.getApplicationInfo(mPackage, 0);
                appString = getString(R.string.prompt, app.loadLabel(pm), getString(R.string.app));
                ((ImageView) view.findViewById(R.id.icon)).setImageDrawable(app.loadIcon(pm));
            }


            ((TextView) view.findViewById(R.id.prompt)).setText(appString);
            ((CompoundButton) view.findViewById(R.id.check)).setOnCheckedChangeListener(this);


            Builder builder = new AlertDialog.Builder(this);

            builder.setView(view);

            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setTitle(android.R.string.dialog_alert_title);
            builder.setPositiveButton(android.R.string.ok, this);
            builder.setNegativeButton(android.R.string.cancel, this);

            mAlert = builder.create();
            mAlert.setCanceledOnTouchOutside(false);

            mAlert.setOnShowListener(new OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    mButton = mAlert.getButton(DialogInterface.BUTTON_POSITIVE);
                    mButton.setEnabled(false);

                }
            });

            //setCloseOnTouchOutside(false);

            mAlert.show();

        } catch (Exception e) {
            Log.e(TAG, "onResume", e);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onCheckedChanged(CompoundButton button, boolean checked) {
        mButton.setEnabled(checked);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(mConnection);

    }

    @Override
    public void onClick(DialogInterface dialog, int which) {

        if (which == DialogInterface.BUTTON_POSITIVE) {
            try {
                mService.addAllowedExternalApp(mPackage);
            } catch (RemoteException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            setResult(RESULT_OK);
            finish();
        }

        if (which == DialogInterface.BUTTON_NEGATIVE) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

}

