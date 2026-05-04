package com.visionsoldier.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionsoldier.app.data.entity.Conversation
import com.visionsoldier.app.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pantalla de historial completo de chat — agrupa todos los mensajes por día.
 */
@Composable
fun ChatHistoryScreen(
    messages: List<Conversation>,
    isLoaded: Boolean,
    onBack: () -> Unit,
) {
    val palette = LocalVisionPalette.current

    // Agrupar mensajes por fecha (YYYY-MM-DD)
    val groupedByDay: List<Pair<String, List<Conversation>>> = remember(messages) {
        messages
            .groupBy { it.timestamp.take(10) }
            .toSortedMap(compareByDescending { it }) // más reciente primero
            .toList()
    }

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ───────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.bgCard)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = palette.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "HISTORIAL COMPLETO", color = palette.primary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${messages.size} mensajes en total",
                        color = palette.textDim, fontSize = 14.sp,
                    )
                }
            }

            if (!isLoaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = palette.primary, strokeWidth = 2.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("Cargando historial...", color = palette.textDim, fontSize = 14.sp)
                    }
                }
            } else if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin conversaciones aún.\nHabla con Jarvis para empezar.",
                        color = palette.textDim, fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    groupedByDay.forEach { (dateStr, dayMessages) ->
                        // ── Day header ───────────────────────
                        item(key = "header_$dateStr") {
                            DayHeader(dateStr = dateStr, messageCount = dayMessages.size, palette = palette)
                        }

                        // ── Messages for this day ────────────
                        items(dayMessages, key = { "msg_${it.id}" }) { msg ->
                            HistoryBubble(msg = msg, palette = palette)
                        }

                        // Spacer between day groups
                        item(key = "spacer_$dateStr") {
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

// ── Day Header ────────────────────────────────────────────────

@Composable
private fun DayHeader(dateStr: String, messageCount: Int, palette: VisionPalette) {
    val displayDate = try {
        val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
        val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es"))
            .replaceFirstChar { it.uppercase() }
        val monthName = date.month.getDisplayName(TextStyle.FULL, Locale("es"))
            .replaceFirstChar { it.uppercase() }
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        when (date) {
            today -> "Hoy — ${date.dayOfMonth} de $monthName"
            yesterday -> "Ayer — ${date.dayOfMonth} de $monthName"
            else -> "$dayName, ${date.dayOfMonth} de $monthName ${date.year}"
        }
    } catch (_: Exception) {
        dateStr
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(0.5.dp)
                    .background(palette.primary.copy(alpha = 0.2f)),
            )
            Text(
                text = displayDate,
                color = palette.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(0.5.dp)
                    .background(palette.primary.copy(alpha = 0.2f)),
            )
        }
        Text(
            "$messageCount mensaje${if (messageCount != 1) "s" else ""}",
            color = palette.textDim, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

// ── History Message Bubble ────────────────────────────────────

@Composable
private fun HistoryBubble(msg: Conversation, palette: VisionPalette) {
    val isVision = msg.role == "vision"
    val time = if (msg.timestamp.length >= 16) msg.timestamp.substring(11, 16) else ""

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isVision) Arrangement.Start else Arrangement.End,
    ) {
        Box(
            Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp, topEnd = 12.dp,
                        bottomStart = if (isVision) 0.dp else 12.dp,
                        bottomEnd = if (isVision) 12.dp else 0.dp,
                    )
                )
                .background(
                    if (isVision) palette.bgCard.copy(0.9f)
                    else palette.primary.copy(0.15f),
                )
                .border(
                    0.5.dp,
                    if (isVision) palette.primary.copy(0.2f) else Color.Transparent,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 300.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isVision) "VISION" else "TÚ",
                        color = if (isVision) palette.primary else palette.accent,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (time.isNotEmpty()) {
                        Text(
                            time, color = palette.textDim, fontSize = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    msg.content,
                    color = if (isVision) Color(0xFFE0F7FA) else Color.White,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}
