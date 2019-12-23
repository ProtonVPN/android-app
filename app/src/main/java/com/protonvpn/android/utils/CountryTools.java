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
package com.protonvpn.android.utils;

import android.content.Context;

import java.util.HashMap;

public class CountryTools {

    public static HashMap<String, Double> locationMap;
    private static HashMap<String, String> countryNamingMap;

    static {
        locationMap = new HashMap<String, Double>() {{
            put("FR_x", 2310.0);
            put("FR_y", 567.0);
            put("LT_x", 2604.0);
            put("LT_y", 420.0);
            put("US_x", 760.0);
            put("US_y", 700.0);
            put("ES_x", 2215.0);
            put("ES_y", 690.0);
            put("CA_x", 875.0);
            put("CA_y", 400.0);
            put("GB_x", 2265.0);
            put("GB_y", 475.0);
            put("UK_x", 2265.0);
            put("UK_y", 475.0);
            put("DE_x", 2420.0);
            put("DE_y", 495.0);
            put("AU_x", 4355.0);
            put("AU_y", 1855.0);
            put("CH_x", 2390.0);
            put("CH_y", 564.0);
            put("HK_x", 4033.0);
            put("HK_y", 999.0);
            put("IT_x", 2456.0);
            put("IT_y", 647.0);
            put("IS_x", 2080.0);
            put("IS_y", 260.0);
            put("JP_x", 4330.0);
            put("JP_y", 755.0);
            put("NL_x", 2355.0);
            put("NL_y", 466.0);
            put("SE_x", 2485.0);
            put("SE_y", 300.0);
            put("SG_x", 3905.0);
            put("SG_y", 1379.0);
            put("AL_x", 2560.0);
            put("AL_y", 665.0);
            put("AT_x", 2485.0);
            put("AT_y", 550.0);
            put("AR_x", 1300.0);
            put("AR_y", 2000.0);
            put("AZ_x", 2980.0);
            put("AZ_y", 667.0);
            put("BE_x", 2343.0);
            put("BE_y", 495.0);
            put("BA_x", 2527.0);
            put("BA_y", 661.0);
            put("BR_x", 1469.0);
            put("BR_y", 1577.0);
            put("BG_x", 2660.0);
            put("BG_y", 631.0);
            put("CZ_x", 2482.0);
            put("CZ_y", 509.0);
            put("CL_x", 1170.0);
            put("CL_y", 1951.0);
            put("CY_x", 2759.0);
            put("CY_y", 777.0);
            put("CR_x", 925.0);
            put("CR_y", 1231.0);
            put("HR_x", 2495.0);
            put("HR_y", 608.0);
            put("DK_x", 2413.0);
            put("DK_y", 401.0);
            put("EG_x", 2742.0);
            put("EG_y", 863.0);
            put("EE_x", 2615.0);
            put("EE_y", 356.0);
            put("FI_x", 2615.0);
            put("FI_y", 295.0);
            put("GE_x", 2915.0);
            put("GE_y", 648.0);
            put("GR_x", 2600.0);
            put("GR_y", 720.0);
            put("HU_x", 2550.0);
            put("HU_y", 558.0);
            put("IN_x", 3483.0);
            put("IN_y", 1071.0);
            put("ID_x", 4159.0);
            put("ID_y", 1481.0);
            put("IE_x", 2176.0);
            put("IE_y", 458.0);
            put("IL_x", 2793.0);
            put("IL_y", 830.0);
            put("LV_x", 2612.0);
            put("LV_y", 388.0);
            put("LU_x", 2363.0);
            put("LU_y", 513.0);
            put("MK_x", 2585.0);
            put("MK_y", 657.0);
            put("MY_x", 3878.0);
            put("MY_y", 1335.0);
            put("MX_x", 667.0);
            put("MX_y", 976.0);
            put("MD_x", 2679.0);
            put("MD_y", 561.0);
            put("NZ_x", 4760.0);
            put("NZ_y", 2171.0);
            put("NO_x", 2411.0);
            put("NO_y", 311.0);
            put("PL_x", 2554.0);
            put("PL_y", 472.0);
            put("PT_x", 2148.0);
            put("PT_y", 688.0);
            put("RO_x", 2636.0);
            put("RO_y", 583.0);
            put("RU_x", 2833.0);
            put("RU_y", 366.0);
            put("RS_x", 2569.0);
            put("RS_y", 607.0);
            put("SK_x", 2552.0);
            put("SK_y", 527.0);
            put("SL_x", 2483.0);
            put("SL_y", 575.0);
            put("ZA_x", 2629.0);
            put("ZA_y", 1950.0);
            put("KR_x", 4171.0);
            put("KR_y", 743.0);
            put("TH_x", 3848.0);
            put("TH_y", 1128.0);
            put("TW_x", 4135.0);
            put("TW_y", 975.0);
            put("TR_x", 2779.0);
            put("TR_y", 696.0);
            put("UA_x", 2715.0);
            put("UA_y", 517.0);
            put("AE_x", 3103.0);
            put("AE_y", 976.0);
            put("VN_x", 3961.0);
            put("VN_y", 1144.0);
        }};
    }

