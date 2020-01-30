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
package com.protonvpn.android.models.vpn;

import com.protonvpn.android.utils.CountryTools;
import com.protonvpn.android.utils.Log;

import java.io.Serializable;

public class TranslatedCoordinates implements Serializable {

    private final Double positionX;
    private final Double positionY;

    TranslatedCoordinates(String forFlag) {
        this.positionX = CountryTools.locationMap.get(forFlag + "_x");
        this.positionY = CountryTools.locationMap.get(forFlag + "_y");
        if (positionX == null || positionY == null) {
            Log.d("Can't translate coordinates for: " + forFlag);
        }
    }

    public boolean hasValidCoordinates() {
        return positionX != null && positionY != null;
    }

    public Double getPositionX() {
        return positionX;
    }

    public Double getPositionY() {
        return positionY;
    }

    public double[] asCoreCoordinates() {
        return new double[] {positionX, positionY};
    }

    public int compareTo(TranslatedCoordinates b) {
        if (isNull() || b.isNull()) {
            return 0;
        }
        if (this.positionY > b.positionY) {
            return 1;
        }
        if (this.positionY < b.positionY) {
            return -1;
        }
        else {
            return 0;
        }
    }

    private boolean isNull() {
        return positionY == null && positionX == null;
    }
}