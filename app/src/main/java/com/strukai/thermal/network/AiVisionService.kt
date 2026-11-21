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

class AiVisionService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun convertScreenshotToReceipt(
        base64Jpeg: String,
        settings: AppSettings
    ): ReceiptData = withContext(Dispatchers.IO) {
        val apiKey = settings.activeApiKey()
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "API Key ${settings.selectedProvider.displayName} belum diisi. Silakan atur di tab Pengaturan."
            )
        }

        val rawResponseText = when (settings.selectedProvider) {
            AiProvider.GEMINI -> callGeminiVision(
                apiKey = apiKey,
                model = settings.activeModel(),
                base64Jpeg = base64Jpeg
            )
            AiProvider.OPENROUTER -> callOpenAiCompatibleVision(
                endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = apiKey,
                model = settings.activeModel(),
                base64Jpeg = base64Jpeg,
                extraHeaders = mapOf(
                    "HTTP-Referer" to "https://github.com/struk-ai-thermal",
                    "X-Title" to "StrukAI Thermal"
                )
            )
            AiProvider.OPENAI -> callOpenAiCompatibleVision(
                endpointUrl = settings.openAiBaseUrl.trim()
                    .ifBlank { "https://api.openai.com/v1/chat/completions" },
                apiKey = apiKey,
                model = settings.activeModel(),
                base64Jpeg = base64Jpeg,
                extraHeaders = emptyMap()
            )
        }

        ReceiptParser.parseAiOutput(rawResponseText, settings)
    }

    private fun callGeminiVision(
        apiKey: String,
        model: String,
        base64Jpeg: String
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val payload = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
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
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.1)
                addProperty("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(extractApiError("Gemini", response.code, bodyString))
            }

            val root = JsonParser.parseString(bodyString).asJsonObject
            val candidates = root.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) {
                throw IOException("Gemini tidak mengembalikan hasil ekstraksi.")
            }
            val parts = candidates[0].asJsonObject
                .getAsJsonObject("content")
                ?.getAsJsonArray("parts")
            val text = parts?.firstOrNull()?.asJsonObject?.get("text")?.asString
            if (text.isNullOrBlank()) {
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
            addProperty("temperature", 0.1)
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
                throw IOException(extractApiError("API Vision", response.code, bodyString))
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
