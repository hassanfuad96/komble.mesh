package com.bitchat.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
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
                        // Bluetooth printers state
                        var btPrinters by remember { mutableStateOf<List<BluetoothConnection>>(emptyList()) }
                        var selectedBtPrinter by remember { mutableStateOf<BluetoothConnection?>(null) }
                        // Recent logs from polling-only pipeline
                        val recentLogs = remember { mutableStateListOf<PrintLog>() }
                        LaunchedEffect(Unit) {
                            while (true) {
                                val all = AppDatabaseHelper(context).queryRecentLogs(limit = 100)
                                val pollOnly = all.filter { it.type == "poll_print" || it.type == "poll_fetch" }
                                recentLogs.clear()
                                recentLogs.addAll(pollOnly)
                                delay(2000)
                            }
                        }
                        val defaultPrinter = savedPrinters.firstOrNull { it.id == defaultPrinterId }
                        var paperWidth by remember { mutableStateOf((defaultPrinter?.paperWidthMm ?: 80).toString()) }
                        var dotsPerMm by remember { mutableStateOf((defaultPrinter?.dotsPerMm ?: 8).toString()) }
                        var initHex by remember { mutableStateOf(defaultPrinter?.initHex ?: "1B,40") }
                        var cutterHex by remember { mutableStateOf(defaultPrinter?.cutterHex ?: "1D,56,42,00") }
                        var drawerHex by remember { mutableStateOf(defaultPrinter?.drawerHex ?: "1B,70,00,19,FA") }
                        var newLabel by remember { mutableStateOf("") }

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
                                // Saved printers card
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = if (savedPrinters.isNotEmpty()) "Saved Printers" else "No Printer Configured",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (savedPrinters.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    enabled = !isTesting,
                                                    onClick = {
                                                        isTesting = true
                                                        coroutineScope.launch {
                                                            var okCount = 0
                                                            val total = savedPrinters.size
                                                            val db = com.bitchat.android.db.AppDatabaseHelper(context)
                                                            for (sp in savedPrinters) {
                                                                val ok = EscPosPrinterClient().sendTestReceipt(
                                                                    host = sp.host,
                                                                    port = sp.port,
                                                                    initBytes = EscPosUtils.parseHexCsv(sp.initHex ?: initHex),
                                                                    cutterBytes = EscPosUtils.parseHexCsv(sp.cutterHex ?: cutterHex)
                                                                )
                                                                db.insertPrintLog(
                                                                    PrintLog(
                                                                        printerId = sp.id,
                                                                        host = sp.host,
                                                                        port = sp.port,
                                                                        label = sp.label,
                                                                        type = "escpos_test",
                                                                        success = ok
                                                                    )
                                                                )
                                                                if (ok) okCount++
                                                            }
                                                            isTesting = false
                                                            lastTestMessage = "ESC/POS test (all): $okCount/$total succeeded"
                                                        }
                                                    }
                                                ) { Text(if (isTesting) "Testing…" else "ESC/POS Test (All)", fontFamily = FontFamily.Monospace) }
                                                OutlinedButton(
                                                    enabled = !isTesting,
                                                    onClick = {
                                                        isTesting = true
                                                        coroutineScope.launch {
                                                            var okCount = 0
                                                            val total = savedPrinters.size
                                                            val db = com.bitchat.android.db.AppDatabaseHelper(context)
                                                            for (sp in savedPrinters) {
                                                                val ok = try {
                                                                    withTimeout(8000) {
                                                                        if (sp.port == 631) {
                                                                            IppPrintClient().sendTextPage(sp.host, sp.port)
                                                                        } else {
                                                                            JetDirectTextPrinterClient().sendTextPage(
                                                                                host = sp.host,
                                                                                port = sp.port,
                                                                                text = "Bitchat Test Page\r\nMerchant Connected\r\nTime: ${System.currentTimeMillis()}\r\n"
                                                                            )
                                                                        }
                                                                    }
                                                                } catch (_: TimeoutCancellationException) { false }
                                                                db.insertPrintLog(
                                                                    PrintLog(
                                                                        printerId = sp.id,
                                                                        host = sp.host,
                                                                        port = sp.port,
                                                                        label = sp.label,
                                                                        type = if (sp.port == 631) "ipp_text" else "text_test",
                                                                        success = ok
                                                                    )
                                                                )
                                                                if (ok) okCount++
                                                            }
                                                            isTesting = false
                                                            lastTestMessage = "Text test (all): $okCount/$total succeeded"
                                                        }
                                                    }
                                                ) { Text(if (isTesting) "Testing…" else "Text Test (All)", fontFamily = FontFamily.Monospace) }
                                            }
                                        }
                                        if (savedPrinters.isNotEmpty()) {
                                            savedPrinters.forEach { sp ->
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = "${sp.label?.let { "$it — " } ?: ""}${sp.name ?: "ESC/POS"} — ${sp.host}:${sp.port}" + if (sp.id == defaultPrinterId) " • Default" else "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                                    )
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
                                                    var labelText by remember(sp.id) { mutableStateOf(sp.label ?: "") }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        OutlinedTextField(
                                                            value = labelText,
                                                            onValueChange = { labelText = it.take(24) },
                                                            label = { Text("Label") },
                                                            singleLine = true,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        OutlinedButton(onClick = {
                                                            val updated = sp.copy(label = labelText)
                                                            printerSettings.addPrinter(updated)
                                                            val idx = savedPrinters.indexOfFirst { it.id == sp.id }
                                                            if (idx >= 0) savedPrinters[idx] = updated
                                                            if (sp.id == defaultPrinterId) {
                                                                // keep advanced fields aligned with default
                                                                paperWidth = (updated.paperWidthMm ?: 80).toString()
                                                                dotsPerMm = (updated.dotsPerMm ?: 8).toString()
                                                                initHex = updated.initHex ?: "1B,40"
                                                                cutterHex = updated.cutterHex ?: "1D,56,42,00"
                                                                drawerHex = updated.drawerHex ?: "1B,70,00,19,FA"
                                                            }
                                                        }) { Text("Save Label", fontFamily = FontFamily.Monospace) }
                                                    }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        enabled = !isTesting,
                                                        onClick = {
                                                            isTesting = true
                                                                coroutineScope.launch {
                                                                    val ok = EscPosPrinterClient().sendTestReceipt(
                                                                        host = sp.host,
                                                                        port = sp.port,
                                                                        initBytes = EscPosUtils.parseHexCsv(sp.initHex ?: initHex),
                                                                        cutterBytes = EscPosUtils.parseHexCsv(sp.cutterHex ?: cutterHex)
                                                                    )
                                                                    isTesting = false
                                                                    lastTestMessage = if (ok) "ESC/POS test succeeded" else "ESC/POS test failed"
                                                    com.bitchat.android.db.AppDatabaseHelper(context).insertPrintLog(
                                                                        PrintLog(
                                                                            printerId = sp.id,
                                                                            host = sp.host,
                                                                            port = sp.port,
                                                                            label = sp.label,
                                                                            type = "escpos_test",
                                                                            success = ok
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        ) { Text(if (isTesting) "Testing…" else "ESC/POS Test", fontFamily = FontFamily.Monospace) }
                                                        OutlinedButton(
                                                            enabled = !isTesting,
                                                            onClick = {
                                                                isTesting = true
                                                                coroutineScope.launch {
                                                                    val ok = try {
                                                                        withTimeout(8000) {
                                                                            if (sp.port == 631) {
                                                                                IppPrintClient().sendTextPage(
                                                                                    host = sp.host,
                                                                                    port = sp.port
                                                                                )
                                                                            } else {
                                                                                JetDirectTextPrinterClient().sendTextPage(
                                                                                    host = sp.host,
                                                                                    port = sp.port,
                                                                                    text = "Bitchat Test Page\r\nMerchant Connected\r\nTime: ${System.currentTimeMillis()}\r\n"
                                                                                )
                                                                            }
                                                                        }
                                                                    } catch (_: TimeoutCancellationException) {
                                                                        false
                                                                    }
                                                                    isTesting = false
                                                                    lastTestMessage = if (ok) {
                                                                        if (sp.port == 631) "Text print succeeded (IPP)" else "Text print succeeded (JetDirect)"
                                                                    } else "Text print failed"
                                                    com.bitchat.android.db.AppDatabaseHelper(context).insertPrintLog(
                                                                        PrintLog(
                                                                            printerId = sp.id,
                                                                            host = sp.host,
                                                                            port = sp.port,
                                                                            label = sp.label,
                                                                            type = if (sp.port == 631) "ipp_text" else "text_test",
                                                                            success = ok
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        ) { Text(if (isTesting) "Testing…" else "Text Test", fontFamily = FontFamily.Monospace) }
                                                        OutlinedButton(
                                                            onClick = {
                                                                printerSettings.removePrinter(sp.id)
                                                                savedPrinters.removeAll { it.id == sp.id }
                                                                defaultPrinterId = printerSettings.getDefaultPrinter()?.id
                                                            }
                                                        ) { Text("Delete", fontFamily = FontFamily.Monospace) }
                                                        if (sp.id != defaultPrinterId) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    printerSettings.setDefaultPrinterId(sp.id)
                                                                    defaultPrinterId = sp.id
                                                                    // Update advanced defaults to this printer
                                                                    paperWidth = (sp.paperWidthMm ?: 80).toString()
                                                                    dotsPerMm = (sp.dotsPerMm ?: 8).toString()
                                                                    initHex = sp.initHex ?: "1B,40"
                                                                    cutterHex = sp.cutterHex ?: "1D,56,42,00"
                                                                    drawerHex = sp.drawerHex ?: "1B,70,00,19,FA"
                                                                }
                                                            ) { Text("Set Default", fontFamily = FontFamily.Monospace) }
                                                        }
                                                    }
                                                    // Per-printer categories UI
                                                    var showCat by remember(sp.id) { mutableStateOf(false) }
                                                    var categories by remember(sp.id) { mutableStateOf<List<CategoriesApiService.Category>>(emptyList()) }
                                                    var catLoading by remember(sp.id) { mutableStateOf(false) }
                                                    var selectedIds by remember(sp.id) { mutableStateOf(sp.selectedCategoryIds ?: emptyList()) }
                                                    var includeUncat by remember(sp.id) { mutableStateOf(sp.uncategorizedSelected == true) }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        OutlinedButton(onClick = {
                                                            showCat = !showCat
                                                            if (showCat && categories.isEmpty() && !catLoading) {
                                                                catLoading = true
                                                                coroutineScope.launch {
                                                                    val auth = MerchantAuthManager.getInstance(context).getAuthorizationHeader()
                                                                    val list = CategoriesApiService.fetchCategories(auth)
                                                                    categories = list
                                                                    catLoading = false
                                                                }
                                                            }
                                                        }) { Text(if (showCat) "Hide Categories" else "Edit Categories", fontFamily = FontFamily.Monospace) }
                                                        if (showCat) {
                                                            OutlinedButton(onClick = {
                                                                printerSettings.setPrinterCategories(sp.id, selectedIds, includeUncat)
                                                                val idx = savedPrinters.indexOfFirst { it.id == sp.id }
                                                                val updated = sp.copy(selectedCategoryIds = selectedIds, uncategorizedSelected = includeUncat)
                                                                if (idx >= 0) savedPrinters[idx] = updated
                                                                lastTestMessage = "Saved categories for ${sp.label ?: sp.name ?: "ESC/POS"}"
                                                            }) { Text("Save Categories", fontFamily = FontFamily.Monospace) }
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
                                    ) { Text("Scan Wi‑Fi Printers", fontFamily = FontFamily.Monospace) }
                                    OutlinedButton(
                                        enabled = scanning,
                                        onClick = {
                                            scanning = false
                                            discoveryManager.stopDiscovery()
                                        }
                                    ) { Text("Stop Scan", fontFamily = FontFamily.Monospace) }
                                }

                                // Discovered list
                                if (discoveredPrinters.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        discoveredPrinters.take(6).forEach { p ->
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(p.name, style = MaterialTheme.typography.bodyMedium)
                                                        Text("${p.host}:${p.port} • ${p.serviceType}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                                    }
                                                    OutlinedButton(onClick = {
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
                                                        // reflect in UI state
                                                        val idx = savedPrinters.indexOfFirst { it.host == sp.host && it.port == sp.port }
                                                        if (idx >= 0) savedPrinters[idx] = sp else savedPrinters.add(sp)
                                                        defaultPrinterId = printerSettings.getDefaultPrinter()?.id
                                                    }) { Text("Connect", fontFamily = FontFamily.Monospace) }
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
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = manualPortText,
                                        onValueChange = { manualPortText = it.filter { ch -> ch.isDigit() }.take(5) },
                                        label = { Text("Port") },
                                        singleLine = true,
                                        modifier = Modifier.width(120.dp)
                                    )
                                }
                                OutlinedTextField(
                                    value = newLabel,
                                    onValueChange = { newLabel = it.take(24) },
                                    label = { Text("Label (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
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
                                    }
                                ) { Text("Connect Manually", fontFamily = FontFamily.Monospace) }

                                // Advanced settings (Loyverse-style)
                                TextButton(onClick = { showAdvanced = !showAdvanced }) {
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
                                        }
                                    ) { Text("Scan BT Printers", fontFamily = FontFamily.Monospace) }
                                    if (selectedBtPrinter != null) {
                                        OutlinedButton(
                                            onClick = {
                                                if (selectedBtPrinter == null) return@OutlinedButton
                                                coroutineScope.launch {
                                                    try {
                                                        val conn = selectedBtPrinter!!
                                                        val dev = conn.device
                                                        val addr = try { dev?.address ?: "bt" } catch (_: Exception) { "bt" }
                                                        val name = try { dev?.name } catch (_: Exception) { null }
                                                        val connected = conn.connect()
                                                        val printer = EscPosPrinter(connected, 203, 48f, 32)
                                                        printer.printFormattedText("[C]<b>BT Test</b>\n[C]------------------------------\n[L]Hello from KomBLE.mesh\n")
                                                        printer.printFormattedText("[C]\n[C]\n")
                                                        lastTestMessage = "Bluetooth test printed"
                                                        // Auto-save BT printer to the same Saved Printers list (host=MAC, port=0)
                                                        runCatching {
                                                            val sp = SavedPrinter(
                                                                name = name ?: "Bluetooth ESC/POS",
                                                                host = addr,
                                                                port = 0,
                                                                label = (newLabel.ifBlank { null }) ?: (name ?: "Bluetooth ESC/POS"),
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
                                                            lastTestMessage = "Bluetooth test printed • saved printer"
                                                        }
                                                        AppDatabaseHelper(context).insertPrintLog(
                                                            PrintLog(
                                                                printerId = null,
                                                                host = addr,
                                                                port = 0,
                                                                label = name,
                                                                type = "bt_escpos_test",
                                                                success = true
                                                            )
                                                        )
                                                    } catch (t: Throwable) {
                                                        val dev = selectedBtPrinter!!.device
                                                        val addr = try { dev?.address ?: "bt" } catch (_: Exception) { "bt" }
                                                        val name = try { dev?.name } catch (_: Exception) { null }
                                                        lastTestMessage = "BT test failed: ${t.message}"
                                                        AppDatabaseHelper(context).insertPrintLog(
                                                            PrintLog(
                                                                printerId = null,
                                                                host = addr,
                                                                port = 0,
                                                                label = name,
                                                                type = "bt_escpos_test",
                                                                success = false
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        ) { Text("Connect & Test Selected", fontFamily = FontFamily.Monospace) }
                                        OutlinedButton(
                                            onClick = {
                                                val conn = selectedBtPrinter ?: return@OutlinedButton
                                                val dev = conn.device
                                                val addr = try { dev?.address ?: "bt" } catch (_: Exception) { "bt" }
                                                val name = try { dev?.name ?: "Bluetooth ESC/POS" } catch (_: Exception) { "Bluetooth ESC/POS" }
                                                // Save BT printer with MAC address in host and port=0
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
                                                lastTestMessage = "Saved Bluetooth printer: ${sp.label ?: sp.name}"
                                            }
                                        ) { Text("Save Selected BT Printer", fontFamily = FontFamily.Monospace) }
                                    }
                                }
                                if (btPrinters.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        btPrinters.take(6).forEach { conn ->
                                            val dev = conn.device
                                            val name = try { dev?.name ?: "Unknown" } catch (_: Exception) { "Unknown" }
                                            val addr = try { dev?.address ?: "" } catch (_: Exception) { "" }
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                                                shape = RoundedCornerShape(6.dp)
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
                                                    OutlinedButton(onClick = { selectedBtPrinter = conn }) { Text("Select", fontFamily = FontFamily.Monospace) }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Recent Print Logs
                                Text(
                                    text = "Polling Print Logs",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Polling API every 5s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                if (recentLogs.isEmpty()) {
                                    Text(
                                        text = "No polling print logs yet",
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
