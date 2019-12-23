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

import android.widget.ImageView;

import com.protonvpn.android.R;
import com.protonvpn.android.components.BaseFragment;
import com.protonvpn.android.components.ContentLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatTextView;
import butterknife.BindView;

@ContentLayout(R.layout.fragment_image)
public class OnboardingFragment extends BaseFragment {

    @DrawableRes private int imageResId;
    @StringRes private int titleResId;
    @StringRes private int descriptionResId;
    @BindView(R.id.mapView) ImageView imageView;
    @BindView(R.id.textTitle) AppCompatTextView textTitle;
    @BindView(R.id.textDescription) AppCompatTextView textDescription;

    public OnboardingFragment() {
    }

    public static OnboardingFragment newInstance(@DrawableRes int imageResId, @StringRes int titleResId,
                                                 @StringRes int descriptionResId) {
        OnboardingFragment fragment = new OnboardingFragment();
        fragment.imageResId = imageResId;
        fragment.titleResId = titleResId;
        fragment.descriptionResId = descriptionResId;
        return fragment;
    }

    @Override
    public void onViewCreated() {
        setRetainInstance(true);
        imageView.setImageResource(imageResId);
        // FIXME: For some reason there are cases then titleRes and descriptionRes are 0 and this crashes
        // with resource not found. This is probably due to TaskStackBuilder. But this needs more
        // investigation
        // as it's non reproducable
        if (titleResId != 0 && descriptionResId != 0) {
            textTitle.setText(getString(titleResId));
            textDescription.setText(getString(descriptionResId));
        }
    }
}