package com.visionsoldier.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionsoldier.app.data.entity.Profile
import com.visionsoldier.app.ui.theme.*

@Composable
fun OnboardingScreen(
    profile: Profile?,
    onSave: (Profile) -> Unit
) {
    val palette = LocalVisionPalette.current
    var name by remember { mutableStateOf(profile?.name ?: "") }
    var profession by remember { mutableStateOf(profile?.profession ?: "") }
    var interests by remember { mutableStateOf(profile?.interests ?: "") }
    var traits by remember { mutableStateOf(profile?.traits ?: "amable, servicial") }
    var password by remember { mutableStateOf(profile?.diaryPassword ?: "") }
    var apiKey by remember { mutableStateOf(profile?.apiKey ?: "") }

    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "INICIALIZANDO PROTOCOLO",
                color = palette.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Para activar a VISION, por favor configure sus credenciales y preferencias.",
                color = palette.textDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // API KEY
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key (Requerido)", color = palette.accent) },
                colors = textFieldColors(palette),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "La clave se guardará de forma segura en su dispositivo.",
                color = palette.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp).align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Su Nombre", color = palette.textDim) },
                colors = textFieldColors(palette),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = profession,
                onValueChange = { profession = it },
                label = { Text("Profesión / Ocupación", color = palette.textDim) },
                colors = textFieldColors(palette),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = interests,
                onValueChange = { interests = it },
                label = { Text("Intereses (ej. anime, tecnología)", color = palette.textDim) },
                colors = textFieldColors(palette),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña del Diario (Opcional)", color = palette.textDim) },
                visualTransformation = PasswordVisualTransformation(),
                colors = textFieldColors(palette),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                    ))
                },
                enabled = apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    disabledContainerColor = palette.primary.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "ACTIVAR SISTEMA",
                    color = palette.background,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun textFieldColors(palette: VisionPalette) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = palette.primary, unfocusedBorderColor = palette.textDim,
    cursorColor = palette.primary, focusedTextColor = palette.primary,
    unfocusedTextColor = palette.textDim
)
