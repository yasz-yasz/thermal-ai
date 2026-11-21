package com.strukai.thermal.model

import java.util.UUID

enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val presetModels: List<String>,
    val keyHint: String,
    val helperNote: String
) {
    GEMINI(
        displayName = "Google Gemini",
        defaultModel = "gemini-2.0-flash",
        presetModels = listOf(
            "gemini-2.0-flash",
            "gemini-2.5-flash",
            "gemini-1.5-flash"
        ),
        keyHint = "AIza...",
        helperNote = "Gratis via Google AI Studio (aistudio.google.com)"
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultModel = "google/gemini-2.0-flash-exp:free",
        presetModels = listOf(
            "google/gemini-2.0-flash-exp:free",
            "meta-llama/llama-3.2-11b-vision-instruct:free",
            "qwen/qwen2.5-vl-72b-instruct:free"
        ),
        keyHint = "sk-or-v1-...",
        helperNote = "Banyak model Vision :free di openrouter.ai"
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultModel = "gpt-4o-mini",
        presetModels = listOf(
            "gpt-4o-mini",
            "gpt-4o"
        ),
        keyHint = "sk-...",
        helperNote = "Mendukung OpenAI API atau Base URL OpenAI-compatible"
    )
}

enum class PaperWidth(
    val label: String,
    val mm: Int,
    val maxCharsPerLine: Int
) {
    MM_58("58mm (32 kar)", 58, 32),
    MM_80("80mm (48 kar)", 80, 48)
}

data class ReceiptRow(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val value: String = ""
)

data class ReceiptData(
    val headerTitle: String = "STRUK TRANSAKSI",
    val headerSubtitle: String = "",
    val transactionTitle: String = "BUKTI PEMBAYARAN",
    val status: String = "BERHASIL",
    val dateTime: String = "",
    val referenceNo: String = "",
    val details: List<ReceiptRow> = listOf(
        ReceiptRow(label = "Keterangan", value = "Transaksi")
    ),
    val totalLabel: String = "TOTAL BAYAR",
    val totalAmount: String = "Rp 0",
    val footerNote: String = "Simpan struk ini sebagai bukti transaksi yang sah. Terima kasih!"
)

data class PrinterDeviceInfo(
    val name: String,
    val address: String
)

data class AppSettings(
    val selectedProvider: AiProvider = AiProvider.GEMINI,
    val geminiApiKey: String = "",
    val geminiModel: String = AiProvider.GEMINI.defaultModel,
    val openRouterApiKey: String = "",
    val openRouterModel: String = AiProvider.OPENROUTER.defaultModel,
    val openAiApiKey: String = "",
    val openAiModel: String = AiProvider.OPENAI.defaultModel,
    val openAiBaseUrl: String = "https://api.openai.com/v1/chat/completions",
    val paperWidth: PaperWidth = PaperWidth.MM_58,
    val autoCutPaper: Boolean = false,
    val defaultStoreName: String = "",
    val defaultStoreSubtitle: String = "",
    val defaultFooterNote: String = "Simpan struk ini sebagai bukti transaksi yang sah. Terima kasih!",
    val overrideHeaderWithStore: Boolean = true,
    val savedPrinterName: String = "",
    val savedPrinterAddress: String = ""
) {
    fun activeApiKey(): String = when (selectedProvider) {
        AiProvider.GEMINI -> geminiApiKey.trim()
        AiProvider.OPENROUTER -> openRouterApiKey.trim()
        AiProvider.OPENAI -> openAiApiKey.trim()
    }

    fun activeModel(): String = when (selectedProvider) {
        AiProvider.GEMINI -> geminiModel.trim().ifEmpty { AiProvider.GEMINI.defaultModel }
        AiProvider.OPENROUTER -> openRouterModel.trim().ifEmpty { AiProvider.OPENROUTER.defaultModel }
        AiProvider.OPENAI -> openAiModel.trim().ifEmpty { AiProvider.OPENAI.defaultModel }
    }
}
