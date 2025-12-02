package com.bitchat.android.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bitchat.android.db.AppDatabaseHelper
import com.bitchat.android.printer.PrinterManager
import kotlinx.coroutines.launch

class MerchantAutoPrintIssuesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MerchantAutoPrintIssuesScreen() }
    }
}

@Composable
fun MerchantAutoPrintIssuesScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val whatsappGreen = Color(0xFF25D366)
    val surfaceDark = Color(0xFF1F1F1F)

    data class IssueRow(val orderId: String, val reason: String, val timestamp: Long?)

    var issues by remember { mutableStateOf(listOf<IssueRow>()) }
    var statusMessage by remember { mutableStateOf("") }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    fun parseOrderId(label: String?): String? {
        if (label.isNullOrBlank()) return null
        val idx = label.indexOf("order=")
        if (idx < 0) return null
        val start = idx + 6
        val endPipe = label.indexOf('|', start)
        return if (endPipe >= 0) label.substring(start, endPipe) else label.substring(start)
    }

    fun refresh() {
        val logs = AppDatabaseHelper(context).queryRecentLogs(limit = 500)
        val receiveMap = mutableMapOf<String, Long>()
        logs.filter { it.type == "ws_receive" }.forEach { log ->
            val oid = parseOrderId(log.label)
            if (!oid.isNullOrBlank()) receiveMap[oid] = log.timestamp
        }
        val printedOk = mutableSetOf<String>()
        val missingStationAt = mutableSetOf<String>()
        val noMatchAt = mutableSetOf<String>()

        logs.forEach { log ->
            if (log.type == "ws_print") {
                if (log.success) {
                    // Success: cannot derive order directly; mark globally to exclude if paired later
                } else {
                    if ((log.label ?: "").contains("no_station_printers")) {
                        // Associate with most recent receive order within a short window
                        val nearest = receiveMap.entries.maxByOrNull { it.value ?: 0L }
                        nearest?.let { missingStationAt.add(it.key) }
                    } else if ((log.label ?: "").contains("no_matching_items")) {
                        val nearest = receiveMap.entries.maxByOrNull { it.value ?: 0L }
                        nearest?.let { noMatchAt.add(it.key) }
                    }
                }
            }
            if (log.type == "ws_print" && log.success) {
                // Heuristic: if success shortly after a receive, treat order as printed
                // We can't parse order id from log; exclude all recent candidates to reduce noise
                // Leave issues only when explicit failure markers exist
                // printedOk remains unused; kept for future improvements
            }
        }

        val allIssueIds = (missingStationAt + noMatchAt).toSet()
        val rows = allIssueIds.map { oid ->
            val reason = when {
                missingStationAt.contains(oid) -> "No station printers"
                noMatchAt.contains(oid) -> "No matching items"
                else -> "Auto print failed"
            }
            IssueRow(orderId = oid, reason = reason, timestamp = receiveMap[oid])
        }
        issues = rows.distinctBy { it.orderId }
        // Prune selections for unseen items
        val visibleIds = issues.map { it.orderId }.toSet()
        selected.keys.filter { it !in visibleIds }.forEach { selected.remove(it) }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Auto Print Issues", style = MaterialTheme.typography.titleLarge, color = whatsappGreen, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { refresh() }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Refresh", fontFamily = FontFamily.Monospace) }
            Button(onClick = {
                scope.launch {
                    if (issues.isEmpty()) {
                        statusMessage = "No issues"
                        return@launch
                    }
                    var ok = 0
                    issues.forEach { row ->
                        try { if (PrinterManager.printOrderById(context, row.orderId) > 0) ok++ } catch (_: Throwable) {}
                    }
                    statusMessage = "Retried ${ok}/${issues.size}"
                    refresh()
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Retry All", fontFamily = FontFamily.Monospace) }
            Button(onClick = {
                scope.launch {
                    val targets = issues.filter { selected[it.orderId] == true }
                    if (targets.isEmpty()) { statusMessage = "No orders selected"; return@launch }
                    var ok = 0
                    targets.forEach { row ->
                        try { if (PrinterManager.printOrderById(context, row.orderId) > 0) ok++ } catch (_: Throwable) {}
                    }
                    statusMessage = "Retried ${ok}/${targets.size}"
                    refresh()
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Retry Selected", fontFamily = FontFamily.Monospace) }
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(issues) { row ->
                Surface(color = surfaceDark, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, whatsappGreen), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(text = "Order #${row.orderId}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFCCCCCC), maxLines = 1)
                                Text(text = row.reason, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFA000))
                            }
                            Checkbox(checked = selected[row.orderId] == true, onCheckedChange = { checked -> selected[row.orderId] = checked })
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    val count = PrinterManager.printOrderById(context, row.orderId)
                                    statusMessage = if (count > 0) "Retried 1" else "Retry failed"
                                    refresh()
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Retry", fontFamily = FontFamily.Monospace) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(text = statusMessage, color = Color(0xFFFFEB3B), fontFamily = FontFamily.Monospace)
        }
    }
}

