package com.strukai.thermal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strukai.thermal.model.PaperWidth
import com.strukai.thermal.model.PrinterDeviceInfo
import com.strukai.thermal.model.ReceiptData
import com.strukai.thermal.ui.MainUiState

@Composable
fun EditorPrintScreen(
    uiState: MainUiState,
    onUpdateReceipt: ((ReceiptData) -> ReceiptData) -> Unit,
    onAddRow: () -> Unit,
    onUpdateRow: (String, String, String) -> Unit,
    onRemoveRow: (String) -> Unit,
    onMoveRow: (Int, Int) -> Unit,
    onToggleRawMode: (Boolean) -> Unit,
    onUpdateRawText: (String) -> Unit,
    onSelectPaperWidth: (PaperWidth) -> Unit,
    onApplyStoreProfile: () -> Unit,
    onRequestPrinterList: () -> Unit,
    onSelectPrinter: (PrinterDeviceInfo) -> Unit,
    onPrint: () -> Unit,
    onCopyText: (String) -> Unit,
    onShareText: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var showPrinterDialog by remember { mutableStateOf(false) }

    val previewText = uiState.currentPreviewText()
    val receipt = uiState.receipt

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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pratinjau Kertas Thermal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaperWidth.entries.forEach { width ->
                            FilterChip(
                                selected = uiState.settings.paperWidth == width,
                                onClick = { onSelectPaperWidth(width) },
                                label = { Text("${width.mm}mm") }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFFDF7))
                        .border(1.dp, Color(0xFFD6D3C4), RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    Text(
                        text = previewText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = if (uiState.settings.paperWidth == PaperWidth.MM_58) 12.sp else 11.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFF1F2937)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Printer Bluetooth",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = uiState.settings.savedPrinterName.ifBlank { "Belum dipilih" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onRequestPrinterList()
                            showPrinterDialog = true
                        }
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pilih Printer")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPrint,
                        modifier = Modifier.weight(1.4f),
                        enabled = !uiState.isPrinting
                    ) {
                        if (uiState.isPrinting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mencetak...")
                        } else {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cetak ${uiState.settings.paperWidth.mm}mm")
                        }
                    }

                    FilledTonalButton(
                        onClick = { onCopyText(previewText) },
                        modifier = Modifier.weight(0.6f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin", modifier = Modifier.size(18.dp))
                    }

                    FilledTonalButton(
                        onClick = { onShareText(previewText) },
                        modifier = Modifier.weight(0.6f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Bagikan", modifier = Modifier.size(18.dp))
                    }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Struk Sebelum Cetak",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Teks Bebas",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = uiState.useRawTextEditor,
                            onCheckedChange = onToggleRawMode
                        )
                    }
                }

                if (uiState.useRawTextEditor) {
                    Text(
                        text = "Mode Teks Bebas: ubah teks struk secara langsung sesuai keinginan sebelum dicetak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = uiState.rawReceiptText,
                        onValueChange = onUpdateRawText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        label = { Text("Isi Teks Struk") }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onApplyStoreProfile) {
                            Text("Pakai Header/Footer Toko")
                        }
                    }

                    OutlinedTextField(
                        value = receipt.headerTitle,
                        onValueChange = { v -> onUpdateReceipt { it.copy(headerTitle = v) } },
                        label = { Text("Header / Nama Toko") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = receipt.headerSubtitle,
                        onValueChange = { v -> onUpdateReceipt { it.copy(headerSubtitle = v) } },
                        label = { Text("Subjudul / Alamat / No. WA") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = receipt.transactionTitle,
                            onValueChange = { v -> onUpdateReceipt { it.copy(transactionTitle = v) } },
                            label = { Text("Judul Transaksi") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = receipt.status,
                            onValueChange = { v -> onUpdateReceipt { it.copy(status = v) } },
                            label = { Text("Status") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = receipt.dateTime,
                            onValueChange = { v -> onUpdateReceipt { it.copy(dateTime = v) } },
                            label = { Text("Tanggal & Jam") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = receipt.referenceNo,
                            onValueChange = { v -> onUpdateReceipt { it.copy(referenceNo = v) } },
                            label = { Text("No. Referensi") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rincian Baris Struk (${receipt.details.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FilledTonalButton(onClick = onAddRow) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tambah Baris")
                        }
                    }

                    receipt.details.forEachIndexed { index, row ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = row.label,
                                        onValueChange = { newLabel ->
                                            onUpdateRow(row.id, newLabel, row.value)
                                        },
                                        label = { Text("Label") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                    OutlinedTextField(
                                        value = row.value,
                                        onValueChange = { newVal ->
                                            onUpdateRow(row.id, row.label, newVal)
                                        },
                                        label = { Text("Nilai") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(
                                        onClick = { onMoveRow(index, -1) },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Naik")
                                    }
                                    IconButton(
                                        onClick = { onMoveRow(index, 1) },
                                        enabled = index < receipt.details.lastIndex
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Turun")
                                    }
                                    IconButton(onClick = { onRemoveRow(row.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = receipt.totalLabel,
                            onValueChange = { v -> onUpdateReceipt { it.copy(totalLabel = v) } },
                            label = { Text("Label Total") },
                            singleLine = true,
                            modifier = Modifier.weight(0.85f)
                        )
                        OutlinedTextField(
                            value = receipt.totalAmount,
                            onValueChange = { v -> onUpdateReceipt { it.copy(totalAmount = v) } },
                            label = { Text("Nominal Total") },
                            singleLine = true,
                            modifier = Modifier.weight(1.15f)
                        )
                    }

                    OutlinedTextField(
                        value = receipt.footerNote,
                        onValueChange = { v -> onUpdateReceipt { it.copy(footerNote = v) } },
                        label = { Text("Catatan Footer") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showPrinterDialog) {
        AlertDialog(
            onDismissRequest = { showPrinterDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pilih Printer Thermal")
                    IconButton(onClick = onRequestPrinterList) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            },
            text = {
                if (uiState.pairedPrinters.isEmpty()) {
                    Text(
                        "Belum ada perangkat Bluetooth yang terpasang (paired). Pasangkan printer thermal Anda di Pengaturan Bluetooth Android terlebih dahulu, lalu tekan Refresh."
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.pairedPrinters.forEach { device ->
                            val isSelected = device.address == uiState.settings.savedPrinterAddress
                            OutlinedButton(
                                onClick = {
                                    onSelectPrinter(device)
                                    showPrinterDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = if (isSelected) "✓ ${device.name}" else device.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = device.address,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrinterDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}
