package com.astra.agent.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val PREVIEW_BASE = "https://zeniq.preview/"

/**
 * Full-screen live preview of a generated website, like an artifact preview:
 * the page runs in a WebView (JavaScript on), and you can reload it, look at its code, open it in the browser or close it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SitePreview(path: String, title: String, onClose: () -> Unit, onOpenExternal: () -> Unit) {
    val c = LocalIos.current
    val ctx = LocalContext.current
    val html by produceState<String?>(null, path) {
        value = withContext(Dispatchers.IO) { try { File(path).readText() } catch (_: Throwable) { null } }
    }
    var showCode by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val web = remember { arrayOfNulls<WebView>(1) }
    DisposableEffect(Unit) { onDispose { try { web[0]?.destroy() } catch (_: Throwable) {} ; web[0] = null } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Close", c.red) { onClose() }
                Text(title, Modifier.weight(1f), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                PillButton(if (showCode) "Preview" else "Code") { showCode = !showCode }
                PillButton("Reload", c.green) { reload++ }
                PillButton("Open") { onOpenExternal() }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val page = html
                when {
                    page == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    showCode -> Text(page, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                        color = c.text, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
                    else -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.allowFileAccess = false
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    // links to other sites open in the phone's browser; page anchors stay inside the preview
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val u = request.url
                                        val scheme = u.scheme ?: return false
                                        if (u.toString().startsWith(PREVIEW_BASE)) return false
                                        if (scheme == "http" || scheme == "https" || scheme == "mailto" || scheme == "tel") {
                                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
                                            return true
                                        }
                                        return false
                                    }
                                }
                                web[0] = this
                            }
                        },
                        update = { wv ->
                            val stamp = page.hashCode() * 31 + reload
                            if (wv.tag != stamp) {
                                wv.tag = stamp
                                wv.loadDataWithBaseURL(PREVIEW_BASE, page, "text/html", "utf-8", null)
                            }
                        }
                    )
                }
            }
        }
    }
}
