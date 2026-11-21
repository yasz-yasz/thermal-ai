package com.strukai.thermal.data

import android.content.Context
import com.strukai.thermal.model.AiProvider
import com.strukai.thermal.model.AppSettings
import com.strukai.thermal.model.PaperWidth

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("struk_ai_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): AppSettings {
        val providerName = prefs.getString(KEY_PROVIDER, AiProvider.GEMINI.name)
        val provider = runCatching { AiProvider.valueOf(providerName ?: AiProvider.GEMINI.name) }
            .getOrDefault(AiProvider.GEMINI)

        val paperName = prefs.getString(KEY_PAPER_WIDTH, PaperWidth.MM_58.name)
        val paperWidth = runCatching { PaperWidth.valueOf(paperName ?: PaperWidth.MM_58.name) }
            .getOrDefault(PaperWidth.MM_58)

        val migrationVersion = prefs.getInt(KEY_MODEL_MIGRATION_VERSION, 0)
        val rawSavedGeminiModel = (prefs.getString(KEY_GEMINI_MODEL, AiProvider.GEMINI.defaultModel)
            ?: AiProvider.GEMINI.defaultModel).trim().removePrefix("models/")

        val migratedGeminiModel = when {
            rawSavedGeminiModel.isBlank() || rawSavedGeminiModel in DEPRECATED_GEMINI_MODELS -> {
                prefs.edit()
                    .putString(KEY_GEMINI_MODEL, AiProvider.GEMINI.defaultModel)
                    .putInt(KEY_MODEL_MIGRATION_VERSION, CURRENT_MODEL_MIGRATION_VERSION)
                    .apply()
                AiProvider.GEMINI.defaultModel
            }
            migrationVersion < CURRENT_MODEL_MIGRATION_VERSION && rawSavedGeminiModel == "gemini-3.8-flash" -> {
                prefs.edit()
                    .putString(KEY_GEMINI_MODEL, AiProvider.GEMINI.defaultModel)
                    .putInt(KEY_MODEL_MIGRATION_VERSION, CURRENT_MODEL_MIGRATION_VERSION)
                    .apply()
                AiProvider.GEMINI.defaultModel
            }
            else -> {
                if (migrationVersion < CURRENT_MODEL_MIGRATION_VERSION) {
                    prefs.edit().putInt(KEY_MODEL_MIGRATION_VERSION, CURRENT_MODEL_MIGRATION_VERSION).apply()
                }
                rawSavedGeminiModel
            }
        }

        val rawSavedOpenRouterModel = (prefs.getString(KEY_OPENROUTER_MODEL, AiProvider.OPENROUTER.defaultModel)
            ?: AiProvider.OPENROUTER.defaultModel).trim()
        val migratedOpenRouterModel = if (rawSavedOpenRouterModel == "google/gemini-2.0-flash-exp:free") {
            prefs.edit().putString(KEY_OPENROUTER_MODEL, AiProvider.OPENROUTER.defaultModel).apply()
            AiProvider.OPENROUTER.defaultModel
        } else {
            rawSavedOpenRouterModel.ifBlank { AiProvider.OPENROUTER.defaultModel }
        }

        return AppSettings(
            selectedProvider = provider,
            geminiApiKey = prefs.getString(KEY_GEMINI_API_KEY, "") ?: "",
            geminiModel = migratedGeminiModel,
            openRouterApiKey = prefs.getString(KEY_OPENROUTER_API_KEY, "") ?: "",
            openRouterModel = migratedOpenRouterModel,
            openAiApiKey = prefs.getString(KEY_OPENAI_API_KEY, "") ?: "",
            openAiModel = prefs.getString(KEY_OPENAI_MODEL, AiProvider.OPENAI.defaultModel)
                ?: AiProvider.OPENAI.defaultModel,
            openAiBaseUrl = prefs.getString(
                KEY_OPENAI_BASE_URL,
                "https://api.openai.com/v1/chat/completions"
            ) ?: "https://api.openai.com/v1/chat/completions",
            paperWidth = paperWidth,
            autoCutPaper = prefs.getBoolean(KEY_AUTO_CUT, false),
            defaultStoreName = prefs.getString(KEY_STORE_NAME, "") ?: "",
            defaultStoreSubtitle = prefs.getString(KEY_STORE_SUBTITLE, "") ?: "",
            defaultFooterNote = prefs.getString(
                KEY_DEFAULT_FOOTER,
                "Simpan struk ini sebagai bukti transaksi yang sah. Terima kasih!"
            ) ?: "Simpan struk ini sebagai bukti transaksi yang sah. Terima kasih!",
            overrideHeaderWithStore = prefs.getBoolean(KEY_OVERRIDE_HEADER, true),
            savedPrinterName = prefs.getString(KEY_PRINTER_NAME, "") ?: "",
            savedPrinterAddress = prefs.getString(KEY_PRINTER_ADDRESS, "") ?: ""
        )
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit()
            .putInt(KEY_MODEL_MIGRATION_VERSION, CURRENT_MODEL_MIGRATION_VERSION)
            .putString(KEY_PROVIDER, settings.selectedProvider.name)
            .putString(KEY_GEMINI_API_KEY, settings.geminiApiKey)
            .putString(KEY_GEMINI_MODEL, settings.geminiModel.trim().removePrefix("models/"))
            .putString(KEY_OPENROUTER_API_KEY, settings.openRouterApiKey)
            .putString(KEY_OPENROUTER_MODEL, settings.openRouterModel.trim())
            .putString(KEY_OPENAI_API_KEY, settings.openAiApiKey)
            .putString(KEY_OPENAI_MODEL, settings.openAiModel.trim())
            .putString(KEY_OPENAI_BASE_URL, settings.openAiBaseUrl.trim())
            .putString(KEY_PAPER_WIDTH, settings.paperWidth.name)
            .putBoolean(KEY_AUTO_CUT, settings.autoCutPaper)
            .putString(KEY_STORE_NAME, settings.defaultStoreName)
            .putString(KEY_STORE_SUBTITLE, settings.defaultStoreSubtitle)
            .putString(KEY_DEFAULT_FOOTER, settings.defaultFooterNote)
            .putBoolean(KEY_OVERRIDE_HEADER, settings.overrideHeaderWithStore)
            .putString(KEY_PRINTER_NAME, settings.savedPrinterName)
            .putString(KEY_PRINTER_ADDRESS, settings.savedPrinterAddress)
            .apply()
    }

    companion object {
        private const val CURRENT_MODEL_MIGRATION_VERSION = 2

        private val DEPRECATED_GEMINI_MODELS = setOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-exp",
            "gemini-2.5-flash",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-pro-vision"
        )

        private const val KEY_MODEL_MIGRATION_VERSION = "model_migration_version"
        private const val KEY_PROVIDER = "selected_provider"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        private const val KEY_OPENROUTER_MODEL = "openrouter_model"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_OPENAI_MODEL = "openai_model"
        private const val KEY_OPENAI_BASE_URL = "openai_base_url"
        private const val KEY_PAPER_WIDTH = "paper_width"
        private const val KEY_AUTO_CUT = "auto_cut"
        private const val KEY_STORE_NAME = "store_name"
        private const val KEY_STORE_SUBTITLE = "store_subtitle"
        private const val KEY_DEFAULT_FOOTER = "default_footer"
        private const val KEY_OVERRIDE_HEADER = "override_header"
        private const val KEY_PRINTER_NAME = "printer_name"
        private const val KEY_PRINTER_ADDRESS = "printer_address"
    }
}
