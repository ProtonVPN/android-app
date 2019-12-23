/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openpvpn.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import de.blinkt.openpvpn.activities.FileSelect;
import de.blinkt.openpvpn.R;

public class InlineFileTab extends Fragment
{

	private static final int MENU_SAVE = 0;
	private EditText mInlineData;

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mInlineData.setText(((FileSelect)getActivity()).getInlineData());
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState)
	{

		View v = inflater.inflate(R.layout.file_dialog_inline, container, false);
		mInlineData =(EditText) v.findViewById(R.id.inlineFileData);
		return v;
	}

	public void setData(String data) {
		if(mInlineData!=null)
			mInlineData.setText(data);

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.add(0, MENU_SAVE, 0, R.string.menu_use_inline_data)
		.setIcon(android.R.drawable.ic_menu_save)
		.setAlphabeticShortcut('u')
		.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId()==MENU_SAVE){
			((FileSelect)getActivity()).saveInlineData(null, mInlineData.getText().toString());
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}