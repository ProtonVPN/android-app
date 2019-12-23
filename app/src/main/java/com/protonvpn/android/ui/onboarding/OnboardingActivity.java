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

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.protonvpn.android.R;
import com.protonvpn.android.components.BaseActivity;
import com.protonvpn.android.components.ContentLayout;
import com.protonvpn.android.components.ViewPagerAdapter;
import com.protonvpn.android.ui.LoginActivity;
import com.protonvpn.android.utils.AnimationTools;

import androidx.viewpager.widget.ViewPager;
import butterknife.BindView;
import butterknife.OnClick;
import me.relex.circleindicator.CircleIndicator;

import static com.protonvpn.android.api.ProtonApiRetroFit.SIGNUP_URL;

@ContentLayout(R.layout.onboarding_intro)
public class OnboardingActivity extends BaseActivity implements ViewPager.OnPageChangeListener {

    @BindView(R.id.viewPager) ViewPager viewPager;
    @BindView(R.id.indicator) CircleIndicator indicator;
    @BindView(R.id.buttonLayout) LinearLayout buttonLayout;
    @BindView(R.id.buttonSignup) Button buttonSignup;
    @BindView(R.id.buttonLogin) Button buttonLogin;
    @BindView(R.id.textSkip) TextView textSkip;
    @BindView(R.id.textNext) TextView textNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFrag(
            OnboardingFragment.newInstance(R.drawable.onboarding_start, R.string.onboardingStartSlideTitle,
                R.string.onboardingStartSlideDescription));
        adapter.addFrag(
            OnboardingFragment.newInstance(R.drawable.onboarding_middle, R.string.onboardingMiddleSlideTitle,
                R.string.onboardingMiddleSlideDescription));
        adapter.addFrag(
            OnboardingFragment.newInstance(R.drawable.onboarding_finish, R.string.onboardingEndingSlideTitle,
                R.string.onboardingEndingSlideDescription));
        viewPager.setAdapter(adapter);
        indicator.setViewPager(viewPager);
        viewPager.addOnPageChangeListener(this);
        arrangeButtons();
    }

    @Override
    public void onBackPressed() {
        navigateTo(LoginActivity.class);
        finish();
    }

    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        arrangeButtons();
    }

    private void arrangeButtons() {
        transformButtons(viewPager.getCurrentItem() == 2 ? 1 : 0);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        transformButtons(position, positionOffset);
    }

    private void transformButtons(float offset) {
        transformButtons(1, offset);
    }

    private void transformButtons(int position, float offset) {
        translateButton(position, offset, buttonLogin, false);
        translateButton(position, offset, buttonSignup, true);
        if (position == 1) {
            textSkip.setAlpha(1 - offset);
            textNext.setAlpha(1 - offset);
        }
    }

    private void translateButton(int position, float positionOffset, View view, boolean translateLeft) {
        float leftOrRight =
            AnimationTools.convertDpToPixel(getResources().getBoolean(R.bool.isTablet) ? 640 : 220);
        if (translateLeft) {
            leftOrRight = -leftOrRight;
        }
        if (position == 1) {
            float offset = 1 - positionOffset;
            int translationX = (int) (leftOrRight * offset);
            view.setTranslationX(translationX);
        }
    }

    @Override
    public void onPageSelected(int position) {

    }

    @Override
    public void onPageScrollStateChanged(int state) {

    }

    @OnClick(R.id.buttonLogin)
    public void buttonLogin() {
        navigateTo(LoginActivity.class);
        finish();
    }

    @OnClick(R.id.buttonSignup)
    public void buttonSignup() {
        openUrl(SIGNUP_URL);
    }

    @OnClick(R.id.textSkip)
    public void textSkip() {
        navigateTo(LoginActivity.class);
        finish();
    }

    @OnClick(R.id.textNext)
    public void textNext() {
        viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
    }

}
