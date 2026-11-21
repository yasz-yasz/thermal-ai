package com.strukai.thermal.network

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.strukai.thermal.model.AiProvider
import com.strukai.thermal.model.AppSettings
import com.strukai.thermal.model.ReceiptData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ConversionResult(
    val receipt: ReceiptData,
    val resolvedModel: String
)

private class ApiHttpException(
    val code: Int,
    override val message: String
) : IOException(message)

class AiVisionService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun convertScreenshotToReceipt(
        base64Jpeg: String,
        settings: AppSettings
    ): ConversionResult = withContext(Dispatchers.IO) {
        val apiKey = settings.activeApiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "API Key ${settings.selectedProvider.displayName} belum diisi. Silakan atur di tab Pengaturan."
            )
        }

        val initialModel = settings.activeModel()
        val (rawResponseText, usedModel) = when (settings.selectedProvider) {
            AiProvider.GEMINI -> callGeminiWithAutoFallback(
                apiKey = apiKey,
                preferredModel = initialModel,
                base64Jpeg = base64Jpeg
            )
            AiProvider.OPENROUTER -> {
                val text = callOpenAiCompatibleVision(
                    endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                    apiKey = apiKey,
                    model = initialModel,
                    base64Jpeg = base64Jpeg,
                    extraHeaders = mapOf(
                        "HTTP-Referer" to "https://github.com/struk-ai-thermal",
                        "X-Title" to "StrukAI Thermal"
                    )
                )
                text to initialModel
            }
            AiProvider.OPENAI -> {
                val text = callOpenAiCompatibleVision(
                    endpointUrl = settings.openAiBaseUrl.trim()
                        .ifBlank { "https://api.openai.com/v1/chat/completions" },
                    apiKey = apiKey,
                    model = initialModel,
                    base64Jpeg = base64Jpeg,
                    extraHeaders = emptyMap()
                )
                text to initialModel
            }
        }

        ConversionResult(
            receipt = ReceiptParser.parseAiOutput(rawResponseText, settings),
            resolvedModel = usedModel
        )
    }

    suspend fun fetchAvailableGeminiModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            throw IllegalStateException("Isi API Key Google Gemini terlebih dahulu.")
        }
        fetchGeminiModelsBlocking(trimmedKey)
    }

    private fun callGeminiWithAutoFallback(
        apiKey: String,
        preferredModel: String,
        base64Jpeg: String
    ): Pair<String, String> {
        val normalized = normalizeGeminiModel(preferredModel)
        val tried = mutableSetOf<String>()

        try {
            tried.add(normalized)
            return callGeminiVision(apiKey, normalized, base64Jpeg) to normalized
        } catch (firstErr: ApiHttpException) {
            if (firstErr.code != 404) {
                throw firstErr
            }

            // 1. Jika pesan error 404 dari Google menyarankan model pengganti, langsung coba
            val suggested = extractRecommendedGeminiModel(firstErr.message)
            if (!suggested.isNullOrBlank() && suggested !in tried) {
                tried.add(suggested)
                runCatching {
                    return callGeminiVision(apiKey, suggested, base64Jpeg) to suggested
                }
            }

            // 2. Ambil daftar model Gemini aktif dari endpoint /v1beta/models lalu pilih terbaik
            val liveModels = runCatching { fetchGeminiModelsBlocking(apiKey) }.getOrDefault(emptyList())
            val candidates = (liveModels + AiProvider.GEMINI.presetModels)
                .map { normalizeGeminiModel(it) }
                .distinct()
                .filter { it !in tried }

            var lastError: Throwable = firstErr
            for (candidate in candidates.take(4)) {
                tried.add(candidate)
                try {
                    return callGeminiVision(apiKey, candidate, base64Jpeg) to candidate
                } catch (e: ApiHttpException) {
                    lastError = e
                    if (e.code != 404) {
                        throw e
                    }
                }
            }
            throw lastError
        }
    }

    private fun fetchGeminiModelsBlocking(apiKey: String): List<String> {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey&pageSize=100"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiHttpException(response.code, extractApiError("Gemini", response.code, bodyString))
            }

            val root = JsonParser.parseString(bodyString).asJsonObject
            val modelsArray = root.getAsJsonArray("models") ?: return emptyList()

            val supported = mutableListOf<String>()
            for (el in modelsArray) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                val name = obj.get("name")?.asString?.removePrefix("models/")?.trim().orEmpty()
                if (!name.startsWith("gemini-")) continue
                if (name.contains("embedding") || name.contains("tts") || name.contains("imagen") || name.contains("veo") || name.contains("audio")) {
                    continue
                }

                val methods = obj.getAsJsonArray("supportedGenerationMethods")
                val supportsGenerate = methods?.any {
                    it.isJsonPrimitive && it.asString == "generateContent"
                } == true

                if (supportsGenerate) {
                    supported.add(name)
                }
            }

            return rankGeminiVisionModels(supported)
        }
    }

    private fun callGeminiVision(
        apiKey: String,
        model: String,
        base64Jpeg: String
    ): String {
        val cleanModel = normalizeGeminiModel(model)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"
        val payload = buildGeminiRequestPayload(cleanModel, base64Jpeg)

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiHttpException(response.code, extractApiError("Gemini", response.code, bodyString))
            }

            val text = extractGeminiResponseText(bodyString)
            if (text.isBlank()) {
                throw IOException("Respons Gemini kosong.")
            }
            return text
        }
    }

    private fun callOpenAiCompatibleVision(
        endpointUrl: String,
        apiKey: String,
        model: String,
        base64Jpeg: String,
        extraHeaders: Map<String, String>
    ): String {
        val payload = JsonObject().apply {
            addProperty("model", model)
            if (!model.contains("gemini-4", ignoreCase = true)) {
                addProperty("temperature", 0.1)
            }
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("content", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty("text", RECEIPT_EXTRACTION_PROMPT)
                        })
                        add(JsonObject().apply {
                            addProperty("type", "image_url")
                            add("image_url", JsonObject().apply {
                                addProperty("url", "data:image/jpeg;base64,$base64Jpeg")
                            })
                        })
                    })
                })
            })
        }

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .header("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(jsonMediaType))

        extraHeaders.forEach { (k, v) -> requestBuilder.header(k, v) }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiHttpException(response.code, extractApiError("API Vision", response.code, bodyString))
            }

            val root = JsonParser.parseString(bodyString).asJsonObject
            val choices = root.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                throw IOException("Provider AI tidak mengembalikan hasil.")
            }
            val message = choices[0].asJsonObject.getAsJsonObject("message")
            val contentElement = message?.get("content")
            val text = when {
                contentElement == null || contentElement.isJsonNull -> ""
                contentElement.isJsonPrimitive -> contentElement.asString
                contentElement.isJsonArray -> contentElement.asJsonArray
                    .mapNotNull { it.asJsonObject.get("text")?.asString }
                    .joinToString("\n")
                else -> ""
            }
            if (text.isBlank()) {
                throw IOException("Respons teks dari provider AI kosong.")
            }
            return text
        }
    }

    private fun extractApiError(providerLabel: String, code: Int, body: String): String {
        val message = runCatching {
            val json = JsonParser.parseString(body).asJsonObject
            val err = json.get("error")
            when {
                err != null && err.isJsonObject -> err.asJsonObject.get("message")?.asString
                err != null && err.isJsonPrimitive -> err.asString
                else -> null
            }
        }.getOrNull()

        return if (!message.isNullOrBlank()) {
            "$providerLabel Error ($code): $message"
        } else {
            "$providerLabel gagal (HTTP $code). Periksa API Key, kuota, dan nama model."
        }
    }

    companion object {
        fun normalizeGeminiModel(model: String): String {
            return model.trim()
                .removePrefix("models/")
                .ifBlank { AiProvider.GEMINI.defaultModel }
        }

        fun buildGeminiRequestPayload(model: String, base64Jpeg: String): JsonObject {
            val cleanModel = normalizeGeminiModel(model).lowercase()
            val isGemini4 = cleanModel.startsWith("gemini-4")
            val supportsThinkingLevel = isGemini4 || cleanModel.startsWith("gemini-3")

            val generationConfig = JsonObject().apply {
                addProperty("responseMimeType", "application/json")
                if (!isGemini4) {
                    // Gemini 4 mengabaikan/tidak merekomendasikan parameter sampling (temperature/topP/topK)
                    addProperty("temperature", 0.1)
                }
                if (supportsThinkingLevel) {
                    // Gunakan LOW agar konversi OCR struk cepat & hemat token pada Gemini 4 Hydrogen / Gemini 3
                    add("thinkingConfig", JsonObject().apply {
                        addProperty("thinkingLevel", "LOW")
                    })
                }
            }

            return JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("text", RECEIPT_EXTRACTION_PROMPT)
                            })
                            add(JsonObject().apply {
                                add("inline_data", JsonObject().apply {
                                    addProperty("mime_type", "image/jpeg")
                                    addProperty("data", base64Jpeg)
                                })
                            })
                        })
                    })
                })
                add("generationConfig", generationConfig)
            }
        }

        fun extractGeminiResponseText(bodyString: String): String {
            val root = JsonParser.parseString(bodyString).asJsonObject
            val candidates = root.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                throw IOException("Gemini tidak mengembalikan hasil ekstraksi.")
            }
            val parts = candidates[0].asJsonObject
                .getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?: return ""

            val answerParts = mutableListOf<String>()
            val fallbackParts = mutableListOf<String>()
            for (el in parts) {
                if (!el.isJsonObject) continue
                val obj = el.asJsonObject
                val partText = obj.get("text")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                if (partText.isBlank()) continue
                val isThought = obj.get("thought")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
                if (isThought) {
                    fallbackParts.add(partText)
                } else {
                    answerParts.add(partText)
                }
            }

            return if (answerParts.isNotEmpty()) {
                answerParts.joinToString("\n")
            } else {
                fallbackParts.joinToString("\n")
            }
        }

        fun extractRecommendedGeminiModel(errorMessage: String): String? {
            val regex = Regex("use\\s+(?:models/)?(gemini-[a-zA-Z0-9._-]+)", RegexOption.IGNORE_CASE)
            return regex.find(errorMessage)?.groupValues?.getOrNull(1)?.trim()
        }

        fun rankGeminiVisionModels(models: List<String>): List<String> {
            return models.distinct().sortedWith(
                compareByDescending<String> { it.equals("gemini-4-hydrogen", ignoreCase = true) }
                    .thenByDescending { it.contains("hydrogen", ignoreCase = true) }
                    .thenByDescending { extractVersionNumber(it) }
                    .thenByDescending { it.contains("flash", ignoreCase = true) && !it.contains("lite", ignoreCase = true) }
                    .thenByDescending { !it.contains("exp", ignoreCase = true) && !it.contains("preview", ignoreCase = true) }
                    .thenBy { it.length }
            )
        }

        private fun extractVersionNumber(modelName: String): Double {
            val match = Regex("gemini-(\\d+(?:\\.\\d+)?)").find(modelName)
            return match?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        }

        private val RECEIPT_EXTRACTION_PROMPT = """
            Kamu adalah asisten OCR khusus untuk mengonversi screenshot bukti transaksi (transfer bank, e-wallet, QRIS, pulsa, token PLN, tagihan PPOB, nota belanja) menjadi data struk printer thermal yang rapi dalam bahasa Indonesia.

            Tugas:
            Baca semua informasi penting pada gambar screenshot lalu kembalikan HANYA JSON valid (tanpa penjelasan tambahan) dengan skema berikut:
            {
              "headerTitle": "Nama bank/aplikasi/merchant atau jenis layanan utama",
              "headerSubtitle": "Subjudul singkat jika ada, atau string kosong",
              "transactionTitle": "Judul singkat jenis transaksi, misal: BUKTI TRANSFER, TOKEN PLN, TOP UP DANA, PEMBAYARAN QRIS",
              "status": "Status transaksi, misal: BERHASIL / LUNAS / PENDING",
              "dateTime": "Tanggal dan jam transaksi persis seperti di gambar",
              "referenceNo": "Nomor referensi / No Resi / ID Transaksi jika ada",
              "details": [
                {"label": "Nama kolom ringkas", "value": "Isi nilai akurat"}
              ],
              "totalLabel": "TOTAL BAYAR",
              "totalAmount": "Nominal total lengkap dengan mata uang, misal: Rp 102.500",
              "footerNote": "Catatan penutup singkat untuk pelanggan"
            }

            Aturan ketat:
            1. Jangan mengarang angka, nomor rekening, nomor HP, ID pelanggan, atau token/SN. Salin 100% akurat.
            2. Jika ada nomor token PLN / SN / kode voucher, pastikan masuk di array details dengan label jelas (misal "Token" atau "SN").
            3. Pisahkan nominal transaksi dan biaya admin (jika terlihat) di dalam array details.
            4. Buat label ringkas (maksimal 14 karakter jika memungkinkan) agar rapi pada kertas thermal 58mm.
            5. Abaikan elemen UI HP seperti jam status bar, baterai, sinyal, dan tombol navigasi.
        """.trimIndent()
    }
}
