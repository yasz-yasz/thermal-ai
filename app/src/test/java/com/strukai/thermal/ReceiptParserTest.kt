package com.strukai.thermal

import com.strukai.thermal.model.AppSettings
import com.strukai.thermal.network.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    @Test
    fun parseMarkdownFencedJson_extractsFieldsAccurately() {
        val rawAi = """
            ```json
            {
              "headerTitle": "BCA MOBILE",
              "headerSubtitle": "",
              "transactionTitle": "BUKTI TRANSFER",
              "status": "BERHASIL",
              "dateTime": "21/11/2026 08:30",
              "referenceNo": "REF123456",
              "details": [
                {"label": "Penerima", "value": "BUDI SANTOSO"},
                {"label": "Nominal", "value": "Rp 100.000"},
                {"label": "Admin", "value": "Rp 2.500"}
              ],
              "totalLabel": "TOTAL BAYAR",
              "totalAmount": "Rp 102.500",
              "footerNote": "Terima kasih"
            }
            ```
        """.trimIndent()

        val settings = AppSettings(
            defaultStoreName = "AGEN YAS",
            defaultStoreSubtitle = "Pemalang",
            overrideHeaderWithStore = true
        )

        val receipt = ReceiptParser.parseAiOutput(rawAi, settings)

        assertEquals("AGEN YAS", receipt.headerTitle)
        assertEquals("Pemalang", receipt.headerSubtitle)
        assertEquals("BUKTI TRANSFER", receipt.transactionTitle)
        assertEquals("BERHASIL", receipt.status)
        assertEquals("REF123456", receipt.referenceNo)
        assertEquals(3, receipt.details.size)
        assertEquals("Rp 102.500", receipt.totalAmount)
    }

    @Test
    fun parseInvalidJson_returnsFallbackRows() {
        val raw = "Nominal: Rp 50.000\nTujuan: 08123456789"
        val receipt = ReceiptParser.parseAiOutput(raw, AppSettings())

        assertTrue(receipt.details.isNotEmpty())
        assertEquals("Nominal", receipt.details[0].label)
        assertEquals("Rp 50.000", receipt.details[0].value)
    }
}
