package com.bitchat.android.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bitchat.android.mesh.BluetoothPermissionManager
import com.bitchat.android.db.AppDatabaseHelper
import com.bitchat.android.db.PrintLog
import com.bitchat.android.printer.DantSuEscPosPrinterAdapter
import com.bitchat.android.printer.EscPosPrinterClient
import com.bitchat.android.printer.PrinterSettingsManager
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MerchantTerminalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MerchantTerminalScreen() }
    }
}

@Composable
fun MerchantTerminalScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // WebSocket removed; background polling handles order prints
    var logs by remember { mutableStateOf<List<PrintLog>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var btPrinters by remember { mutableStateOf<List<BluetoothConnection>>(emptyList()) }
    var selectedBtPrinter by remember { mutableStateOf<BluetoothConnection?>(null) }
    val availableAutoEvents = remember { listOf("paid","printed","ready","order.paid","order.printed","order.ready","order.*","*") }
    var selectedAutoEvents by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savedPrinters by remember { mutableStateOf<List<com.bitchat.android.printer.SavedPrinter>>(emptyList()) }
    val whatsappGreen = Color(0xFF25D366)
    val whatsappDark = Color(0xFF128C7E)
    val surfaceDark = Color(0xFF1F1F1F)

    LaunchedEffect(Unit) {
        selectedAutoEvents = MerchantWebSocketManager.getAutoPrintEvents(context)
        savedPrinters = com.bitchat.android.printer.PrinterSettingsManager(context).getPrinters()
        while (true) {
            logs = AppDatabaseHelper(context).queryRecentLogs(limit = 100)
            delay(2000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Merchant Terminal\nPrinter Status",
                style = MaterialTheme.typography.titleLarge,
                color = whatsappGreen
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            AppDatabaseHelper(context).clearLogs()
                            logs = emptyList()
                            statusMessage = "Logs cleared"
                        } catch (_: Exception) { }
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = whatsappGreen),
                border = BorderStroke(1.dp, whatsappGreen)
            ) { Text("Clear Logs", fontFamily = FontFamily.Monospace) }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            color = surfaceDark,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, whatsappGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(14.dp).background(if (MerchantWebSocketManager.isConnected()) whatsappGreen else Color(0xFFFF5252), RoundedCornerShape(7.dp)))
                    Text(text = "Websocket Connection:", color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace)
                    Text(text = if (MerchantWebSocketManager.isConnected()) "Connected" else "Disconnected", color = Color.White, fontFamily = FontFamily.Monospace)
                }
                var wsEnabled by remember { mutableStateOf(MerchantWebSocketManager.isConnected()) }
                Switch(checked = wsEnabled, onCheckedChange = { checked ->
                    wsEnabled = checked
                    scope.launch {
                        if (checked) MerchantWebSocketManager.start(context) else MerchantWebSocketManager.stop()
                    }
                })
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(text = "Auto Print Events", color = whatsappGreen)
        Spacer(Modifier.height(4.dp))
        Surface(color = surfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, whatsappGreen), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            availableAutoEvents.forEach { opt ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(
                        checked = selectedAutoEvents.contains(opt),
                        onCheckedChange = { checked: Boolean ->
                            selectedAutoEvents = if (checked) selectedAutoEvents + opt else selectedAutoEvents - opt
                        }
                    )
                    Text(text = opt, color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        MerchantWebSocketManager.setAutoPrintEvents(context, selectedAutoEvents)
                        statusMessage = "Auto print updated"
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Save", fontFamily = FontFamily.Monospace) }
                val connected = MerchantWebSocketManager.isConnected()
                Text(text = if (connected) "WS:connected" else "WS:disconnected", color = if (connected) whatsappGreen else Color(0xFFFF5252), fontFamily = FontFamily.Monospace)
            }
        }
        }

        Spacer(Modifier.height(8.dp))

        if (savedPrinters.isNotEmpty()) {
            Text(text = "Per-Printer Auto Print", color = whatsappGreen)
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                savedPrinters.forEach { sp ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val title = (sp.label ?: sp.name ?: "Printer") + " — ${sp.host}:${sp.port}"
                        Text(text = title, color = Color(0xFFCCCCCC), fontFamily = FontFamily.Monospace)
                        var enabled by remember { mutableStateOf(sp.autoPrintEnabled != false) }
                        Switch(checked = enabled, onCheckedChange = { chk ->
                            enabled = chk
                            com.bitchat.android.printer.PrinterSettingsManager(context).setPrinterAutoPrint(sp.id, chk)
                            savedPrinters = com.bitchat.android.printer.PrinterSettingsManager(context).getPrinters()
                        })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        if (statusMessage.isNotEmpty()) {
            Text(text = statusMessage, color = Color(0xFFFFEB3B))
            Spacer(Modifier.height(8.dp))
        }

        // Polling-only: show recent logs below

        if (btPrinters.isNotEmpty()) {
            Text(text = "Bluetooth Printers", color = Color(0xFF80D8FF))
            Spacer(Modifier.height(4.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                items(btPrinters) { conn ->
                    val dev = conn.device
                    val name = try { dev?.name ?: "Unknown" } catch (_: Exception) { "Unknown" }
                    val addr = try { dev?.address ?: "" } catch (_: Exception) { "" }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedBtPrinter = conn }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = "$name (${addr})",
                            color = Color(0xFFCCCCCC),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (selectedBtPrinter != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            try {
                                val dev = selectedBtPrinter!!.device
                                val addr = try { dev?.address ?: "bt" } catch (_: Exception) { "bt" }
                                val name = try { dev?.name } catch (_: Exception) { null }
                                val connected = selectedBtPrinter!!.connect()
                                val printer = EscPosPrinter(connected, 203, 48f, 32)
                                printer.printFormattedText("[C]<b>BT Test</b>\n[C]------------------------------\n[L]Hello from KomBLE.mesh\n")
                                printer.printFormattedText("[C]\n[C]\n")
                                statusMessage = "Bluetooth test printed"
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
                                statusMessage = "BT test failed: ${t.message}"
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
                    }) { Text("Connect & Test Selected") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Text(text = "Recent Print Logs (latest 100)", color = whatsappGreen)
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(logs) { log ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        text = "${log.timestamp} • ${log.label ?: "ESC/POS"} — ${log.host}:${log.port}",
                        color = Color(0xFFAAAAAA),
                        fontFamily = FontFamily.Monospace
                    )
                    val ok = log.success
                    Text(
                        text = "${if (ok) "success" else "failed"} • type=${log.type}",
                        color = if (ok) Color(0xFF00E676) else Color(0xFFFF5252),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
