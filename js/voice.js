// ============================================================
// VISION SOLDIER — Sistema de Voz con Wake Word "JARVIS"
// ============================================================

class VisionVoice {
  constructor() {
    this.synth = window.speechSynthesis;
    this.recognition = null;
    this.isListening = false;
    this.isSpeaking = false;
    this.isWoken = false;           // ¿Se dijo "Jarvis"?
    this.selectedVoice = null;
    this.useElevenLabs = !!CONFIG.ELEVENLABS_API_KEY;
    this.restartTimer = null;
    this.commandBuffer = "";
    this.wokenTimer = null;         // Timer para volver a modo espera
    this.WOKEN_TIMEOUT = 8000;     // 8 segundos para capturar comando tras "Jarvis"

    // Variantes del wake word
    this.WAKE_WORDS = ["jarvis", "jarvis", "jarvis!", "yarvis", "yarvis,", "jarwis", "jarvís", "harvis"];

    this.init();
  }

  // ── Inicialización ─────────────────────────────────────────

  async init() {
    if (this.synth) {
      speechSynthesis.onvoiceschanged = () => this.selectBestVoice();
      // Retry voice selection a couple of times (Android WebView can be slow)
      this.selectBestVoice();
      setTimeout(() => this.selectBestVoice(), 800);
      setTimeout(() => this.selectBestVoice(), 2500);
    }
    this.setupRecognition();
  }

  selectBestVoice() {
    const voices = this.synth.getVoices();
    if (!voices.length) return;

    // Palabras clave de voces femeninas a evitar
    const femaleKws = /female|mujer|zira|susan|hazel|kate|heather|samantha|victoria|anna|cortana|microsoft.*elena|microsoft.*sabina/i;

    const priorities = [
      // ElevenLabs se maneja aparte — aquí priorizamos Web Speech
      // 1. Voces masculinas en español (ideales para respuestas en es-MX)
      (v) => v.lang.startsWith("es") && /google/i.test(v.name) && !femaleKws.test(v.name),
      (v) => v.lang.startsWith("es") && /pablo|jorge|diego|carlos|miguel|antonio/i.test(v.name),
      (v) => v.lang.startsWith("es") && !femaleKws.test(v.name),
      // 2. Voces masculinas en inglés (profundas, conocidas)
      (v) => /google uk english male/i.test(v.name),
      (v) => /microsoft david|microsoft mark|microsoft james|microsoft george/i.test(v.name),
      (v) => /^david$|^james$|^daniel$|^thomas$|^oliver$|^paul$/i.test(v.name),
      (v) => v.lang === "en-GB" && !femaleKws.test(v.name),
      (v) => v.lang.startsWith("en") && !femaleKws.test(v.name),
      // 3. Cualquier voz no femenina
      (v) => !femaleKws.test(v.name),
    ];

    for (const check of priorities) {
      const found = voices.find(check);
      if (found) { this.selectedVoice = found; return; }
    }
    if (!this.selectedVoice && voices.length) this.selectedVoice = voices[0];
  }

  // ── Web Speech Recognition Setup ───────────────────────────

  setupRecognition() {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      console.warn("SpeechRecognition no disponible en este navegador.");
      return;
    }

    this.recognition = new SR();
    this.recognition.lang = "es-MX";
    this.recognition.continuous = true;        // Siempre escuchando
    this.recognition.interimResults = true;    // Resultados parciales
    this.recognition.maxAlternatives = 3;

    this.recognition.onstart = () => {
      this.isListening = true;
      document.dispatchEvent(new CustomEvent("voice:listening", { detail: true }));
    };

    this.recognition.onend = () => {
      this.isListening = false;
      // Auto-reiniciar si no está hablando (mantener siempre activo)
      if (!this.isSpeaking && CONFIG.VOICE_ENABLED) {
        this.restartTimer = setTimeout(() => this.startContinuous(), 400);
      }
    };

    this.recognition.onerror = (e) => {
      // Ignorar errores comunes y reiniciar
      if (["no-speech", "aborted", "network"].includes(e.error)) {
        if (!this.isSpeaking && CONFIG.VOICE_ENABLED) {
          clearTimeout(this.restartTimer);
          this.restartTimer = setTimeout(() => this.startContinuous(), 600);
        }
      } else {
        console.warn("Recognition error:", e.error);
      }
    };

