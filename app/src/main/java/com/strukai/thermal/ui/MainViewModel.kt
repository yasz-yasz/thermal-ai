package com.strukai.thermal.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.strukai.thermal.data.ImageEncoder
import com.strukai.thermal.data.SettingsRepository
import com.strukai.thermal.model.AiProvider
import com.strukai.thermal.model.AppSettings
import com.strukai.thermal.model.PaperWidth
import com.strukai.thermal.model.PrinterDeviceInfo
import com.strukai.thermal.model.ReceiptData
import com.strukai.thermal.model.ReceiptRow
import com.strukai.thermal.network.AiVisionService
import com.strukai.thermal.printer.BluetoothPrinterManager
import com.strukai.thermal.printer.EscPosFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab(val title: String) {
    CONVERT("Konversi"),
    EDITOR("Edit & Cetak"),
    SETTINGS("Pengaturan")
}

data class MainUiState(
    val activeTab: MainTab = MainTab.CONVERT,
    val settings: AppSettings = AppSettings(),
    val selectedImageUri: Uri? = null,
    val selectedBitmap: Bitmap? = null,
    val encodedBase64: String? = null,
    val isConverting: Boolean = false,
    val isPrinting: Boolean = false,
    val receipt: ReceiptData = ReceiptData(),
    val hasGeneratedReceipt: Boolean = false,
    val useRawTextEditor: Boolean = false,
    val rawReceiptText: String = "",
    val pairedPrinters: List<PrinterDeviceInfo> = emptyList(),
    val discoveredGeminiModels: List<String> = emptyList(),
    val isLoadingGeminiModels: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
) {
    fun currentPreviewText(): String {
        return if (useRawTextEditor) {
            rawReceiptText
        } else {
            EscPosFormatter.buildPlainTextPreview(receipt, settings.paperWidth)
        }
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val aiVisionService = AiVisionService()
    private val printerManager = BluetoothPrinterManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        val loaded = settingsRepository.loadSettings()
        val initialReceipt = ReceiptData(
            headerTitle = loaded.defaultStoreName.ifBlank { "STRUK TRANSAKSI" },
            headerSubtitle = loaded.defaultStoreSubtitle,
            footerNote = loaded.defaultFooterNote
        )
        _uiState.update {
            it.copy(
                settings = loaded,
                receipt = initialReceipt,
                rawReceiptText = EscPosFormatter.buildPlainTextPreview(initialReceipt, loaded.paperWidth)
            )
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null, statusMessage = "Memuat gambar screenshot...") }
            runCatching {
                ImageEncoder.loadAndEncode(getApplication(), uri)
            }.onSuccess { encoded ->
                _uiState.update {
                    it.copy(
                        selectedImageUri = uri,
                        selectedBitmap = encoded.previewBitmap,
                        encodedBase64 = encoded.base64,
                        activeTab = MainTab.CONVERT,
                        statusMessage = "Screenshot siap dikonversi dengan ${it.settings.selectedProvider.displayName}."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        statusMessage = null,
                        errorMessage = err.message ?: "Gagal memuat gambar."
                    )
                }
            }
        }
    }

    fun convertScreenshotWithAi() {
        val state = _uiState.value
        val base64 = state.encodedBase64
        if (base64.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Pilih gambar screenshot terlebih dahulu.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConverting = true,
                    errorMessage = null,
                    statusMessage = "Menganalisis screenshot dengan ${it.settings.selectedProvider.displayName}..."
                )
            }
            runCatching {
                aiVisionService.convertScreenshotToReceipt(base64, state.settings)
            }.onSuccess { result ->
                val parsedReceipt = result.receipt
                val usedModel = result.resolvedModel
                var updatedSettings = state.settings
                val modelSwitched = state.settings.selectedProvider == AiProvider.GEMINI &&
                    usedModel != state.settings.activeModel()
                if (modelSwitched) {
                    updatedSettings = state.settings.copy(geminiModel = usedModel)
                    settingsRepository.saveSettings(updatedSettings)
                }

                val rawText = EscPosFormatter.buildPlainTextPreview(parsedReceipt, updatedSettings.paperWidth)
                val infoMsg = if (modelSwitched) {
                    "Struk berhasil dibuat (model otomatis diperbarui ke $usedModel)."
                } else {
                    "Struk berhasil dibuat! Silakan periksa atau edit sebelum dicetak."
                }
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        settings = updatedSettings,
                        receipt = parsedReceipt,
                        rawReceiptText = rawText,
                        useRawTextEditor = false,
                        hasGeneratedReceipt = true,
                        activeTab = MainTab.EDITOR,
                        statusMessage = infoMsg
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        statusMessage = null,
                        errorMessage = err.message ?: "Gagal mengonversi screenshot."
                    )
                }
            }
        }
    }

    fun updateReceipt(transform: (ReceiptData) -> ReceiptData) {
        _uiState.update { state ->
            val updated = transform(state.receipt)
            state.copy(
                receipt = updated,
                rawReceiptText = if (state.useRawTextEditor) {
                    state.rawReceiptText
                } else {
                    EscPosFormatter.buildPlainTextPreview(updated, state.settings.paperWidth)
                }
            )
        }
    }

    fun addDetailRow(label: String = "", value: String = "") {
        updateReceipt { receipt ->
            receipt.copy(details = receipt.details + ReceiptRow(label = label, value = value))
        }
    }

    fun updateDetailRow(rowId: String, label: String, value: String) {
        updateReceipt { receipt ->
            receipt.copy(
                details = receipt.details.map { row ->
                    if (row.id == rowId) row.copy(label = label, value = value) else row
                }
            )
        }
    }

    fun removeDetailRow(rowId: String) {
        updateReceipt { receipt ->
            receipt.copy(details = receipt.details.filterNot { it.id == rowId })
        }
    }

    fun moveDetailRow(index: Int, direction: Int) {
        updateReceipt { receipt ->
            val target = index + direction
            if (index !in receipt.details.indices || target !in receipt.details.indices) {
                receipt
            } else {
                val mutable = receipt.details.toMutableList()
                val item = mutable.removeAt(index)
                mutable.add(target, item)
                receipt.copy(details = mutable)
            }
        }
    }

    fun toggleRawTextMode(enableRaw: Boolean) {
        _uiState.update { state ->
            val syncedRaw = if (enableRaw) {
                EscPosFormatter.buildPlainTextPreview(state.receipt, state.settings.paperWidth)
            } else {
                state.rawReceiptText
            }
            state.copy(
                useRawTextEditor = enableRaw,
                rawReceiptText = syncedRaw
            )
        }
    }

    fun updateRawReceiptText(text: String) {
        _uiState.update { it.copy(rawReceiptText = text) }
    }

    fun applyDefaultStoreHeaderNow() {
        val s = _uiState.value.settings
        updateReceipt { receipt ->
            receipt.copy(
                headerTitle = s.defaultStoreName.ifBlank { receipt.headerTitle },
                headerSubtitle = s.defaultStoreSubtitle.ifBlank { receipt.headerSubtitle },
                footerNote = s.defaultFooterNote.ifBlank { receipt.footerNote }
            )
        }
        _uiState.update { it.copy(statusMessage = "Header & footer profil toko diterapkan.") }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _uiState.update { state ->
            val updated = transform(state.settings)
            settingsRepository.saveSettings(updated)
            state.copy(
                settings = updated,
                rawReceiptText = if (state.useRawTextEditor) {
                    state.rawReceiptText
                } else {
                    EscPosFormatter.buildPlainTextPreview(state.receipt, updated.paperWidth)
                }
            )
        }
    }

    fun refreshGeminiModels() {
        val apiKey = _uiState.value.settings.geminiApiKey.trim()
        if (apiKey.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Isi API Key Google Gemini terlebih dahulu.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingGeminiModels = true, errorMessage = null) }
            runCatching {
                aiVisionService.fetchAvailableGeminiModels(apiKey)
            }.onSuccess { models ->
                _uiState.update { state ->
                    val current = state.settings.geminiModel
                    val updatedSettings = if (models.isNotEmpty() && current !in models) {
                        val next = state.settings.copy(geminiModel = models.first())
                        settingsRepository.saveSettings(next)
                        next
                    } else {
                        state.settings
                    }
                    state.copy(
                        isLoadingGeminiModels = false,
                        discoveredGeminiModels = models.take(10),
                        settings = updatedSettings,
                        statusMessage = if (models.isEmpty()) {
                            "Tidak ada model Gemini generateContent ditemukan."
                        } else {
                            "Ditemukan ${models.size} model Gemini aktif."
                        }
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingGeminiModels = false,
                        errorMessage = err.message ?: "Gagal memuat daftar model Gemini."
                    )
                }
            }
        }
    }

    fun setProvider(provider: AiProvider) {
        updateSettings { it.copy(selectedProvider = provider) }
    }

    fun setPaperWidth(width: PaperWidth) {
        updateSettings { it.copy(paperWidth = width) }
    }

    fun refreshPairedPrinters() {
        printerManager.getPairedPrinters()
            .onSuccess { list ->
                _uiState.update {
                    it.copy(
                        pairedPrinters = list,
                        errorMessage = null,
                        statusMessage = if (list.isEmpty()) {
                            "Belum ada perangkat Bluetooth terpasang (paired)."
                        } else {
                            "Ditemukan ${list.size} perangkat Bluetooth terpasang."
                        }
                    )
                }
            }
            .onFailure { err ->
                _uiState.update {
                    it.copy(errorMessage = err.message ?: "Gagal membaca perangkat Bluetooth.")
                }
            }
    }

    fun selectPrinter(printer: PrinterDeviceInfo) {
        updateSettings {
            it.copy(
                savedPrinterName = printer.name,
                savedPrinterAddress = printer.address
            )
        }
        _uiState.update {
            it.copy(statusMessage = "Printer dipilih: ${printer.name}")
        }
    }

    fun printCurrentReceipt() {
        val state = _uiState.value
        val address = state.settings.savedPrinterAddress
        if (address.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Belum ada printer thermal dipilih. Tekan tombol Pilih Printer terlebih dahulu.")
            }
            return
        }

        val payload = if (state.useRawTextEditor) {
            EscPosFormatter.buildRawTextEscPosBytes(
                rawText = state.rawReceiptText,
                paperWidth = state.settings.paperWidth,
                autoCut = state.settings.autoCutPaper
            )
        } else {
            EscPosFormatter.buildEscPosBytes(
                receipt = state.receipt,
                paperWidth = state.settings.paperWidth,
                autoCut = state.settings.autoCutPaper
            )
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPrinting = true,
                    errorMessage = null,
                    statusMessage = "Mengirim struk ke ${state.settings.savedPrinterName}..."
                )
            }
            printerManager.printBytes(address, payload)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isPrinting = false,
                            statusMessage = "Struk berhasil dicetak (${state.settings.paperWidth.mm}mm)!"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isPrinting = false,
                            statusMessage = null,
                            errorMessage = err.message ?: "Gagal mencetak ke printer thermal."
                        )
                    }
                }
        }
    }

    fun testPrint() {
        val state = _uiState.value
        val address = state.settings.savedPrinterAddress
        if (address.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Pilih printer Bluetooth terlebih dahulu.") }
            return
        }
        val payload = EscPosFormatter.buildTestPrintBytes(
            paperWidth = state.settings.paperWidth,
            autoCut = state.settings.autoCutPaper
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(isPrinting = true, errorMessage = null, statusMessage = "Menjalankan Test Print...")
            }
            printerManager.printBytes(address, payload)
                .onSuccess {
                    _uiState.update {
                        it.copy(isPrinting = false, statusMessage = "Test Print berhasil dicetak!")
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isPrinting = false,
                            statusMessage = null,
                            errorMessage = err.message ?: "Test Print gagal."
                        )
                    }
                }
        }
    }
}
