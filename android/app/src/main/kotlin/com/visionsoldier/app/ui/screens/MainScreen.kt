package com.visionsoldier.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.visionsoldier.app.ui.theme.*
import com.visionsoldier.app.ui.viewmodel.ChatMessage
import com.visionsoldier.app.ui.viewmodel.MainViewModel
import com.visionsoldier.app.ui.viewmodel.OrbState

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSendMessage: (String) -> Unit,
    onMinimize: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    when (state.currentView) {
        "onboarding" -> OnboardingScreen(
            profile = state.profile,
            onSave = {
                viewModel.updateProfile(it)
                Toast.makeText(context, "Configuración completada", Toast.LENGTH_SHORT).show()
            }
        )
        "diary" -> {
            val pwd = state.profile?.diaryPassword
            if (!pwd.isNullOrEmpty() && !state.isDiaryUnlocked) {
                PasswordScreen(
                    onUnlock = { input ->
                        if (input == pwd) viewModel.unlockDiary()
                        else Toast.makeText(context, "Contraseña incorrecta", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { viewModel.setCurrentView("main") }
                )
            } else {
                DiaryScreen(
                    entries = state.diaryEntries,
                    onAddEntry = { content, title, cat, imp -> viewModel.addDiaryEntry(content, title, cat, imp) },
                    onDeleteEntry = { viewModel.deleteDiaryEntry(it) },
                    onBack = {
                        viewModel.lockDiary()
                        viewModel.setCurrentView("main")
                    }
                )
            }
        }
        "tasks" -> TasksScreen(
            tasks = state.tasks,
            onAddTask = { viewModel.addTask(it) },
            onToggleTask = { id, done -> viewModel.toggleTask(id, done) },
            onDeleteTask = { viewModel.deleteTask(it) },
            onBack = { viewModel.setCurrentView("main") }
        )
        "profile" -> ProfileScreen(
            profile = state.profile,
            viewModel = viewModel,
            currentPalette = state.colorPalette,
            onSave = {
                viewModel.updateProfile(it)
                Toast.makeText(context, "Perfil guardado", Toast.LENGTH_SHORT).show()
                viewModel.setCurrentView("main")
            },
            onBack = { viewModel.setCurrentView("main") }
        )
        "chatHistory" -> {
            LaunchedEffect(Unit) { viewModel.loadFullHistory() }
            ChatHistoryScreen(
                messages = state.fullHistory,
                isLoaded = state.isHistoryLoaded,
                onBack = { viewModel.setCurrentView("main") }
            )
        }
        else -> MainView(state, viewModel, onSendMessage, onMinimize)
    }
}

@Composable
private fun MainView(
    state: com.visionsoldier.app.ui.viewmodel.UiState,
    viewModel: MainViewModel,
    onSendMessage: (String) -> Unit,
    onMinimize: () -> Unit,
) {
    val palette = LocalVisionPalette.current
    var inputText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()      // solo top — BottomNav maneja el bottom con navigationBarsPadding()
        ) {
            Header(orbState = state.orbState, onMinimize = onMinimize)

            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    JarvisOrb(state = state.orbState)

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = state.statusText,
                        color = palette.textDim,
                        fontSize = 15.sp,
                    )
                    if (state.transcriptText.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.transcriptText,
                            color = palette.primary.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            // Barra de tareas activas
            val activeTasks = state.tasks.filter { !it.completed }
            if (activeTasks.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(palette.bgCard)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "TAREAS ACTIVAS", color = JarvisGold, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    activeTasks.take(3).forEach { task ->
                        Text(
                            "• ${task.text}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            maxLines = 1,
                        )
                    }
                    if (activeTasks.size > 3) {
                        Text("+${activeTasks.size - 3} más...", color = palette.textDim, fontSize = 14.sp)
                    }
                }
            }

            // Chat drawer (historial)
            if (state.isDrawerOpen && state.messages.isNotEmpty()) {
                Column(Modifier.fillMaxWidth()) {
                    // Handle
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.bgCard)
                            .clickable { viewModel.toggleDrawer() }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(palette.primary))
                        Spacer(Modifier.width(8.dp))
                        Text("▼ HISTORIAL", color = palette.primary, fontSize = 14.sp)
                    }
                    ChatDrawer(messages = state.messages, modifier = Modifier.fillMaxWidth().height(180.dp))
                    // Botón "Ver historial completo"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.bgCard)
                            .clickable { viewModel.setCurrentView("chatHistory") }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "VER HISTORIAL COMPLETO ▸", color = palette.textDim, fontSize = 14.sp,
                        )
                    }
                }
            } else if (state.messages.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(palette.bgCard.copy(alpha = 0.6f))
                        .clickable { viewModel.toggleDrawer() }
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("▲ HISTORIAL", color = palette.textDim, fontSize = 14.sp)
                }
            }

            InputBar(text = inputText, onTextChange = { inputText = it }, onSend = {
                if (inputText.isNotBlank()) {
                    viewModel.addMessage("user", inputText)
                    onSendMessage(inputText)
                    inputText = ""
                }
            })

            // Navegación con padding para la barra de sistema de Android
            BottomNav(current = "main", onNavigate = { viewModel.setCurrentView(it) })
        }
    }
}