    this.recognition.onresult = (event) => {
      this.handleResult(event);
    };
  }

  // ── Iniciar escucha continua ────────────────────────────────

  startContinuous() {
    if (!this.recognition || this.isListening || this.isSpeaking) return;
    try {
      this.recognition.start();
    } catch (e) {
      // Ya está corriendo
    }
  }

  stopContinuous() {
    clearTimeout(this.restartTimer);
    if (this.recognition && this.isListening) {
      try { this.recognition.stop(); } catch(e) {}
    }
    this.isListening = false;
  }

  // ── Procesamiento de resultados ────────────────────────────

  handleResult(event) {
    let interimText = "";
    let finalText = "";

    for (let i = event.resultIndex; i < event.results.length; i++) {
      const result = event.results[i];
      const transcript = result[0].transcript.toLowerCase().trim();

      // Verificar alternativas también
      const alternatives = Array.from({ length: result.length }, (_, j) =>
        result[j].transcript.toLowerCase().trim()
      );
      const allText = [transcript, ...alternatives].join(" ");

      if (result.isFinal) {
        finalText += result[0].transcript.trim();
      } else {
        interimText += result[0].transcript.trim();
      }

      // ── Detectar wake word ──────────────────────────────
      if (!this.isWoken) {
        const detected = this.WAKE_WORDS.some(ww =>
          allText.includes(ww) || transcript.startsWith(ww)
        );

        if (detected) {
          // Extraer comando después del wake word
          let command = this.extractCommandAfterWakeWord(result[0].transcript);
          this.onWakeWord(command);
        }
      } else {
        // Ya está woken — acumular comando
        if (result.isFinal && finalText.trim()) {
          this.onCommand(finalText.trim());
        }
      }
    }

    // Mostrar transcripción en tiempo real
    const displayText = interimText || finalText;
    if (displayText) {
      document.dispatchEvent(new CustomEvent("voice:interim", { detail: displayText }));
    }
  }

  extractCommandAfterWakeWord(transcript) {
    const lower = transcript.toLowerCase();
    for (const ww of this.WAKE_WORDS) {
      const idx = lower.indexOf(ww);
      if (idx !== -1) {
        const after = transcript.substring(idx + ww.length).trim();
        // Limpiar signos de puntuación al inicio
        return after.replace(/^[,.:!?;\s]+/, "").trim();
      }
    }
    return "";
  }

  // ── Wake word detectado ─────────────────────────────────────

  onWakeWord(immediateCommand = "") {
    this.isWoken = true;
    clearTimeout(this.wokenTimer);

    document.dispatchEvent(new CustomEvent("voice:woken", { detail: { command: immediateCommand } }));

    if (immediateCommand && immediateCommand.length > 2) {
      // Hay comando inmediato — procesar ya
      setTimeout(() => this.onCommand(immediateCommand), 300);
    } else {
      // Esperar comando por N segundos
      this.wokenTimer = setTimeout(() => {
        if (this.isWoken) this.resetWakeState();
      }, this.WOKEN_TIMEOUT);
    }
  }

  onCommand(command) {
    if (!command || command.trim().length < 2) {
      this.resetWakeState();
      return;
    }
    clearTimeout(this.wokenTimer);
    this.isWoken = false;

    document.dispatchEvent(new CustomEvent("voice:command", { detail: command.trim() }));
  }

  resetWakeState() {
    this.isWoken = false;
    clearTimeout(this.wokenTimer);
    document.dispatchEvent(new CustomEvent("voice:reset"));
  }

  // ── Síntesis de Voz ────────────────────────────────────────

  async speak(text) {
    if (!CONFIG.VOICE_ENABLED || !text) return;

    const clean = text
      .replace(/\*\*(.*?)\*\*/g, "$1")
      .replace(/\*(.*?)\*/g, "$1")
      .replace(/#{1,6}\s/g, "")
      .replace(/`(.*?)`/g, "$1")
      .trim();

    this.isSpeaking = true;
    this.stopContinuous();
    document.dispatchEvent(new CustomEvent("voice:speakStart"));

    if (this.useElevenLabs) {
      await this.speakElevenLabs(clean);
    } else {
      await this.speakWebSpeech(clean);
    }

    this.isSpeaking = false;
    document.dispatchEvent(new CustomEvent("voice:speakEnd"));

    // Reactivar escucha
    if (CONFIG.VOICE_ENABLED) {
      setTimeout(() => this.startContinuous(), 600);
    }
  }

  async speakElevenLabs(text) {
    try {
      const response = await fetch(
        `${CONFIG.ELEVENLABS_API_URL}/${CONFIG.ELEVENLABS_VOICE_ID}`,
        {
          method: "POST",
          headers: { "Accept": "audio/mpeg", "Content-Type": "application/json", "xi-api-key": CONFIG.ELEVENLABS_API_KEY },
          body: JSON.stringify({
            text,
            model_id: "eleven_multilingual_v2",
            voice_settings: { stability: 0.72, similarity_boost: 0.85, style: 0.3, use_speaker_boost: true },
          }),
        }
      );
      if (!response.ok) throw new Error("ElevenLabs falló");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      await this.playAudio(url);
    } catch {
      await this.speakWebSpeech(text);
    }
  }

  speakWebSpeech(text) {
    return new Promise((resolve) => {
      this.synth.cancel();
      // Dividir texto largo en frases para mejor fluidez
      const chunks = this.splitText(text, 200);
      let i = 0;
      const speakNext = () => {
        if (i >= chunks.length) { resolve(); return; }
        const utt = new SpeechSynthesisUtterance(chunks[i++]);
        utt.voice = this.selectedVoice;
        utt.rate = CONFIG.TTS_RATE;
        utt.pitch = CONFIG.TTS_PITCH;
        utt.volume = CONFIG.TTS_VOLUME;
        utt.lang = "es-MX";
        utt.onend = speakNext;
        utt.onerror = speakNext;
        this.synth.speak(utt);
      };
      speakNext();
    });
  }

  splitText(text, maxLen) {
    if (text.length <= maxLen) return [text];
    const chunks = [];
    const sentences = text.split(/(?<=[.!?])\s+/);
    let current = "";
    for (const s of sentences) {
      if ((current + s).length > maxLen && current) { chunks.push(current.trim()); current = ""; }
      current += s + " ";
    }
    if (current.trim()) chunks.push(current.trim());
    return chunks.length ? chunks : [text];
  }

  playAudio(url) {
    return new Promise((resolve) => {
      const audio = new Audio(url);
      audio.onended = () => { URL.revokeObjectURL(url); resolve(); };
      audio.onerror = () => { URL.revokeObjectURL(url); resolve(); };
      audio.play().catch(resolve);
    });
  }

  stopSpeaking() {
    this.synth?.cancel();
    this.isSpeaking = false;
  }
}

// Instancia global
const VOICE = new VisionVoice();
