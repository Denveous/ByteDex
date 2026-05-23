package org.openmmo.bytedex.api.server

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class WorkersTest {
    @Test
    fun `inferFields separates global, session and variable bytes`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val samples = listOf(
            a to byteArrayOf(0x01, 0x02, 0xAA.toByte()),
            a to byteArrayOf(0x01, 0x02, 0xBB.toByte()),
            b to byteArrayOf(0x01, 0x03, 0xCC.toByte()),
            b to byteArrayOf(0x01, 0x03, 0xDD.toByte()),
        )

        val fields = inferFields(samples)

        assertEquals(3, fields.size)
        assertEquals(0, fields[0].offset)
        assertTrue(fields[0].isGlobalConstant == true)
        assertTrue(fields[1].isSessionConstant == true)
        assertTrue(fields[1].isGlobalConstant == false)
        assertTrue(fields[2].isSessionConstant == false)
        assertTrue(fields[2].isGlobalConstant == false)
    }

    @Test
    fun `inferFields appends a variable-length tail field`() {
        val s = UUID.randomUUID()
        val fields = inferFields(
            listOf(s to byteArrayOf(0x01), s to byteArrayOf(0x01, 0x02, 0x03)),
        )
        assertEquals(2, fields.size)
        assertEquals(1, fields.last().offset)
        assertEquals(2, fields.last().length)
    }
}
