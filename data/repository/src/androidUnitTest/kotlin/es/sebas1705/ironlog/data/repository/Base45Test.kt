package es.sebas1705.ironlog.data.repository

import es.sebas1705.ironlog.data.repository.share.Base45
import es.sebas1705.ironlog.data.repository.share.QrCapacity
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Base45Test {
    @Test
    fun `encodes the RFC 9285 examples`() {
        assertEquals("BB8", Base45.encode("AB".encodeToByteArray()))
        assertEquals("%69 VD92EX0", Base45.encode("Hello!!".encodeToByteArray()))
        assertEquals("UJCLQE7W581", Base45.encode("base-45".encodeToByteArray()))
        assertEquals("", Base45.encode(ByteArray(0)))
    }

    @Test
    fun `decodes the RFC 9285 examples`() {
        assertEquals("ietf!", Base45.decode("QED8WEX0").decodeToString())
        assertEquals("Hello!!", Base45.decode("%69 VD92EX0").decodeToString())
    }

    @Test
    fun `random bytes round trip and stay in the QR alphanumeric charset`() {
        val random = Random(7)
        repeat(200) { size ->
            val bytes = random.nextBytes(size)
            val text = Base45.encode(bytes)
            assertContentEquals(bytes, Base45.decode(text))
            if (size > 0) assertTrue(QrCapacity.isAlphanumeric(text), text)
        }
        // The extremes of a pair and of a single byte.
        val edges = byteArrayOf(-1, -1, 0, 0, -1)
        assertContentEquals(edges, Base45.decode(Base45.encode(edges)))
    }

    @Test
    fun `invalid text is rejected`() {
        assertFailsWith<IllegalArgumentException> { Base45.decode("GGW") } // 65 536 > 0xFFFF
        assertFailsWith<IllegalArgumentException> { Base45.decode("ABCD") } // length 3n + 1
        assertFailsWith<IllegalArgumentException> { Base45.decode("bb8") } // lowercase is not base45
        assertFailsWith<IllegalArgumentException> { Base45.decode("::") } // pair above 255
    }

    @Test
    fun `qr capacity matches the ISO tables at level L`() {
        // Alphanumeric capacity at L: version 1 holds 25 characters, version 40 holds 4 296.
        assertEquals(1, QrCapacity.version("A".repeat(25)))
        assertEquals(2, QrCapacity.version("A".repeat(26)))
        assertEquals(40, QrCapacity.version("A".repeat(4_296)))
        assertEquals(null, QrCapacity.version("A".repeat(4_297)))
        // Byte capacity at L: version 1 holds 17 bytes, version 40 holds 2 953.
        assertEquals(1, QrCapacity.version("a".repeat(17)))
        assertEquals(2, QrCapacity.version("a".repeat(18)))
        assertEquals(40, QrCapacity.version("a".repeat(2_953)))
        assertEquals(21, QrCapacity.modules(1))
        assertEquals(177, QrCapacity.modules(40))
    }
}
