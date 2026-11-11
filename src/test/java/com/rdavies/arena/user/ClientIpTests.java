package com.rdavies.arena.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Which client IPs share a count. */
class ClientIpTests {
    @Test
    void anIpv4AddressIsItsOwnKey() {
        assertEquals("203.0.113.1", ClientIp.key("203.0.113.1"));
    }

    @Test
    void ipv6AddressesInTheSameSlash64ShareAKey() {
        assertEquals(ClientIp.key("2001:db8:1:2:aaaa::1"), ClientIp.key("2001:db8:1:2:bbbb::2"));
        assertNotEquals(ClientIp.key("2001:db8:1:2::1"), ClientIp.key("2001:db8:1:3::1"));
    }

    @Test
    void anIpv4AddressMappedIntoIpv6CountsAsThatIpv4Address() {
        assertEquals("203.0.113.1", ClientIp.key("::ffff:203.0.113.1"));
    }
}
