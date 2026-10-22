package com.astra.agent.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SIDEBAR_LIMIT = 7

private fun whenLabel(t: Long): String =
    DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()

/** Left drawer: New chat + the most recent chats + "Show more". */
@Composable
fun Sidebar(
    vm: AstraViewModel,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onShowMore: () -> Unit
) {
    val c = LocalIos.current
    val chats by vm.chats.collectAsState()
    val active by vm.activeChat.collectAsState()

    Column(Modifier.fillMaxHeight().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text("ZENIQ", Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.text)

        // New chat button
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)).background(c.accent.copy(alpha = 0.12f))
                .pressScale { onNewChat() }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, null, tint = c.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("New chat", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
        }

        SectionHeader("Recent")

        if (chats.isEmpty()) {
            Text("No chats yet. Start talking to ZENIQ!", Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                fontSize = 14.sp, color = c.sub)
        }
        chats.take(SIDEBAR_LIMIT).forEach { chat ->
            val sel = chat.id == active
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 1.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) c.fill else Color.Transparent)
                    .clickable { onOpenChat(chat.id) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ChatBubbleOutline, null, tint = c.sub, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(chat.title, fontSize = 15.sp, maxLines = 1, color = c.text,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        if (chats.size > SIDEBAR_LIMIT) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)).clickable { onShowMore() }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.History, null, tint = c.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Show more (${chats.size - SIDEBAR_LIMIT})", fontSize = 15.sp, color = c.accent, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Full-screen list of every previous chat with search and delete. */
@Composable
fun HistoryScreen(vm: AstraViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = LocalIos.current
    val chats by vm.chats.collectAsState()
    var query by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<ChatMeta?>(null) }
    val shown = if (query.isBlank()) chats else chats.filter { it.title.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).pressScale { onBack() }.background(c.fill, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = c.text, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("History", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        }

        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(c.fill).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, tint = c.sub, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search chats", color = c.sub, fontSize = 16.sp)
                        inner()
                    })
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (chats.isEmpty()) "No chats yet" else "No chats match your search", color = c.sub, fontSize = 15.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { chat ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
                        .clickable { onOpen(chat.id) }.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(chat.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(whenLabel(chat.updated), fontSize = 12.sp, color = c.sub)
                        }
                        Box(Modifier.size(40.dp).pressScale { toDelete = chat }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = c.red, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { chat ->
        AlertDialog(
            containerColor = c.card, shape = RoundedCornerShape(20.dp),
            onDismissRequest = { toDelete = null },
            title = { Text("Delete chat?", fontWeight = FontWeight.SemiBold, color = c.text) },
            text = { Text("\"${chat.title}\" will be removed permanently.", color = c.sub) },
            confirmButton = { TextButton(onClick = { vm.deleteChat(chat.id); toDelete = null }) { Text("Delete", color = c.red) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel", color = c.sub) } }
        )
    }
}
