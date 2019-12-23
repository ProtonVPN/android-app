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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import androidx.annotation.Nullable;

public final class CollectionTools {

    private CollectionTools() {

    }

    public static <T> List<T> findAll(@Nullable final Collection<T> collection,
                                      final Predicate<T> predicate) {
        List<T> items = new ArrayList<>();
        if (collection != null) {
            for (T item : collection) {
                if (predicate.contains(item)) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    public static <T> boolean filter(@Nullable final Collection<T> collection, final Predicate<T> predicate) {
        if (collection != null) {
            for (T item : collection) {
                if (predicate.contains(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    public interface Predicate<T> {

        boolean contains(T item);
    }
}
