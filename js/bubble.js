// ============================================================
// VISION SOLDIER — Burbuja Flotante & Sistema de Comentarios
// ============================================================

class VisionBubble {
  constructor() {
    // Timers
    this.reminderInterval  = null;
    this.watchdogInterval  = null;
    this.debateInterval    = null;
    this.idleInterval      = null;
    this._speechTimer      = null;

    // Intervalos (ms)
    this.REMINDER_INTERVAL = 12 * 60 * 1000; // 12 minutos entre comentarios regulares
    this.WATCHDOG_INTERVAL = 30 * 1000;       // 30 seg — guardián de voz
    this.IDLE_THRESHOLD    = 20 * 60 * 1000;  // 20 min de inactividad en la app

    // Estado
    this.minimized         = false;
    this.isDragging        = false;
    this.speechVisible     = false;
    this.wakeLock          = null;
    this._debateFiredDate  = null; // evita disparar el debate dos veces en el mismo día

    // Rotación de tipos de comentario (el "debate" se gestiona aparte)
    this.COMMENT_TYPES     = ['curiosity', 'news', 'english_word'];
    this.lastTypeIndex     = -1;

    // Caché en memoria para el día actual
    this._cache            = {};

    // Elementos del DOM
    this.el          = null;
    this.speechEl    = null;
    this.speechTitle = null;
    this.speechText  = null;
    this.statusDot   = null;
  }

  // ── Inicialización ───────────────────────────────────────────

  async init() {
    this.el          = document.getElementById('vision-bubble');
    this.speechEl    = document.getElementById('bubble-speech');
    this.speechTitle = document.getElementById('bubble-speech-title');
    this.speechText  = document.getElementById('bubble-speech-text');
    this.statusDot   = document.getElementById('bubble-status-dot');

    if (!this.el) return;

    this.bindEvents();
    this.listenVoiceEvents();
    this.listenAppEvents();

    await this.requestWakeLock();
    await this.requestNotificationPermission();

    this.listenVisibility();
    this.startWatchdog();
    this.startReminderLoop();
    this.startDebateScheduler();
    this.startIdleDetection();
  }

  // ── Wake Lock ────────────────────────────────────────────────

  async requestWakeLock() {
    if (!('wakeLock' in navigator)) return;
    try {
      this.wakeLock = await navigator.wakeLock.request('screen');
      this.wakeLock.addEventListener('release', () => { this.wakeLock = null; });
    } catch (_) { /* no crítico */ }
  }

  // ── Notificaciones del sistema ───────────────────────────────

  async requestNotificationPermission() {
    if (!('Notification' in window) || Notification.permission !== 'default') return;
    try { await Notification.requestPermission(); } catch (_) { /* no crítico */ }
  }

