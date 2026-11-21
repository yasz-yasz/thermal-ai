package com.strukai.thermal.printer

import com.strukai.thermal.model.PaperWidth
import com.strukai.thermal.model.ReceiptData
import com.strukai.thermal.model.ReceiptRow
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object EscPosFormatter {

    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val TEXT_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    private val TEXT_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01)
    private val FEED_LINES = byteArrayOf(0x1B, 0x64, 0x04)
    private val CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    fun buildPlainTextPreview(receipt: ReceiptData, paperWidth: PaperWidth): String {
        val width = paperWidth.maxCharsPerLine
        val divider = "-".repeat(width)
        val doubleDivider = "=".repeat(width)
        val sb = StringBuilder()

        if (receipt.headerTitle.isNotBlank()) {
            wrapText(sanitize(receipt.headerTitle).uppercase(), width).forEach {
                sb.appendLine(centerLine(it, width))
            }
        }
        if (receipt.headerSubtitle.isNotBlank()) {
            receipt.headerSubtitle.lines().forEach { subLine ->
                wrapText(sanitize(subLine), width).forEach {
                    sb.appendLine(centerLine(it, width))
                }
            }
        }

        sb.appendLine(doubleDivider)

        if (receipt.transactionTitle.isNotBlank()) {
            wrapText(sanitize(receipt.transactionTitle).uppercase(), width).forEach {
                sb.appendLine(centerLine(it, width))
            }
        }
        if (receipt.status.isNotBlank()) {
            sb.appendLine(centerLine("[ ${sanitize(receipt.status).uppercase()} ]", width))
        }

        sb.appendLine(divider)

        if (receipt.dateTime.isNotBlank()) {
            formatKeyValue("Tanggal", receipt.dateTime, width).forEach { sb.appendLine(it) }
        }
        if (receipt.referenceNo.isNotBlank()) {
            formatKeyValue("No. Ref", receipt.referenceNo, width).forEach { sb.appendLine(it) }
        }

        if (receipt.dateTime.isNotBlank() || receipt.referenceNo.isNotBlank()) {
            sb.appendLine(divider)
        }

        receipt.details.forEach { row ->
            val cleanLabel = sanitize(row.label)
            val cleanValue = sanitize(row.value)
            if (cleanLabel.isNotBlank() || cleanValue.isNotBlank()) {
                formatKeyValue(cleanLabel, cleanValue, width).forEach { sb.appendLine(it) }
            }
        }

        sb.appendLine(doubleDivider)

        val totalLabel = sanitize(receipt.totalLabel.ifBlank { "TOTAL" }).uppercase()
        val totalAmount = sanitize(receipt.totalAmount)
        formatKeyValue(totalLabel, totalAmount, width).forEach { sb.appendLine(it) }

        sb.appendLine(doubleDivider)

        if (receipt.footerNote.isNotBlank()) {
            receipt.footerNote.lines().forEach { line ->
                wrapText(sanitize(line), width).forEach {
                    sb.appendLine(centerLine(it, width))
                }
            }
        }

        return sb.toString().trimEnd()
    }

    fun buildEscPosBytes(
        receipt: ReceiptData,
        paperWidth: PaperWidth,
        autoCut: Boolean
    ): ByteArray {
        val width = paperWidth.maxCharsPerLine
        val divider = "-".repeat(width)
        val doubleDivider = "=".repeat(width)
        val out = ByteArrayOutputStream()

        out.write(ESC_INIT)

        // Header
        out.write(ALIGN_CENTER)
        if (receipt.headerTitle.isNotBlank()) {
            out.write(BOLD_ON)
            out.write(TEXT_DOUBLE_HEIGHT)
            wrapText(sanitize(receipt.headerTitle).uppercase(), width).forEach { line ->
                out.writeLine(line)
            }
            out.write(TEXT_NORMAL)
            out.write(BOLD_OFF)
        }
        if (receipt.headerSubtitle.isNotBlank()) {
            receipt.headerSubtitle.lines().forEach { sub ->
                wrapText(sanitize(sub), width).forEach { out.writeLine(it) }
            }
        }

        out.write(ALIGN_LEFT)
        out.writeLine(doubleDivider)

        // Title & Status
        out.write(ALIGN_CENTER)
        out.write(BOLD_ON)
        if (receipt.transactionTitle.isNotBlank()) {
            wrapText(sanitize(receipt.transactionTitle).uppercase(), width).forEach { out.writeLine(it) }
        }
        if (receipt.status.isNotBlank()) {
            out.writeLine("[ ${sanitize(receipt.status).uppercase()} ]")
        }
        out.write(BOLD_OFF)

        out.write(ALIGN_LEFT)
        out.writeLine(divider)

        if (receipt.dateTime.isNotBlank()) {
            formatKeyValue("Tanggal", receipt.dateTime, width).forEach { out.writeLine(it) }
        }
        if (receipt.referenceNo.isNotBlank()) {
            formatKeyValue("No. Ref", receipt.referenceNo, width).forEach { out.writeLine(it) }
        }
        if (receipt.dateTime.isNotBlank() || receipt.referenceNo.isNotBlank()) {
            out.writeLine(divider)
        }

        receipt.details.forEach { row ->
            val cleanLabel = sanitize(row.label)
            val cleanValue = sanitize(row.value)
            if (cleanLabel.isNotBlank() || cleanValue.isNotBlank()) {
                formatKeyValue(cleanLabel, cleanValue, width).forEach { out.writeLine(it) }
            }
        }

        out.writeLine(doubleDivider)

        // Total
        out.write(BOLD_ON)
        out.write(TEXT_DOUBLE_HEIGHT)
        val totalLabel = sanitize(receipt.totalLabel.ifBlank { "TOTAL" }).uppercase()
        val totalAmount = sanitize(receipt.totalAmount)
        formatKeyValue(totalLabel, totalAmount, width).forEach { out.writeLine(it) }
        out.write(TEXT_NORMAL)
        out.write(BOLD_OFF)

        out.writeLine(doubleDivider)

        // Footer
        if (receipt.footerNote.isNotBlank()) {
            out.write(ALIGN_CENTER)
            receipt.footerNote.lines().forEach { line ->
                wrapText(sanitize(line), width).forEach { out.writeLine(it) }
            }
            out.write(ALIGN_LEFT)
        }

        out.write(FEED_LINES)
        if (autoCut) {
            out.write(CUT_PARTIAL)
        }

        return out.toByteArray()
    }

    fun buildRawTextEscPosBytes(
        rawText: String,
        paperWidth: PaperWidth,
        autoCut: Boolean
    ): ByteArray {
        val width = paperWidth.maxCharsPerLine
        val out = ByteArrayOutputStream()
        out.write(ESC_INIT)
        out.write(ALIGN_LEFT)
        out.write(TEXT_NORMAL)

        rawText.lines().forEach { line ->
            val clean = sanitize(line)
            if (clean.isEmpty()) {
                out.writeLine("")
            } else if (clean.length <= width) {
                out.writeLine(clean)
            } else {
                wrapText(clean, width).forEach { out.writeLine(it) }
            }
        }

        out.write(FEED_LINES)
        if (autoCut) {
            out.write(CUT_PARTIAL)
        }
        return out.toByteArray()
    }

    fun buildTestPrintBytes(paperWidth: PaperWidth, autoCut: Boolean): ByteArray {
        val sample = ReceiptData(
            headerTitle = "STRUKAI THERMAL",
            headerSubtitle = "Test Koneksi Printer ${paperWidth.mm}mm",
            transactionTitle = "TEST PRINT BERHASIL",
            status = "OK",
            dateTime = "Lebar: ${paperWidth.maxCharsPerLine} karakter",
            referenceNo = "TEST-001",
            details = listOf(
                ReceiptRow(label = "Kertas", value = "${paperWidth.mm} mm"),
                ReceiptRow(label = "Format", value = "ESC/POS")
            ),
            totalLabel = "STATUS",
            totalAmount = "SIAP CETAK",
            footerNote = "Printer thermal terhubung dengan baik."
        )
        return buildEscPosBytes(sample, paperWidth, autoCut)
    }

    fun formatKeyValue(label: String, value: String, width: Int): List<String> {
        val cleanLabel = sanitize(label).trim()
        val cleanValue = sanitize(value).trim()

        if (cleanLabel.isEmpty()) {
            return wrapText(cleanValue, width)
        }
        if (cleanValue.isEmpty()) {
            return wrapText(cleanLabel, width)
        }

        val combinedLen = cleanLabel.length + 1 + cleanValue.length
        if (combinedLen <= width) {
            val spaces = " ".repeat(width - cleanLabel.length - cleanValue.length)
            return listOf("$cleanLabel$spaces$cleanValue")
        }

        val result = mutableListOf<String>()
        result.addAll(wrapText("$cleanLabel:", width))
        wrapText(cleanValue, width).forEach { line ->
            val pad = (width - line.length).coerceAtLeast(0)
            result.add(" ".repeat(pad) + line)
        }
        return result
    }

    fun centerLine(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        val leftPad = (width - text.length) / 2
        return " ".repeat(leftPad) + text
    }

    fun wrapText(text: String, maxWidth: Int): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return listOf("")
        if (trimmed.length <= maxWidth) return listOf(trimmed)

        val words = trimmed.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            if (word.length > maxWidth) {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current = StringBuilder()
                }
                var idx = 0
                while (idx < word.length) {
                    val end = (idx + maxWidth).coerceAtMost(word.length)
                    lines.add(word.substring(idx, end))
                    idx = end
                }
            } else if (current.isEmpty()) {
                current.append(word)
            } else if (current.length + 1 + word.length <= maxWidth) {
                current.append(' ').append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines
    }

    fun sanitize(input: String): String {
        return input
            .replace('\u00A0', ' ')
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u2022', '*')
            .replace(Regex("[^\\x20-\\x7E\\n]"), "")
    }

    private fun ByteArrayOutputStream.writeLine(text: String) {
        write(text.toByteArray(Charset.forName("US-ASCII")))
        write(0x0A)
    }
}
