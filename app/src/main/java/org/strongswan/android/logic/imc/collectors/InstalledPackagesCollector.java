/*
 * Copyright (C) 2013 Tobias Brunner
 * Copyright (C) 2012 Christoph Buehler
 * Copyright (C) 2012 Patrick Loetscher
 * Hochschule fuer Technik Rapperswil
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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import org.strongswan.android.logic.imc.attributes.Attribute;
import org.strongswan.android.logic.imc.attributes.InstalledPackagesAttribute;

import java.util.List;

public class InstalledPackagesCollector implements Collector {

    private final PackageManager mPackageManager;

    public InstalledPackagesCollector(Context context) {
        mPackageManager = context.getPackageManager();
    }

    @Override
    public Attribute getMeasurement() {
        InstalledPackagesAttribute attribute = new InstalledPackagesAttribute();
        List<PackageInfo> packages = mPackageManager.getInstalledPackages(0);
        for (PackageInfo info : packages) {
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || info.packageName == null
                || info.versionName == null) {    /* ignore packages installed in the system image */
                continue;
            }
            attribute.addPackage(info.packageName, info.versionName);
        }
        return attribute;
    }
}
