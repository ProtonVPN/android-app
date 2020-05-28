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
package com.protonvpn.android.utils

import android.content.Context
import android.os.Build
import com.protonvpn.android.ProtonApplication
import java.util.Locale

object CountryTools {

    fun getFlagResource(context: Context, flag: String): Int {
        val desiredFlag = context.resources.getIdentifier(
                flag.toLowerCase(Locale.ROOT) + "_flag", "drawable", context.packageName)
        return if (desiredFlag > 0)
            desiredFlag
        else
            context.resources.getIdentifier("zz_flag", "drawable", context.packageName)
    }

    fun getPreferredLocale(context: Context): Locale {
        val configuration = context.resources.configuration
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            configuration.locales[0] else configuration.locale
        return if (locale.language in Constants.AVAILABLE_LOCALES) locale else Locale.US
    }

    fun getFullName(country: String?): String {
        val locale = Locale("", country)
        val localized = locale.getDisplayCountry(
                getPreferredLocale(ProtonApplication.getAppContext()))
        return if (localized.length < MAX_LOCALIZED_LENGTH)
            localized
        else
            locale.getDisplayCountry(Locale.US)
    }

    val locationMap = mapOf(
            "FR_x" to 2310.0,
            "FR_y" to 567.0,
            "LT_x" to 2604.0,
            "LT_y" to 420.0,
            "US_x" to 760.0,
            "US_y" to 700.0,
            "ES_x" to 2215.0,
            "ES_y" to 690.0,
            "CA_x" to 875.0,
            "CA_y" to 400.0,
            "GB_x" to 2265.0,
            "GB_y" to 475.0,
            "UK_x" to 2265.0,
            "UK_y" to 475.0,
            "DE_x" to 2420.0,
            "DE_y" to 495.0,
            "AU_x" to 4355.0,
            "AU_y" to 1855.0,
            "CH_x" to 2390.0,
            "CH_y" to 564.0,
            "HK_x" to 4033.0,
            "HK_y" to 999.0,
            "IT_x" to 2456.0,
            "IT_y" to 647.0,
            "IS_x" to 2080.0,
            "IS_y" to 260.0,
            "JP_x" to 4330.0,
            "JP_y" to 755.0,
            "NL_x" to 2355.0,
            "NL_y" to 466.0,
            "SE_x" to 2485.0,
            "SE_y" to 300.0,
            "SG_x" to 3905.0,
            "SG_y" to 1379.0,
            "AL_x" to 2560.0,
            "AL_y" to 665.0,
            "AT_x" to 2485.0,
            "AT_y" to 550.0,
            "AR_x" to 1300.0,
            "AR_y" to 2000.0,
            "AZ_x" to 2980.0,
            "AZ_y" to 667.0,
            "BE_x" to 2343.0,
            "BE_y" to 495.0,
            "BA_x" to 2527.0,
            "BA_y" to 661.0,
            "BR_x" to 1469.0,
            "BR_y" to 1577.0,
            "BG_x" to 2660.0,
            "BG_y" to 631.0,
            "CZ_x" to 2482.0,
            "CZ_y" to 509.0,
            "CL_x" to 1170.0,
            "CL_y" to 1951.0,
            "CY_x" to 2759.0,
            "CY_y" to 777.0,
            "CR_x" to 925.0,
            "CR_y" to 1231.0,
            "HR_x" to 2495.0,
            "HR_y" to 608.0,
            "DK_x" to 2413.0,
            "DK_y" to 401.0,
            "EG_x" to 2742.0,
            "EG_y" to 863.0,
            "EE_x" to 2615.0,
            "EE_y" to 356.0,
            "FI_x" to 2615.0,
            "FI_y" to 295.0,
            "GE_x" to 2915.0,
            "GE_y" to 648.0,
            "GR_x" to 2600.0,
            "GR_y" to 720.0,
            "HU_x" to 2550.0,
            "HU_y" to 558.0,
            "IN_x" to 3483.0,
            "IN_y" to 1071.0,
            "ID_x" to 4159.0,
            "ID_y" to 1481.0,
            "IE_x" to 2176.0,
            "IE_y" to 458.0,
            "IL_x" to 2793.0,
            "IL_y" to 830.0,
            "LV_x" to 2612.0,
            "LV_y" to 388.0,
            "LU_x" to 2363.0,
            "LU_y" to 513.0,
            "MK_x" to 2585.0,
            "MK_y" to 657.0,
            "MY_x" to 3878.0,
            "MY_y" to 1335.0,
            "MX_x" to 667.0,
            "MX_y" to 976.0,
            "MD_x" to 2679.0,
            "MD_y" to 561.0,
            "NZ_x" to 4760.0,
            "NZ_y" to 2171.0,
            "NO_x" to 2411.0,
            "NO_y" to 311.0,
            "PL_x" to 2554.0,
            "PL_y" to 472.0,
            "PT_x" to 2148.0,
            "PT_y" to 688.0,
            "RO_x" to 2636.0,
            "RO_y" to 583.0,
            "RU_x" to 2833.0,
            "RU_y" to 366.0,
            "RS_x" to 2569.0,
            "RS_y" to 607.0,
            "SK_x" to 2552.0,
            "SK_y" to 527.0,
            "SL_x" to 2483.0,
            "SL_y" to 575.0,
            "SI_x" to 2481.0,
            "SI_y" to 578.0,
            "ZA_x" to 2629.0,
            "ZA_y" to 1950.0,
            "KR_x" to 4171.0,
            "KR_y" to 743.0,
            "TH_x" to 3848.0,
            "TH_y" to 1128.0,
            "TW_x" to 4135.0,
            "TW_y" to 975.0,
            "TR_x" to 2779.0,
            "TR_y" to 696.0,
            "UA_x" to 2715.0,
            "UA_y" to 517.0,
            "AE_x" to 3103.0,
            "AE_y" to 976.0,
            "VN_x" to 3961.0,
            "VN_y" to 1144.0)

    private const val MAX_LOCALIZED_LENGTH = 60
}