// ── Bottom Navigation ─────────────────────────────────────────

@Composable
fun BottomNav(current: String, onNavigate: (String) -> Unit) {
    val palette = LocalVisionPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.bgCard)
            .navigationBarsPadding()    // evita que los botones queden bajo la barra de sistema
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NavItem(Icons.Default.Adjust, "VISION",  current == "main")    { onNavigate("main") }
        NavItem(Icons.Default.CheckCircle, "TAREAS",  current == "tasks")   { onNavigate("tasks") }
        NavItem(Icons.Default.Book, "DIARIO", current == "diary")   { onNavigate("diary") }
        NavItem(Icons.Default.Person, "PERFIL", current == "profile") { onNavigate("profile") }
    }
}

@Composable
fun NavItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val palette = LocalVisionPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) palette.primary.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) palette.primary else palette.textDim,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (active) palette.primary else palette.textDim,
            fontSize = 15.sp,
        )
        // Indicador activo
        if (active) {
            Spacer(Modifier.height(2.dp))
            Box(Modifier.size(20.dp, 2.dp).clip(RoundedCornerShape(1.dp)).background(palette.primary))
        }
    }
}

// ── Header ────────────────────────────────────────────────────

@Composable
fun Header(orbState: OrbState, onMinimize: () -> Unit) {
    val palette = LocalVisionPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.bgCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Logo arc
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(2.dp, palette.primary, CircleShape)
                .background(palette.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(palette.primary, palette.primary.copy(alpha = 0.2f)))
            ))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "VISION", color = palette.primary, fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Soldier Protocol · Online", color = palette.textDim, fontSize = 14.sp
            )
        }
        StatusIndicator(orbState = orbState)
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = onMinimize) {
            Icon(Icons.Default.KeyboardArrowDown, "Minimizar", tint = palette.primary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun StatusIndicator(orbState: OrbState) {
    val palette = LocalVisionPalette.current
    val infinite = rememberInfiniteTransition(label = "dot")
    val alpha by infinite.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )
    val color = when (orbState) {
        OrbState.LISTENING -> palette.accent
        OrbState.WOKEN     -> palette.primary
        OrbState.THINKING  -> JarvisGold
        OrbState.SPEAKING  -> JarvisGold
        OrbState.IDLE      -> palette.textDim
    }
    val label = when (orbState) {
        OrbState.LISTENING -> "ESCUCHANDO"
        OrbState.WOKEN     -> "ACTIVADO"
        OrbState.THINKING  -> "PROCESANDO"
        OrbState.SPEAKING  -> "HABLANDO"
        OrbState.IDLE      -> "INACTIVO"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 14.sp)
    }
}

// ── Orbe JARVIS con anillos orbitales ──────────────────────────

