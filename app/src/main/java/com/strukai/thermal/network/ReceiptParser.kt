package com.strukai.thermal.network

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.strukai.thermal.model.AppSettings
import com.strukai.thermal.model.ReceiptData
import com.strukai.thermal.model.ReceiptRow

object ReceiptParser {

    private val gson = Gson()

    fun parseAiOutput(rawContent: String, settings: AppSettings): ReceiptData {
        val cleaned = extractJsonObject(rawContent)
        val root = runCatching { JsonParser.parseString(cleaned).asJsonObject }.getOrNull()
            ?: return fallbackReceipt(rawContent, settings)

        val aiHeaderTitle = root.stringOrEmpty("headerTitle").ifBlank { "STRUK TRANSAKSI" }
        val aiHeaderSubtitle = root.stringOrEmpty("headerSubtitle")
        val transactionTitle = root.stringOrEmpty("transactionTitle").ifBlank { "BUKTI TRANSAKSI" }
        val status = root.stringOrEmpty("status").ifBlank { "BERHASIL" }
        val dateTime = root.stringOrEmpty("dateTime")
        val referenceNo = root.stringOrEmpty("referenceNo")
        val totalLabel = root.stringOrEmpty("totalLabel").ifBlank { "TOTAL BAYAR" }
        val totalAmount = root.stringOrEmpty("totalAmount").ifBlank { "Rp 0" }
        val aiFooter = root.stringOrEmpty("footerNote")

        val rows = mutableListOf<ReceiptRow>()
        val detailsElement: JsonElement? = root.get("details")
        if (detailsElement != null && detailsElement.isJsonArray) {
            for (item in detailsElement.asJsonArray) {
                if (item.isJsonObject) {
                    val obj = item.asJsonObject
                    val label = obj.stringOrEmpty("label").trim()
                    val value = obj.stringOrEmpty("value").trim()
                    if (label.isNotEmpty() || value.isNotEmpty()) {
                        rows.add(ReceiptRow(label = label, value = value))
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            rows.add(ReceiptRow(label = "Keterangan", value = transactionTitle))
        }

        val finalHeaderTitle = if (settings.overrideHeaderWithStore && settings.defaultStoreName.isNotBlank()) {
            settings.defaultStoreName.trim()
        } else {
            aiHeaderTitle
        }

        val finalHeaderSubtitle = if (settings.overrideHeaderWithStore && settings.defaultStoreSubtitle.isNotBlank()) {
            settings.defaultStoreSubtitle.trim()
        } else if (aiHeaderSubtitle.isNotBlank()) {
            aiHeaderSubtitle
        } else {
            settings.defaultStoreSubtitle.trim()
        }

        val finalFooter = settings.defaultFooterNote.trim()
            .ifBlank { aiFooter.ifBlank { "Terima kasih atas transaksi Anda." } }

        return ReceiptData(
            headerTitle = finalHeaderTitle,
            headerSubtitle = finalHeaderSubtitle,
            transactionTitle = transactionTitle,
            status = status,
            dateTime = dateTime,
            referenceNo = referenceNo,
            details = rows,
            totalLabel = totalLabel,
            totalAmount = totalAmount,
            footerNote = finalFooter
        )
    }

    fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        val withoutFence = trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val start = withoutFence.indexOf('{')
        val end = withoutFence.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            withoutFence.substring(start, end + 1)
        } else {
            withoutFence
        }
    }

    private fun fallbackReceipt(rawContent: String, settings: AppSettings): ReceiptData {
        val lines = rawContent.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(15)

        val rows = if (lines.isEmpty()) {
            listOf(ReceiptRow(label = "Info", value = "Tidak ada data terbaca"))
        } else {
            lines.mapIndexed { index, line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    ReceiptRow(label = parts[0].trim(), value = parts[1].trim())
                } else {
                    ReceiptRow(label = "Baris ${index + 1}", value = line)
                }
            }
        }

        return ReceiptData(
            headerTitle = settings.defaultStoreName.ifBlank { "STRUK TRANSAKSI" },
            headerSubtitle = settings.defaultStoreSubtitle,
            transactionTitle = "HASIL EKSTRAKSI AI",
            status = "BERHASIL",
            details = rows,
            totalLabel = "TOTAL",
            totalAmount = "-",
            footerNote = settings.defaultFooterNote
        )
    }

    private fun JsonObject.stringOrEmpty(key: String): String {
        val element = get(key) ?: return ""
        return if (element.isJsonNull) "" else runCatching { element.asString }.getOrDefault("")
    }

    fun toPrettyJson(receipt: ReceiptData): String = gson.toJson(receipt)
}
