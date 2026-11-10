package com.rdavies.arena.user;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

/** The key a client IP's attempts are counted under. */
final class ClientIp {
    private static final int IPV6_NETWORK_BYTES = 8;

    private ClientIp() {}

    /**
     * One host can hold a whole IPv6 /64, so that is what gets counted. Only an address with a
     * colon is parsed, because a colon-free string could send InetAddress off to do a DNS lookup.
     */
    static String key(String ip) {
        if (!ip.contains(":")) {
            return ip;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address instanceof Inet6Address
                    ? HexFormat.of().formatHex(address.getAddress(), 0, IPV6_NETWORK_BYTES)
                    : address.getHostAddress(); // an IPv4 address written in IPv6 form
        } catch (UnknownHostException e) {
            return ip;
        }
    }
}
