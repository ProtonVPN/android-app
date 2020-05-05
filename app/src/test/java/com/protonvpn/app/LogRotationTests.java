/*
 * Copyright (c) 2019 Proton Technologies AG
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
package com.protonvpn.app;

import com.protonvpn.android.utils.Constants;
import com.protonvpn.android.utils.Log;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class LogRotationTests {

    private final static String FILE_NAME = "TestFile";
    RandomAccessFile file = new RandomAccessFile(FILE_NAME, "rw");

    public LogRotationTests() throws FileNotFoundException {}

    @Test
    public void testCorrectSize() throws IOException {
        long correctSize = Constants.MAX_LOG_SIZE;
        file.setLength(correctSize);
        Log.checkForLogTruncation(FILE_NAME);
        Assert.assertEquals(correctSize, file.length());
    }

    @Test
    public void testTooLargeSize() throws IOException {
        long tooLargeSize = Constants.MAX_LOG_SIZE + 1;
        file.setLength(tooLargeSize);
        Log.checkForLogTruncation(FILE_NAME);
        Assert.assertEquals(0, file.length());
    }

    @After
    public void finalize() throws IOException {
        file.close();
        File file = new File(FILE_NAME);
        file.delete();
    }
}