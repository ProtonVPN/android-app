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

public class ConstantTime {

    // We can't trust MessageDigest.isEqual since Apache Harmony's is not constant time
    // and OpenJDK's wasn't until SE 6 Update 17
    public static boolean isEqual(final byte[] a, final byte[] b) {
        if (a.length != b.length) {
            return false;
        }

        byte diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }

        return diff == 0;
    }

    public static String encodeBase64(final byte[] raw, final boolean pad) {
        return encodeBase64With(raw, new StdBase64(), pad);
    }

    public static String encodeBase64DotSlash(final byte[] raw, final boolean pad) {
        return encodeBase64With(raw, new DotSlashBase64(), pad);
    }

    private static String encodeBase64With(final byte[] raw, final Base64Encoder encoder, final boolean pad) {
        final StringBuilder builder = new StringBuilder();

        for (int i = 0; i + 3 <= raw.length; i += 3) {
            final int d1 = (raw[i] & 0xff) >> 2;
            final int d2 = 63 & ((raw[i] & 0xff) << 4 | (raw[i + 1] & 0xff) >> 4);
            final int d3 = 63 & ((raw[i + 1] & 0xff) << 2 | (raw[i + 2] & 0xff) >> 6);
            final int d4 = 63 & raw[i + 2];

            builder.append(encoder.encode6Bits(d1));
            builder.append(encoder.encode6Bits(d2));
            builder.append(encoder.encode6Bits(d3));
            builder.append(encoder.encode6Bits(d4));
        }

        final int d1, d2, d3;
        switch (raw.length % 3) {
            case 0:
                // No padding necessary
                break;
            case 1:
                d1 = (raw[raw.length - 1] & 0xff) >> 2;
                d2 = 63 & ((raw[raw.length - 1] & 0xff) << 4);

                builder.append(encoder.encode6Bits(d1));
                builder.append(encoder.encode6Bits(d2));
                if (pad) {
                    builder.append('=');
                    builder.append('=');
                }
                break;
            case 2:
                d1 = (raw[raw.length - 2] & 0xff) >> 2;
                d2 = 63 & ((raw[raw.length - 2] & 0xff) << 4 | (raw[raw.length - 1] & 0xff) >> 4);
                d3 = 63 & (raw[raw.length - 1] & 0xff) << 2;

                builder.append(encoder.encode6Bits(d1));
                builder.append(encoder.encode6Bits(d2));
                builder.append(encoder.encode6Bits(d3));
                if (pad) {
                    builder.append('=');
                }
                break;
        }

        return builder.toString();
    }

    private interface Base64Encoder {

        char encode6Bits(int bits);
    }

    private static class StdBase64 implements Base64Encoder {

        // [A-Z][a-z][0-9]+/
        public char encode6Bits(final int bits) {
            // We proceed by a series of conditionals, done in a constant time way.

            // Comparisons are done via subtraction and arithmetic right shift,
            // as (bits - x) >> 16 is 0b000000... or 0b111111... if bits >= x
            // or < x, respectively. We don't need to clear out a full 16 bits,
            // but it's certainly safe and I think some processors can handle
            // power of two or at least byte-aligned shifts slightly faster.

            int ret = bits + '/' - 63;
            // if (bits < 63) { ret = '+' + bits - 62; }
            ret += ((bits - 63) >> 16) & (('+' - 62) - ('/' - 63));
            // if (bits < 62) { ret = '0' + bits - 52; }
            ret += ((bits - 62) >> 16) & (('0' - 52) - ('+' - 62));
            // if (bits < 52) { ret = 'a' + bits - 26; }
            ret += ((bits - 52) >> 16) & (('a' - 26) - ('0' - 52));
            // if (bits < 26) { ret = 'A' + bits; }
            ret += ((bits - 26) >> 16) & (('A') - ('a' - 26));

            return (char) ret;
        }
    }

    private static class DotSlashBase64 implements Base64Encoder {

        // ./[A-Z][a-z][0-9]
        public char encode6Bits(final int bits) {
            // See StdBase64 for implementation explanation

            int ret = bits + '0' - 54;
            // if (bits < 54) { ret = 'a' + bits - 28; }
            ret += ((bits - 54) >> 16) & (('a' - 28) - ('0' - 54));
            // if (bits < 28) { ret = 'A' + bits - 2; }
            ret += ((bits - 28) >> 16) & (('A' - 2) - ('a' - 28));
            // The characters ./ are adjacent in ascii
            // if (bits < 2) { ret = '.' + bits; }
            ret += ((bits - 2) >> 16) & (('.') - ('A' - 2));

            return (char) ret;
        }
    }
}
