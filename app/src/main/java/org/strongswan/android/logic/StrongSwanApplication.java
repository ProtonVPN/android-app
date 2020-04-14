/*
 * Copyright (C) 2014 Tobias Brunner
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

package org.strongswan.android.logic;

import android.content.Context;

import org.jetbrains.annotations.NotNull;
import org.strongswan.android.security.LocalCertificateKeyStoreProvider;

import java.security.Security;

import dagger.android.support.DaggerApplication;

public abstract class StrongSwanApplication extends DaggerApplication {

    private static Context mContext;

    public static final boolean USE_BYOD = true;

    static {
        Security.addProvider(new LocalCertificateKeyStoreProvider());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        StrongSwanApplication.mContext = getApplicationContext();
        TrustedCertificateManager.storeCertificate(
            TrustedCertificateManager.parseCertificate(getBaseContext()));
    }

    /**
     * Returns the current application context
     *
     * @return context
     */
    public static Context getContext() {
        return StrongSwanApplication.mContext;
    }

    public static void setAppContextForTest(@NotNull Context c) {
        mContext = c;
    }
}
