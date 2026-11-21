package com.strukai.thermal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.strukai.thermal.model.AiProvider
import com.strukai.thermal.model.AppSettings
import com.strukai.thermal.model.PaperWidth
import com.strukai.thermal.ui.MainUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onTestPrint: () -> Unit
) {
    val settings = uiState.settings
    val scrollState = rememberScrollState()
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pengaturan AI Vision",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AiProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = settings.selectedProvider == provider,
                            onClick = { onUpdateSettings { it.copy(selectedProvider = provider) } },
                            label = { Text(provider.displayName) }
                        )
                    }
                }

                val activeProvider = settings.selectedProvider
                Text(
                    text = activeProvider.helperNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val currentKey = when (activeProvider) {
                    AiProvider.GEMINI -> settings.geminiApiKey
                    AiProvider.OPENROUTER -> settings.openRouterApiKey
                    AiProvider.OPENAI -> settings.openAiApiKey
                }

                OutlinedTextField(
                    value = currentKey,
                    onValueChange = { value ->
                        onUpdateSettings { s ->
                            when (activeProvider) {
                                AiProvider.GEMINI -> s.copy(geminiApiKey = value)
                                AiProvider.OPENROUTER -> s.copy(openRouterApiKey = value)
                                AiProvider.OPENAI -> s.copy(openAiApiKey = value)
                            }
                        }
                    },
                    label = { Text("API Key ${activeProvider.displayName}") },
                    placeholder = { Text(activeProvider.keyHint) },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Tampilkan/Sembunyikan Key"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                val currentModel = when (activeProvider) {
                    AiProvider.GEMINI -> settings.geminiModel
                    AiProvider.OPENROUTER -> settings.openRouterModel
                    AiProvider.OPENAI -> settings.openAiModel
                }

                OutlinedTextField(
                    value = currentModel,
                    onValueChange = { value ->
                        onUpdateSettings { s ->
                            when (activeProvider) {
                                AiProvider.GEMINI -> s.copy(geminiModel = value)
                                AiProvider.OPENROUTER -> s.copy(openRouterModel = value)
                                AiProvider.OPENAI -> s.copy(openAiModel = value)
                            }
                        }
                    },
                    label = { Text("Model Vision (${activeProvider.displayName})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Preset Model Cepat:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    activeProvider.presetModels.forEach { preset ->
                        FilterChip(
                            selected = currentModel == preset,
                            onClick = {
                                onUpdateSettings { s ->
                                    when (activeProvider) {
                                        AiProvider.GEMINI -> s.copy(geminiModel = preset)
                                        AiProvider.OPENROUTER -> s.copy(openRouterModel = preset)
                                        AiProvider.OPENAI -> s.copy(openAiModel = preset)
                                    }
                                }
                            },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (activeProvider == AiProvider.OPENAI) {
                    OutlinedTextField(
                        value = settings.openAiBaseUrl,
                        onValueChange = { url -> onUpdateSettings { it.copy(openAiBaseUrl = url) } },
                        label = { Text("Endpoint URL (OpenAI / Compatible)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Profil Toko / Agen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = settings.defaultStoreName,
                    onValueChange = { v -> onUpdateSettings { it.copy(defaultStoreName = v) } },
                    label = { Text("Nama Toko / Agen Default") },
                    placeholder = { Text("Contoh: KEDAI SOLUSI") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = settings.defaultStoreSubtitle,
                    onValueChange = { v -> onUpdateSettings { it.copy(defaultStoreSubtitle = v) } },
                    label = { Text("Alamat / No. WhatsApp Default") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = settings.defaultFooterNote,
                    onValueChange = { v -> onUpdateSettings { it.copy(defaultFooterNote = v) } },
                    label = { Text("Catatan Footer Default") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Timpa Header Struk Otomatis",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Gunakan nama & alamat toko Anda saat hasil AI diterima",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.overrideHeaderWithStore,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(overrideHeaderWithStore = checked) }
                        }
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pengaturan Printer Thermal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperWidth.entries.forEach { width ->
                        FilterChip(
                            selected = settings.paperWidth == width,
                            onClick = { onUpdateSettings { it.copy(paperWidth = width) } },
                            label = { Text(width.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Cut Kertas (ESC/POS)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Aktifkan hanya jika printer thermal mendukung pemotong otomatis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoCutPaper,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(autoCutPaper = checked) }
                        }
                    )
                }

                Text(
                    text = "Printer Tersimpan: ${settings.savedPrinterName.ifBlank { "Belum dipilih" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FilledTonalButton(
                    onClick = onTestPrint,
                    enabled = !uiState.isPrinting && settings.savedPrinterAddress.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Print (${settings.paperWidth.mm}mm)")
                }
            }
        }
    }
}
