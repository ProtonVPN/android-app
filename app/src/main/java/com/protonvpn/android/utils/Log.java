/*
 * Copyright (c) 2017 Proton AG
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
package com.protonvpn.android.utils;

import com.protonvpn.android.BuildConfig;
import com.protonvpn.android.logging.LogCategory;
import com.protonvpn.android.logging.LogLevel;
import com.protonvpn.android.logging.ProtonLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.util.Locale;

import io.sentry.Sentry;

public final class Log {

    private Log() {}

    public static void e(String message, Exception e) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e(tag(), message, e);
        }
    }

    public static void exception(Throwable e) {
        if (!BuildConfig.DEBUG) {
            // FIXME Re-enable non-critical exception handling, once Sentry can handle higher loads
            //    Sentry.capture(e);
        }
    }

    public static void e(String message) {
        if (BuildConfig.DEBUG) {
            android.util.Log.e(tag(), withSourceInfo(message));
        }
    }

    public static void w(String message) {
        if (BuildConfig.DEBUG) {
            android.util.Log.w(tag(), withSourceInfo(message));
        }
    }

    public static void i(String message) {
        if (BuildConfig.DEBUG) {
            android.util.Log.i(tag(), withSourceInfo(message));
        }
    }

    public static void d(String message) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d(tag(), withSourceInfo(message));
        }
    }

    public static void v(String message) {
        if (BuildConfig.DEBUG) {
            android.util.Log.v(tag(), withSourceInfo(message));
        }
    }

    private static String tag() {
        return String.format("ProtonVpn:" + " [~%1$s]", Thread.currentThread().getName());
    }

    private static String withSourceInfo(String msg) {
        ProtonLogger.INSTANCE.logCustom(LogLevel.DEBUG, LogCategory.APP, msg);
        final StackTraceElement trace = Thread.currentThread().getStackTrace()[4];
        return String.format(Locale.getDefault(), "(%1$s:%2$d) %3$s", trace.getFileName(),
            trace.getLineNumber(), msg);
    }
}