    static {
        countryNamingMap = new HashMap<String, String>() {{
            put("AF", "Afghanistan");
            put("AX", "Aland Islands");
            put("AL", "Albania");
            put("DZ", "Algeria");
            put("AS", "American Samoa");
            put("AD", "Andorra");
            put("AO", "Angola");
            put("AI", "Anguilla");
            put("AQ", "Antarctica");
            put("AG", "Antigua and Barbuda");
            put("AR", "Argentina");
            put("AM", "Armenia");
            put("AW", "Aruba");
            put("AU", "Australia");
            put("AT", "Austria");
            put("AZ", "Azerbaijan");
            put("BS", "Bahamas");
            put("BH", "Bahrain");
            put("BD", "Bangladesh");
            put("BB", "Barbados");
            put("BY", "Belarus");
            put("BE", "Belgium");
            put("BZ", "Belize");
            put("BJ", "Benin");
            put("BM", "Bermuda");
            put("BT", "Bhutan");
            put("BO", "Bolivia");
            put("BQ", "Bonaire");
            put("BA", "Bosnia and Herzegovina");
            put("BW", "Botswana");
            put("BV", "Bouvet Island");
            put("BR", "Brazil");
            put("IO", "British Indian Ocean Territory");
            put("BN", "Brunei Darussalam");
            put("BG", "Bulgaria");
            put("BF", "Burkina Faso");
            put("BI", "Burundi");
            put("KH", "Cambodia");
            put("CM", "Cameroon");
            put("CA", "Canada");
            put("CV", "Cape Verde");
            put("KY", "Cayman Islands");
            put("CF", "Central African Republic");
            put("TD", "Chad");
            put("CL", "Chile");
            put("CN", "China");
            put("CX", "Christmas Island");
            put("CC", "Cocos Islands");
            put("CO", "Colombia");
            put("KM", "Comoros");
            put("CG", "Congo");
            put("CD", "Congo DR");
            put("CK", "Cook Islands");
            put("CR", "Costa Rica");
            put("CI", "Côte d'Ivoire");
            put("HR", "Croatia");
            put("CU", "Cuba");
            put("CW", "Curaçao");
            put("CY", "Cyprus");
            put("CZ", "Czechia");
            put("DK", "Denmark");
            put("DJ", "Djibouti");
            put("DM", "Dominica");
            put("DO", "Dominican Republic");
            put("EC", "Ecuador");
            put("EG", "Egypt");
            put("SV", "El Salvador");
            put("GQ", "Equatorial Guinea");
            put("ER", "Eritrea");
            put("EE", "Estonia");
            put("ET", "Ethiopia");
            put("FK", "Falkland Islands");
            put("FO", "Faroe Islands");
            put("FJ", "Fiji");
            put("FI", "Finland");
            put("FR", "France");
            put("GF", "French Guiana");
            put("PF", "French Polynesia");
            put("TF", "French Southern Territories");
            put("GA", "Gabon");
            put("GM", "Gambia");
            put("GE", "Georgia");
            put("DE", "Germany");
            put("GH", "Ghana");
            put("GI", "Gibraltar");
            put("GR", "Greece");
            put("GL", "Greenland");
            put("GD", "Grenada");
            put("GP", "Guadeloupe");
            put("GU", "Guam");
            put("GT", "Guatemala");
            put("GG", "Guernsey");
            put("GN", "Guinea");
            put("GW", "Guinea-Bissau");
            put("GY", "Guyana");
            put("HT", "Haiti");
            put("HM", "Heard Island and McDonald Islands");
            put("VA", "Holy See (Vatican City State)");
            put("HN", "Honduras");
            put("HK", "Hong Kong");
            put("HU", "Hungary");
            put("IS", "Iceland");
            put("IN", "India");
            put("ID", "Indonesia");
            put("IR", "Iran");
            put("IQ", "Iraq");
            put("IE", "Ireland");
            put("IM", "Isle of Man");
            put("IL", "Israel");
            put("IT", "Italy");
            put("JM", "Jamaica");
            put("JP", "Japan");
            put("JE", "Jersey");
            put("JO", "Jordan");
            put("KZ", "Kazakhstan");
            put("KE", "Kenya");
            put("KI", "Kiribati");
            put("KP", "North Korea");
            put("KR", "South Korea");
            put("KW", "Kuwait");
            put("KG", "Kyrgyzstan");
            put("LA", "Lao People's Democratic Republic");
            put("LV", "Latvia");
            put("LB", "Lebanon");
            put("LS", "Lesotho");
            put("LR", "Liberia");
            put("LY", "Libya");
            put("LI", "Liechtenstein");
            put("LT", "Lithuania");
            put("LU", "Luxembourg");
            put("MO", "Macao");
            put("MK", "Macedonia");
            put("MG", "Madagascar");
            put("MW", "Malawi");
            put("MY", "Malaysia");
            put("MV", "Maldives");
            put("ML", "Mali");
            put("MT", "Malta");
            put("MH", "Marshall Islands");
            put("MQ", "Martinique");
            put("MR", "Mauritania");
            put("MU", "Mauritius");
            put("YT", "Mayotte");
            put("MX", "Mexico");
            put("FM", "Micronesia");
            put("MD", "Moldova");
            put("MC", "Monaco");
            put("MN", "Mongolia");
            put("ME", "Montenegro");
            put("MS", "Montserrat");
            put("MA", "Morocco");
            put("MZ", "Mozambique");
            put("MM", "Myanmar");
            put("NA", "Namibia");
            put("NR", "Nauru");
            put("NP", "Nepal");
            put("NL", "Netherlands");
            put("NC", "New Caledonia");
            put("NZ", "New Zealand");
            put("NI", "Nicaragua");
            put("NE", "Niger");
            put("NG", "Nigeria");
            put("NU", "Niue");
            put("NF", "Norfolk Island");
            put("MP", "Northern Mariana Islands");
            put("NO", "Norway");
            put("OM", "Oman");
            put("PK", "Pakistan");
            put("PW", "Palau");
            put("PS", "Palestinian Territory");
            put("PA", "Panama");
            put("PG", "Papua New Guinea");
            put("PY", "Paraguay");
            put("PE", "Peru");
            put("PH", "Philippines");
            put("PN", "Pitcairn");
            put("PL", "Poland");
            put("PT", "Portugal");
            put("PR", "Puerto Rico");
            put("QA", "Qatar");
            put("RE", "Réunion");
            put("RO", "Romania");
            put("RU", "Russia");
            put("RW", "Rwanda");
            put("BL", "Saint Barthélemy");
            put("SH", "Saint Helena");
            put("KN", "Saint Kitts and Nevis");
            put("LC", "Saint Lucia");
            put("MF", "Saint Martin");
            put("PM", "Saint Pierre and Miquelon");
            put("VC", "Saint Vincent and the Grenadines");
            put("WS", "Samoa");
            put("SM", "San Marino");
            put("ST", "Sao Tome and Principe");
            put("SA", "Saudi Arabia");
            put("SN", "Senegal");
            put("RS", "Serbia");
            put("SC", "Seychelles");
            put("SL", "Sierra Leone");
            put("SG", "Singapore");
            put("SX", "Sint Maarten");
            put("SK", "Slovakia");
            put("SI", "Slovenia");
            put("SB", "Solomon Islands");
            put("SO", "Somalia");
            put("ZA", "South Africa");
            put("GS", "South Georgia and the South Sandwich Islands");
            put("ES", "Spain");
            put("LK", "Sri Lanka");
            put("SD", "Sudan");
            put("SR", "Suriname");
            put("SS", "South Sudan");
            put("SJ", "Svalbard and Jan Mayen");
            put("SZ", "Swaziland");
            put("SE", "Sweden");
            put("CH", "Switzerland");
            put("SY", "Syrian Arab Republic");
            put("TW", "Taiwan");
            put("TJ", "Tajikistan");
            put("TZ", "Tanzania");
            put("TH", "Thailand");
            put("TL", "Timor-Leste");
            put("TG", "Togo");
            put("TK", "Tokelau");
            put("TO", "Tonga");
            put("TT", "Trinidad and Tobago");
            put("TN", "Tunisia");
            put("TR", "Turkey");
            put("TM", "Turkmenistan");
            put("TC", "Turks and Caicos Islands");
            put("TV", "Tuvalu");
            put("UG", "Uganda");
            put("UA", "Ukraine");
            put("AE", "United Arab Emirates");
            put("GB", "United Kingdom");
            put("UK", "United Kingdom");
            put("US", "United States");
            put("UM", "USA Islands");
            put("UY", "Uruguay");
            put("UZ", "Uzbekistan");
            put("VU", "Vanuatu");
            put("VE", "Venezuela");
            put("VN", "Viet Nam");
            put("VG", "Virgin Islands, British");
            put("VI", "Virgin Islands, U.S.");
            put("WF", "Wallis and Futuna");
            put("EH", "Western Sahara");
            put("YE", "Yemen");
            put("ZM", "Zambia");
            put("ZW", "Zimbabwe");
        }};
    }

    public static int getFlagResource(Context context, String flag) {
        int desiredFlag = context.getResources()
            .getIdentifier(flag.toLowerCase() + "_flag", "drawable", context.getPackageName());
        return desiredFlag > 0 ? desiredFlag :
            context.getResources().getIdentifier("zz_flag", "drawable", context.getPackageName());
    }

    public static String getFullName(String country) {
        return countryNamingMap.get(country) == null ? "Unknown" : countryNamingMap.get(country);
    }

    private static void initMap() {

    }
}
