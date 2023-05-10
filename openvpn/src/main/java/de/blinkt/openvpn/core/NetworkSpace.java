/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package de.blinkt.openvpn.core;

import android.annotation.TargetApi;
import android.net.IpPrefix;
import android.os.Build;

import androidx.annotation.NonNull;

import java.lang.annotation.Target;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.TreeSet;
import java.util.Vector;

import de.blinkt.openvpn.BuildConfig;



public class NetworkSpace {

    static void assertTrue(boolean f)
    {
        if (!f)
            throw new IllegalStateException();
    }

    static class IpAddress implements Comparable<IpAddress> {
        private BigInteger netAddress;
        public int networkMask;
        private boolean included;
        private boolean isV4;
        private BigInteger firstAddress;
        private BigInteger lastAddress;


        /**
         * sorts the networks with following criteria:
         * 1. compares first 1 of the network
         * 2. smaller networks are returned as smaller
         */
        @Override
        public int compareTo(@NonNull IpAddress another) {
            int comp = getFirstAddress().compareTo(another.getFirstAddress());
            if (comp != 0)
                return comp;


            if (networkMask > another.networkMask)
                return -1;
            else if (another.networkMask == networkMask)
                return 0;
            else
                return 1;
        }

        /**
         * Warning ignores the included integer
         *
         * @param o the object to compare this instance with.
         */
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof IpAddress))
                return super.equals(o);


            IpAddress on = (IpAddress) o;
            return (networkMask == on.networkMask) && on.getFirstAddress().equals(getFirstAddress());
        }

        public IpAddress(CIDRIP ip, boolean include) {
            included = include;
            netAddress = BigInteger.valueOf(ip.getInt());
            networkMask = ip.len;
            isV4 = true;
        }

        public IpAddress(Inet6Address address, int mask, boolean include) {
            networkMask = mask;
            included = include;

            int s = 128;

            netAddress = BigInteger.ZERO;
            for (byte b : address.getAddress()) {
                s -= 8;
                netAddress = netAddress.add(BigInteger.valueOf((b & 0xFF)).shiftLeft(s));
            }
        }

        public BigInteger getLastAddress() {
            if (lastAddress == null)
                lastAddress = getMaskedAddress(true);
            return lastAddress;
        }


        public BigInteger getFirstAddress() {
            if (firstAddress == null)
                firstAddress = getMaskedAddress(false);
            return firstAddress;
        }


        private BigInteger getMaskedAddress(boolean one) {
            BigInteger numAddress = netAddress;

            int numBits;
            if (isV4) {
                numBits = 32 - networkMask;
            } else {
                numBits = 128 - networkMask;
            }

            for (int i = 0; i < numBits; i++) {
                if (one)
                    numAddress = numAddress.setBit(i);
                else
                    numAddress = numAddress.clearBit(i);
            }
            return numAddress;
        }


        @NonNull
        @Override
        public String toString() {
            //String in = included ? "+" : "-";
            if (isV4)
                return String.format(Locale.US, "%s/%d", getIPv4Address(), networkMask);
            else
                return String.format(Locale.US, "%s/%d", getIPv6Address(), networkMask);
        }

        IpAddress(BigInteger baseAddress, int mask, boolean included, boolean isV4) {
            this.netAddress = baseAddress;
            this.networkMask = mask;
            this.included = included;
            this.isV4 = isV4;
        }


        public IpAddress[] split() {
            IpAddress firstHalf = new IpAddress(getFirstAddress(), networkMask + 1, included, isV4);
            IpAddress secondHalf = new IpAddress(firstHalf.getLastAddress().add(BigInteger.ONE), networkMask + 1, included, isV4);
            if (BuildConfig.DEBUG)
                assertTrue(secondHalf.getLastAddress().equals(getLastAddress()));
            return new IpAddress[]{firstHalf, secondHalf};
        }

        String getIPv4Address() {
            if (BuildConfig.DEBUG) {
                assertTrue(isV4);
                assertTrue(netAddress.longValue() <= 0xffffffffl);
                assertTrue(netAddress.longValue() >= 0);
            }
            long ip = netAddress.longValue();
            return String.format(Locale.US, "%d.%d.%d.%d", (ip >> 24) % 256, (ip >> 16) % 256, (ip >> 8) % 256, ip % 256);
        }

        String getIPv6Address() {
            if (BuildConfig.DEBUG) assertTrue(!isV4);
            BigInteger r = netAddress;

            String ipv6str = null;
            boolean lastPart = true;

            while (r.compareTo(BigInteger.ZERO) == 1) {

                long part = r.mod(BigInteger.valueOf(0x10000)).longValue();
                if (ipv6str != null || part != 0) {
                    if (ipv6str == null && !lastPart)
                            ipv6str = ":";

                    if (lastPart)
                        ipv6str = String.format(Locale.US, "%x", part, ipv6str);
                    else
                        ipv6str = String.format(Locale.US, "%x:%s", part, ipv6str);
                }

                r = r.shiftRight(16);
                lastPart = false;
            }
            if (ipv6str == null)
                return "::";


            return ipv6str;
        }

        public boolean containsNet(IpAddress network) {
            // this.first >= net.first &&  this.last <= net.last
            BigInteger ourFirst = getFirstAddress();
            BigInteger ourLast = getLastAddress();
            BigInteger netFirst = network.getFirstAddress();
            BigInteger netLast = network.getLastAddress();

            boolean a = ourFirst.compareTo(netFirst) != 1;
            boolean b = ourLast.compareTo(netLast) != -1;
            return a && b;

        }

        @TargetApi(Build.VERSION_CODES.TIRAMISU)
        public IpPrefix getPrefix() throws UnknownHostException {
            if (isV4){
                /* add 0x01 00 00 00 00, so that all representations are 5 byte otherwise
                /* numbers that are above 0x7fffffff get a leading 0x00 byte to not be negative
                 and small number 1-3 bytes*/
                byte[] ipBytes = netAddress.add(BigInteger.valueOf(0x0100000000L)).toByteArray();
                ipBytes = Arrays.copyOfRange(ipBytes, 1, 5);

                InetAddress inet4addr = Inet4Address.getByAddress(ipBytes);
                return new IpPrefix(inet4addr, networkMask);
            }
            else
            {
                /* same dance for IPv6 */
                byte[] ipBytes = netAddress.add(BigInteger.ONE.shiftLeft(128)).toByteArray();
                ipBytes = Arrays.copyOfRange(ipBytes, 1, 17);

                InetAddress inet6addr = Inet6Address.getByAddress(ipBytes);
                return new IpPrefix(inet6addr, networkMask);
            }
        }
    }


    TreeSet<IpAddress> mIpAddresses = new TreeSet<IpAddress>();


    public Collection<IpAddress> getNetworks(boolean included) {
        Vector<IpAddress> ips = new Vector<IpAddress>();
        for (IpAddress ip : mIpAddresses) {
            if (ip.included == included)
                ips.add(ip);
        }
        return ips;
    }

    public void clear() {
        mIpAddresses.clear();
    }


    void addIP(CIDRIP cidrIp, boolean include) {

        mIpAddresses.add(new IpAddress(cidrIp, include));
    }

    public void addIPSplit(CIDRIP cidrIp, boolean include) {
        IpAddress newIP = new IpAddress(cidrIp, include);
        IpAddress[] splitIps = newIP.split();
        for (IpAddress split : splitIps)
            mIpAddresses.add(split);
    }

    void addIPv6(Inet6Address address, int mask, boolean included) {
        mIpAddresses.add(new IpAddress(address, mask, included));
    }

    TreeSet<IpAddress> generateIPList() {

        PriorityQueue<IpAddress> networks = new PriorityQueue<IpAddress>(mIpAddresses);

        TreeSet<IpAddress> ipsDone = new TreeSet<IpAddress>();

        IpAddress currentNet = networks.poll();
        if (currentNet == null)
            return ipsDone;

        while (currentNet != null) {
            // Check if it and the next of it are compatible
            IpAddress nextNet = networks.poll();

            if (BuildConfig.DEBUG) assertTrue(currentNet!=null);
            if (nextNet == null || currentNet.getLastAddress().compareTo(nextNet.getFirstAddress()) == -1) {
                // Everything good, no overlapping nothing to do
                ipsDone.add(currentNet);

                currentNet = nextNet;
            } else {
                // This network is smaller or equal to the next but has the same base address
                if (currentNet.getFirstAddress().equals(nextNet.getFirstAddress()) && currentNet.networkMask >= nextNet.networkMask) {
                    if (currentNet.included == nextNet.included) {
                        // Included in the next next and same type
                        // Simply forget our current network
                        currentNet = nextNet;
                    } else {
                        // our currentNet is included in next and types differ. Need to split the next network
                        IpAddress[] newNets = nextNet.split();


                        // TODO: The contains method of the Priority is stupid linear search

                        // First add the second half to keep the order in networks
                        if (!networks.contains(newNets[1]))
                            networks.add(newNets[1]);

                        if (newNets[0].getLastAddress().equals(currentNet.getLastAddress())) {
                            if (BuildConfig.DEBUG)
                                assertTrue(newNets[0].networkMask == currentNet.networkMask);
                            // Don't add the lower half that would conflict with currentNet
                        } else {
                            if (!networks.contains(newNets[0]))
                                networks.add(newNets[0]);
                        }
                        // Keep currentNet as is
                    }
                } else {
                    if (BuildConfig.DEBUG) {
                        assertTrue(currentNet.networkMask < nextNet.networkMask);
                        assertTrue(nextNet.getFirstAddress().compareTo(currentNet.getFirstAddress()) == 1);
                        assertTrue(currentNet.getLastAddress().compareTo(nextNet.getLastAddress()) != -1);
                    }
                    // This network is bigger than the next and last ip of current >= next

                    //noinspection StatementWithEmptyBody
                    if (currentNet.included == nextNet.included) {
                        // Next network is in included in our network with the same type,
                        // simply ignore the next and move on
                    } else {
                        // We need to split our network
                        IpAddress[] newNets = currentNet.split();


                        if (newNets[1].networkMask == nextNet.networkMask) {
                            if (BuildConfig.DEBUG) {
                                assertTrue(newNets[1].getFirstAddress().equals(nextNet.getFirstAddress()));
                                assertTrue(newNets[1].getLastAddress().equals(currentNet.getLastAddress()));
                                // split second equal the next network, do not add it
                            }
                            networks.add(nextNet);
                        } else {
                            // Add the smaller network first
                            networks.add(newNets[1]);
                            networks.add(nextNet);
                        }
                        currentNet = newNets[0];

                    }
                }
            }

        }

        return ipsDone;
    }

    Collection<IpAddress> getPositiveIPList() {
        TreeSet<IpAddress> ipsSorted = generateIPList();

        Vector<IpAddress> ips = new Vector<IpAddress>();
        for (IpAddress ia : ipsSorted) {
            if (ia.included)
                ips.add(ia);
        }

        return ips;
    }

}
