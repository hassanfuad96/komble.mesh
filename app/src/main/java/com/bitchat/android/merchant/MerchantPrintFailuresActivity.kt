package com.bitchat.android.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bitchat.android.db.AppDatabaseHelper
import com.bitchat.android.printer.PrinterManager
import kotlinx.coroutines.launch

class MerchantPrintFailuresActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MerchantPrintFailuresScreen() }
    }
}

@Composable
fun MerchantPrintFailuresScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var failures by remember { mutableStateOf(listOf<AppDatabaseHelper.OrderRow>()) }
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    var statusMessage by remember { mutableStateOf("") }
    val whatsappGreen = Color(0xFF25D366)
    val surfaceDark = Color(0xFF1F1F1F)

    fun refresh() {
        // Include both explicit print failures and orders that are paid (awaiting print)
        failures = AppDatabaseHelper(context).getOrdersByStatuses(listOf("print_failed", "Paid", "paid"), 200)
        val visibleIds = failures.map { it.orderId }.toSet()
        val toRemove = selected.keys.filter { it !in visibleIds }
        toRemove.forEach { selected.remove(it) }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF111111)).padding(16.dp)) {
        Text(
            text = "Print Failures",
            style = MaterialTheme.typography.titleLarge,
            color = whatsappGreen,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 360.dp
            if (narrow) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { refresh() }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black), modifier = Modifier.fillMaxWidth()) { Text("Refresh", fontFamily = FontFamily.Monospace, maxLines = 1) }
                    Button(onClick = {
                        scope.launch {
                            failures.forEach { row ->
                                try {
                                    val printed = PrinterManager.printOrderById(context, row.orderId)
                                    if (printed > 0) {
                                        val auth = MerchantAuthManager.getInstance(context).getAuthorizationHeader()
                                        MerchantOrderStatusApi.markPrinted(row.orderId, auth)
                                    }
                                } catch (_: Exception) { }
                            }
                            refresh()
                            statusMessage = "Retried ${failures.size}"
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black), modifier = Modifier.fillMaxWidth()) { Text("Retry All", fontFamily = FontFamily.Monospace, maxLines = 1) }
                    Button(onClick = {
                        scope.launch {
                            val targets = failures.filter { selected[it.orderId] == true }
                            if (targets.isEmpty()) {
                                statusMessage = "No orders selected"
                            } else {
                                var okCount = 0
                                targets.forEach { row ->
                                    try {
                                        val printed = PrinterManager.printOrderById(context, row.orderId)
                                        if (printed > 0) {
                                            okCount += 1
                                            val auth = MerchantAuthManager.getInstance(context).getAuthorizationHeader()
                                            MerchantOrderStatusApi.markPrinted(row.orderId, auth)
                                        }
                                    } catch (_: Exception) { }
                                }
                                statusMessage = "Reprinted ${okCount}/${targets.size}"
                            }
                            refresh()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black), modifier = Modifier.fillMaxWidth()) { Text("Retry Selected", fontFamily = FontFamily.Monospace, maxLines = 1) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { refresh() }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Refresh", fontFamily = FontFamily.Monospace, maxLines = 1) }
                    Button(onClick = {
                        scope.launch {
                            failures.forEach { row ->
                                try {
                                    val printed = PrinterManager.printOrderById(context, row.orderId)
                                    if (printed > 0) {
                                        val auth = MerchantAuthManager.getInstance(context).getAuthorizationHeader()
                                        MerchantOrderStatusApi.markPrinted(row.orderId, auth)
                                    }
                                } catch (_: Exception) { }
                            }
                            refresh()
                            statusMessage = "Retried ${failures.size}"
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Retry All", fontFamily = FontFamily.Monospace, maxLines = 1) }
                    Button(onClick = {
                        scope.launch {
                            val targets = failures.filter { selected[it.orderId] == true }
                            if (targets.isEmpty()) {
                                statusMessage = "No orders selected"
                            } else {
                                var okCount = 0
                                targets.forEach { row ->
                                    try {
                                        val printed = PrinterManager.printOrderById(context, row.orderId)
                                        if (printed > 0) {
                                            okCount += 1
                                            val authMgr = MerchantAuthManager.getInstance(context)
                                            val auth = authMgr.getAuthorizationHeader()
                                            val userId = authMgr.getCurrentUser()?.id
                                            MerchantOrderStatusApi.markPrinted(row.orderId, auth, userId)
                                        }
                                    } catch (_: Exception) { }
                                }
                                statusMessage = "Reprinted ${okCount}/${targets.size}"
                            }
                            refresh()
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Retry Selected", fontFamily = FontFamily.Monospace, maxLines = 1) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(failures) { row ->
                Surface(
                    color = surfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, whatsappGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BoxWithConstraints(Modifier.padding(12.dp).fillMaxWidth()) {
                        val narrow = maxWidth < 360.dp
                        if (narrow) {
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(imageVector = Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252))
                                            Text(text = "Order #${row.orderId}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFCCCCCC), maxLines = 1)
                                        }
                                        val created = row.createdAt ?: ""
                                        Text(text = created, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAAAAA), maxLines = 1)
                                    }
                                    Checkbox(
                                        checked = selected[row.orderId] == true,
                                        onCheckedChange = { checked -> selected[row.orderId] = checked }
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        scope.launch {
                                            val count = PrinterManager.printOrderById(context, row.orderId)
                                            if (count > 0) {
                                                val authMgr = MerchantAuthManager.getInstance(context)
                                                val auth = authMgr.getAuthorizationHeader()
                                                val userId = authMgr.getCurrentUser()?.id
                                                MerchantOrderStatusApi.markPrinted(row.orderId, auth, userId)
                                            }
                                            refresh()
                                            statusMessage = if (count > 0) "Reprinted 1" else "Retry failed"
                                        }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black), modifier = Modifier.weight(1f)) {
                                        Icon(imageVector = Icons.Outlined.Autorenew, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Retry", fontFamily = FontFamily.Monospace, maxLines = 1)
                                    }
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(imageVector = Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFFF5252))
                                        Text(text = "Order #${row.orderId}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFCCCCCC), maxLines = 1)
                                    }
                                    val created = row.createdAt ?: ""
                                    Text(text = created, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAAAAA), maxLines = 1)
                                }
                                Checkbox(
                                    checked = selected[row.orderId] == true,
                                    onCheckedChange = { checked -> selected[row.orderId] = checked }
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        scope.launch {
                                            val count = PrinterManager.printOrderById(context, row.orderId)
                                            if (count > 0) {
                                                val authMgr = MerchantAuthManager.getInstance(context)
                                                val auth = authMgr.getAuthorizationHeader()
                                                val userId = authMgr.getCurrentUser()?.id
                                                MerchantOrderStatusApi.markPrinted(row.orderId, auth, userId)
                                            }
                                            refresh()
                                            statusMessage = if (count > 0) "Reprinted 1" else "Retry failed"
                                        }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) {
                                        Icon(imageVector = Icons.Outlined.Autorenew, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Retry", fontFamily = FontFamily.Monospace, maxLines = 1)
                                    }
                                }
                            }
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
