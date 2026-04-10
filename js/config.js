// ============================================================
// VISION SOLDIER — Configuración Central
// ============================================================

const CONFIG = {
  // --- APIs ---
  GEMINI_API_KEY: "AIzaSyDfe7yellqXpR4rmxLxyMuM9435cdTzpxU",
  GEMINI_MODEL: "gemini-1.5-flash",
  GEMINI_API_URL: "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",

  ELEVENLABS_API_KEY: "", // Agregar cuando tengas la key de ElevenLabs
  ELEVENLABS_VOICE_ID: "pNInz6obpgDQGcFmaJgB", // Adam - voz masculina elegante
  ELEVENLABS_API_URL: "https://api.elevenlabs.io/v1/text-to-speech",

  // --- App ---
  APP_NAME: "VISION SOLDIER",
  APP_VERSION: "1.0.0",
  DB_NAME: "VisionSoldierDB",
  DB_VERSION: 1,

  // --- IA Settings ---
  MAX_HISTORY_MESSAGES: 10,     // Últimos N mensajes en el contexto (ahorro de tokens)
  MAX_DIARY_CONTEXT: 8,          // Máximo de entradas del diario en el contexto
  MAX_RESPONSE_TOKENS: 1024,     // Máximo de tokens en respuesta

  // --- Voz ---
  VOICE_ENABLED: true,
  TTS_RATE: 0.95,                // Velocidad de habla (Web Speech fallback)
  TTS_PITCH: 0.85,               // Tono de voz (más bajo = más Jarvis)
  TTS_VOLUME: 1.0,
};
