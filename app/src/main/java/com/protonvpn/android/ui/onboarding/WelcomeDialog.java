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
package com.protonvpn.android.ui.onboarding;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.protonvpn.android.R;
import com.protonvpn.android.models.config.UserData;
import com.protonvpn.android.utils.HtmlTools;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import dagger.android.support.DaggerDialogFragment;

public class WelcomeDialog extends DaggerDialogFragment {

    private static final String TAG = "WelcomeDialog";
    @BindView(R.id.textTitle) TextView textTitle;
    @BindView(R.id.textDescription) TextView textDescription;
    @BindView(R.id.image) ImageView imageView;
    @Inject UserData userData;

    final CountDownTimer timer = new CountDownTimer(TimeUnit.MINUTES.toMillis(4), 1000) {
        @Override
        public void onTick(long millisUntilFinished) {
            textDescription.setText(HtmlTools.fromHtml(getString(R.string.accountTrialExpires,
                userData.getVpnInfoResponse().getTrialRemainingTimeString())));
        }

        @Override
        public void onFinish() {

        }
    };

    public enum DialogType {
        WELCOME, TRIAL
    }

    private DialogType type;

    public static void showDialog(FragmentManager manager, DialogType type) {
        WelcomeDialog dialog = new WelcomeDialog();
        dialog.type = type;
        FragmentTransaction ft = manager.beginTransaction();
        dialog.show(ft, TAG);
    }

    public void onStart() {
        super.onStart();
        if (type == DialogType.TRIAL) {
            timer.start();
        }
    }

    public void onStop() {
        super.onStop();
        if (type == DialogType.TRIAL) {
            timer.cancel();
        }
    }

    public static DialogFragment getDialog(FragmentManager manager) {
        return (DialogFragment) manager.findFragmentByTag(TAG);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.OnboardingDialogStyle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        super.onCreateView(inflater, parent, state);
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_welcome, parent, true);
        ButterKnife.bind(this, view);
        initLayout();
        setCancelable(false);
        return view;
    }

    @OnClick(R.id.buttonGotIt)
    public void buttonGotIt() {
        timer.cancel();
        dismiss();
    }

    private void initLayout() {
        if (type == DialogType.WELCOME) {
            textTitle.setText("Thanks for joining ever growing ProtonVPN community.");
            textTitle.setTypeface(Typeface.DEFAULT_BOLD);
            textDescription.setText("Use your newly created account credentials to login.");
            imageView.setImageResource(R.drawable.onboarding_welcome);
        }
        else {
            textTitle.setText(
                "As a welcome gift you have access to a full ProtonVPN subscription including advanced "
                    + "features for the first days!");
            imageView.setImageResource(R.drawable.onboarding_trial_welcome);
        }
    }
}