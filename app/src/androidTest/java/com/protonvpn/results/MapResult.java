/*
 * Copyright (c) 2018 Proton Technologies AG
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
package com.protonvpn.results;

import com.protonvpn.testsHelper.UIActionsTestHelper;

public class MapResult extends UIActionsTestHelper {

    public MapResult isUSNodeSelected() {
        checkIfMapNodeIsSelected("United States");
        return this;
    }

    public MapResult isUSNodeNotSelected() {
        checkIfMapNodeIsNotSelected("United States");
        return this;
    }

    public MapResult isSwedenNodeSelected() {
        checkIfMapNodeIsSelected("Sweden");
        return this;
    }

    public MapResult isFranceSecureCoreNodeSelected() {
        checkIfMapNodeIsSelected("Sweden >> France");
        return this;
    }
}