@Composable
fun JarvisOrb(state: OrbState) {
    val palette = LocalVisionPalette.current
    val infinite = rememberInfiniteTransition(label = "orb")

    // Anillos orbitales
    val ring1 by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "ring1"
    )
    val ring2 by infinite.animateFloat(
        360f, 0f,
        infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "ring2"
    )
    val ring3 by infinite.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "ring3"
    )

    // Animación de respiración del orbe
    val breathSpeed = when (state) {
        OrbState.THINKING -> 700; OrbState.SPEAKING -> 350; else -> 2400
    }
    val breathScale by infinite.animateFloat(
        1f, if (state == OrbState.SPEAKING) 1.12f else 1.05f,
        infiniteRepeatable(tween(breathSpeed, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "breath"
    )
    val glowAlpha by infinite.animateFloat(
        0.25f, 0.9f,
        infiniteRepeatable(tween(breathSpeed, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow"
    )

    val orbColor = when (state) {
        OrbState.WOKEN    -> palette.accent
        OrbState.THINKING -> JarvisGold
        OrbState.SPEAKING -> JarvisGold
        else              -> palette.primary
    }
    val accentColor = if (state == OrbState.SPEAKING || state == OrbState.THINKING) JarvisGold else palette.accent

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(280.dp),
    ) {
        // ── Anillo 3 (más externo, muy sutil) ─────────────────
        Canvas(Modifier.size(260.dp)) {
            rotate(ring3) {
                drawCircle(orbColor.copy(alpha = 0.06f), size.minDimension / 2f - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
        }

        // ── Anillo 2 (medio, con punto de luz) ────────────────
        Canvas(Modifier.size(220.dp)) {
            rotate(ring2) {
                val r = size.minDimension / 2f - 1.dp.toPx()
                drawCircle(accentColor.copy(alpha = 0.12f), r, style = Stroke(1.dp.toPx()))
                drawCircle(accentColor, 3.5.dp.toPx(), center = Offset(center.x, center.y - r))
            }
        }

        // ── Anillo 1 (más interno, más brillante) ─────────────
        Canvas(Modifier.size(180.dp)) {
            rotate(ring1) {
                val r = size.minDimension / 2f - 1.dp.toPx()
                drawCircle(orbColor.copy(alpha = 0.2f), r, style = Stroke(1.5.dp.toPx()))
                drawCircle(orbColor, 5.dp.toPx(), center = Offset(center.x, center.y - r))
            }
        }

        // ── Glow exterior del orbe ────────────────────────────
        Box(
            Modifier
                .size(135.dp)
                .scale(breathScale)
                .clip(CircleShape)
                .background(orbColor.copy(alpha = glowAlpha * 0.12f))
        )

        // ── Núcleo del orbe ───────────────────────────────────
        Box(
            Modifier
                .size(120.dp)
                .scale(breathScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        0.0f to palette.orbGrad1.copy(alpha = 0.9f),
                        0.25f to palette.orbGrad2.copy(alpha = 0.75f),
                        0.55f to palette.orbGrad3.copy(alpha = 0.55f),
                        0.8f  to palette.background.copy(alpha = 0.9f),
                        1.0f  to palette.background,
                    )
                )
                .border(1.5.dp, orbColor.copy(alpha = glowAlpha * 0.7f), CircleShape)
        )

        // ── Ondas de audio (solo al hablar) ───────────────────
        if (state == OrbState.SPEAKING) {
            OrbWaveBars(color = JarvisGold)
        }
    }
}

@Composable
private fun OrbWaveBars(color: Color) {
    val infinite = rememberInfiniteTransition(label = "waves")
    val bars = 8
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bars) { i ->
            val maxH = listOf(12, 22, 34, 42, 38, 28, 18, 11)[i].dp
            val animH by infinite.animateFloat(
                maxH.value * 0.3f, maxH.value,
                infiniteRepeatable(
                    tween(300 + i * 50, easing = EaseInOutSine),
                    RepeatMode.Reverse
                ),
                label = "bar$i"
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height(animH.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = 0.75f))
            )
        }
    }
}

// ── Chat Drawer ───────────────────────────────────────────────

@Composable
fun ChatDrawer(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    val palette = LocalVisionPalette.current
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .background(palette.bgCard)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages) { msg -> ChatBubble(message = msg) }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val palette = LocalVisionPalette.current
    val isVision = message.role == "vision"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isVision) Arrangement.Start else Arrangement.End,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(
                    topStart = 12.dp, topEnd = 12.dp,
                    bottomStart = if (isVision) 0.dp else 12.dp,
                    bottomEnd   = if (isVision) 12.dp else 0.dp,
                ))
                .background(
                    if (isVision) palette.bgCard.copy(0.9f)
                    else palette.primary.copy(0.18f)
                )
                .border(
                    0.5.dp,
                    if (isVision) palette.primary.copy(0.25f) else Color.Transparent,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 280.dp)
        ) {
            Column {
                if (isVision) {
                    Text(
                        "VISION", color = palette.primary, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    message.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

// ── Barra de Entrada ───────────────────────────────────────────

@Composable
fun InputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
    val palette = LocalVisionPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.bgCard)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text, onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("O escríbele aquí...", color = palette.textDim, fontSize = 14.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = palette.primary,
                unfocusedBorderColor = palette.textDim,
                cursorColor          = palette.primary,
                focusedTextColor     = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor   = MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            shape = RoundedCornerShape(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            modifier = Modifier.clip(CircleShape).background(palette.primary.copy(alpha = 0.15f)),
        ) {
            Icon(Icons.Default.Send, contentDescription = "Enviar", tint = palette.primary)
        }
    }
}
