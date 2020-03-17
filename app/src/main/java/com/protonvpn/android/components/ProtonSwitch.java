/*
 * Copyright (c) 2017 Proton Technologies AG
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
package com.protonvpn.android.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.protonvpn.android.R;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import butterknife.BindView;
import butterknife.ButterKnife;

public class ProtonSwitch extends FrameLayout {

    @BindView(R.id.switchProton) SwitchCompat switchProton;
    @BindView(R.id.textTitle) TextView textTitle;
    @BindView(R.id.textDescription) TextView textDescription;
    @BindView(R.id.imageChevron) ImageView imageChevron;
    @BindView(R.id.divider) View divider;

    public ProtonSwitch(@NonNull Context context) {
        super(context);
    }

    public ProtonSwitch(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        initAttrs(attrs);
    }

    public SwitchCompat getSwitchProton() {
        return switchProton;
    }

    public ProtonSwitch(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
        initAttrs(attrs);
    }

    public void setTitle(CharSequence text) {
        textTitle.setText(text);
    }

    public void setDescription(CharSequence text) {
        textDescription.setText(text);
    }

    private void initAttrs(AttributeSet attrs) {
        TypedArray a = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.ProtonSwitch, 0, 0);

        try {
            String title = a.getString(R.styleable.ProtonSwitch_textTitle);
            String description = a.getString(R.styleable.ProtonSwitch_textDescription);
            boolean isButton = a.getBoolean(R.styleable.ProtonSwitch_isButton, false);
            boolean isInfo = a.getBoolean(R.styleable.ProtonSwitch_isInfo, false);
            textTitle.setText(title);
            if (description == null || description.isEmpty()) {
                textDescription.setVisibility(GONE);
            }
            else {
                textDescription.setText(description);
            }
            if (isInfo) {
                textDescription.setMovementMethod(LinkMovementMethod.getInstance());
            }
            switchProton.setChecked(a.getBoolean(R.styleable.ProtonSwitch_switchValue, false));
            switchProton.setEnabled(a.getBoolean(R.styleable.ProtonSwitch_switchEditable, true));
            switchProton.setVisibility((isButton || isInfo) ? GONE : VISIBLE);
            imageChevron.setVisibility((isButton && !isInfo) ? VISIBLE : GONE);
            divider.setVisibility(
                a.getBoolean(R.styleable.ProtonSwitch_dividerHidden, false) ? View.INVISIBLE : View.VISIBLE);
        }
        finally {
            a.recycle();
        }
    }

    public void setDividerVisibility(int visibility) {
        divider.setVisibility(visibility);
    }

    private void init() {
        inflate(getContext(), R.layout.item_proton_switch, this);
        ButterKnife.bind(this);
    }
}