  sendSystemNotification(title, body) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    try {
      new Notification(`VISION: ${title}`, {
        body,
        icon: './icons/icon-192.png',
        badge: './icons/icon-192.png',
        silent: false,
      });
    } catch (_) { /* no crítico */ }
  }

  // ── Page Visibility ──────────────────────────────────────────

  listenVisibility() {
    document.addEventListener('visibilitychange', async () => {
      if (document.visibilityState !== 'visible') return;
      if (!this.wakeLock) await this.requestWakeLock();
      if (CONFIG.VOICE_ENABLED && !VOICE.isListening && !VOICE.isSpeaking) {
        setTimeout(() => VOICE.startContinuous(), 600);
      }
    });
  }

  // ── Watchdog — garantiza que la voz nunca se apague ─────────

  startWatchdog() {
    this.watchdogInterval = setInterval(() => {
      const busy = typeof APP !== 'undefined' && APP.isProcessing;
      if (CONFIG.VOICE_ENABLED && !VOICE.isListening && !VOICE.isSpeaking && !busy) {
        VOICE.startContinuous();
      }
    }, this.WATCHDOG_INTERVAL);
  }

  // ── Detección de inactividad (app abierta, sin conv 20 min) ──

  startIdleDetection() {
    this.idleInterval = setInterval(() => {
      if (this.minimized) return; // Si está minimizado, el loop regular lo cubre
      if (document.visibilityState !== 'visible') return;
      if (typeof APP === 'undefined') return;

      const lastMsg = APP.lastMessageTime || 0;
      const idleMs  = Date.now() - lastMsg;

      if (idleMs >= this.IDLE_THRESHOLD) {
        // Reset para no repetir inmediatamente
        APP.lastMessageTime = Date.now();
        this.triggerComment('in-app');
      }
    }, 60 * 1000); // Revisar cada minuto
  }

  // ── Comentarios en loop (modo mini) ─────────────────────────

  startReminderLoop() {
    this.reminderInterval = setInterval(() => {
      if (this.minimized) this.triggerComment('bubble');
    }, this.REMINDER_INTERVAL);
  }

  // ── Debate a las 12:15 PM ────────────────────────────────────

  startDebateScheduler() {
    this.debateInterval = setInterval(() => {
      const now = new Date();
      const today = now.toDateString();
      if (now.getHours() === 12 && now.getMinutes() === 15 && this._debateFiredDate !== today) {
        this._debateFiredDate = today;
        const delivery = this.minimized ? 'bubble' : 'in-app';
        this.triggerComment(delivery, 'debate');
      }
    }, 60 * 1000); // Revisar cada minuto
  }

  // ── Lógica de comentarios ────────────────────────────────────

  getNextType() {
    // Rotar entre los 3 tipos regulares sin repetir el mismo consecutivo
    const available = this.COMMENT_TYPES.filter((_, i) => i !== this.lastTypeIndex);
    const pick = available[Math.floor(Math.random() * available.length)];
    this.lastTypeIndex = this.COMMENT_TYPES.indexOf(pick);
    return pick;
  }

  async triggerComment(delivery = 'in-app', forceType = null) {
    const type = forceType || this.getNextType();
    try {
      const { title, text } = await this.generateComment(type);
      if (!text) return;
      this.deliver(title, text, type, delivery);
    } catch (e) {
      console.warn('BUBBLE: Error en triggerComment:', e);
    }
  }

  async generateComment(type) {
    const today = new Date().toDateString();
    const cacheKey = `${type}_${today}`;

    // Caché en memoria para no re-generar el mismo día el mismo tipo
    if (this._cache[cacheKey]) return this._cache[cacheKey];

    // Revisar caché en IndexedDB
    const [cachedText, cachedDate] = await Promise.all([
      DB.getProfile(`comment_${type}`),
      DB.getProfile(`comment_${type}_date`),
    ]);
    if (cachedDate === today && cachedText) {
      const result = this._buildResult(type, cachedText);
      this._cache[cacheKey] = result;
      return result;
    }

    // Generar nuevo comentario via Gemini
    const prompt = this._buildPrompt(type);
    try {
      const resp = await fetch(`${CONFIG.GEMINI_API_URL}?key=${CONFIG.GEMINI_API_KEY}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          contents: [{ role: 'user', parts: [{ text: prompt }] }],
          generationConfig: { temperature: 0.85, maxOutputTokens: 180 },
        }),
      });
      const data = await resp.json();
      const text = data.candidates?.[0]?.content?.parts?.[0]?.text?.trim();

      if (text) {
        await Promise.all([
          DB.setProfile(`comment_${type}`, text),
          DB.setProfile(`comment_${type}_date`, today),
        ]);
        const result = this._buildResult(type, text);
        this._cache[cacheKey] = result;
        return result;
      }
    } catch (e) {
      console.warn('BUBBLE: Error generando comentario:', e);
    }

    return { title: 'VISION', text: null };
  }

  _buildPrompt(type) {
    const dateStr = new Date().toLocaleDateString('es-MX', {
      weekday: 'long', day: 'numeric', month: 'long',
    });
    const prompts = {
      curiosity:
        `Dame UN dato curioso, científico o histórico sorprendente. ` +
        `1-2 oraciones en español, estilo JARVIS sarcástico y elegante. Sin asteriscos ni markdown.`,

      news:
        `Hoy es ${dateStr}. Cuéntame sobre un tema o evento interesante del mundo — ` +
        `puede ser algo histórico de este día o un tema de actualidad relevante. ` +
        `1-2 oraciones en español, estilo JARVIS. Sin markdown.`,

      english_word:
        `Dame UNA palabra en inglés interesante o poco común. Incluye: ` +
        `1) la palabra en inglés, 2) pronunciación aproximada en español, ` +
        `3) significado en español, 4) un ejemplo breve de uso en contexto. ` +
        `Responde de forma natural en español, estilo JARVIS sarcástico. Sin markdown.`,

      debate:
        `Son las 12:15 PM — momento perfecto para reflexionar. ` +
        `Dame UN tema profundo de conversación, filosófico o de debate intelectual. ` +
        `Preséntalo como una pregunta o premisa que invite a pensar. ` +
        `2-3 oraciones en español, estilo JARVIS. Sin markdown.`,
    };
    return prompts[type] || prompts.curiosity;
  }

  _buildResult(type, text) {
    const titles = {
      curiosity:    '¿SABÍAS QUE...?',
      news:         'TEMA DEL DÍA',
      english_word: 'PALABRA EN INGLÉS',
      debate:       'REFLEXIÓN · 12:15 PM',
    };
    return { title: titles[type] || 'VISION', text };
  }

  // ── Entrega del comentario ───────────────────────────────────

  deliver(title, text, type, delivery) {
    if (delivery === 'bubble') {
      // Mostrar en la burbuja flotante
      this.showSpeech(title, text, type);
      // Notificación del sistema si la app está en background
      if (document.visibilityState !== 'visible') {
        this.sendSystemNotification(title, text);
      }
    } else {
      // Entregar dentro de la app (speak + chat)
      document.dispatchEvent(new CustomEvent('bubble:comment', {
        detail: { title, text, type },
      }));
    }
  }

  // ── Speech bubble ────────────────────────────────────────────

  showSpeech(title, text, type = 'info') {
    if (!this.speechEl) return;
    if (this.speechTitle) this.speechTitle.textContent = title;
    if (this.speechText)  this.speechText.textContent  = text;

    const typeClass = type === 'debate' ? 'debate' : type === 'english_word' ? 'english' : type;
    this.speechEl.className = `bubble-speech ${typeClass} visible`;
    this.speechVisible = true;

    clearTimeout(this._speechTimer);
    this._speechTimer = setTimeout(() => this.hideSpeech(), 14000);
  }

  hideSpeech() {
    if (this.speechEl) this.speechEl.classList.remove('visible');
    this.speechVisible = false;
  }

  // ── Minimizar / Restaurar ────────────────────────────────────

  minimize() {
    document.body.classList.add('app-minimized');
    this.el?.classList.add('mode-mini');
    this.minimized = true;
    // Disparar primer comentario rápido al entrar en modo mini
    setTimeout(() => this.triggerComment('bubble'), 1500);
  }

  restore() {
    document.body.classList.remove('app-minimized');
    this.el?.classList.remove('mode-mini');
    this.minimized = false;
    this.hideSpeech();
    if (typeof APP !== 'undefined') APP.lastMessageTime = Date.now();
  }

  // ── Eventos ──────────────────────────────────────────────────

  bindEvents() {
    const orbClick = document.getElementById('bubble-orb-click');
    orbClick?.addEventListener('click', () => {
      if (this.isDragging) return;
      if (this.minimized) {
        this.restore();
      } else if (this.speechVisible) {
        this.hideSpeech();
      } else {
        this.triggerComment('bubble');
      }
    });

    orbClick?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') orbClick.click();
    });

    document.getElementById('bubble-speech-close')?.addEventListener('click', (e) => {
      e.stopPropagation();
      this.hideSpeech();
    });

    document.getElementById('minimize-btn')?.addEventListener('click', () => this.minimize());

    this.setupDrag();
  }

  listenVoiceEvents() {
    document.addEventListener('voice:listening',  () => this.updateDot('listening'));
    document.addEventListener('voice:woken',      () => this.updateDot('woken'));
    document.addEventListener('voice:speakStart', () => this.updateDot('speaking'));
    document.addEventListener('voice:speakEnd',   () => this.updateDot('listening'));
    document.addEventListener('voice:reset',      () => this.updateDot('listening'));
  }

  listenAppEvents() {
    // Cuando el usuario envía un mensaje, resetear el idle timer
    document.addEventListener('app:message', () => {
      if (typeof APP !== 'undefined') APP.lastMessageTime = Date.now();
    });
  }

  updateDot(state) {
    if (!this.statusDot) return;
    this.statusDot.className = `bubble-status-dot state-${state}`;
  }

  // ── Drag ─────────────────────────────────────────────────────

  setupDrag() {
    if (!this.el) return;
    let startX = 0, startY = 0, startLeft = 0, startTop = 0, moved = false;
    const clamp = (v, min, max) => Math.min(Math.max(v, min), max);

    const onMove = (cx, cy) => {
      if (Math.abs(cx - startX) > 4 || Math.abs(cy - startY) > 4) moved = true;
      if (!moved) return;
      this.isDragging = true;
      const newL = clamp(startLeft + cx - startX, 0, window.innerWidth  - this.el.offsetWidth);
      const newT = clamp(startTop  + cy - startY, 0, window.innerHeight - this.el.offsetHeight);
      this.el.style.left = `${newL}px`; this.el.style.top = `${newT}px`;
      this.el.style.right = 'auto';     this.el.style.bottom = 'auto';
    };

    const onEnd = () => {
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup',   onEnd);
      document.removeEventListener('touchmove', onTouchMove);
      document.removeEventListener('touchend',  onEnd);
      setTimeout(() => { this.isDragging = false; }, 80);
    };

    const onMouseMove = (e) => onMove(e.clientX, e.clientY);
    const onTouchMove = (e) => { e.preventDefault(); onMove(e.touches[0].clientX, e.touches[0].clientY); };

    const onStart = (cx, cy) => {
      moved = false; startX = cx; startY = cy;
      const r = this.el.getBoundingClientRect();
      startLeft = r.left; startTop = r.top;
    };

    this.el.addEventListener('mousedown', (e) => {
      onStart(e.clientX, e.clientY);
      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup',   onEnd);
    });

    this.el.addEventListener('touchstart', (e) => {
      onStart(e.touches[0].clientX, e.touches[0].clientY);
      document.addEventListener('touchmove', onTouchMove, { passive: false });
      document.addEventListener('touchend',  onEnd);
    }, { passive: true });
  }
}

const BUBBLE = new VisionBubble();
