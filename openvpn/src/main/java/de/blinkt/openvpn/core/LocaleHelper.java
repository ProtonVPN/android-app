/*
 * Copyright (c) 2012-2021 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

public class LocaleHelper {
    static private Locale desiredLocale = null;

    public static void setDesiredLocale(Context c) {
        Locale current = Locale.getDefault();
        boolean defForce = true;

        /* Languages that have proofreaders */
        String[] whitelisted = {new Locale("de").getLanguage(), new Locale("ja").getLanguage(),
                                new Locale("tr").getLanguage(), new Locale("zh-TW").getLanguage()};

        String currentLanguage = current.getLanguage();
        for (String lang : whitelisted) {
            if (lang.equals(currentLanguage)) {
                defForce = false;
                break;
            }
        }

        if (current.toLanguageTag().startsWith("zh-Hant"))
            defForce = false;

        boolean allow_translation = Preferences.getDefaultSharedPreferences(c).getBoolean("allow_translation", defForce);

        if (!allow_translation)
            desiredLocale =  new Locale("en", current.getCountry());
    }

    public static Context updateResources(Context context) {
        if (desiredLocale == null)
            return context;

        Locale.setDefault(desiredLocale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        if (Build.VERSION.SDK_INT >= 17) {
            config.setLocale(desiredLocale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = desiredLocale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        return context;
    }

    public static void onConfigurationChange(Context context)
    {
        Resources res = context.getResources();

        Locale current;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            current = res.getConfiguration().getLocales().get(0);
        else
            current = res.getConfiguration().locale;


        if (current == desiredLocale)
            return;

        Configuration config = new Configuration(res.getConfiguration());

        config.setLocale(desiredLocale);

        res.updateConfiguration(config, res.getDisplayMetrics());
    }
}
