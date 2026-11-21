package com.strukai.thermal

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.strukai.thermal.ui.MainTab
import com.strukai.thermal.ui.MainViewModel
import com.strukai.thermal.ui.screens.ConvertScreen
import com.strukai.thermal.ui.screens.EditorPrintScreen
import com.strukai.thermal.ui.screens.SettingsScreen
import com.strukai.thermal.ui.theme.StrukAIThermalTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingShareIntent(intent)

        setContent {
            StrukAIThermalTheme {
                StrukAIApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val extraUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            val clipUri = intent.clipData?.let { clip ->
                if (clip.itemCount > 0) clip.getItemAt(0).uri else null
            }
            val sharedUri = extraUri ?: clipUri
            if (sharedUri != null) {
                viewModel.onImageSelected(sharedUri)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrukAIApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onImageSelected(uri)
        }
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.refreshPairedPrinters()
        }
    }

    fun ensureBluetoothAndRefresh(afterGranted: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val required = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val missing = required.any {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing) {
                btPermissionLauncher.launch(required)
                return
            }
        }
        afterGranted()
    }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val message = uiState.errorMessage ?: uiState.statusMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "StrukAI Thermal",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = uiState.activeTab == MainTab.CONVERT,
                    onClick = { viewModel.selectTab(MainTab.CONVERT) },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    label = { Text(MainTab.CONVERT.title) }
                )
                NavigationBarItem(
                    selected = uiState.activeTab == MainTab.EDITOR,
                    onClick = { viewModel.selectTab(MainTab.EDITOR) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    label = { Text(MainTab.EDITOR.title) }
                )
                NavigationBarItem(
                    selected = uiState.activeTab == MainTab.SETTINGS,
                    onClick = { viewModel.selectTab(MainTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(MainTab.SETTINGS.title) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.activeTab) {
                MainTab.CONVERT -> ConvertScreen(
                    uiState = uiState,
                    onPickImage = { imagePickerLauncher.launch("image/*") },
                    onSelectProvider = viewModel::setProvider,
                    onConvert = viewModel::convertScreenshotWithAi,
                    onOpenSettings = { viewModel.selectTab(MainTab.SETTINGS) },
                    onOpenEditor = { viewModel.selectTab(MainTab.EDITOR) }
                )

                MainTab.EDITOR -> EditorPrintScreen(
                    uiState = uiState,
                    onUpdateReceipt = viewModel::updateReceipt,
                    onAddRow = { viewModel.addDetailRow() },
                    onUpdateRow = viewModel::updateDetailRow,
                    onRemoveRow = viewModel::removeDetailRow,
                    onMoveRow = viewModel::moveDetailRow,
                    onToggleRawMode = viewModel::toggleRawTextMode,
                    onUpdateRawText = viewModel::updateRawReceiptText,
                    onSelectPaperWidth = viewModel::setPaperWidth,
                    onApplyStoreProfile = viewModel::applyDefaultStoreHeaderNow,
                    onRequestPrinterList = {
                        ensureBluetoothAndRefresh { viewModel.refreshPairedPrinters() }
                    },
                    onSelectPrinter = viewModel::selectPrinter,
                    onPrint = {
                        ensureBluetoothAndRefresh { viewModel.printCurrentReceipt() }
                    },
                    onCopyText = { text ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Struk Thermal", text))
                    },
                    onShareText = { text ->
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Bagikan Struk"))
                    }
                )

                MainTab.SETTINGS -> SettingsScreen(
                    uiState = uiState,
                    onUpdateSettings = viewModel::updateSettings,
                    onTestPrint = {
                        ensureBluetoothAndRefresh { viewModel.testPrint() }
                    }
                )
            }
        }
    }
}
