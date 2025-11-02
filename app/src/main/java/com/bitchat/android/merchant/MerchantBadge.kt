package com.bitchat.android.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Merchant badge component to display merchant status
 */
@Composable
fun MerchantBadge(
    modifier: Modifier = Modifier,
    showUserInfo: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val authManager = remember { MerchantAuthManager.getInstance(context) }
    val isLoggedIn by authManager.isLoggedIn.collectAsState()
    val currentUser by authManager.currentUser.collectAsState()
    
    if (isLoggedIn && currentUser != null) {
        val colorScheme = MaterialTheme.colorScheme
        
        Surface(
            onClick = onClick ?: {},
            modifier = modifier,
            color = Color(0xFF4CAF50).copy(alpha = 0.15f), // Merchant green
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Store,
                    contentDescription = "Merchant",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(16.dp)
                )
                
                if (showUserInfo) {
                    Text(
                        text = currentUser!!.name,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF4CAF50)
                    )
                } else {
                    Text(
                        text = "MERCHANT",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

/**
 * Compact merchant indicator for header areas
 */
@Composable
fun MerchantIndicator(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authManager = remember { MerchantAuthManager.getInstance(context) }
    val isLoggedIn by authManager.isLoggedIn.collectAsState()
    
    if (isLoggedIn) {
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF4CAF50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Verified Merchant",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * Merchant info card for detailed display
 */
@Composable
fun MerchantInfoCard(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val authManager = remember { MerchantAuthManager.getInstance(context) }
    val isLoggedIn by authManager.isLoggedIn.collectAsState()
    val currentUser by authManager.currentUser.collectAsState()
    
    if (isLoggedIn && currentUser != null) {
        val colorScheme = MaterialTheme.colorScheme
        val user = currentUser!!
        
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Store,
                            contentDescription = "Merchant",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Text(
                            text = "Merchant Account",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = colorScheme.onBackground
                        )
                    }
                    
                    MerchantIndicator()
                }
                
                // User Info
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = colorScheme.onBackground
                    )
                    
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    
                    if (user.phone.isNotBlank()) {
                        Text(
                            text = user.phone,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                    
                    Text(
                        text = "Currency: ${user.currency}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                
                // Logout Button
                OutlinedButton(
                    onClick = {
                        authManager.logout()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }
            }
        }
    }
}