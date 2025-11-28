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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    LaunchedEffect(Unit) {
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
        Text(
            text = "Merchant Terminal",
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF00E676)
        )

        Spacer(Modifier.height(8.dp))

        // Place action buttons prominently below the header to avoid clipping on narrow screens
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                try { context.startActivity(android.content.Intent(context, MerchantPrintFailuresActivity::class.java)) } catch (_: Exception) { }
            }) { Text("Print Failures") }
            Button(onClick = {
                try { context.startActivity(android.content.Intent(context, MerchantPrintSuccessActivity::class.java)) } catch (_: Exception) { }
            }) { Text("Print Success") }
        }
        Spacer(Modifier.height(8.dp))

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
            Text(text = "Recent Print Logs (latest 100)", color = Color(0xFF80D8FF))
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