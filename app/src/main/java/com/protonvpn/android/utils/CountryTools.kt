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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.protonvpn.android.BuildConfig
import com.protonvpn.android.ProtonApplication
import com.protonvpn.android.R
import java.util.Locale

object CountryTools {

    private val supportedLanguages by lazy {
        BuildConfig.SUPPORTED_LOCALES.map {
            // Get the language for comparisons via a Locale object, see:
            // https://developer.android.com/reference/java/util/Locale#getLanguage()
            Locale(it.split("-r")[0]).language
        }
    }

    @JvmStatic
    fun getFlagResource(context: Context, flag: String?): Int {
        val desiredFlag = flag?.let {
            val flagResName = "flag_${flagCode(it)}"
            context.resources.getIdentifier(flagResName, "drawable", context.packageName)
        } ?: 0
        return if (desiredFlag > 0)
            desiredFlag
        else
            context.resources.getIdentifier("zz", "drawable", context.packageName)
    }

    /**
     * Returns a large and detailed flag resource.
     * Falls back to getFlagResource which returns drawables of a different size so don't rely on the intrinsic size of
     * the returned drawable.
     */
    fun getLargeFlagResource(context: Context, flag: String?): Int {
        val flagResId = if (flag != null) {
            val flagResName = "flag_large_${flagCode(flag)}"
            context.resources.getIdentifier(flagResName, "drawable", context.packageName)
        } else 0
        return flagResId.takeIf { it > 0 } ?: getFlagResource(context, flag)
    }

    fun getPreferredLocale(): Locale {
        val context = ProtonApplication.getAppContext()
        val configuration = context.resources.configuration
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            configuration.locales[0] else configuration.locale
        return if (locale?.language in supportedLanguages) locale else Locale.US
    }

    fun getFullName(country: String?): String {
        val locale = Locale("", country)
        val localized = locale.getDisplayCountry(getPreferredLocale())
        return if (localized.length < MAX_LOCALIZED_LENGTH)
            localized
        else
            locale.getDisplayCountry(Locale.US)
    }

    enum class Continent(@StringRes val nameRes: Int, @DrawableRes val iconRes: Int) {
        Europe(R.string.nameEurope, R.drawable.europe),
        America(R.string.nameAmerica, R.drawable.america),
        Asia(R.string.nameAsia, R.drawable.asia),
        AfricaAndMiddleEast(R.string.nameAfricaAndMiddleEast, R.drawable.africa_middleeast),
        Oceania(R.string.nameOceania, R.drawable.oceania)
    }

    data class CountryData(val x: Double, val y: Double, val continent: Continent)

    private fun flagCode(flag: String) =
        if (flag.lowercase(Locale.ROOT) == "uk") "gb" else flag.lowercase(Locale.ROOT)

