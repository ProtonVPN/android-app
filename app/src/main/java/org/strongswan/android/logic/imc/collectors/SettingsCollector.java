/*
 * Copyright (C) 2013 Tobias Brunner
 * Copyright (C) 2012 Christoph Buehler
 * Copyright (C) 2012 Patrick Loetscher
 * HSR Hochschule fuer Technik Rapperswil
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.  See <http://www.fsf.org/copyleft/gpl.txt>.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 */

package org.strongswan.android.logic.imc.collectors;

import java.util.Locale;

import org.strongswan.android.logic.imc.attributes.Attribute;
import org.strongswan.android.logic.imc.attributes.SettingsAttribute;

import android.content.ContentResolver;
import android.content.Context;

public class SettingsCollector implements Collector
{
	private final ContentResolver mContentResolver;
	private final String[] mSettings;

	public SettingsCollector(Context context, String[] args)
	{
		mContentResolver = context.getContentResolver();
		mSettings = args;
	}

	@Override
	public Attribute getMeasurement()
	{
		if (mSettings == null || mSettings.length == 0)
		{
			return null;
		}
		SettingsAttribute attribute = new SettingsAttribute();
		for (String name : mSettings)
		{
			String value = android.provider.Settings.Secure.getString(mContentResolver, name.toLowerCase(Locale.US));
			if (value == null)
			{
				value = android.provider.Settings.System.getString(mContentResolver, name.toLowerCase(Locale.US));
			}
			if (value != null)
			{
				attribute.addSetting(name, value);
			}
		}
		return attribute;
	}
}
