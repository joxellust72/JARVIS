package com.visionsoldier.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionsoldier.app.data.entity.Task
import com.visionsoldier.app.ui.theme.*

private enum class TaskFilter(val label: String) { ALL("TODAS"), PENDING("PENDIENTES"), DONE("COMPLETADAS") }

@Composable
fun TasksScreen(
    tasks: List<Task>,
    onAddTask: (String) -> Unit,
    onToggleTask: (Long, Boolean) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalVisionPalette.current
    var showDialog by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(TaskFilter.ALL) }

    val filteredTasks = remember(tasks, filter) {
        when (filter) {
            TaskFilter.ALL -> tasks
            TaskFilter.PENDING -> tasks.filter { !it.completed }
            TaskFilter.DONE -> tasks.filter { it.completed }
        }
    }
    val pendingCount = tasks.count { !it.completed }
    val doneCount = tasks.count { it.completed }

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
                    "TAREAS", color = palette.primary, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, "Nueva tarea", tint = palette.accent)
                }
            }

            // ── Stats bar ────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Pendientes: $pendingCount", color = JarvisGold, fontSize = 15.sp)
                Text("Completadas: $doneCount", color = palette.accent, fontSize = 15.sp)
                Text("Total: ${tasks.size}", color = palette.textDim, fontSize = 15.sp)
            }

            // ── Filter pills ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaskFilter.entries.forEach { f ->
                    val isActive = filter == f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isActive) palette.primary.copy(alpha = 0.2f)
                                else palette.bgCard,
                            )
                            .border(
                                1.dp,
                                if (isActive) palette.primary.copy(alpha = 0.5f)
                                else palette.textDim.copy(alpha = 0.2f),
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { filter = f }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            f.label,
                            color = if (isActive) palette.primary else palette.textDim,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Task list ────────────────────────────────────
            if (filteredTasks.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val msg = when (filter) {
                        TaskFilter.PENDING -> "¡Sin tareas pendientes! 🎉"
                        TaskFilter.DONE -> "Sin tareas completadas aún."
                        else -> "Sin tareas.\nDi \"Jarvis, nueva tarea: ...\""
                    }
                    Text(
                        msg, color = palette.textDim, fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskCard(task = task, palette = palette, onToggle = onToggleTask, onDelete = onDeleteTask)
                    }
                }
            }
        }
    }

    if (showDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = palette.bgCard,
            title = { Text("Nueva Tarea", color = palette.primary) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("¿Qué necesita hacer?", color = palette.textDim) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary, unfocusedBorderColor = palette.textDim,
                        cursorColor = palette.primary, focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) { onAddTask(text); showDialog = false }
                }) { Text("AGREGAR", color = palette.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("CANCELAR", color = palette.textDim) }
            },
        )
    }
}

// ── Task Card with circular checkbox ──────────────────────────

@Composable
private fun TaskCard(
    task: Task,
    palette: VisionPalette,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val accentColor by animateColorAsState(
        targetValue = if (task.completed) palette.accent else palette.primary,
        animationSpec = tween(300),
        label = "taskAccent",
    )
    val borderAlpha = if (task.completed) 0.3f else 0.2f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.bgCard)
            .border(0.5.dp, accentColor.copy(alpha = borderAlpha), RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left color bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(56.dp)
                .background(accentColor.copy(alpha = if (task.completed) 0.5f else 0.8f)),
        )

        Spacer(Modifier.width(10.dp))

        // Circular checkbox
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (task.completed) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                )
                .border(
                    2.dp,
                    if (task.completed) accentColor else palette.textDim.copy(alpha = 0.4f),
                    CircleShape,
                )
                .clickable { onToggle(task.id, !task.completed) },
            contentAlignment = Alignment.Center,
        ) {
            if (task.completed) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Completada",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = task.text,
            color = if (task.completed) palette.textDim else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 10.dp),
        )

        IconButton(onClick = { onDelete(task.id) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, "Borrar", tint = palette.textDim, modifier = Modifier.size(16.dp))
        }

        Spacer(Modifier.width(4.dp))
    }
}
