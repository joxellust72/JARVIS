# VISION SOLDIER — App Android Nativa

Aplicación Android nativa en **Kotlin + Jetpack Compose** que convierte VISION SOLDIER en una app con control real del dispositivo.

## ✅ Capacidades

| Comando de voz | Acción |
|---|---|
| *"Jarvis, abre WhatsApp"* | Lanza la app |
| *"Jarvis, manda mensaje a Mamá: llegué"* | Abre WhatsApp con el mensaje |
| *"Jarvis, busca recetas de pasta"* | Búsqueda en Google |
| *"Jarvis, llama a Juan"* | Abre el marcador |
| *"Jarvis, pon una alarma a las 7"* | Configura alarma |
| *"Jarvis, reproduce música de The Weeknd"* | Abre Spotify/YouTube |
| *"Jarvis, escribe en WhatsApp a Luz: te llamo"* | **AccessibilityService** — escribe y envía sin tocar |
| *"Jarvis, borra el chat con Luz"* | **AccessibilityService** — elimina el chat |
| *"Jarvis, publica en Instagram: Buen día"* | **AccessibilityService** — escribe en Instagram |
| *"Jarvis, ve atrás"* | **AccessibilityService** — navegación global |
| *"Jarvis, guarda esto: hoy fue un buen día"* | **Room DB** — guarda en el diario |
| *"Jarvis, nueva tarea: comprar leche"* | **Room DB** — agrega tarea |
| *"Jarvis, mis tareas"* | **Room DB** — lista tareas pendientes |
| Cualquier otra cosa | Conversación con Gemini AI |

---

## 🚀 Cómo abrir en Android Studio

### Opción A — Recomendada

1. Instala [Android Studio](https://developer.android.com/studio) si no lo tienes
2. Abre Android Studio → **File > Open** → selecciona la carpeta `android/`
3. Espera que Gradle sincronice (descarga dependencias automáticamente)
4. Listo para compilar ▶️

### Opción B — Línea de comandos

1. Corre `download-wrapper.bat` para descargar el Gradle wrapper JAR
2. Abre una terminal en esta carpeta y ejecuta:
   ```
   gradlew.bat assembleDebug
   ```

---

## ⚙️ Configuración

### 1. API Key de Gemini

Abre [`VisionForegroundService.kt`](app/src/main/kotlin/com/visionsoldier/app/service/VisionForegroundService.kt) y reemplaza:

```kotlin
const val GEMINI_API_KEY = "TU_API_KEY_AQUI"
```

Con tu clave actual (la misma que usas en la web app).

### 2. Permisos que la app pedirá al instalar

- 🎙️ **Micrófono** — escucha continua para el wake word "Jarvis"
- 📳 **Notificaciones** — servicio persistente en segundo plano
- 🪟 **Mostrar sobre otras apps** — para la burbuja flotante real
- ♿ **Accesibilidad** — control total de la interfaz de otras apps (WhatsApp, Instagram, etc.)

### 3. Activar AccessibilityService

Para que VISION pueda escribir en WhatsApp/Instagram y controlar otras apps:

1. Ve a **Ajustes > Accesibilidad**
2. Busca **VISION SOLDIER**
3. Activa el servicio y acepta los permisos

---

## 🏗️ Arquitectura

```
VisionForegroundService  ←─  Siempre activo (micrófono + IA)
        │
        ├── SpeechEngine      ← Wake word "Jarvis" + TTS
        ├── GeminiClient      ← gemini-2.0-flash API
        ├── CommandRouter     ← Detecta intención del comando (16 tipos)
        ├── SystemActions     ← Ejecuta: abrir app, SMS, llamada, búsqueda...
        └── VisionRepository  ← Room Database (diario, tareas, conversaciones)

VisionAccessibilityService ←─  Control total de UI de otras apps
        │
        └── AccessibilityActions ← Escribir texto, click, scroll, navegar

ProactiveScheduler         ←─  WorkManager: comentarios cada 15 min + debate 12:15 PM
        │
        └── ProactiveWorker   ← Genera contenido con Gemini API

BubbleOverlayService       ←─  Burbuja flotante real (WindowManager)
        │                       Muestra comentarios proactivos
        └── Fade-in/out + drag + auto-dismiss

MainActivity               ←─  UI en Jetpack Compose (JARVIS style)
MainViewModel              ←─  Estado reactivo + Room flows
```

### Base de datos (Room)

```
vision_soldier.db
├── diary          — Entradas del diario (título, contenido, categoría, importancia)
├── tasks          — Tareas (texto, completada, timestamp)
├── conversations  — Historial de chat (rol, contenido, sesión)
└── proactive_cache — Caché de comentarios proactivos del día
```

---

## 📦 Dependencias principales

- **Jetpack Compose** — UI declarativa
- **OkHttp** — llamadas HTTP a Gemini API
- **Android SpeechRecognizer** — reconocimiento de voz continuo
- **TextToSpeech** — síntesis de voz JARVIS
- **Room Database** — persistencia local (diario, tareas, conversaciones)
- **WorkManager** — comentarios proactivos programados
- **DataStore Preferences** — almacenamiento local ligero
- **ForegroundService** — mantiene el app vivo en background
- **AccessibilityService** — control de UI de otras apps

---

## ✅ Mejoras implementadas

- [x] **AccessibilityService** — escribir en WhatsApp/Instagram, navegar, borrar chats, control total
- [x] **Diario y Tareas** — Room Database con UI Compose (pantallas dedicadas + navegación)
- [x] **Comentarios proactivos** — datos curiosos, noticias, debate 12:15 PM, palabras en inglés
- [x] **Widget/Burbuja** — burbuja flotante mejorada con comentarios proactivos

## 🔮 Próximas fases

- [ ] **Publicar en Play Store**
- [ ] **Widget de pantalla de inicio** — Android App Widget real
- [ ] **Paleta de colores** — migrar sistema de temas del web app