    val locationMap = mapOf(
        "AE" to CountryData(3103.0, 976.0, Continent.AfricaAndMiddleEast),
        "AL" to CountryData(2560.0, 665.0, Continent.Europe),
        "AR" to CountryData(1300.0, 2000.0, Continent.America),
        "AT" to CountryData(2485.0, 550.0, Continent.Europe),
        "AU" to CountryData(4355.0, 1855.0, Continent.Oceania),
        "AZ" to CountryData(2980.0, 667.0, Continent.Asia),
        "BA" to CountryData(2527.0, 661.0, Continent.Europe),
        "BE" to CountryData(2343.0, 495.0, Continent.Europe),
        "BG" to CountryData(2660.0, 631.0, Continent.Europe),
        "BR" to CountryData(1469.0, 1577.0, Continent.America),
        "CA" to CountryData(875.0, 400.0, Continent.America),
        "CH" to CountryData(2390.0, 564.0, Continent.Europe),
        "CL" to CountryData(1170.0, 1951.0, Continent.America),
        "CO" to CountryData(1100.0, 1339.0, Continent.America),
        "CR" to CountryData(925.0, 1231.0, Continent.America),
        "CY" to CountryData(2759.0, 777.0, Continent.Europe),
        "CZ" to CountryData(2482.0, 509.0, Continent.Europe),
        "DE" to CountryData(2420.0, 495.0, Continent.Europe),
        "DK" to CountryData(2413.0, 401.0, Continent.Europe),
        "EC" to CountryData(1010.0, 1440.0, Continent.America),
        "EE" to CountryData(2615.0, 356.0, Continent.Europe),
        "EG" to CountryData(2742.0, 863.0, Continent.AfricaAndMiddleEast),
        "ES" to CountryData(2215.0, 690.0, Continent.Europe),
        "FI" to CountryData(2615.0, 295.0, Continent.Europe),
        "FR" to CountryData(2310.0, 567.0, Continent.Europe),
        "GB" to CountryData(2265.0, 475.0, Continent.Europe),
        "GE" to CountryData(2915.0, 648.0, Continent.Asia),
        "GR" to CountryData(2600.0, 720.0, Continent.Europe),
        "HK" to CountryData(4033.0, 999.0, Continent.Asia),
        "HR" to CountryData(2495.0, 608.0, Continent.Europe),
        "HU" to CountryData(2550.0, 558.0, Continent.Europe),
        "ID" to CountryData(4159.0, 1481.0, Continent.Asia),
        "IE" to CountryData(2176.0, 458.0, Continent.Europe),
        "IL" to CountryData(2793.0, 830.0, Continent.AfricaAndMiddleEast),
        "IN" to CountryData(3483.0, 1071.0, Continent.Asia),
        "IS" to CountryData(2080.0, 260.0, Continent.Europe),
        "IT" to CountryData(2456.0, 647.0, Continent.Europe),
        "JP" to CountryData(4330.0, 755.0, Continent.Asia),
        "KH" to CountryData(3911.0, 1194.0, Continent.Asia),
        "KR" to CountryData(4171.0, 743.0, Continent.Asia),
        "LT" to CountryData(2604.0, 420.0, Continent.Europe),
        "LU" to CountryData(2363.0, 513.0, Continent.Europe),
        "LV" to CountryData(2612.0, 388.0, Continent.Europe),
        "MA" to CountryData(2145.0, 860.0, Continent.AfricaAndMiddleEast),
        "MD" to CountryData(2679.0, 561.0, Continent.Europe),
        "MK" to CountryData(2585.0, 657.0, Continent.Europe),
        "MM" to CountryData(3755.0, 1032.0, Continent.Asia),
        "MT" to CountryData(2483.0, 765.0, Continent.Europe),
        "MX" to CountryData(667.0, 976.0, Continent.America),
        "MY" to CountryData(3878.0, 1335.0, Continent.Asia),
        "NG" to CountryData(2385.0, 1235.0, Continent.AfricaAndMiddleEast),
        "NL" to CountryData(2355.0, 466.0, Continent.Europe),
        "NO" to CountryData(2411.0, 311.0, Continent.Europe),
        "NZ" to CountryData(4760.0, 2171.0, Continent.Oceania),
        "PE" to CountryData(1056.0, 1589.0, Continent.America),
        "PH" to CountryData(4159.0, 1135.0, Continent.Asia),
        "PK" to CountryData(3330.0, 860.0, Continent.Asia),
        "PL" to CountryData(2554.0, 472.0, Continent.Europe),
        "PR" to CountryData(1216.0, 1076.0, Continent.America),
        "PT" to CountryData(2148.0, 688.0, Continent.Europe),
        "RO" to CountryData(2636.0, 583.0, Continent.Europe),
        "RS" to CountryData(2569.0, 607.0, Continent.Europe),
        "RU" to CountryData(2833.0, 366.0, Continent.Europe),
        "SE" to CountryData(2485.0, 300.0, Continent.Europe),
        "SG" to CountryData(3905.0, 1379.0, Continent.Asia),
        "SI" to CountryData(2481.0, 578.0, Continent.Europe),
        "SK" to CountryData(2552.0, 527.0, Continent.Europe),
        "SL" to CountryData(2483.0, 575.0, Continent.AfricaAndMiddleEast),
        "TH" to CountryData(3848.0, 1128.0, Continent.Asia),
        "TR" to CountryData(2779.0, 696.0, Continent.AfricaAndMiddleEast),
        "TW" to CountryData(4135.0, 975.0, Continent.Asia),
        "UA" to CountryData(2715.0, 517.0, Continent.Europe),
        "UK" to CountryData(2265.0, 475.0, Continent.Europe),
        "US" to CountryData(760.0, 700.0, Continent.America),
        "VN" to CountryData(3961.0, 1144.0, Continent.Asia),
        "ZA" to CountryData(2629.0, 1950.0, Continent.AfricaAndMiddleEast),
    )

