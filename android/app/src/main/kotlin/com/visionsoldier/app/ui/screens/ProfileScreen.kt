package com.visionsoldier.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionsoldier.app.data.entity.Profile
import com.visionsoldier.app.ui.theme.*

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.visionsoldier.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    profile: Profile?,
    viewModel: MainViewModel,
    currentPalette: String,
    onSave: (Profile) -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalVisionPalette.current

    var name by remember { mutableStateOf(profile?.name ?: "Jarvis") }
    var profession by remember { mutableStateOf(profile?.profession ?: "Automatización y control") }
    var interests by remember { mutableStateOf(profile?.interests ?: "anime, videojuegos, tecnología") }
    var traits by remember { mutableStateOf(profile?.traits ?: "algo olvidadizo, curioso") }
    var password by remember { mutableStateOf(profile?.diaryPassword ?: "") }
    var apiKey by remember { mutableStateOf(profile?.apiKey ?: "") }
    var voiceEngine by remember { mutableStateOf(profile?.voiceEngine ?: "native") }
    var elevenLabsApiKey by remember { mutableStateOf(profile?.elevenLabsApiKey ?: "") }
    var elevenLabsVoiceId by remember { mutableStateOf(profile?.elevenLabsVoiceId ?: "") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            backupPassword = ""
            showExportDialog = true
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            backupPassword = ""
            showImportDialog = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().background(palette.bgCard)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = palette.primary)
                }
                Text(
                    "PERFIL Y AJUSTES", color = palette.primary, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Paleta de colores ────────────────────────────
                SectionLabel(" PALETA DE COLORES", palette)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Palettes.values.forEach { p ->
                        val isSelected = currentPalette == p.id
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(p.swatchGrad))
                                .then(
                                    if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                    else Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                )
                                .clickable { viewModel.setPalette(p.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check, contentDescription = "Seleccionada",
                                    tint = Color.White, modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Palettes.values.forEach { p ->
                        Text(
                            p.displayName.split(" ").last(),
                            modifier = Modifier.weight(1f),
                            color = if (currentPalette == p.id) palette.primary else palette.textDim,
                            fontSize = 15.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Datos personales ─────────────────────────────
                SectionLabel("DATOS PERSONALES", palette)
                ProfileField(value = name, onValueChange = { name = it }, label = "Nombre preferido", palette = palette)
                ProfileField(value = profession, onValueChange = { profession = it }, label = "Profesión / Ocupación", palette = palette)

                // ── Personalidad ─────────────────────────────────
                SectionLabel("PERSONALIDAD DE LA IA", palette)
                ProfileField(value = interests, onValueChange = { interests = it }, label = "Intereses (separados por coma)", palette = palette)
                ProfileField(value = traits, onValueChange = { traits = it }, label = "Rasgos (separados por coma)", palette = palette)

                // ── Sistema ──────────────────────────────────────
                SectionLabel("SISTEMA", palette)
                OutlinedTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    label = { Text("Gemini API Keys (separadas por coma)", color = palette.textDim) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = themedFieldColors(palette),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Si la IA no responde, obtén una clave gratis en aistudio.google.com",
                    color = palette.textDim, fontSize = 14.sp, lineHeight = 14.sp,
                )

                // ── Motor de Voz ─────────────────────────────────
                Spacer(Modifier.height(8.dp))
                SectionLabel(" MOTOR DE VOZ", palette)
                Text(
                    "Elige cómo habla VISION. La voz nativa es gratis y funciona offline. ElevenLabs ofrece voces premium con calidad de estudio.",
                    color = palette.textDim, fontSize = 14.sp, lineHeight = 14.sp,
                )
                Spacer(Modifier.height(8.dp))

                // Selector de motor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VoiceOptionCard(
                        label = "TTS NATIVO",
                        description = "Android · Gratis · Offline",
                        isSelected = voiceEngine == "native",
                        palette = palette,
                        modifier = Modifier.weight(1f),
                        onClick = { voiceEngine = "native" }
                    )
                    VoiceOptionCard(
                        label = "ELEVENLABS",
                        description = "Premium · Requiere API",
                        isSelected = voiceEngine == "elevenlabs",
                        palette = palette,
                        modifier = Modifier.weight(1f),
                        onClick = { voiceEngine = "elevenlabs" }
                    )
                }

                // Campos de ElevenLabs (visibles solo si está seleccionado)
                if (voiceEngine == "elevenlabs") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = elevenLabsApiKey,
                        onValueChange = { elevenLabsApiKey = it },
                        label = { Text("ElevenLabs API Key", color = palette.accent) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = themedFieldColors(palette),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Obtén tu clave en elevenlabs.io → Profile → API Key",
                        color = palette.textDim, fontSize = 14.sp, lineHeight = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = elevenLabsVoiceId,
                        onValueChange = { elevenLabsVoiceId = it },
                        label = { Text("Voice ID (opcional)", color = palette.textDim) },
                        colors = themedFieldColors(palette),
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Adam (por defecto)", color = palette.textDim.copy(alpha = 0.5f)) },
                    )
                    Text(
                        "Voces recomendadas: Adam (grave), Josh (calmado), Antoni (elegante). Busca más en elevenlabs.io/voice-library",
                        color = palette.textDim, fontSize = 14.sp, lineHeight = 14.sp,
                    )
                }

                // ── Seguridad ────────────────────────────────────
                SectionLabel("SEGURIDAD", palette)
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Contraseña del Diario", color = palette.textDim) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = themedFieldColors(palette),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Si define una contraseña, el diario estará bloqueado.",
                    color = palette.textDim, fontSize = 15.sp, lineHeight = 16.sp,
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        onSave(Profile(
                            id = 1,
                            name = name,
                            profession = profession,
                            interests = interests,
                            traits = traits,
                            diaryPassword = password,
                            apiKey = apiKey,
                            voiceEngine = voiceEngine,
                            elevenLabsApiKey = elevenLabsApiKey,
                            elevenLabsVoiceId = elevenLabsVoiceId,
                        ))
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "GUARDAR CAMBIOS", color = palette.background,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── Backup ───────────────────────────────────────
                SectionLabel("COPIA DE SEGURIDAD", palette)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { exportLauncher.launch("vision_backup.enc") },
                        modifier = Modifier.weight(1f).height(45.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.bgCard),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("EXPORTAR", color = palette.primary, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f).height(45.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.bgCard),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("IMPORTAR", color = palette.primary, fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }

        // Export Dialog
        if (showExportDialog) {
            BackupDialog(
                title = "Encriptar Copia de Seguridad",
                description = "Introduce una contraseña para proteger tu backup.",
                password = backupPassword,
                onPasswordChange = { backupPassword = it },
                confirmLabel = "Aceptar",
                palette = palette,
                onConfirm = {
                    if (backupPassword.isNotBlank() && selectedUri != null) {
                        coroutineScope.launch {
                            val success = viewModel.exportToFile(selectedUri!!, backupPassword, context)
                            val msg = if (success) "Exportación exitosa" else "Error al exportar"
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            showExportDialog = false
                        }
                    }
                },
                onDismiss = { showExportDialog = false },
            )
        }

        // Import Dialog
        if (showImportDialog) {
            BackupDialog(
                title = "Restaurar Copia de Seguridad",
                description = "Introduce la contraseña de este backup. ATENCIÓN: Esto reemplazará tus datos actuales.",
                password = backupPassword,
                onPasswordChange = { backupPassword = it },
                confirmLabel = "Restaurar",
                palette = palette,
                onConfirm = {
                    if (backupPassword.isNotBlank() && selectedUri != null) {
                        coroutineScope.launch {
                            val success = viewModel.importFromFile(selectedUri!!, backupPassword, context)
                            val msg = if (success) "Importación exitosa" else "Error o contraseña incorrecta"
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                        }
                    }
                },
                onDismiss = { showImportDialog = false },
            )
        }
    }
}

