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

public class SrpTools {

  /*  public static SRPClient.Proofs srpProofsForInfo(final String username, final String password, final
  LoginInfoResponse infoResponse, final int fallbackAuthVersion) throws NoSuchAlgorithmException {
        int authVersion = infoResponse.getVersion();
        if (authVersion == 0) {
            authVersion = fallbackAuthVersion;
        }

        if (authVersion <= 2) {
            return null;
        }

        final OpenPgp openPgp = OpenPgp.createInstance();
        final byte[] modulus = Base64.decode(openPgp.readClearsignedMessage(infoResponse.getModulus()),
        Base64.DEFAULT);
        final byte[] hashedPassword = PasswordUtils.hashPassword(authVersion, password, username,
        Base64.decode(infoResponse.getSalt(), Base64.DEFAULT), modulus);

        return SRPClient.generateProofs(2048, modulus, Base64.decode(infoResponse.getServerEphemeral(),
        Base64.DEFAULT), hashedPassword);
    } */
}
