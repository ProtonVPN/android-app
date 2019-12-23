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

import org.apache.commons.lang3.ArrayUtils;
import org.mindrot.jbcrypt.BCrypt;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PasswordUtils {

    public static final String BCRYPT_PREFIX = "$2a$10$";

    public static final int CURRENT_AUTH_VERSION = 4;

    public static String cleanUserName(String username) {
        return username.replaceAll("_|\\.|-", "").toLowerCase();
    }

    public static byte[] expandHash(final byte[] input) throws NoSuchAlgorithmException {
        final byte[] output = new byte[2048 / 8];
        final MessageDigest digest = MessageDigest.getInstance("SHA-512");

        digest.update(input);
        digest.update((byte) 0);
        System.arraycopy(digest.digest(), 0, output, 0, 512 / 8);
        digest.reset();

        digest.update(input);
        digest.update((byte) 1);
        System.arraycopy(digest.digest(), 0, output, 512 / 8, 512 / 8);
        digest.reset();

        digest.update(input);
        digest.update((byte) 2);
        System.arraycopy(digest.digest(), 0, output, 1024 / 8, 512 / 8);
        digest.reset();

        digest.update(input);
        digest.update((byte) 3);
        System.arraycopy(digest.digest(), 0, output, 1536 / 8, 512 / 8);
        digest.reset();

        return output;
    }

    private static String bcrypt(final String password, final String salt) {
        final String ret = BCrypt.hashpw(password, BCRYPT_PREFIX + salt);
        return "$2y$" + ret.substring(4);
    }

    public static byte[] hashPassword(final int authVersion, final String password, final String username,
                                      final byte[] salt, final byte[] modulus) {
        switch (authVersion) {
            case 4:
                return hashPasswordVersion4(password, salt, modulus);
            case 3:
                return hashPasswordVersion3(password, salt, modulus);
            default:
                throw new IllegalArgumentException("Unsupported Auth Version");
        }
    }

    private static byte[] hashPasswordVersion4(final String password, final byte[] salt,
                                               final byte[] modulus) {
        return hashPasswordVersion3(password, salt, modulus);
    }

    private static byte[] hashPasswordVersion3(final String password, final byte[] salt,
                                               final byte[] modulus) {
        try {
            final String encodedSalt =
                ConstantTime.encodeBase64DotSlash(ArrayUtils.addAll(salt, "proton".getBytes("UTF8")), false);
            return expandHash(ArrayUtils.addAll(bcrypt(password, encodedSalt).getBytes("UTF8"), modulus));
        }
        catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

}
