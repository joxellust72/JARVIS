// ============================================================
// VISION SOLDIER — Motor de IA (Gemini API)
// ============================================================

class VisionAI {
  constructor() {
    this.sessionId = `session_${Date.now()}`;
  }

  // Construye el array de mensajes para Gemini con el historial
  async buildMessages(userMessage) {
    const recentMessages = await DB.getRecentMessages(CONFIG.MAX_HISTORY_MESSAGES);
    const diaryContext = await DB.buildDiaryContext();
    const systemPrompt = PERSONALITY.getSystemPrompt(diaryContext);

    // Formato de Gemini: systemInstruction + messages
    const history = recentMessages.map((msg) => ({
      role: msg.role === "vision" ? "model" : "user",
      parts: [{ text: msg.content }],
    }));

    return { systemPrompt, history, userMessage };
  }

  // Llama a Gemini API
  async ask(userMessage) {
    try {
      const { systemPrompt, history, userMessage: msg } = await this.buildMessages(userMessage);

      const payload = {
        system_instruction: {
          parts: [{ text: systemPrompt }],
        },
        contents: [
          ...history,
          { role: "user", parts: [{ text: msg }] },
        ],
        generationConfig: {
          maxOutputTokens: CONFIG.MAX_RESPONSE_TOKENS,
          temperature: 0.85,
          topP: 0.94,
        },
        safetySettings: [
          { category: "HARM_CATEGORY_HARASSMENT", threshold: "BLOCK_NONE" },
          { category: "HARM_CATEGORY_HATE_SPEECH", threshold: "BLOCK_NONE" },
          { category: "HARM_CATEGORY_SEXUALLY_EXPLICIT", threshold: "BLOCK_MEDIUM_AND_ABOVE" },
          { category: "HARM_CATEGORY_DANGEROUS_CONTENT", threshold: "BLOCK_NONE" },
        ],
      };

      const response = await fetch(
        `${CONFIG.GEMINI_API_URL}?key=${CONFIG.GEMINI_API_KEY}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        }
      );

      if (!response.ok) {
        const err = await response.json();
        console.error("Gemini Error:", err);
        throw new Error(err.error?.message || "Error en la API de Gemini");
      }

      const data = await response.json();
      const text = data.candidates?.[0]?.content?.parts?.[0]?.text;

      if (!text) throw new Error("Respuesta vacía de Gemini");

      // Guardar el intercambio en la DB
      await DB.addMessage("user", userMessage, this.sessionId);
      await DB.addMessage("vision", text, this.sessionId);

      return text;
    } catch (error) {
      console.error("Error en VisionAI.ask:", error);
      throw error;
    }
  }

  // Detectar si un mensaje es una entrada de diario
  isDiaryEntry(message) {
    const diaryTriggers = [
      "guarda esto", "anota", "recuerda que", "quiero que recuerdes",
      "hoy me pasó", "hoy tuve", "guárdalo en el diario", "agregar al diario",
      "nota:", "recordatorio:", "quiero que sepas que", "te cuento que",
    ];
    const lower = message.toLowerCase();
    return diaryTriggers.some((trigger) => lower.includes(trigger));
  }

  // Extraer contenido de una entrada de diario del mensaje
  async extractDiaryEntry(message) {
    const prompt = `El usuario te dijo: "${message}"
    
Extrae la información relevante para crear una entrada de diario.
Responde ÚNICAMENTE con un JSON válido con esta estructura exacta:
{
  "title": "título breve de máximo 6 palabras",
  "content": "el contenido completo relevante",
  "category": "trabajo|personal|salud|meta|otros",
  "importance": 3,
  "tags": ["tag1", "tag2"]
}
Solo el JSON, sin explicaciones ni texto adicional.`;

    try {
      const response = await fetch(
        `${CONFIG.GEMINI_API_URL}?key=${CONFIG.GEMINI_API_KEY}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            contents: [{ role: "user", parts: [{ text: prompt }] }],
            generationConfig: { temperature: 0.2, maxOutputTokens: 300 },
          }),
        }
      );
      const data = await response.json();
      const text = data.candidates?.[0]?.content?.parts?.[0]?.text || "{}";
      const clean = text.replace(/```json|```/g, "").trim();
      return JSON.parse(clean);
    } catch (e) {
      return {
        title: "Nueva entrada",
        content: message,
        category: "personal",
        importance: 3,
        tags: [],
      };
    }
  }
}

// Instancia global
const AI = new VisionAI();
