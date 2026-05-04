package com.visionsoldier.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionsoldier.app.data.entity.DiaryEntry
import com.visionsoldier.app.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DiaryScreen(
    entries: List<DiaryEntry>,
    onAddEntry: (String, String, String, Int) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalVisionPalette.current
    var showDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }

    // Map entries by date (first 10 chars = "YYYY-MM-DD")
    val entriesByDate = remember(entries) {
        entries.groupBy { it.date.take(10) }
    }
    val selectedDateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val entriesForDate = entriesByDate[selectedDateStr] ?: emptyList()

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
                Text(
                    "DIARIO", color = palette.primary, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, "Nueva entrada", tint = palette.accent)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                // ── Calendar ─────────────────────────────────
                item {
                    CalendarCard(
                        currentMonth = currentMonth,
                        selectedDate = selectedDate,
                        entriesByDate = entriesByDate,
                        palette = palette,
                        onMonthChange = { currentMonth = it },
                        onDateSelected = { selectedDate = it },
                    )
                }

                // ── Entries for selected date ────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    val dayName = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es"))
                        .replaceFirstChar { it.uppercase() }
                    val monthName = selectedDate.month.getDisplayName(TextStyle.FULL, Locale("es"))
                        .replaceFirstChar { it.uppercase() }
                    Text(
                        "$dayName, ${selectedDate.dayOfMonth} de $monthName",
                        color = palette.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${entriesForDate.size} entrada${if (entriesForDate.size != 1) "s" else ""}",
                        color = palette.textDim, fontSize = 15.sp,
                    )
                }

                if (entriesForDate.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Sin entradas este día.\nToca + o di \"Jarvis, guarda esto: ...\"",
                                color = palette.textDim,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                } else {
                    items(entriesForDate, key = { it.id }) { entry ->
                        NotebookCard(entry = entry, palette = palette, onDelete = onDeleteEntry)
                    }
                }
            }
        }
    }

    if (showDialog) {
        DiaryDialog(
            palette = palette,
            onDismiss = { showDialog = false },
            onSave = { content, category, importance ->
                onAddEntry(content, "", category, importance)
                showDialog = false
            },
        )
    }
}

// ── Calendar Card ─────────────────────────────────────────────

@Composable
private fun CalendarCard(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    entriesByDate: Map<String, List<DiaryEntry>>,
    palette: VisionPalette,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val daysOfWeek = listOf("Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do")
    val monthName = currentMonth.month.getDisplayName(TextStyle.FULL, Locale("es"))
        .replaceFirstChar { it.uppercase() }
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bgCard)
            .padding(14.dp),
    ) {
        // Month navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ChevronLeft, "Anterior", tint = palette.primary)
            }
            Text(
                "$monthName ${currentMonth.year}",
                color = palette.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ChevronRight, "Siguiente", tint = palette.primary)
            }
        }

        Spacer(Modifier.height(10.dp))

        // Day-of-week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    day, modifier = Modifier.weight(1f),
                    color = palette.textDim, fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Calendar grid
        val firstDay = currentMonth.atDay(1)
        // Monday = 1, Sunday = 7 — offset so Monday is column 0
        val startOffset = (firstDay.dayOfWeek.value - 1) // 0 for Monday
        val daysInMonth = currentMonth.lengthOfMonth()
        val totalCells = startOffset + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - startOffset + 1

                    if (dayNum in 1..daysInMonth) {
                        val date = currentMonth.atDay(dayNum)
                        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val hasEntries = entriesByDate.containsKey(dateStr)
                        val isSelected = date == selectedDate
                        val isToday = date == today

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .then(
                                    when {
                                        isSelected -> Modifier.background(palette.primary.copy(alpha = 0.25f))
                                        isToday -> Modifier.border(1.dp, palette.primary.copy(alpha = 0.4f), CircleShape)
                                        else -> Modifier
                                    }
                                )
                                .clickable { onDateSelected(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$dayNum",
                                    color = when {
                                        isSelected -> palette.primary
                                        isToday -> palette.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (hasEntries) {
                                    Box(
                                        Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(palette.accent)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ── Notebook-style Entry Card ─────────────────────────────────

@Composable
private fun NotebookCard(
    entry: DiaryEntry,
    palette: VisionPalette,
    onDelete: (Long) -> Unit,
) {
    val catColor = when (entry.category) {
        "trabajo" -> JarvisGold
        "salud" -> palette.accent
        "meta" -> Color(0xFFC080FF)
        else -> palette.primary
    }
    val lineColor = palette.textDim.copy(alpha = 0.08f)
    val marginColor = catColor.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.bgCard),
    ) {
        // Ruled lines background
        Canvas(modifier = Modifier.matchParentSize()) {
            val lineSpacing = 26.dp.toPx()
            val topOffset = 46.dp.toPx() // start after header
            var y = topOffset
            while (y < size.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += lineSpacing
            }
            // Left margin line
            drawLine(
                color = marginColor,
                start = Offset(6.dp.toPx(), 0f),
                end = Offset(6.dp.toPx(), size.height),
                strokeWidth = 2.5f,
            )
        }

        Column(modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
            // Category + importance + delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.category.uppercase(), color = catColor, fontSize = 14.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text("⭐".repeat(entry.importance.coerceIn(1, 5)), fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                // Time
                val time = if (entry.date.length >= 16) entry.date.substring(11, 16) else ""
                if (time.isNotEmpty()) {
                    Text(
                        time, color = palette.textDim, fontSize = 14.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(onClick = { onDelete(entry.id) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, "Borrar", tint = palette.textDim, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(Modifier.height(6.dp))

            // Content
            Text(
                entry.content,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 26.sp, // match ruled lines
                fontStyle = FontStyle.Italic,
            )
        }
    }
}

// ── New Entry Dialog (no title, auto-date) ────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiaryDialog(
    palette: VisionPalette,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit,
) {
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("personal") }
    var importance by remember { mutableIntStateOf(3) }

    val today = LocalDate.now()
    val dayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es"))
        .replaceFirstChar { it.uppercase() }
    val monthName = today.month.getDisplayName(TextStyle.FULL, Locale("es"))
        .replaceFirstChar { it.uppercase() }
    val dateHeader = "$dayName, ${today.dayOfMonth} de $monthName ${today.year}"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.bgCard,
        title = {
            Column {
                Text(
                    "Nueva Entrada", color = palette.primary, fontSize = 15.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    dateHeader, color = palette.textDim,
                    fontSize = 15.sp,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = content, onValueChange = { content = it },
                    label = { Text("¿Qué quieres escribir hoy?", color = palette.textDim) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary, unfocusedBorderColor = palette.textDim,
                        cursorColor = palette.primary, focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    minLines = 3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("personal", "trabajo", "salud", "meta").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }, fontSize = 15.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.primary.copy(alpha = 0.2f),
                            ),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Importancia: ", color = palette.textDim, fontSize = 14.sp)
                    (1..5).forEach { i ->
                        Text(
                            if (i <= importance) "⭐" else "☆", fontSize = 18.sp,
                            modifier = Modifier.clickable { importance = i }.padding(2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (content.isNotBlank()) onSave(content, category, importance) }) {
                Text("GUARDAR", color = palette.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCELAR", color = palette.textDim) }
        },
    )
}
