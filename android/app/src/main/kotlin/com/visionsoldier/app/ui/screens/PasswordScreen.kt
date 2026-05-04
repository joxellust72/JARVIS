package com.visionsoldier.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.visionsoldier.app.ui.theme.*

@Composable
fun PasswordScreen(
    onUnlock: (String) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalVisionPalette.current
    var password by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(Icons.Default.Lock, "Bloqueado", tint = palette.primary, modifier = Modifier.size(64.dp))
            
            Text("ARCHIVO BLOQUEADO", color = palette.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Se requiere autorización para acceder al diario personal.", color = palette.textDim, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Contraseña", color = palette.textDim) },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.primary, unfocusedBorderColor = palette.textDim,
                    cursorColor = palette.primary, focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onBack) {
                    Text("CANCELAR", color = palette.textDim)
                }
                Button(
                    onClick = { onUnlock(password) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("AUTORIZAR", color = palette.background, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
