package com.bitchat.android.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bitchat.android.db.AppDatabaseHelper
import com.bitchat.android.printer.PrinterManager
import kotlinx.coroutines.launch

class MerchantPrintSuccessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MerchantPrintSuccessScreen() }
    }
}

@Composable
fun MerchantPrintSuccessScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var printed by remember { mutableStateOf(listOf<AppDatabaseHelper.OrderRow>()) }
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val whatsappGreen = Color(0xFF25D366)
    val surfaceDark = Color(0xFF1F1F1F)

    fun refresh() {
        printed = AppDatabaseHelper(context).getOrdersByStatus("printed", 200)
        // Ensure selection map only contains visible items
        val visibleIds = printed.map { it.orderId }.toSet()
        val toRemove = selected.keys.filter { it !in visibleIds }
        toRemove.forEach { selected.remove(it) }
        val toRemoveExp = expanded.keys.filter { it !in visibleIds }
        toRemoveExp.forEach { expanded.remove(it) }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF111111)).padding(16.dp)) {
        Text(
            text = "Print Success",
            style = MaterialTheme.typography.titleLarge,
            color = whatsappGreen,
            fontFamily = FontFamily.Monospace
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { refresh() }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Refresh", fontFamily = FontFamily.Monospace) }
            Button(onClick = {
                scope.launch {
                    printed.forEach { row ->
                        try {
                            val count = PrinterManager.printOrderById(context, row.orderId)
                            if (count > 0) {
                                val authMgr = MerchantAuthManager.getInstance(context)
                                val auth = authMgr.getAuthorizationHeader()
                                val userId = authMgr.getCurrentUser()?.id
                                MerchantOrderStatusApi.markPrinted(row.orderId, auth, userId)
                            }
                        } catch (_: Exception) { }
                    }
                    refresh()
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Reprint All", fontFamily = FontFamily.Monospace) }
            Button(onClick = {
                scope.launch {
                    val targets = printed.filter { selected[it.orderId] == true }
                    targets.forEach { row ->
                        try {
                            val count = PrinterManager.printOrderById(context, row.orderId)
                            if (count > 0) {
                                val authMgr = MerchantAuthManager.getInstance(context)
                                val auth = authMgr.getAuthorizationHeader()
                                val userId = authMgr.getCurrentUser()?.id
                                MerchantOrderStatusApi.markPrinted(row.orderId, auth, userId)
                            }
                        } catch (_: Exception) { }
                    }
                    refresh()
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) { Text("Reprint Selected", fontFamily = FontFamily.Monospace) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(printed) { row ->
                Surface(
                    color = surfaceDark,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, whatsappGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = selected[row.orderId] == true,
                                onCheckedChange = { checked -> selected[row.orderId] = checked }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = whatsappGreen)
                                    Text(text = "Order #${row.orderId}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFFCCCCCC))
                                }
                                val created = row.createdAt ?: ""
                                Text(text = created, style = MaterialTheme.typography.bodySmall, color = Color(0xFFAAAAAA))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val isExpanded = expanded[row.orderId] == true
                            Button(onClick = { expanded[row.orderId] = !isExpanded }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) {
                                Icon(imageVector = if (isExpanded) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (isExpanded) "Hide" else "Details", fontFamily = FontFamily.Monospace)
                            }
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
                            }, colors = ButtonDefaults.buttonColors(containerColor = whatsappGreen, contentColor = Color.Black)) {
                                Icon(imageVector = Icons.Outlined.Print, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Reprint", fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                if (expanded[row.orderId] == true) {
                    // Show order item details
                    val items = remember(row.orderId) { AppDatabaseHelper(context).getOrderItems(row.orderId) }
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp, bottom = 6.dp)) {
                        if (items.isEmpty()) {
                            Text(text = "No items recorded", style = MaterialTheme.typography.bodySmall)
                        } else {
                            items.forEach { item ->
                                Text(
                                    text = "- ${item.quantity} x ${item.name}${item.variant?.let { " (${it})" } ?: ""}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
