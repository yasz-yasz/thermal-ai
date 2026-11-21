package com.strukai.thermal

import com.strukai.thermal.model.PaperWidth
import com.strukai.thermal.model.ReceiptData
import com.strukai.thermal.model.ReceiptRow
import com.strukai.thermal.printer.EscPosFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosFormatterTest {

    @Test
    fun formatKeyValue_alignsWithin58mmAnd80mm() {
        val line58 = EscPosFormatter.formatKeyValue("Admin", "Rp 2.500", PaperWidth.MM_58.maxCharsPerLine)
        assertEquals(1, line58.size)
        assertEquals(32, line58[0].length)

        val line80 = EscPosFormatter.formatKeyValue("Admin", "Rp 2.500", PaperWidth.MM_80.maxCharsPerLine)
        assertEquals(1, line80.size)
        assertEquals(48, line80[0].length)
    }

    @Test
    fun buildEscPosBytes_containsInitAndContent() {
        val receipt = ReceiptData(
            headerTitle = "TOKO TEST",
            transactionTitle = "TOKEN PLN",
            status = "BERHASIL",
            details = listOf(ReceiptRow(label = "Token", value = "1234-5678-9012-3456-7890")),
            totalLabel = "TOTAL",
            totalAmount = "Rp 52.500"
        )

        val bytes = EscPosFormatter.buildEscPosBytes(receipt, PaperWidth.MM_58, autoCut = true)
        assertTrue(bytes.size > 20)
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])
    }

    @Test
    fun sanitize_removesUnsupportedUnicode() {
        val cleaned = EscPosFormatter.sanitize("\u201CRp\u00A050.000\u201D \u2014 OK \u2705")
        assertEquals("\"Rp 50.000\" - OK ", cleaned)
    }
}
