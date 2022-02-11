/*
 * Copyright (c) 2012-2020 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.Intent;

public class VariantConfig {
    static Intent getOpenUrlIntent(Context c, boolean external) {
        return new Intent(Intent.ACTION_VIEW);
    }

}
