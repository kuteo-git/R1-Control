package com.r1control.app.domain

import com.r1control.app.domain.model.AddressInput
import com.r1control.app.domain.model.AddressInput.Reason
import com.r1control.app.domain.model.R1Address
import kotlin.test.Test
import kotlin.test.assertEquals

class R1AddressTest {
    private fun valid(text: String) = (R1Address.parse(text) as AddressInput.Valid).address
    private fun reason(text: String) = (R1Address.parse(text) as AddressInput.Invalid).reason

    @Test fun portDefaultsTo8088() {
        assertEquals(R1Address("192.168.1.20", 8088), valid("192.168.1.20"))
    }

    @Test fun hostAndPort() {
        assertEquals(R1Address("10.25.113.151", 9000), valid("10.25.113.151:9000"))
        assertEquals("http://10.25.113.151:9000", valid("10.25.113.151:9000").baseUrl)
        assertEquals("10.25.113.151:9000", valid("10.25.113.151:9000").toString())
    }

    @Test fun forgivesPaddingSchemeAndTrailingSlash() {
        assertEquals(R1Address("192.168.1.20", 8088), valid("  http://192.168.1.20:8088/ "))
    }

    @Test fun hostNames() {
        assertEquals(R1Address("r1.local", 8088), valid("R1.local"))
        assertEquals(R1Address("r1", 80), valid("r1:80"))
    }

    @Test fun rejectsEmpty() {
        assertEquals(Reason.Empty, reason(""))
        assertEquals(Reason.Empty, reason("   "))
    }

    @Test fun rejectsJunkHosts() {
        for (junk in listOf(
            "256.1.1.1", "1.2.3", "1.2.3.4.5", "192.168.1.20/path", "a b", "-r1", "r1-", "..", "exa_mple",
            ":8088", "[::1]:8088", "https://192.168.1.20", "192.168.1.20?x=1", "192.168.1.20:8088:1",
        )) {
            assertEquals(Reason.BadHost, reason(junk), junk)
        }
    }

    @Test fun rejectsJunkPorts() {
        for (junk in listOf("1.2.3.4:", "1.2.3.4:0", "1.2.3.4:65536", "1.2.3.4:80a", "1.2.3.4:-1", "1.2.3.4:123456")) {
            assertEquals(Reason.BadPort, reason(junk), junk)
        }
        assertEquals(65535, valid("1.2.3.4:65535").port)
        assertEquals(1, valid("1.2.3.4:1").port)
    }
}
