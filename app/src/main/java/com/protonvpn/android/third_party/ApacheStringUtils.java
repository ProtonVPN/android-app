/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Code extracted from:
// https://github.com/apache/commons-lang/blob/master/src/main/java/org/apache/commons/lang3/StringUtils.java

package com.protonvpn.android.third_party;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class ApacheStringUtils {

    /**
     * The empty String {@code ""}.
     * @since 2.0
     */
    public static final String EMPTY = "";

    /**
     * Pattern used in {@link #stripAccents(String)}.
     */
    private static final Pattern STRIP_ACCENTS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+"); //$NON-NLS-1$

    /**
     * Removes diacritics (~= accents) from a string. The case will not be altered.
     * <p>For instance, '&agrave;' will be replaced by 'a'.</p>
     * <p>Decomposes ligatures and digraphs per the KD column in the
     * <a href = "https://www.unicode.org/charts/normalization/">Unicode Normalization Chart.</a></p>
     *
     * <pre>
     * StringUtils.stripAccents(null)                = null
     * StringUtils.stripAccents("")                  = ""
     * StringUtils.stripAccents("control")           = "control"
     * StringUtils.stripAccents("&eacute;clair")     = "eclair"
     * </pre>
     *
     * @param input String to be stripped
     * @return input text with diacritics removed
     *
     * @since 3.0
     */
    // See also Lucene's ASCIIFoldingFilter (Lucene 2.9) that replaces accented characters by their unaccented equivalent (and uncommitted bug fix: https://issues.apache.org/jira/browse/LUCENE-1343?focusedCommentId=12858907&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#action_12858907).
    public static String stripAccents(final String input) {
        if (isEmpty(input)) {
            return input;
        }
        // nosemgrep: gitlab.find_sec_bugs.IMPROPER_UNICODE-1
        final StringBuilder decomposed = new StringBuilder(Normalizer.normalize(input, Normalizer.Form.NFKD));
        convertRemainingAccentCharacters(decomposed);
        return STRIP_ACCENTS_PATTERN.matcher(decomposed).replaceAll(EMPTY);
    }

    /**
     * Tests if a CharSequence is empty ("") or null.
     *
     * <pre>
     * StringUtils.isEmpty(null)      = true
     * StringUtils.isEmpty("")        = true
     * StringUtils.isEmpty(" ")       = false
     * StringUtils.isEmpty("bob")     = false
     * StringUtils.isEmpty("  bob  ") = false
     * </pre>
     *
     * <p>NOTE: This method changed in Lang version 2.0.
     * It no longer trims the CharSequence.
     * That functionality is available in isBlank().</p>
     *
     * @param cs  the CharSequence to check, may be null
     * @return {@code true} if the CharSequence is empty or null
     * @since 3.0 Changed signature from isEmpty(String) to isEmpty(CharSequence)
     */
    public static boolean isEmpty(final CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    private static void convertRemainingAccentCharacters(final StringBuilder decomposed) {
        for (int i = 0; i < decomposed.length(); i++) {
            final char charAt = decomposed.charAt(i);
            switch (charAt) {
                case '\u0141':
                    decomposed.setCharAt(i, 'L');
                    break;
                case '\u0142':
                    decomposed.setCharAt(i, 'l');
                    break;
                // D with stroke
                case '\u0110':
                    // LATIN CAPITAL LETTER D WITH STROKE
                    decomposed.setCharAt(i, 'D');
                    break;
                case '\u0111':
                    // LATIN SMALL LETTER D WITH STROKE
                    decomposed.setCharAt(i, 'd');
                    break;
                // I with bar
                case '\u0197':
                    decomposed.setCharAt(i, 'I');
                    break;
                case '\u0268':
                    decomposed.setCharAt(i, 'i');
                    break;
                case '\u1D7B':
                    decomposed.setCharAt(i, 'I');
                    break;
                case '\u1DA4':
                    decomposed.setCharAt(i, 'i');
                    break;
                case '\u1DA7':
                    decomposed.setCharAt(i, 'I');
                    break;
                // U with bar
                case '\u0244':
                    // LATIN CAPITAL LETTER U BAR
                    decomposed.setCharAt(i, 'U');
                    break;
                case '\u0289':
                    // LATIN SMALL LETTER U BAR
                    decomposed.setCharAt(i, 'u');
                    break;
                case '\u1D7E':
                    // LATIN SMALL CAPITAL LETTER U WITH STROKE
                    decomposed.setCharAt(i, 'U');
                    break;
                case '\u1DB6':
                    // MODIFIER LETTER SMALL U BAR
                    decomposed.setCharAt(i, 'u');
                    break;
                // T with stroke
                case '\u0166':
                    // LATIN CAPITAL LETTER T WITH STROKE
                    decomposed.setCharAt(i, 'T');
                    break;
                case '\u0167':
                    // LATIN SMALL LETTER T WITH STROKE
                    decomposed.setCharAt(i, 't');
                    break;
                default:
                    break;
            }
        }
    }
}
