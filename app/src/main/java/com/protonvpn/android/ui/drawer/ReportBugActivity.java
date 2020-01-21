/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.android.ui.drawer;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import com.protonvpn.android.BuildConfig;
import com.protonvpn.android.R;
import com.protonvpn.android.api.ProtonApiRetroFit;
import com.protonvpn.android.components.BaseActivity;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.ProtonSwitch;
import com.protonvpn.android.models.config.UserData;

import org.strongswan.android.logic.CharonVpnService;

import java.io.File;

import javax.inject.Inject;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import butterknife.BindView;
import butterknife.OnClick;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

@ContentLayout(R.layout.activity_report)
public class ReportBugActivity extends BaseActivity {

    @BindView(R.id.coordinator) CoordinatorLayout coordinator;
    @BindView(R.id.editReport) AppCompatEditText editReport;
    @BindView(R.id.editEmail) AppCompatEditText editEmail;
    @BindView(R.id.switchAttachLog) ProtonSwitch switchAttachLog;
    @Inject ProtonApiRetroFit api;
    @Inject UserData userData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initToolbarWithUpEnabled();
        addHideKeyboard(coordinator);
    }

    @SuppressLint("ClickableViewAccessibility")
    public void addHideKeyboard(View view) {
        if (!(view instanceof EditText)) {
            view.setOnTouchListener((v, event) -> {
                hideKeyboard(editReport);
                return false;
            });
        }

        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View innerView = ((ViewGroup) view).getChildAt(i);
                addHideKeyboard(innerView);
            }
        }
    }

    @OnClick(R.id.buttonReport)
    public void buttonReport() {
        if (checkInput()) {
            postReport();
        }
    }

    boolean isEmailValid(CharSequence email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean checkInput() {
        String email = editEmail.getText().toString().trim();
        String description = editReport.getText().toString();
        if (TextUtils.isEmpty(email)) {
            editEmail.setError("Email can't be empty");
            return false;
        }
        if (!isEmailValid(email)) {
            editEmail.setError("Invalid email address");
            return false;
        }
        if (TextUtils.isEmpty(description)) {
            editReport.setError("Please write us what is happening");
            return false;
        }
        return true;
    }

    private void postReport() {
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM)

            .addFormDataPart("Client", "Android app")
            .addFormDataPart("ClientVersion", BuildConfig.VERSION_NAME)
            .addFormDataPart("Username", userData.getUser())
            .addFormDataPart("Email", editEmail.getText().toString().trim())
            .addFormDataPart("Plan", userData.getVpnInfoResponse().getUserTierName())
            .addFormDataPart("OS", "Android")
            .addFormDataPart("OSVersion", String.valueOf(Build.VERSION.RELEASE))
            .addFormDataPart("ClientType", "2")
            .addFormDataPart("Country", "Unknown")
            .addFormDataPart("ISP", "Unknown")
            .addFormDataPart("Title", "Report from Android app")
            .addFormDataPart("Description", editReport.getText().toString());

        if (switchAttachLog.getSwitchProton().isChecked() && !userData.isOpenVPNSelected()) {
            File log = new File(getContext().getFilesDir(), CharonVpnService.LOG_FILE);
            if (log.exists()) {
                builder.addFormDataPart(log.getName(), log.getName(),
                        RequestBody.create(MediaType.parse(log.getName()), log));
            }
        }

        api.postBugReport(this, builder.build(), result -> {
            if (result.isSuccess()) {
                Toast.makeText(this, "Thank you for your report", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}