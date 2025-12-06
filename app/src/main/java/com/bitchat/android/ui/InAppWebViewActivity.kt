package com.bitchat.android.ui

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.viewinterop.AndroidView
import com.bitchat.android.R
import kotlinx.coroutines.launch

class InAppWebViewActivity : OrientationAwareActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra("url") ?: "about:blank"

        setContent {
            MaterialTheme { InAppWebViewScreen(initialUrl = url) }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun InAppWebViewScreen(initialUrl: String) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val webViewRef: MutableState<WebView?> = remember { mutableStateOf(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var pageTitle by remember { mutableStateOf("Komble Mesh") }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val host: String = try {
        val uri = Uri.parse(currentUrl)
        uri.host ?: currentUrl
    } catch (_: Throwable) { currentUrl }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth()) {
                TopAppBar(
                    navigationIcon = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { try { (context as? OrientationAwareActivity)?.finish() } catch (_: Throwable) {} }) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                            }
                            if (canGoBack) {
                                IconButton(onClick = { try { webViewRef.value?.goBack() } catch (_: Throwable) {} }) {
                                    Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface
                            )
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                val isHttps = try { Uri.parse(currentUrl).scheme.equals("https", true) } catch (_: Throwable) { false }
                                Icon(
                                    imageVector = if (isHttps) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                    contentDescription = if (isHttps) "Secure" else "Not secure",
                                    tint = if (isHttps) colorScheme.primary else colorScheme.error,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(
                                    text = host,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_SEND)
                                intent.type = "text/plain"
                                intent.putExtra(Intent.EXTRA_TEXT, currentUrl)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(Intent.createChooser(intent, "Share link"))
                            } catch (_: Exception) {}
                        }) { Icon(Icons.Filled.Share, contentDescription = "Share") }

                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }) { Icon(Icons.Filled.OpenInBrowser, contentDescription = "Open in browser") }

                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(enabled = false, onClick = {}, text = {
                                Column {
                                    Text(stringResource(id = R.string.app_name), fontFamily = FontFamily.Monospace)
                                    Text(host, fontFamily = FontFamily.Monospace, color = colorScheme.onSurface.copy(alpha = 0.7f))
                                }
                            })
                            Divider()
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = {
                                menuExpanded = false
                                try { webViewRef.value?.reload() } catch (_: Throwable) {}
                            }, leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) })
                            DropdownMenuItem(text = { Text("Open in browser") }, onClick = {
                                menuExpanded = false
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }, leadingIcon = { Icon(Icons.Filled.OpenInBrowser, contentDescription = null) })
                            DropdownMenuItem(text = { Text("Copy link") }, onClick = {
                                menuExpanded = false
                                try {
                                    clipboard.setText(AnnotatedString(currentUrl))
                                    scope.launch { snackbarHostState.showSnackbar("Link copied") }
                                } catch (_: Exception) {}
                            })
                            DropdownMenuItem(text = { Text("Share link") }, onClick = {
                                menuExpanded = false
                                try {
                                    val intent = Intent(Intent.ACTION_SEND)
                                    intent.type = "text/plain"
                                    intent.putExtra(Intent.EXTRA_TEXT, currentUrl)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(Intent.createChooser(intent, "Share link"))
                                } catch (_: Exception) {}
                            }, leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) })
                            DropdownMenuItem(text = { Text("Report") }, onClick = {
                                menuExpanded = false
                                try {
                                    val intent = Intent(Intent.ACTION_SEND)
                                    intent.type = "text/plain"
                                    intent.putExtra(Intent.EXTRA_TEXT, "Report this link: $currentUrl")
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(Intent.createChooser(intent, "Report link"))
                                } catch (_: Exception) {}
                            })
                            DropdownMenuItem(text = { Text("Learn more") }, onClick = {
                                menuExpanded = false
                                try {
                                    val scheme = if (currentUrl.lowercase().startsWith("https")) "https" else "http"
                                    val learn = Uri.parse("$scheme://$host")
                                    val intent = Intent(Intent.ACTION_VIEW, learn)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            })
                        }
                    }
                )
                if (progress in 0f..0.99f) {
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { context2 ->
                    WebView(context2).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title ?: pageTitle
                            }
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = (newProgress / 100f).coerceIn(0f, 1f)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                return false
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                currentUrl = url ?: currentUrl
                                canGoBack = canGoBack || (view?.canGoBack() == true)
                                progress = 1f
                            }
                        }
                        loadUrl(initialUrl)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { webView -> webViewRef.value = webView },
                onRelease = { webView ->
                    try { webView.stopLoading() } catch (_: Throwable) {}
                    try { webView.clearHistory() } catch (_: Throwable) {}
                    try { webView.clearCache(true) } catch (_: Throwable) {}
                    try { webView.loadUrl("about:blank") } catch (_: Throwable) {}
                    try { webView.removeAllViews() } catch (_: Throwable) {}
                    try { webView.destroy() } catch (_: Throwable) {}
                }
            )
        }
    }
}