// ── Componentes auxiliares ─────────────────────────────────────

@Composable
private fun SectionLabel(text: String, palette: VisionPalette) {
    Text(
        text, color = palette.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    palette: VisionPalette,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = palette.textDim) },
        colors = themedFieldColors(palette),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun themedFieldColors(palette: VisionPalette) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = palette.primary, unfocusedBorderColor = palette.textDim,
    cursorColor = palette.primary, focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun VoiceOptionCard(
    label: String,
    description: String,
    isSelected: Boolean,
    palette: VisionPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) palette.primary else palette.textDim.copy(alpha = 0.3f)
    val bgColor = if (isSelected) palette.primary.copy(alpha = 0.1f) else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, borderColor, CircleShape)
                        .background(if (isSelected) palette.primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(palette.background))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    label, color = if (isSelected) palette.primary else palette.textDim,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                color = palette.textDim,
                fontSize = 14.sp,
                lineHeight = 13.sp,
            )
        }
    }
}

@Composable
private fun BackupDialog(
    title: String,
    description: String,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmLabel: String,
    palette: VisionPalette,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = palette.primary) },
        text = {
            Column {
                Text(description, color = palette.textDim)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password, onValueChange = onPasswordChange,
                    label = { Text("Contraseña", color = palette.textDim) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = themedFieldColors(palette),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = palette.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = palette.textDim) }
        },
        containerColor = palette.bgCard,
    )
}