    val codeToMapCountryName = mapOf(
        "AF" to "Afghanistan",
        "AO" to "Angola",
        "AL" to "Albania",
        "AE" to "UnitedArabEmirates",
        "AR" to "Argentina",
        "AM" to "Armenia",
        "AU" to "Australia",
        "AT" to "Austria",
        "AZ" to "Azerbaijan",
        "BI" to "Burundi",
        "BE" to "Belgium",
        "BJ" to "Benin",
        "BF" to "BurkinaFaso",
        "BD" to "Bangladesh",
        "BG" to "Bulgaria",
        "BA" to "BosniaandHerzegovina",
        "BY" to "Belarus",
        "BZ" to "Belize",
        "BO" to "Bolivia",
        "BR" to "Brazil",
        "BN" to "BruneiDarussalam",
        "BT" to "Bhutan",
        "BW" to "Botswana",
        "CF" to "CentralAfricanRepublic",
        "CA" to "Canada",
        "CH" to "Switzerland",
        "CL" to "Chile",
        "CN" to "China",
        "CI" to "Côted'Ivoire",
        "CM" to "Cameroon",
        "CD" to "DemocraticRepublicoftheCongo",
        "CG" to "RepublicofCongo",
        "CO" to "Colombia",
        "CR" to "CostaRica",
        "CU" to "Cuba",
        "CZ" to "CzechRep",
        "DE" to "Germany",
        "DJ" to "Djibouti",
        "DK" to "Denmark",
        "DO" to "DominicanRepublic",
        "DZ" to "Algeria",
        "EC" to "Ecuador",
        "EG" to "Egypt",
        "ER" to "Eritrea",
        "EE" to "Estonia",
        "ET" to "Ethiopia",
        "FI" to "Finland",
        "FJ" to "Fiji",
        "GA" to "Gabon",
        "GB" to "UnitedKingdom",
        "UK" to "UnitedKingdom",
        "GE" to "Georgia",
        "GH" to "Ghana",
        "GN" to "Guinea",
        "GM" to "TheGambia",
        "GW" to "Guinea-Bissau",
        "GQ" to "EquatorialGuinea",
        "GR" to "Greece",
        "GL" to "Greenland",
        "GT" to "Guatemala",
        "GY" to "Guyana",
        "HN" to "Honduras",
        "HR" to "Croatia",
        "HT" to "Haiti",
        "HU" to "Hungary",
        "ID" to "Indonesia",
        "IN" to "India",
        "IE" to "Ireland",
        "IR" to "Iran",
        "IQ" to "Iraq",
        "IS" to "Iceland",
        "IL" to "Israel",
        "IT" to "Italy",
        "JM" to "Jamaica",
        "JO" to "Jordan",
        "JP" to "Japan",
        "KZ" to "Kazakhstan",
        "KE" to "Kenya",
        "KG" to "Kyrgyzstan",
        "KH" to "Cambodia",
        "KR" to "SouthKorea",
        "XK" to "Kosovo",
        "KW" to "Kuwait",
        "LA" to "LaoPDR",
        "LB" to "Lebanon",
        "LR" to "Liberia",
        "LY" to "Libya",
        "LK" to "SriLanka",
        "LS" to "Lesotho",
        "LT" to "Lithuania",
        "LU" to "Luxembourg",
        "LV" to "Latvia",
        "MA" to "Morocco",
        "MD" to "Moldova",
        "MG" to "Madagascar",
        "MX" to "Mexico",
        "MK" to "Macedonia",
        "ML" to "Mali",
        "MM" to "Myanmar",
        "ME" to "Montenegro",
        "MN" to "Mongolia",
        "MZ" to "Mozambique",
        "MR" to "Mauritania",
        "MW" to "Malawi",
        "MY" to "Malaysia",
        "NA" to "Namibia",
        "NE" to "Niger",
        "NG" to "Nigeria",
        "NI" to "Nicaragua",
        "NL" to "Netherlands",
        "NO" to "Norway",
        "NP" to "Nepal",
        "NZ" to "NewZealand",
        "OM" to "Oman",
        "PK" to "Pakistan",
        "PA" to "Panama",
        "PE" to "Peru",
        "PH" to "Phillipines", // The path in SVG has a typo.
        "PG" to "PapuaNewGuinea",
        "PK" to "Pakistan",
        "PL" to "Poland",
        "KP" to "DemRepKorea",
        "PT" to "Portugal",
        "PY" to "Paraguay",
        "PS" to "Palestine",
        "QA" to "Qatar",
        "RO" to "Romania",
        "RU" to "Russia",
        "RW" to "Rwanda",
        "EH" to "WesternSahara",
        "SA" to "SaudiArabia",
        "SD" to "Sudan",
        "SS" to "SouthSudan",
        "SN" to "Senegal",
        "SL" to "SierraLeone",
        "SV" to "ElSalvador",
        "RS" to "Serbia",
        "SR" to "Suriname",
        "SK" to "Slovakia",
        "SI" to "Slovenia",
        "SE" to "Sweden",
        "SZ" to "Swaziland",
        "SY" to "Syria",
        "TD" to "Chad",
        "TG" to "Togo",
        "TH" to "Thailand",
        "TJ" to "Tajikistan",
        "TM" to "Turkmenistan",
        "TL" to "Timor-Leste",
        "TN" to "Tunisia",
        "TR" to "Turkey",
        "TW" to "Taiwan",
        "TZ" to "Tanzania",
        "UG" to "Uganda",
        "UA" to "Ukraine",
        "UY" to "Uruguay",
        "US" to "UnitedStatesofAmerica",
        "UZ" to "Uzbekistan",
        "VE" to "Venezuela",
        "VN" to "Vietnam",
        "VU" to "Vanuatu",
        "YE" to "Yemen",
        "ZA" to "SouthAfrica",
        "ZM" to "Zambia",
        "ZW" to "Zimbabwe",
        "SO" to "Somalia",
        "GF" to "France",
        "FR" to "France",
        "ES" to "Spain",
        "AW" to "Aruba",
        "AI" to "Anguilla",
        "AD" to "Andorra",
        "AG" to "AntiguaandBarbuda",
        "BS" to "Bahamas",
        "BM" to "Bermuda",
        "BB" to "Barbados",
        "KM" to "Comoros",
        "CV" to "CapeVerde",
        "KY" to "CaymanIslands",
        "DM" to "Dominica",
        "FK" to "FalklandIslands",
        "FO" to "FaeroeIslands",
        "GD" to "Grenada",
        "HK" to "HongKong",
        "KN" to "SaintKittsandNevis",
        "LC" to "SaintLucia",
        "LI" to "Liechtenstein",
        "MF" to "SaintMartinFrench",
        "MV" to "Maldives",
        "MT" to "Malta",
        "MS" to "Montserrat",
        "MU" to "Mauritius",
        "NC" to "NewCaledonia",
        "NR" to "Nauru",
        "PN" to "PitcairnIslands",
        "PR" to "PuertoRico",
        "PF" to "FrenchPolynesia",
        "SG" to "Singapore",
        "SB" to "SolomonIslands",
        "ST" to "SãoToméandPrincipe",
        "SX" to "SaintMartinDutch",
        "SC" to "Seychelles",
        "TC" to "TurksandCaicosIslands",
        "TO" to "Tonga",
        "TT" to "TrinidadandTobago",
        "VC" to "SaintVincentandtheGrenadines",
        "VG" to "BritishVirginIslands",
        "VI" to "UnitedStatesVirginIslands",
        "CY" to "Cyprus",
        "RE" to "ReunionFrance",
        "YT" to "MayotteFrance",
        "MQ" to "MartiniqueFrance",
        "GP" to "GuadeloupeFrance",
        "CW" to "CuracoNetherlands",
        "IC" to "CanaryIslandsSpain"
    )

    private const val MAX_LOCALIZED_LENGTH = 60

    const val LOCATION_TO_TV_MAP_COORDINATES_RATIO = 0.294f
}
