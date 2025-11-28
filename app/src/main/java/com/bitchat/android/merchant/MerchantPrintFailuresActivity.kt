package com.bitchat.android.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    fun refresh() {
        // Include both explicit print failures and orders that are paid (awaiting print)
        failures = AppDatabaseHelper(context).getOrdersByStatuses(listOf("print_failed", "Paid", "paid"), 200)
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Print Failures",
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { refresh() }) { Text("Refresh") }
            Button(onClick = {
                scope.launch {
                    failures.forEach { row ->
                        try {
                            val printed = PrinterManager.printOrderById(context, row.orderId)
                            if (printed > 0) {
                                // Ensure remote printed status update as well
                                val auth = MerchantAuthManager.getInstance(context).getAuthorizationHeader()
                                MerchantOrderStatusApi.markPrinted(row.orderId, auth)
                            }
                        } catch (_: Exception) { }
                    }
                    refresh()
                }
            }) { Text("Retry All") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(failures) { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Order #${row.orderId}", style = MaterialTheme.typography.bodyLarge)
                        val created = row.createdAt ?: ""
                        Text(text = created, style = MaterialTheme.typography.bodySmall)
                    }
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
                            }
                        }) { Text("Retry") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}