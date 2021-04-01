/*
 * Copyright (c) 2012-2017 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.annotation.SuppressLint;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class TestLogFileHandler {

    byte[] testUnescaped = new byte[]{0x00, 0x55, -27, 0x00, 0x56, 0x10, -128, 0x55, 0x54};
    byte[] expectedEscaped = new byte[]{0x55, 0x00, 0x00, 0x00, 0x09, 0x00, 0x56, 0x00, -27, 0x00, 0x56, 0x01, 0x10, -128, 0x56, 0x00, 0x54};
    private TestingLogFileHandler lfh;


    @Before
    public void setup() {
        lfh = new TestingLogFileHandler();
    }

    @Test
    public void testWriteByteArray() throws IOException {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        lfh.setLogFile(byteArrayOutputStream);

        lfh.writeEscapedBytes(testUnescaped);

        byte[] result = byteArrayOutputStream.toByteArray();
        Assert.assertTrue(Arrays.equals(expectedEscaped, result));
    }

    @Test
    public void readByteArray() throws IOException {

        ByteArrayInputStream in = new ByteArrayInputStream(expectedEscaped);

        lfh.readCacheContents(in);

        Assert.assertTrue(Arrays.equals(testUnescaped, lfh.mRestoredByteArray));

    }

    @Test
    public void testMarschal() throws UnsupportedEncodingException {
        LogItem li = new LogItem(VpnStatus.LogLevel.DEBUG, 72, "foobar");
        LogItem li2 = marschalAndBack(li);
        testEquals(li, li2);
        Assert.assertEquals(li, li2);
    }

    @Test
    public void testMarschalArgs() throws UnsupportedEncodingException {
        LogItem li = new LogItem(VpnStatus.LogLevel.DEBUG, 72, 772, "sinnloser Text", 7723, 723.2f, 7.2);
        LogItem li2 = marschalAndBack(li);
        testEquals(li, li2);
        Assert.assertEquals(li, li2);
    }

    @Test
    public void testMarschalString() throws UnsupportedEncodingException {
        LogItem li = new LogItem(VpnStatus.LogLevel.DEBUG, "Nutzlose Nachricht");
        LogItem li2 = marschalAndBack(li);
        testEquals(li, li2);
        Assert.assertEquals(li, li2);
    }


    private void testEquals(LogItem li, LogItem li2) {
        Assert.assertEquals(li.getLogLevel(), li2.getLogLevel());
        Assert.assertEquals(li.getLogtime(), li2.getLogtime());
        Assert.assertEquals(li.getVerbosityLevel(), li2.getVerbosityLevel());
        Assert.assertEquals(li.toString(), li2.toString());

    }

    private LogItem marschalAndBack(LogItem li) throws UnsupportedEncodingException {
        byte[] bytes = li.getMarschaledBytes();

        return new LogItem(bytes, bytes.length);
    }


    @SuppressLint("HandlerLeak")
    static class TestingLogFileHandler extends LogFileHandler {

        public byte[] mRestoredByteArray;

        public TestingLogFileHandler() {
            super(null);
        }

        public void setLogFile(OutputStream out) {
            mLogFile = out;
        }

        @Override
        public void readCacheContents(InputStream in) throws IOException {
            super.readCacheContents(in);
        }

        @Override
        protected void restoreLogItem(byte[] buf, int len) {
            mRestoredByteArray = Arrays.copyOf(buf, len);
        }
    }


}