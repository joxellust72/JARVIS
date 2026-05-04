// ============================================================
// VISION SOLDIER — Router Inteligente de Preguntas
// ============================================================

class SmartRouter {

  // Palabras clave que indican pregunta sobre el diario/personal
  personalKeywords = [
    "me dijiste", "recuerdas cuando", "qué me pasó", "cómo me fue",
    "mi diario", "lo que te conté", "me contaste", "cuándo fue que",
    "recuerdas que", "cuál es mi", "qué sé de", "mis metas",
    "lo que hablamos", "anteriormente", "dijiste que", "me recomendaste",
  ];

  // Palabras clave para guardar en el diario
  diaryKeywords = [
    "guarda esto", "anota", "recuerda que", "quiero que recuerdes",
    "hoy me pasó", "hoy tuve", "guárdalo en el diario", "agregar al diario",
    "nota:", "recordatorio:", "quiero que sepas que", "te cuento que",
    "escribe en el diario", "añade al diario",
  ];

  // Clasificar el tipo de mensaje
  async classify(message) {
    const lower = message.toLowerCase().trim();

    // 1. ¿Es una entrada para el diario?
    if (this.diaryKeywords.some((kw) => lower.includes(kw))) {
      return "diary_save";
    }

    // 2. ¿Es una pregunta sobre el historial personal?
    if (this.personalKeywords.some((kw) => lower.includes(kw))) {
      return "diary_query";
    }

    // 3. Todo lo demás va a Gemini con contexto
    return "gemini";
  }

  // Procesar mensaje según su tipo
  async process(message) {
    const type = await this.classify(message);

    switch (type) {
      case "diary_save":
        return await this.handleDiarySave(message);

      case "diary_query":
        return await this.handleDiaryQuery(message);

      case "gemini":
      default:
        return await this.handleGemini(message);
    }
  }

  // ─── Guardar en el diario ────────────────────────────────────
  async handleDiarySave(message) {
    try {
      const entry = await AI.extractDiaryEntry(message);
      const id = await DB.addDiaryEntry(entry);

      const responses = [
        `Registrado, señor. He añadido "${entry.title}" al diario con prioridad ${entry.importance}/5. Espero que esta vez no necesite que se lo recuerde.`,
        `Entrada guardada, señor. Categoría: ${entry.category}. El registro está en el diario cuando lo necesite, que probablemente será pronto.`,
        `Anotado en el diario, señor. Título: "${entry.title}". A diferencia de su memoria, yo no olvido.`,
        `Confirmado, señor. Guardado en el diario bajo la categoría "${entry.category}". En el futuro, puede que este momento le resulte más significativo de lo que parece ahora.`,
      ];

      const response = responses[Math.floor(Math.random() * responses.length)];

      // Guardar también en conversación
      await DB.addMessage("user", message, AI.sessionId);
      await DB.addMessage("vision", response, AI.sessionId);

      // Notificar a la UI para actualizar el diario
      document.dispatchEvent(new CustomEvent("diary:updated"));

      return { text: response, type: "diary_save", entryId: id };
    } catch (error) {
      console.error("Error guardando en diario:", error);
      return { text: "Hubo un problema al guardar en el diario, señor. Los sistemas están siendo... cooperativos en formas inesperadas.", type: "error" };
    }
  }

  // ─── Consultar el diario ─────────────────────────────────────
  async handleDiaryQuery(message) {
    const entries = await DB.getAllDiaryEntries();

    if (!entries.length) {
      const response = PERSONALITY.noMemoryResponses[Math.floor(Math.random() * PERSONALITY.noMemoryResponses.length)];
      await DB.addMessage("user", message, AI.sessionId);
      await DB.addMessage("vision", response, AI.sessionId);
      return { text: response, type: "diary_query" };
    }

    // Construir contexto del diario y dejar que Gemini responda
    const diaryText = entries.slice(0, 20).map((e) => {
      const date = new Date(e.date).toLocaleDateString("es-MX");
      return `[${date}] ${e.category?.toUpperCase()}: ${e.title ? e.title + " — " : ""}${e.content}`;
    }).join("\n");

    const augmented = `[Consultando el diario personal del señor]\n\nPregunta: ${message}\n\nEntradas disponibles:\n${diaryText}`;
    const response = await AI.ask(augmented);
    return { text: response, type: "diary_query" };
  }

  // ─── Respuesta con Gemini ────────────────────────────────────
  async handleGemini(message) {
    const response = await AI.ask(message);
    return { text: response, type: "gemini" };
  }
}

// Instancia global
const ROUTER = new SmartRouter();
