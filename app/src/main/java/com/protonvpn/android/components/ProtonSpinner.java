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

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.protonvpn.android.R;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

public class ProtonSpinner<T extends Listable> extends AppCompatEditText {

    List<T> items;
    List<Item> listableItems;
    CharSequence hint;
    T selectedItem;

    OnItemSelectedListener<T> onItemSelectedListener;
    private OnValidateSelection<T> onValidateSelection;

    public ProtonSpinner(Context context) {
        super(context);

        hint = getHint();
    }

    public ProtonSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);

        hint = getHint();
    }

    public ProtonSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        hint = getHint();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        setFocusable(false);
        setClickable(true);
    }

    public void setItems(@NonNull List<T> items) {
        this.items = items;
        this.listableItems = new ArrayList<>();
        for (T item : this.items) {
            listableItems.add(new Item(item.getLabel(getContext()), 0));
        }

        configureOnClickListener();
    }

    public void setSelectedItem(@Nullable T item) {
        selectedItem = item;
        setText(item != null ? item.getLabel(getContext()) : "");
    }

    private void configureOnClickListener() {
        final ListAdapter adapter =
            new ArrayAdapter<Item>(getContext(), R.layout.component_dialog_item, listableItems) {
                @NonNull
                public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                    if (convertView == null) {
                        convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.component_dialog_item, parent, false);
                    }
                    Item item = listableItems.get(position);
                    TextView textDialog = convertView.findViewById(R.id.textDialog);
                    textDialog.setText(item.getText());

                    textDialog.setCompoundDrawablesWithIntrinsicBounds(0, 0, item.getIcon(), 0);
                    int dp5 = (int) (5 * getResources().getDisplayMetrics().density + 0.5f);
                    textDialog.setCompoundDrawablePadding(dp5);

                    return convertView;
                }
            };

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext(), R.style.AlertDialog);
                builder.setTitle(hint);
                builder.setPositiveButton("close", null);
                builder.setAdapter(adapter, null);

                final AlertDialog dialog = builder.create();
                dialog.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        if (onValidateSelection == null || onValidateSelection.onValidateSelection(
                            items.get(position))) {
                            afterValidationClick(position);
                            dialog.dismiss();
                        }
                    }
                });
                dialog.show();
            }
        });
    }

    private void afterValidationClick(int which) {
        if (onItemSelectedListener != null) {
            onItemSelectedListener.onItemSelectedListener(items.get(which), which);
        }
        setText(listableItems.get(which).getText());
        selectedItem = items.get(which);
    }

    @Nullable
    public T getSelectedItem() {
        return selectedItem;
    }

    public void setOnItemSelectedListener(OnItemSelectedListener<T> onItemSelectedListener) {
        this.onItemSelectedListener = onItemSelectedListener;
    }

    public void setOnValidateSelection(OnValidateSelection<T> onValidateSelection) {
        this.onValidateSelection = onValidateSelection;
    }

    public interface OnItemSelectedListener<T> {

        void onItemSelectedListener(T item, int selectedIndex);
    }

    public interface OnValidateSelection<T> {

        boolean onValidateSelection(T item);
    }

    public class Item {

        private final String text;
        private final int icon;

        Item(String text, Integer icon) {
            this.text = text;
            this.icon = icon;
        }

        public String getText() {
            return text;
        }

        public int getIcon() {
            return icon;
        }
    }
}