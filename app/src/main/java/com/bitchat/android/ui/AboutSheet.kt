package com.bitchat.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Wifi
import android.bluetooth.BluetoothAdapter
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.nostr.NostrProofOfWork
import com.bitchat.android.nostr.PoWPreferenceManager
import com.bitchat.android.ui.debug.DebugSettingsSheet
import androidx.compose.ui.res.stringResource
import com.bitchat.android.R
import com.bitchat.android.merchant.MerchantAuthManager
import com.bitchat.android.merchant.MerchantLoginScreen
import androidx.compose.material.icons.filled.Person
import kotlinx.coroutines.launch
import com.bitchat.android.printer.PrinterDiscoveryManager
import com.bitchat.android.printer.PrinterSettingsManager
import com.bitchat.android.printer.SavedPrinter
import com.bitchat.android.printer.EscPosPrinterClient
import com.bitchat.android.printer.JetDirectTextPrinterClient
import com.bitchat.android.printer.EscPosUtils
import com.bitchat.android.printer.IppPrintClient
import com.bitchat.android.db.AppDatabaseHelper
import com.bitchat.android.db.PrintLog
import com.bitchat.android.mesh.BluetoothPermissionManager
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.EscPosPrinter
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import com.bitchat.android.merchant.CategoriesApiService
/**
 * About Sheet for bitchat app information
 * Matches the design language of LocationChannelsSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    onShowDebug: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { MerchantAuthManager.getInstance(context) }
    var showMerchantLogin by remember { mutableStateOf(false) }
    
    // Get version name from package info
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0" // fallback version
        }
    }
    
    // Bottom sheet state

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newValue ->
            // Prevent dismiss via swipe; only close via Close button
            newValue != SheetValue.Hidden
        }
    )

    val lazyListState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
        }
    }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.95f else 0f,
        label = "topBarAlpha"
    )

    // Color scheme matching LocationChannelsSheet
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
    
    if (isPresented) {
        ModalBottomSheet(
            modifier = modifier.statusBarsPadding(),
            onDismissRequest = {}, // Do not auto-dismiss; use explicit Close button
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = null
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 80.dp, bottom = 20.dp)
                ) {
                    // Header Section
                    item(key = "header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Text(
                                    text = stringResource(R.string.version_prefix, versionName?:""),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onBackground.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        baselineShift = BaselineShift(0.1f)
                                    )
                                )
                            }

                            Text(
                                text = stringResource(R.string.about_tagline),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Features section
                    item(key = "feature_offline") {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = stringResource(R.string.cd_offline_mesh_chat),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.about_offline_mesh_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.about_offline_mesh_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    item(key = "feature_geohash") {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = stringResource(R.string.cd_online_geohash_channels),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.about_online_geohash_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.about_online_geohash_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    item(key = "feature_encryption") {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.cd_end_to_end_encryption),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.about_e2e_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.about_e2e_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Appearance Section
                    item(key = "appearance_section") {
                        Text(
                            text = stringResource(R.string.about_appearance),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp, bottom = 8.dp)
                        )
                        val themePref by com.bitchat.android.ui.theme.ThemePreferenceManager.themeFlow.collectAsState()
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = themePref.isSystem,
                                onClick = { com.bitchat.android.ui.theme.ThemePreferenceManager.set(context, com.bitchat.android.ui.theme.ThemePreference.System) },
                                label = { Text(stringResource(R.string.about_system), fontFamily = FontFamily.Monospace) }
                            )
                            FilterChip(
                                selected = themePref.isLight,
                                onClick = { com.bitchat.android.ui.theme.ThemePreferenceManager.set(context, com.bitchat.android.ui.theme.ThemePreference.Light) },
                                label = { Text(stringResource(R.string.about_light), fontFamily = FontFamily.Monospace) }
                            )
                            FilterChip(
                                selected = themePref.isDark,
                                onClick = { com.bitchat.android.ui.theme.ThemePreferenceManager.set(context, com.bitchat.android.ui.theme.ThemePreference.Dark) },
                                label = { Text(stringResource(R.string.about_dark), fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }
                    // Proof of Work Section
                    item(key = "pow_section") {
                        Text(
                            text = stringResource(R.string.about_pow),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp, bottom = 8.dp)
                        )
                        LaunchedEffect(Unit) {
                            PoWPreferenceManager.init(context)
                        }

                        val powEnabled by PoWPreferenceManager.powEnabled.collectAsState()
                        val powDifficulty by PoWPreferenceManager.powDifficulty.collectAsState()

                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = !powEnabled,
                                    onClick = { PoWPreferenceManager.setPowEnabled(false) },
                                    label = { Text(stringResource(R.string.about_pow_off), fontFamily = FontFamily.Monospace) }
                                )
                                FilterChip(
                                    selected = powEnabled,
                                    onClick = { PoWPreferenceManager.setPowEnabled(true) },
                                    label = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(stringResource(R.string.about_pow_on), fontFamily = FontFamily.Monospace)
                                            // Show current difficulty
                                            if (powEnabled) {
                                                Surface(
                                                    color = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D),
                                                    shape = RoundedCornerShape(50)
                                                ) { Box(Modifier.size(8.dp)) }
                                            }
                                        }
                                    }
                                )
                            }

                            Text(
                                text = stringResource(R.string.about_pow_tip),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            // Show difficulty slider when enabled
                            if (powEnabled) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.about_pow_difficulty, powDifficulty, NostrProofOfWork.estimateMiningTime(powDifficulty)),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )

                                    Slider(
                                        value = powDifficulty.toFloat(),
                                        onValueChange = { PoWPreferenceManager.setPowDifficulty(it.toInt()) },
                                        valueRange = 0f..32f,
                                        steps = 33,
                                        colors = SliderDefaults.colors(
                                            thumbColor = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D),
                                            activeTrackColor = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
                                        )
                                    )

                                    // Show difficulty description
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.about_pow_difficulty_attempts, powDifficulty, NostrProofOfWork.estimateWork(powDifficulty)),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = when {
                                                    powDifficulty == 0 -> stringResource(R.string.about_pow_desc_none)
                                                    powDifficulty <= 8 -> stringResource(R.string.about_pow_desc_very_low)
                                                    powDifficulty <= 12 -> stringResource(R.string.about_pow_desc_low)
                                                    powDifficulty <= 16 -> stringResource(R.string.about_pow_desc_medium)
                                                    powDifficulty <= 20 -> stringResource(R.string.about_pow_desc_high)
                                                    powDifficulty <= 24 -> stringResource(R.string.about_pow_desc_very_high)
                                                    else -> stringResource(R.string.about_pow_desc_extreme)
                                                },
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Network (Tor) section
                    item(key = "network_section") {
                        val torMode = remember { mutableStateOf(com.bitchat.android.net.TorPreferenceManager.get(context)) }
                        val torStatus by com.bitchat.android.net.TorManager.statusFlow.collectAsState()

                        Text(
                            text = stringResource(R.string.about_network),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp, bottom = 8.dp)
                        )

                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = torMode.value == com.bitchat.android.net.TorMode.OFF,
                                    onClick = {
                                        torMode.value = com.bitchat.android.net.TorMode.OFF
                                        com.bitchat.android.net.TorPreferenceManager.set(context, torMode.value)
                                    },
                                    label = { Text("tor off", fontFamily = FontFamily.Monospace) }
                                )

                                FilterChip(
                                    selected = torMode.value == com.bitchat.android.net.TorMode.ON,
                                    onClick = {
                                        torMode.value = com.bitchat.android.net.TorMode.ON
                                        com.bitchat.android.net.TorPreferenceManager.set(context, torMode.value)
                                    },
                                    label = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("tor on", fontFamily = FontFamily.Monospace)
                                            val statusColor = when {
                                                torStatus.running && torStatus.bootstrapPercent < 100 -> Color(0xFFFF9500)
                                                torStatus.running && torStatus.bootstrapPercent >= 100 -> if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
                                                else -> Color.Red
                                            }
                                            Surface(color = statusColor, shape = CircleShape) {
                                                Box(Modifier.size(8.dp))
                                            }
                                        }
                                    }
                                )
                            }

                            Text(
                                text = stringResource(R.string.about_tor_route),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            if (torMode.value == com.bitchat.android.net.TorMode.ON) {
                                val statusText = if (torStatus.running) "Running" else "Stopped"

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.about_tor_status, statusText, torStatus.bootstrapPercent),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurface.copy(alpha = 0.75f)
                                        )

                                        val lastLog = torStatus.lastLogLine
                                        if (lastLog.isNotEmpty()) {
                                            Text(
                                                text = stringResource(R.string.about_last, lastLog.take(160)),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Merchant Login Section (placed after Network section)
                    item(key = "merchant_section") {
                        val isLoggedIn by authManager.isLoggedIn.collectAsState()
                        val currentUser by authManager.currentUser.collectAsState()

                        Text(
                            text = "Merchant Access",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp, bottom = 8.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Merchant Access",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (isLoggedIn && currentUser != null) "Merchant: ${currentUser!!.name}" else "Merchant Login",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isLoggedIn) "Access merchant features and services" else "Login to access merchant features",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                if (isLoggedIn) {
                                    OutlinedButton(
                                        onClick = { authManager.logout() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Logout", fontFamily = FontFamily.Monospace)
                                    }
                                } else {
                                    Button(
                                        onClick = { showMerchantLogin = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Login as Merchant", fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }

                    // Printer Setup Section (visible after merchant login)
                    item(key = "printer_section") {
                        val isLoggedIn by authManager.isLoggedIn.collectAsState()
                        val context = LocalContext.current
                        val discoveryManager = remember { PrinterDiscoveryManager(context) }
                        val printerSettings = remember { PrinterSettingsManager(context) }
                        val savedPrinters = remember { mutableStateListOf<SavedPrinter>().apply { addAll(printerSettings.getPrinters()) } }
                        var defaultPrinterId by remember { mutableStateOf(printerSettings.getDefaultPrinter()?.id) }
                        val discoveredPrinters = remember { mutableStateListOf<com.bitchat.android.printer.DiscoveredPrinter>() }
                        var scanning by remember { mutableStateOf(false) }
                        var manualHost by remember { mutableStateOf("") }
                        var manualPortText by remember { mutableStateOf("9100") }
                        var isTesting by remember { mutableStateOf(false) }
                        val coroutineScope = rememberCoroutineScope()
                        var lastTestMessage by remember { mutableStateOf<String?>(null) }
                        var showAdvanced by remember { mutableStateOf(false) }
                        val availableAutoEvents = remember { listOf("paid","printed","ready","order.paid","order.printed","order.ready","order.*","*") }
                        var selectedAutoEvents by remember { mutableStateOf<Set<String>>(emptySet()) }
                        var wsEnabled by remember { mutableStateOf(com.bitchat.android.merchant.MerchantWebSocketManager.isConnected()) }
                        // Bluetooth printers state
                        var btPrinters by remember { mutableStateOf<List<BluetoothConnection>>(emptyList()) }
                        var selectedBtPrinter by remember { mutableStateOf<BluetoothConnection?>(null) }
                        // Recent logs from polling-only pipeline
                        val recentLogs = remember { mutableStateListOf<PrintLog>() }
                        LaunchedEffect(Unit) {
                            while (true) {
                                val all = AppDatabaseHelper(context).queryRecentLogs(limit = 100)
                                val wsOnly = all.filter { it.type.startsWith("ws_") || it.type == "ws_print" }
                                recentLogs.clear()
                                recentLogs.addAll(wsOnly)
                                delay(2000)
                            }
                        }
                        LaunchedEffect(Unit) {
                            selectedAutoEvents = com.bitchat.android.merchant.MerchantWebSocketManager.getAutoPrintEvents(context)
                        }
                        val defaultPrinter = savedPrinters.firstOrNull { it.id == defaultPrinterId }
                        var paperWidth by remember { mutableStateOf((defaultPrinter?.paperWidthMm ?: 80).toString()) }
                        var dotsPerMm by remember { mutableStateOf((defaultPrinter?.dotsPerMm ?: 8).toString()) }
                        var initHex by remember { mutableStateOf(defaultPrinter?.initHex ?: "1B,40") }
                        var cutterHex by remember { mutableStateOf(defaultPrinter?.cutterHex ?: "1D,56,42,00") }
                        var drawerHex by remember { mutableStateOf(defaultPrinter?.drawerHex ?: "1B,70,00,19,FA") }
                        var newLabel by remember { mutableStateOf("") }
                        var confirmDeleteId by remember { mutableStateOf<String?>(null) }
                        val accentGreen = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)

                        DisposableEffect(Unit) {
                            onDispose { discoveryManager.stopDiscovery() }
                        }

                        // Show Printer Setup for all users (merchant or not)
                        run {
                            Text(
                                text = "Printer Setup",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 12.dp, bottom = 8.dp)
                            )

                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // WebSocket connection status and toggle
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, accentGreen)
                                ) {
                                    Row(
                                        Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(color = if (com.bitchat.android.merchant.MerchantWebSocketManager.isConnected()) accentGreen else Color(0xFFFF5252), shape = CircleShape) { Box(Modifier.size(10.dp)) }
                                            Text(
                                                text = "WebSocket",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (com.bitchat.android.merchant.MerchantWebSocketManager.isConnected()) "Connected" else "Disconnected",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (com.bitchat.android.merchant.MerchantWebSocketManager.isConnected()) accentGreen else Color(0xFFFF5252),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Switch(
                                            checked = wsEnabled,
                                            onCheckedChange = { checked ->
                                                wsEnabled = checked
                                                if (checked) com.bitchat.android.merchant.MerchantWebSocketManager.start(context) else com.bitchat.android.merchant.MerchantWebSocketManager.stop()
                                            }
                                        )
                                    }
                                }
                                // Auto Print Events selection
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, accentGreen)
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(text = "Auto Print Events", style = MaterialTheme.typography.titleSmall)
                                        availableAutoEvents.forEach { opt ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = selectedAutoEvents.contains(opt), onCheckedChange = { checked ->
                                                    selectedAutoEvents = if (checked) selectedAutoEvents + opt else selectedAutoEvents - opt
                                                })
                                                Text(opt, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Button(onClick = {
                                                com.bitchat.android.merchant.MerchantWebSocketManager.setAutoPrintEvents(context, selectedAutoEvents)
                                                lastTestMessage = "Auto print events saved"
                                            }, colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.Black)) { Text("Save", fontFamily = FontFamily.Monospace) }
                                            Text(text = if (com.bitchat.android.merchant.MerchantWebSocketManager.isConnected()) "WS:connected" else "WS:disconnected", color = if (com.bitchat.android.merchant.MerchantWebSocketManager.isConnected()) accentGreen else Color(0xFFFF5252), fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                                // Saved printers card
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, accentGreen)
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = if (savedPrinters.isNotEmpty()) "Saved Printers" else "No Printer Configured",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    try { val i = android.content.Intent(context, com.bitchat.android.merchant.MerchantAutoPrintIssuesActivity::class.java); context.startActivity(i) } catch (_: Exception) {}
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen),
                                                border = BorderStroke(1.dp, accentGreen)
                                            ) { Text("Auto Print Issues", fontFamily = FontFamily.Monospace) }
                                        }
                                        // Removed global tests: ESC/POS Test (All), Text Test (All)
                                        if (savedPrinters.isNotEmpty()) {
                                            savedPrinters.forEach { sp ->
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    val isBt = sp.port == 0 || sp.host.matches(Regex("(?i)^[0-9a-f]{2}(:[0-9a-f]{2}){5}$"))
                                                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(imageVector = if (isBt) Icons.Filled.Bluetooth else Icons.Filled.Wifi, contentDescription = null, tint = accentGreen)
                                                            Text(
                                                                text = "${sp.label?.let { "$it — " } ?: ""}${sp.name ?: if (isBt) "Bluetooth ESC/POS" else "ESC/POS"} — ${sp.host}:${sp.port}" + if (sp.id == defaultPrinterId) " • Default" else "",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        var autoEnabled by remember(sp.id) { mutableStateOf(sp.autoPrintEnabled != false) }
                                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Text("Auto", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                                            Switch(checked = autoEnabled, onCheckedChange = { chk ->
                                                                autoEnabled = chk
                                                                printerSettings.setPrinterAutoPrint(sp.id, chk)
                                                                val idx = savedPrinters.indexOfFirst { it.id == sp.id }
                                                                if (idx >= 0) savedPrinters[idx] = sp.copy(autoPrintEnabled = chk)
                                                            })
                                                        }
                                                    }
                                                    // Role selector: Main vs Station
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        val role = sp.role ?: "station"
                                                        FilterChip(selected = role == "main", onClick = {
                                                            printerSettings.setPrinterRole(sp.id, "main")
                                                            val idx = savedPrinters.indexOfFirst { it.id == sp.id }
                                                            if (idx >= 0) savedPrinters[idx] = sp.copy(role = "main")
                                                        }, label = { Text("Main Printer", fontFamily = FontFamily.Monospace) })
                                                        FilterChip(selected = role != "main", onClick = {
                                                            printerSettings.setPrinterRole(sp.id, "station")
                                                            val idx = savedPrinters.indexOfFirst { it.id == sp.id }
                                                            if (idx >= 0) savedPrinters[idx] = sp.copy(role = "station")
                                                        }, label = { Text("Station Printer", fontFamily = FontFamily.Monospace) })
                                                    }
                                                    // Quick summary of selected categories for this printer
                                                    val hasAll = sp.selectedCategoryIds?.contains(0) == true
                                                    val countSelected = sp.selectedCategoryIds?.filter { it != 0 }?.size ?: 0
                                                    val includeUncatSummary = sp.uncategorizedSelected == true
                                                    val summaryText = when {
                                                        hasAll -> "Categories: All"
                                                        countSelected == 0 && !includeUncatSummary -> "Categories: None"
                                                        else -> {
                                                            val parts = mutableListOf<String>()
                                                            if (countSelected > 0) parts.add("$countSelected selected")
                                                            if (includeUncatSummary) parts.add("Uncategorized")
                                                            "Categories: " + parts.joinToString(", ")
                                                        }
                                                    }
                                                    Text(
                                                        text = summaryText,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                                                        val narrow = maxWidth < 360.dp
                                                        if (narrow) {
                                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                if (isBt) {
                                                                    Button(enabled = !isTesting, onClick = {
                                                                        isTesting = true
                                                                        coroutineScope.launch {
                                                                            try {
                                                                                val list = BluetoothPrintersConnections().getList()?.toList() ?: emptyList()
                                                                                val conn = list.firstOrNull { it.device?.address == sp.host }
                                                                                val connected = conn?.connect() ?: BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(sp.host)?.let { dev -> com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection(dev).connect() }
                                                                                if (connected == null) throw IllegalStateException("BT not found")
                                                                                val printer = EscPosPrinter(connected, 203, 48f, 32)
                                                                                printer.printFormattedText("[C]<b>BT Test</b>\n[C]------------------------------\n[L]Hello from KomBLE.mesh\n")
                                                                                printer.printFormattedText("[C]\n[C]\n")
                                                                                lastTestMessage = "Bluetooth test printed"
                                                                                AppDatabaseHelper(context).insertPrintLog(PrintLog(printerId = sp.id, host = sp.host, port = 0, label = sp.label, type = "bt_escpos_test", success = true))
                                                                            } catch (t: Throwable) {
                                                                                lastTestMessage = "BT test failed: ${t.message}"
                                                                                AppDatabaseHelper(context).insertPrintLog(PrintLog(printerId = sp.id, host = sp.host, port = 0, label = sp.label, type = "bt_escpos_test", success = false))
                                                                            }
                                                                            isTesting = false
                                                                        }
                                                                    }, colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.Black)) { Text(if (isTesting) "Testing…" else "BT Test", fontFamily = FontFamily.Monospace) }
                                                                } else {
                                                                    Button(enabled = !isTesting, onClick = {
                                                                        isTesting = true
                                                                        coroutineScope.launch {
                                                                            val ok = EscPosPrinterClient().sendTestReceipt(host = sp.host, port = sp.port, initBytes = EscPosUtils.parseHexCsv(sp.initHex ?: initHex), cutterBytes = EscPosUtils.parseHexCsv(sp.cutterHex ?: cutterHex))
                                                                            isTesting = false
                                                                            lastTestMessage = if (ok) "ESC/POS test succeeded" else "ESC/POS test failed"
                                                                            AppDatabaseHelper(context).insertPrintLog(PrintLog(printerId = sp.id, host = sp.host, port = sp.port, label = sp.label, type = "escpos_test", success = ok))
                                                                        }
                                                                    }, colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.Black)) { Text(if (isTesting) "Testing…" else "ESC/POS Test", fontFamily = FontFamily.Monospace) }
                                                                }
                                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    OutlinedButton(onClick = { confirmDeleteId = sp.id }, colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen), border = BorderStroke(1.dp, accentGreen)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete"); Text("Delete", fontFamily = FontFamily.Monospace) } }
                                                                    if (sp.id != defaultPrinterId) {
                                                                        OutlinedButton(onClick = {
                                                                            printerSettings.setDefaultPrinterId(sp.id)
                                                                            defaultPrinterId = sp.id
                                                                            paperWidth = (sp.paperWidthMm ?: 80).toString()
                                                                            dotsPerMm = (sp.dotsPerMm ?: 8).toString()
                                                                            initHex = sp.initHex ?: "1B,40"
                                                                            cutterHex = sp.cutterHex ?: "1D,56,42,00"
                                                                            drawerHex = sp.drawerHex ?: "1B,70,00,19,FA"
                                                                        }, colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen), border = BorderStroke(1.dp, accentGreen)) { Text("Set Default", fontFamily = FontFamily.Monospace) }
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                if (isBt) {
                                                                    Button(enabled = !isTesting, onClick = {
                                                                        isTesting = true
                                                                        coroutineScope.launch {
                                                                            try {
                                                                                val list = BluetoothPrintersConnections().getList()?.toList() ?: emptyList()
                                                                                val conn = list.firstOrNull { it.device?.address == sp.host }
                                                                                val connected = conn?.connect() ?: BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(sp.host)?.let { dev -> com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection(dev).connect() }
                                                                                if (connected == null) throw IllegalStateException("BT not found")
                                                                                val printer = EscPosPrinter(connected, 203, 48f, 32)
                                                                                printer.printFormattedText("[C]<b>BT Test</b>\n[C]------------------------------\n[L]Hello from KomBLE.mesh\n")
                                                                                printer.printFormattedText("[C]\n[C]\n")
                                                                                lastTestMessage = "Bluetooth test printed"
                                                                                AppDatabaseHelper(context).insertPrintLog(PrintLog(printerId = sp.id, host = sp.host, port = 0, label = sp.label, type = "bt_escpos_test", success = true))
                                                                            } catch (t: Throwable) {
                                                                                lastTestMessage = "BT test failed: ${t.message}"
                                                                                AppDatabaseHelper(context).insertPrintLog(PrintLog(printerId = sp.id, host = sp.host, port = 0, label = sp.label, type = "bt_escpos_test", success = false))
                                                                            }
                                                                            isTesting = false
                                                                        }
                                                                    }, colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.Black)) { Text(if (isTesting) "Testing…" else "BT Test", fontFamily = FontFamily.Monospace) }
                                                                } else {
                                                                    Button(enabled = !isTesting, onClick = {
                                                                        isTesting = true
                                                                        coroutineScope.launch {
                                                                            val ok = EscPosPrinterClient().sendTestReceipt(host = sp.host, port = sp.port, initBytes = EscPosUtils.parseHexCsv(sp.initHex ?: initHex), cutterBytes = EscPosUtils.parseHexCsv(sp.cutterHex ?: cutterHex))
                                                                            isTesting = false
                                                                            lastTestMessage = if (ok) "ESC/POS test succeeded" else "ESC/POS test failed"
                                                                            AppDatabaseHelper(context).insertPrintLog(PrintLog(printerId = sp.id, host = sp.host, port = sp.port, label = sp.label, type = "escpos_test", success = ok))
                                                                        }
                                                                    }, colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.Black)) { Text(if (isTesting) "Testing…" else "ESC/POS Test", fontFamily = FontFamily.Monospace) }
                                                                }
                                                                OutlinedButton(onClick = { confirmDeleteId = sp.id }, colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen), border = BorderStroke(1.dp, accentGreen)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete"); Text("Delete", fontFamily = FontFamily.Monospace) } }
                                                                if (sp.id != defaultPrinterId) {
                                                                    OutlinedButton(onClick = {
                                                                        printerSettings.setDefaultPrinterId(sp.id)
                                                                        defaultPrinterId = sp.id
                                                                        paperWidth = (sp.paperWidthMm ?: 80).toString()
                                                                        dotsPerMm = (sp.dotsPerMm ?: 8).toString()
                                                                        initHex = sp.initHex ?: "1B,40"
                                                                        cutterHex = sp.cutterHex ?: "1D,56,42,00"
                                                                        drawerHex = sp.drawerHex ?: "1B,70,00,19,FA"
                                                                    }, colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen), border = BorderStroke(1.dp, accentGreen)) { Text("Set Default", fontFamily = FontFamily.Monospace) }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    // Edit IP/Port for existing printer
                                                    var editMode by remember(sp.id) { mutableStateOf(false) }
                                                    var editHost by remember(sp.id) { mutableStateOf(sp.host) }
                                                    var editPortText by remember(sp.id) { mutableStateOf(sp.port.toString()) }
                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        OutlinedButton(onClick = { editMode = !editMode }, colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen), border = BorderStroke(1.dp, accentGreen)) { Text(if (editMode) "Cancel Edit" else "Edit IP/Port", fontFamily = FontFamily.Monospace) }
                                                        if (editMode) {
                                                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                                                val narrow = maxWidth < 360.dp
                                                                if (narrow) {
                                                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                        OutlinedTextField(
                                                                            value = editHost,
                                                                            onValueChange = { editHost = it },
                                                                            label = { Text("IP / Hostname") },
                                                                            singleLine = true,
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            
                                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                                focusedBorderColor = accentGreen,
                                                                                unfocusedBorderColor = accentGreen.copy(alpha = 0.6f),
                                                                                cursorColor = accentGreen,
                                                                                focusedLabelColor = accentGreen
                                                                            )
                                                                        )
                                                                        OutlinedTextField(
                                                                            value = editPortText,
                                                                            onValueChange = { editPortText = it.filter { ch -> ch.isDigit() }.take(5) },
                                                                            label = { Text("Port") },
                                                                            singleLine = true,
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            
                                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                                focusedBorderColor = accentGreen,
                                                                                unfocusedBorderColor = accentGreen.copy(alpha = 0.6f),
                                                                                cursorColor = accentGreen,
                                                                                focusedLabelColor = accentGreen
                                                                            )
                                                                        )
                                                                    }
                                                                } else {
                                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                        OutlinedTextField(
                                                                            value = editHost,
                                                                            onValueChange = { editHost = it },
                                                                            label = { Text("IP / Hostname") },
                                                                            singleLine = true,
                                                                            modifier = Modifier.weight(1f),
                                                                            
                                                                        )
                                                                        OutlinedTextField(
                                                                            value = editPortText,
                                                                            onValueChange = { editPortText = it.filter { ch -> ch.isDigit() }.take(5) },
                                                                            label = { Text("Port") },
                                                                            singleLine = true,
                                                                            modifier = Modifier.width(120.dp),
                                                                            
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                            OutlinedButton(onClick = {
                                                                val newPort = editPortText.toIntOrNull() ?: sp.port
                                                                printerSettings.updatePrinterHostPort(sp.id, editHost.trim(), newPort)
                                                                val idx = savedPrinters.indexOfFirst { it.id == sp.id }
                                                                if (idx >= 0) savedPrinters[idx] = sp.copy(host = editHost.trim(), port = newPort)
                                                                lastTestMessage = "Updated ${sp.label ?: sp.name ?: "Printer"} to ${editHost.trim()}:${newPort}"
                                                                editMode = false
                                                            }, colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen), border = BorderStroke(1.dp, accentGreen)) { Text("Save", fontFamily = FontFamily.Monospace) }
                                                        }
                                                    }
                                                    // Per-printer categories UI
                                                    var showCat by remember(sp.id) { mutableStateOf(false) }
                                                    var categories by remember(sp.id) { mutableStateOf<List<CategoriesApiService.Category>>(CategoriesApiService.getCached()) }
                                                    var catLoading by remember(sp.id) { mutableStateOf(false) }
                                                    var selectedIds by remember(sp.id) { mutableStateOf(sp.selectedCategoryIds ?: emptyList()) }
                                                    var includeUncat by remember(sp.id) { mutableStateOf(sp.uncategorizedSelected == true) }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        OutlinedButton(onClick = {
                                                            showCat = !showCat
                                                            if (showCat && !catLoading) {
                                                                catLoading = true
                                                                coroutineScope.launch {
                                                                    val auth = MerchantAuthManager.getInstance(context).getAuthorizationHeader()
                                                                    val list = CategoriesApiService.fetchCategories(auth)
                                                                    categories = list
                                                                    catLoading = false
                                                                }
                                                            }
                                                        }, colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen), border = BorderStroke(1.dp, accentGreen)) { Text(if (showCat) "Hide Categories" else "Edit Categories", fontFamily = FontFamily.Monospace) }
                                                        if (showCat) {
                                                            OutlinedButton(onClick = {
                                                                printerSettings.setPrinterCategories(sp.id, selectedIds, includeUncat)
                                                                val idx = savedPrinters.indexOfFirst { it.id == sp.id }
                                                                val updated = sp.copy(selectedCategoryIds = selectedIds, uncategorizedSelected = includeUncat)
                                                                if (idx >= 0) savedPrinters[idx] = updated
                                                                lastTestMessage = "Saved categories for ${sp.label ?: sp.name ?: "ESC/POS"}"
                                                            }, colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen), border = BorderStroke(1.dp, accentGreen)) { Text("Save Categories", fontFamily = FontFamily.Monospace) }
                                                        }
                                                    }
                                                    if (showCat) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            if (catLoading) {
                                                                Text("Loading categories…", fontFamily = FontFamily.Monospace)
                                                            } else {
                                                                // All Categories toggle
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    val isAll = selectedIds.contains(0)
                                                                    Checkbox(checked = isAll, onCheckedChange = { checked ->
                                                                        selectedIds = if (checked) listOf(0) else emptyList()
                                                                    })
                                                                    Text("All Categories", fontFamily = FontFamily.Monospace)
                                                                }
                                                                // Uncategorized toggle
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    Checkbox(checked = includeUncat, onCheckedChange = { includeUncat = it })
                                                                    Text("Include Uncategorized", fontFamily = FontFamily.Monospace)
                                                                }
                                                                // Specific categories
                                                                val apiCats = categories.filter { it.id != null }
                                                                apiCats.forEach { cat ->
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                        val checked = selectedIds.contains(cat.id!!)
                                                                        Checkbox(checked = checked && !selectedIds.contains(0), onCheckedChange = { sel ->
                                                                            if (selectedIds.contains(0)) {
                                                                                // If All is selected, unselect it when choosing specifics
                                                                                selectedIds = emptyList()
                                                                            }
                                                                            selectedIds = if (sel) (selectedIds + cat.id!!).distinct() else selectedIds.filter { it != cat.id!! }
                                                                        })
                                                                        Text(cat.name, fontFamily = FontFamily.Monospace)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            // Confirm delete dialog
                                            if (confirmDeleteId != null) {
                                                val delId = confirmDeleteId
                                                val target = savedPrinters.firstOrNull { it.id == delId }
                                                AlertDialog(
                                                    onDismissRequest = { confirmDeleteId = null },
                                                    title = { Text("Remove Printer") },
                                                    text = {
                                                        Text("Delete ${target?.label ?: target?.name ?: "printer"} (${target?.host}:${target?.port})?")
                                                    },
                                                    confirmButton = {
                                                        TextButton(onClick = {
                                                            val id = confirmDeleteId ?: return@TextButton
                                                            printerSettings.removePrinter(id)
                                                            savedPrinters.removeAll { it.id == id }
                                                            confirmDeleteId = null
                                                            defaultPrinterId = printerSettings.getDefaultPrinter()?.id
                                                            val def = printerSettings.getDefaultPrinter()
                                                            if (def != null) {
                                                                paperWidth = (def.paperWidthMm ?: 80).toString()
                                                                dotsPerMm = (def.dotsPerMm ?: 8).toString()
                                                                initHex = def.initHex ?: "1B,40"
                                                                cutterHex = def.cutterHex ?: "1D,56,42,00"
                                                                drawerHex = def.drawerHex ?: "1B,70,00,19,FA"
                                                            } else {
                                                                paperWidth = "80"
                                                                dotsPerMm = "8"
                                                                initHex = "1B,40"
                                                                cutterHex = "1D,56,42,00"
                                                                drawerHex = "1B,70,00,19,FA"
                                                            }
                                                        }) { Text("Delete") }
                                                    },
                                                    dismissButton = {
                                                        TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") }
                                                    }
                                                )
                                            }

                                            lastTestMessage?.let { msg ->
                                                Text(
                                                    text = msg,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Scan controls
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = !scanning,
                                        onClick = {
                                            scanning = true
                                            discoveredPrinters.clear()
                                            discoveryManager.startDiscovery(
                                                onFound = { p ->
                                                    // De-duplicate by host:port
                                                    if (discoveredPrinters.none { it.host == p.host && it.port == p.port }) {
                                                        discoveredPrinters.add(p)
                                                    }
                                                },
                                                onError = { err ->
                                                    viewModel.sendPrivateDMToNickname(
                                                        nickname = viewModel.nickname.value ?: "",
                                                        content = "Printer discovery error: $err"
                                                    )
                                                }
                                            )
                                        }
                                    , colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.Black)
                                    ) { Text("Scan Wi‑Fi Printers", fontFamily = FontFamily.Monospace) }
                                    OutlinedButton(
                                        enabled = scanning,
                                        onClick = {
                                            scanning = false
                                            discoveryManager.stopDiscovery()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen),
                                        border = BorderStroke(1.dp, accentGreen)
                                    ) { Text("Stop Scan", fontFamily = FontFamily.Monospace) }
                                }

                                // Discovered list
                                if (discoveredPrinters.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        discoveredPrinters.take(6).forEach { p ->
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, accentGreen)
                                            ) {
                                                BoxWithConstraints(modifier = Modifier.padding(12.dp)) {
                                                    val narrow = maxWidth < 360.dp
                                                    if (narrow) {
                                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(imageVector = Icons.Filled.Print, contentDescription = null, tint = accentGreen)
                                                                Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                            }
                                                            Text("${p.host}:${p.port} • ${p.serviceType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            OutlinedButton(
                                                                onClick = {
                                                                    val sp = SavedPrinter(
                                                                        name = p.name,
                                                                        host = p.host,
                                                                        port = if (p.port > 0) p.port else 9100,
                                                                        label = newLabel.ifBlank { null },
                                                                        paperWidthMm = paperWidth.toIntOrNull() ?: 80,
                                                                        dotsPerMm = dotsPerMm.toIntOrNull() ?: 8,
                                                                        initHex = initHex,
                                                                        cutterHex = cutterHex,
                                                                        drawerHex = drawerHex
                                                                    )
                                                                    printerSettings.addPrinter(sp, setDefault = savedPrinters.isEmpty())
                                                                    val idx = savedPrinters.indexOfFirst { it.host == sp.host && it.port == sp.port }
                                                                    if (idx >= 0) savedPrinters[idx] = sp else savedPrinters.add(sp)
                                                                    defaultPrinterId = printerSettings.getDefaultPrinter()?.id
                                                                },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen),
                                                                border = BorderStroke(1.dp, accentGreen)
                                                            ) { Text("Connect", fontFamily = FontFamily.Monospace) }
                                                        }
                                                    } else {
                                                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(imageVector = Icons.Filled.Print, contentDescription = null, tint = accentGreen)
                                                                Column(Modifier.weight(1f)) {
                                                                    Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                    Text("${p.host}:${p.port} • ${p.serviceType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                }
                                                            }
                                                            OutlinedButton(
                                                                onClick = {
                                                                    val sp = SavedPrinter(
                                                                        name = p.name,
                                                                        host = p.host,
                                                                        port = if (p.port > 0) p.port else 9100,
                                                                        label = newLabel.ifBlank { null },
                                                                        paperWidthMm = paperWidth.toIntOrNull() ?: 80,
                                                                        dotsPerMm = dotsPerMm.toIntOrNull() ?: 8,
                                                                        initHex = initHex,
                                                                        cutterHex = cutterHex,
                                                                        drawerHex = drawerHex
                                                                    )
                                                                    printerSettings.addPrinter(sp, setDefault = savedPrinters.isEmpty())
                                                                    val idx = savedPrinters.indexOfFirst { it.host == sp.host && it.port == sp.port }
                                                                    if (idx >= 0) savedPrinters[idx] = sp else savedPrinters.add(sp)
                                                                    defaultPrinterId = printerSettings.getDefaultPrinter()?.id
                                                                },
                                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen),
                                                                border = BorderStroke(1.dp, accentGreen)
                                                            ) { Text("Connect", fontFamily = FontFamily.Monospace) }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Manual entry
                                Text(
                                    text = "Manual IP/Port",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = manualHost,
                                        onValueChange = { manualHost = it },
                                        label = { Text("IP / Hostname") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accentGreen,
                                            unfocusedBorderColor = accentGreen.copy(alpha = 0.6f),
                                            cursorColor = accentGreen,
                                            focusedLabelColor = accentGreen
                                        )
                                    )
                                    OutlinedTextField(
                                        value = manualPortText,
                                        onValueChange = { manualPortText = it.filter { ch -> ch.isDigit() }.take(5) },
                                        label = { Text("Port") },
                                        singleLine = true,
                                        modifier = Modifier.width(120.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = accentGreen,
                                            unfocusedBorderColor = accentGreen.copy(alpha = 0.6f),
                                            cursorColor = accentGreen,
                                            focusedLabelColor = accentGreen
                                        )
                                    )
                                }
                                OutlinedTextField(
                                    value = newLabel,
                                    onValueChange = { newLabel = it.take(24) },
                                    label = { Text("Label (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accentGreen,
                                        unfocusedBorderColor = accentGreen.copy(alpha = 0.6f),
                                        cursorColor = accentGreen,
                                        focusedLabelColor = accentGreen
                                    )
                                )
                                Button(
                                    enabled = manualHost.isNotBlank(),
                                    onClick = {
                                        val port = manualPortText.toIntOrNull() ?: 9100
                                        val sp = SavedPrinter(
                                            name = if (port == 631) "Manual IPP" else "Manual ESC/POS",
                                            host = manualHost.trim(),
                                            port = port,
                                            label = newLabel.ifBlank { null },
                                            paperWidthMm = paperWidth.toIntOrNull() ?: 80,
                                            dotsPerMm = dotsPerMm.toIntOrNull() ?: 8,
                                            initHex = initHex,
                                            cutterHex = cutterHex,
                                            drawerHex = drawerHex
                                        )
                                        printerSettings.addPrinter(sp, setDefault = savedPrinters.isEmpty())
                                        val idx = savedPrinters.indexOfFirst { it.host == sp.host && it.port == sp.port }
                                        if (idx >= 0) savedPrinters[idx] = sp else savedPrinters.add(sp)
                                        defaultPrinterId = printerSettings.getDefaultPrinter()?.id
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.Black)
                                ) { Text("Connect Manually", fontFamily = FontFamily.Monospace) }

                                // Advanced settings (Loyverse-style)
                                TextButton(onClick = { showAdvanced = !showAdvanced }, colors = ButtonDefaults.textButtonColors(contentColor = accentGreen)) {
                                    Text(text = if (showAdvanced) "Hide Advanced" else "Show Advanced", fontFamily = FontFamily.Monospace)
                                }
                                if (showAdvanced) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = paperWidth,
                                                    onValueChange = { paperWidth = it.filter { ch -> ch.isDigit() }.take(3) },
                                                    label = { Text("Paper width (mm)") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = dotsPerMm,
                                                    onValueChange = { dotsPerMm = it.filter { ch -> ch.isDigit() }.take(2) },
                                                    label = { Text("Dots/mm") },
                                                    singleLine = true,
                                                    modifier = Modifier.width(120.dp)
                                                )
                                            }
                                            OutlinedTextField(
                                                value = initHex,
                                                onValueChange = { initHex = it },
                                                label = { Text("Initial ESC/POS (hex, comma)") },
                                                singleLine = true,
                                                placeholder = { Text("1B,40") }
                                            )
                                            OutlinedTextField(
                                                value = cutterHex,
                                                onValueChange = { cutterHex = it },
                                                label = { Text("Cutter ESC/POS (hex, comma)") },
                                                singleLine = true,
                                                placeholder = { Text("1D,56,42,00") }
                                            )
                                            OutlinedTextField(
                                                value = drawerHex,
                                                onValueChange = { drawerHex = it },
                                                label = { Text("Drawer ESC/POS (hex, comma)") },
                                                singleLine = true,
                                                placeholder = { Text("1B,70,00,19,FA") }
                                            )
                                        }
                                    }
                                }

                                // Bluetooth Printers
                                Text(
                                    text = "Bluetooth Printers",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            try {
                                                if (!BluetoothPermissionManager(context).hasBluetoothPermissions()) {
                                                    lastTestMessage = "Bluetooth permissions missing"
                                                } else {
                                                    val list = BluetoothPrintersConnections().getList()
                                                    btPrinters = list?.toList() ?: emptyList()
                                                    lastTestMessage = "Found ${btPrinters.size} Bluetooth devices"
                                                }
                                            } catch (e: Exception) {
                                                lastTestMessage = "BT scan error: ${e.message}"
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentGreen, contentColor = Color.Black)
                                    ) { Text("Scan BT Printers", fontFamily = FontFamily.Monospace) }
                                    // Removed global selected actions; each BT row has its own Connect button
                                }
                                if (btPrinters.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                        btPrinters.take(6).forEach { conn ->
                                            val dev = conn.device
                                            val name = try { dev?.name ?: "Unknown" } catch (_: Exception) { "Unknown" }
                                            val addr = try { dev?.address ?: "" } catch (_: Exception) { "" }
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(0.dp),
                                                border = BorderStroke(1.dp, accentGreen)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .padding(8.dp)
                                                        .fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(name, style = MaterialTheme.typography.bodyMedium)
                                                        Text(addr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                                    }
                                                    OutlinedButton(
                                                        onClick = {
                                                            coroutineScope.launch {
                                                                try {
                                                                    val dev = conn.device
                                                                    val addr = try { dev?.address ?: "bt" } catch (_: Exception) { "bt" }
                                                                    val name = try { dev?.name ?: "Bluetooth ESC/POS" } catch (_: Exception) { "Bluetooth ESC/POS" }
                                                                    val connected = withTimeout(8000) { conn.connect() }
                                                                    if (connected == null) throw IllegalStateException("BT not connected")
                                                                    val sp = SavedPrinter(
                                                                        name = name,
                                                                        host = addr,
                                                                        port = 0,
                                                                        label = (newLabel.ifBlank { null }) ?: name,
                                                                        paperWidthMm = paperWidth.toIntOrNull() ?: 80,
                                                                        dotsPerMm = dotsPerMm.toIntOrNull() ?: 8,
                                                                        initHex = initHex,
                                                                        cutterHex = cutterHex,
                                                                        drawerHex = drawerHex
                                                                    )
                                                                    printerSettings.addPrinter(sp, setDefault = savedPrinters.isEmpty())
                                                                    val idx = savedPrinters.indexOfFirst { it.host == sp.host && it.port == sp.port }
                                                                    if (idx >= 0) savedPrinters[idx] = sp else savedPrinters.add(sp)
                                                                    defaultPrinterId = printerSettings.getDefaultPrinter()?.id
                                                                    selectedBtPrinter = conn
                                                                    lastTestMessage = "Bluetooth connected • saved printer: ${sp.label ?: sp.name}"
                                                                } catch (t: Throwable) {
                                                                    selectedBtPrinter = conn
                                                                    lastTestMessage = "BT connect failed: ${t.message}"
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentGreen),
                                                        border = BorderStroke(1.dp, accentGreen)
                                                    ) { Text("Connect", fontFamily = FontFamily.Monospace) }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Recent Print Logs
                                Text(
                                    text = "WebSocket Event Logs",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Realtime events via WebSocket",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                if (recentLogs.isEmpty()) {
                                    Text(
                                        text = "No WebSocket logs yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        recentLogs.take(10).forEach { log ->
                                            val time = try { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp)) } catch (_: Exception) { "" }
                                            val hostPort = "${log.host}:${log.port}"
                                            val okText = if (log.success) "OK" else "FAIL"
                                            val labelText = log.label ?: ""
                                            Text(
                                                text = "[$time] ${log.type} • $okText • ${labelText} • ${hostPort}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Emergency Warning Section
                    item(key = "warning_section") {
                        val colorScheme = MaterialTheme.colorScheme
                        val errorColor = colorScheme.error

                        Surface(
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                                .fillMaxWidth(),
                            color = errorColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = stringResource(R.string.cd_warning),
                                    tint = errorColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stringResource(R.string.about_emergency_title),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = errorColor
                                    )
                                    Text(
                                        text = stringResource(R.string.about_emergency_tip),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // Footer Section
                    item(key = "footer") {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onShowDebug != null) {
                                TextButton(
                                    onClick = onShowDebug,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Text(
                                        text = stringResource(R.string.about_debug_settings),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.about_footer),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )

                            // Add extra space at bottom for gesture area
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                // TopBar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = topBarAlpha))
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.close_plain),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Full-screen Merchant Login dialog overlay
        if (showMerchantLogin) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showMerchantLogin = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    com.bitchat.android.merchant.MerchantLoginScreen(
                        onBackClick = { showMerchantLogin = false },
                        onLoginSuccess = {
                            // Dismiss login UI
                            showMerchantLogin = false

                            // Resolve target nickname from current user phone (without '@')
                            val user = authManager.getCurrentUser()
                            val phoneNickname = user?.phone?.trim()?.removePrefix("@")

                            if (!phoneNickname.isNullOrBlank()) {
                                // Send greeting and orders info
                                coroutineScope.launch {
                                    viewModel.sendPrivateDMToNickname(phoneNickname, "Hi")

                                    // Fetch orders and forward compacted JSON per-order
                                    val authHeader = authManager.getAuthorizationHeader()
                                    val ordersText = com.bitchat.android.merchant.MerchantOrdersApi.fetchOrdersForUser(user!!.id, authHeader)
                                    if (!ordersText.isNullOrBlank()) {
                                        val compact = com.bitchat.android.merchant.MerchantOrderCompactor.compactOrders(ordersText)
                                        if (compact.isNotEmpty()) {
                                            // Send latest first (reverse original order)
                                            for (msg in compact.asReversed()) {
                                                // If any compact JSON is unexpectedly large, break into safe chunks
                                                val maxChunkChars = 400
                                                var i = 0
                                                while (i < msg.length) {
                                                    val end = minOf(i + maxChunkChars, msg.length)
                                                    viewModel.sendPrivateDMToNickname(phoneNickname, msg.substring(i, end))
                                                    i = end
                                                }
                                            }
                                        } else {
                                            // Fallback to plain message if compaction failed
                                            viewModel.sendPrivateDMToNickname(phoneNickname, "No orders data available or compaction failed.")
                                        }
                                    } else {
                                        // Optionally notify if orders fetch failed
                                        viewModel.sendPrivateDMToNickname(phoneNickname, "No orders data available or fetch failed.")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Password prompt dialog for password-protected channels
 * Kept as dialog since it requires user input
 */
@Composable
fun PasswordPromptDialog(
    show: Boolean,
    channelName: String?,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (show && channelName != null) {
        val colorScheme = MaterialTheme.colorScheme
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.pwd_prompt_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.pwd_prompt_message, channelName ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.pwd_label), style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(
                        text = stringResource(R.string.join),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                }
            },
            containerColor = colorScheme.surface,
            tonalElevation = 8.dp
        )
    }
}
