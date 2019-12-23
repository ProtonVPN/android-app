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

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public class SRPClient {

    private static BigInteger toBI(final byte[] repr) {
        final byte[] reversed = Arrays.copyOf(repr, repr.length);
        ArrayUtils.reverse(reversed);
        return new BigInteger(1, reversed);
    }

    private static byte[] fromBI(final int bitLength, final BigInteger bi) {
        final byte[] twosComp = bi.toByteArray();
        ArrayUtils.reverse(twosComp);
        final byte[] output = new byte[bitLength / 8];
        System.arraycopy(twosComp, 0, output, 0, Math.min(twosComp.length, output.length));
        return output;
    }

    public static Proofs generateProofs(final int bitLength, final byte[] modulusRepr,
                                        final byte[] serverEphemeralRepr,
                                        final byte[] hashedPasswordRepr) throws NoSuchAlgorithmException {
        if (modulusRepr.length * 8 != bitLength || serverEphemeralRepr.length * 8 != bitLength
            || hashedPasswordRepr.length * 8 != bitLength) {
            // FIXME: better error message?
            return null;
        }

        final BigInteger modulus = toBI(modulusRepr);
        final BigInteger serverEphemeral = toBI(serverEphemeralRepr);
        final BigInteger hashedPassword = toBI(hashedPasswordRepr);

        if (modulus.bitLength() != bitLength) {
            return null;
        }

        final BigInteger generator = BigInteger.valueOf(2);

        final BigInteger multiplier =
            toBI(PasswordUtils.expandHash(ArrayUtils.addAll(fromBI(bitLength, generator), modulusRepr))).mod(
                modulus);
        final BigInteger modulusMinusOne = modulus.clearBit(0);

        if (multiplier.compareTo(BigInteger.ONE) <= 0 || multiplier.compareTo(modulusMinusOne) >= 0) {
            return null;
        }

        if (serverEphemeral.compareTo(BigInteger.ONE) <= 0
            || serverEphemeral.compareTo(modulusMinusOne) >= 0) {
            return null;
        }

        if (!modulus.isProbablePrime(10) || !modulus.shiftRight(1).isProbablePrime(10)) {
            return null;
        }

        final SecureRandom random = new SecureRandom();
        BigInteger clientSecret;
        BigInteger clientEphemeral;
        BigInteger scramblingParam;
        do {
            do {
                clientSecret = new BigInteger(bitLength, random);
            }
            while (clientSecret.compareTo(modulusMinusOne) >= 0
                || clientSecret.compareTo(BigInteger.valueOf(bitLength * 2)) <= 0);
            clientEphemeral = generator.modPow(clientSecret, modulus);
            scramblingParam = toBI(PasswordUtils.expandHash(
                ArrayUtils.addAll(fromBI(bitLength, clientEphemeral), serverEphemeralRepr)));
        }
        while (scramblingParam.equals(BigInteger.ZERO)); // Very unlikely

        BigInteger subtracted = serverEphemeral.subtract(
            generator.modPow(hashedPassword, modulus).multiply(multiplier).mod(modulus));
        if (subtracted.compareTo(BigInteger.ZERO) < 0) {
            subtracted = subtracted.add(modulus);
        }
        final BigInteger exponent =
            scramblingParam.multiply(hashedPassword).add(clientSecret).mod(modulusMinusOne);
        final BigInteger sharedSession = subtracted.modPow(exponent, modulus);

        final byte[] clientEphemeralRepr = fromBI(bitLength, clientEphemeral);
        final byte[] clientProof = PasswordUtils.expandHash(
            ArrayUtils.addAll(ArrayUtils.addAll(clientEphemeralRepr, serverEphemeralRepr),
                fromBI(bitLength, sharedSession)));
        final byte[] serverProof = PasswordUtils.expandHash(
            ArrayUtils.addAll(ArrayUtils.addAll(clientEphemeralRepr, clientProof),
                fromBI(bitLength, sharedSession)));

        return new Proofs(clientEphemeralRepr, clientProof, serverProof);
    }

    public static byte[] generateVerifier(final int bitLength, final byte[] modulusRepr,
                                          final byte[] hashedPasswordRepr) {
        final BigInteger modulus = toBI(modulusRepr);
        final BigInteger generator = BigInteger.valueOf(2);
        final BigInteger hashedPassword = toBI(hashedPasswordRepr);

        return fromBI(bitLength, generator.modPow(hashedPassword, modulus));
    }

    public static class Proofs {

        public final byte[] clientEphemeral;
        public final byte[] clientProof;
        public final byte[] expectedServerProof;

        public Proofs(final byte[] clientEphemeral, final byte[] clientProof,
                      final byte[] expectedServerProof) {
            this.clientEphemeral = clientEphemeral;
            this.clientProof = clientProof;
            this.expectedServerProof = expectedServerProof;
        }
    }
}