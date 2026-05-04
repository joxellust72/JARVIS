// ============================================================
// VISION SOLDIER — Controlador Principal (Rediseño Orbe)
// ============================================================

class VisionApp {
  constructor() {
    this.isProcessing    = false;
    this.currentView     = "main";
    this.drawerOpen      = false;
    this.lastMessageTime = Date.now();
    // Diary calendar state
    this.diaryViewMonth    = new Date();
    this.diarySelectedDate = new Date();
    // Tasks filter state
    this.taskFilter = "all";
  }

  // ── Inicialización ─────────────────────────────────────────

  async init() {
    await DB.init();
    this.bindUIEvents();
    this.bindVoiceEvents();
    this.setOrbState("listening");
    this.setWakeStatus("listening");

    // Cargar paleta y API key guardadas
    await this.loadSavedPalette();
    await this.loadApiKeyFromProfile();

    // Cargar historial de chat reciente
    await this.loadChatHistory();

    // Saludo inicial
    const greeting = PERSONALITY.getRandomGreeting();
    this.appendMessage("vision", greeting);
    this.setOrbStatus("Di \"Jarvis\" para comenzar");

    // Activar micrófono continuo
    setTimeout(() => { VOICE.startContinuous(); }, 1200);

    // Cargar perfil guardado
    await this.loadSavedProfile();

    // Renderizar tareas iniciales
    await this.renderTasks();

    // Notificación de inicio flotante
    setTimeout(() => {
      this.addFloatingNotification("SISTEMA ONLINE", "Todos los sistemas operativos. Esperando sus órdenes, señor.", "info", 8000);
    }, 1500);
  }

  // ── Cargar historial de chat desde IndexedDB ───────────────────

  async loadChatHistory() {
    try {
      const messages = await DB.getRecentMessages(30);
      if (!messages.length) return;
      const container = document.getElementById("chat-messages");
      if (!container) return;
      // Insert historical messages without animation
      messages.forEach(msg => {
        const wrapper = document.createElement("div");
        wrapper.className = `message ${msg.role}`;
        const bubble = document.createElement("div");
        bubble.className = "bubble";
        if (msg.role === "vision") {
          const lbl = document.createElement("div");
          lbl.className = "msg-label";
          lbl.textContent = "VISION";
          bubble.appendChild(lbl);
        }
        const content = document.createElement("div");
        content.className = "msg-content";
        content.textContent = msg.content;
        bubble.appendChild(content);

        // Timestamp
        const ts = document.createElement("div");
        ts.className = "msg-time";
        try {
          ts.textContent = new Date(msg.timestamp).toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit" });
        } catch (_) {}
        bubble.appendChild(ts);

        wrapper.appendChild(bubble);
        container.appendChild(wrapper);
      });
      container.scrollTop = container.scrollHeight;
    } catch (e) {
      console.warn("No se pudo cargar el historial:", e);
    }
  }

  // ── Cargar y guardar API key desde perfil ──────────────────────

  async loadApiKeyFromProfile() {
    const savedKey = await DB.getProfile("geminiApiKey");
    if (savedKey && savedKey.trim().length > 10) {
      CONFIG.GEMINI_API_KEY = savedKey.trim();
      CONFIG.GEMINI_API_URL = `https://generativelanguage.googleapis.com/v1beta/models/${CONFIG.GEMINI_MODEL}:generateContent`;
    }
  }

  // ── Estados del Orbe ─────────────────────────────────────────

  setOrbState(state) {
    // state: 'listening' | 'woken' | 'thinking' | 'speaking' | 'idle'
    const orb = document.getElementById("orb");
    if (!orb) return;
    orb.classList.remove("state-listening", "state-woken", "state-thinking", "state-speaking");
    if (state !== "idle") orb.classList.add(`state-${state}`);
  }

  setOrbStatus(text) {
    const el = document.getElementById("orb-status");
    if (el) el.textContent = text;
  }

  setOrbTranscript(text) {
    const el = document.getElementById("orb-transcript");
    if (el) el.textContent = text;
  }

  setWakeStatus(state) {
    // state: 'listening' | 'woken' | 'speaking' | 'off'
    const ws = document.getElementById("wake-status");
    const dot = document.getElementById("wake-dot");
    const label = document.getElementById("wake-label");
    if (!ws) return;
    ws.classList.remove("active", "woken", "speaking");

    const map = {
      listening:  { cls: "active",   label: "ESCUCHANDO",   dotCls: "active" },
      woken:      { cls: "woken",    label: "ACTIVADO",     dotCls: "woken" },
      speaking:   { cls: "speaking", label: "HABLANDO",     dotCls: "speaking" },
      thinking:   { cls: "woken",    label: "PROCESANDO",   dotCls: "woken" },
      off:        { cls: "",         label: "SILENCIADO",   dotCls: "" },
    };
    const cfg = map[state] || map.listening;
    if (cfg.cls) ws.classList.add(cfg.cls);
    if (label) label.textContent = cfg.label;
    if (dot) {
      dot.classList.remove("active", "woken", "speaking");
      if (cfg.dotCls) dot.classList.add(cfg.dotCls);
    }
  }

  // ── Procesamiento de mensajes ─────────────────────────────────

  async processMessage(text) {
    if (!text || !text.trim() || this.isProcessing) return;
    this.isProcessing = true;
    this.lastMessageTime = Date.now(); // Resetear idle timer
    document.dispatchEvent(new CustomEvent('app:message'));

    const inputEl = document.getElementById("chat-input");
    if (inputEl) inputEl.value = "";

    // Mostrar en chat
    this.appendMessage("user", text);
    this.showThinking();

    // Abrir drawer para mostrar la respuesta
    this.openDrawer();

    // Estados visuales
    this.setOrbState("thinking");
    this.setOrbStatus("Procesando...");
    this.setOrbTranscript("");
    this.setWakeStatus("thinking");

    try {
      const result = await ROUTER.process(text);
      this.hideThinking();
      this.appendMessage("vision", result.text);

      // Hablar
      this.setOrbState("speaking");
      this.setWakeStatus("speaking");
      this.setOrbStatus("VISION habla...");
      await VOICE.speak(result.text);

    } catch (err) {
      this.hideThinking();
      const e = (err.message || "").toString();
      let errMsg;
      if (e.includes("GEMINI_KEY_INVALID") || e.includes("401") || e.includes("403")) {
        errMsg = "Señor, mi clave de acceso a la API de Gemini ha expirado o es inválida. Por favor actualícela en su sección de Perfil — campo 'Clave API Gemini'.";
      } else if (e.includes("GEMINI_QUOTA") || e.includes("429")) {
        errMsg = "Señor, he alcanzado el límite de consultas gratuitas de la API por hoy. Puede actualizar su clave API desde el Perfil o intentarlo mañana.";
      } else if (e.includes("Failed to fetch") || e.includes("NetworkError") || e.includes("503")) {
        errMsg = "Sin conexión a la red, señor. Verifique su conexión a internet e intente nuevamente.";
      } else if (e.includes("GEMINI_SERVER")) {
        errMsg = "Los servidores de Gemini están experimentando dificultades técnicas, señor. Intente en unos momentos.";
      } else {
        errMsg = "Parece que mis sistemas están experimentando dificultades, señor. La conexión, probablemente.";
      }
      this.appendMessage("vision", errMsg);
      await VOICE.speak(errMsg);
      console.error("processMessage error:", err);
    } finally {
      this.isProcessing = false;
      this.setOrbState("listening");
      this.setOrbStatus("Di \"Jarvis\" para continuar");
      this.setWakeStatus("listening");
    }
  }

  // ── Chat / Mensajes ────────────────────────────────────────────

  appendMessage(role, text) {
    const container = document.getElementById("chat-messages");
    if (!container) return;

    const wrapper = document.createElement("div");
    wrapper.className = `message ${role} fade-in`;

    const bubble = document.createElement("div");
    bubble.className = "bubble";

    if (role === "vision") {
      const lbl = document.createElement("div");
      lbl.className = "msg-label";
      lbl.textContent = "VISION";
      bubble.appendChild(lbl);
    }

    const content = document.createElement("div");
    content.className = "msg-content";
    content.textContent = text;
    bubble.appendChild(content);
    wrapper.appendChild(bubble);
    container.appendChild(wrapper);
    container.scrollTop = container.scrollHeight;
  }

  showThinking() {
    const container = document.getElementById("chat-messages");
    if (!container) return;
    const el = document.createElement("div");
    el.id = "thinking-indicator";
    el.className = "message vision";
    el.innerHTML = `<div class="bubble"><div class="msg-label">VISION</div><div class="thinking-dots"><span></span><span></span><span></span></div></div>`;
    container.appendChild(el);
    container.scrollTop = container.scrollHeight;
  }

  hideThinking() {
    document.getElementById("thinking-indicator")?.remove();
  }

  // ── Drawer (Historial) ────────────────────────────────────────

  openDrawer() {
    const drawer = document.getElementById("chat-drawer");
    if (drawer) drawer.classList.add("open");
    const arrow = document.getElementById("handle-arrow");
    this.drawerOpen = true;
  }

  closeDrawer() {
    const drawer = document.getElementById("chat-drawer");
    if (drawer) drawer.classList.remove("open");
    this.drawerOpen = false;
  }

  toggleDrawer() {
    if (this.drawerOpen) this.closeDrawer();
    else this.openDrawer();
  }

  // ── Navegación Overlays ────────────────────────────────────────

  async openOverlay(viewId) {
    if (viewId === "diary") {
      const pwd = await DB.getProfile("diaryPassword");
      if (pwd && pwd.trim().length > 0 && !this.diaryUnlocked) {
        this.openPasswordModal();
        return;
      }
      // Reset to today when opening
      this.diarySelectedDate = new Date();
      this.diaryViewMonth    = new Date();
    }

    const el = document.getElementById(`view-${viewId}`);
    if (!el) return;
    el.style.display = "flex";
    requestAnimationFrame(() => el.classList.add("open"));
    if (viewId === "diary")   this.renderDiary();
    if (viewId === "profile") this.renderProfile();
    if (viewId === "tasks")   this.renderTasksView();
    this.currentView = viewId;

    document.querySelectorAll(".nav-btn").forEach(b => b.classList.toggle("active", b.dataset.view === viewId));
  }

  closeOverlay(viewId) {
    const el = document.getElementById(`view-${viewId}`);
    if (!el) return;
    el.classList.remove("open");
    setTimeout(() => { el.style.display = "none"; }, 350);
    this.currentView = "main";
    document.querySelectorAll(".nav-btn").forEach(b => b.classList.toggle("active", b.dataset.view === "main"));
    if (viewId === "diary") this.diaryUnlocked = false;
  }

  // ── Contraseña del Diario ──────────────────────────────────────

  openPasswordModal() {
    const modal = document.getElementById("password-modal");
    if (modal) modal.style.display = "flex";
    const inp = document.getElementById("diary-password-input");
    if (inp) inp.value = "";
    document.getElementById("pwd-mic-btn")?.classList.remove("listening");
  }

  closePasswordModal() {
    const modal = document.getElementById("password-modal");
    if (modal) modal.style.display = "none";
    document.getElementById("pwd-mic-btn")?.classList.remove("listening");
    if (this._pwdVoiceHandler) {
      document.removeEventListener("voice:interim", this._pwdVoiceHandler);
      this._pwdVoiceHandler = null;
    }
  }

  async verifyPassword() {
    const inputPwd = document.getElementById("diary-password-input")?.value?.trim().toLowerCase();
    const storedPwd = await DB.getProfile("diaryPassword");
    if (storedPwd && inputPwd === storedPwd.toLowerCase()) {
      this.diaryUnlocked = true;
      this.closePasswordModal();
      this.openOverlay("diary");
      this.showToast("Archivo desbloqueado, señor.");
      if (CONFIG.VOICE_ENABLED) VOICE.speak("Archivo principal desbloqueado. Acceso concedido.");
    } else {
      this.showToast("Contraseña incorrecta. Acceso denegado.");
    }
  }

  listenForPassword() {
    const btn = document.getElementById("pwd-mic-btn");
    if (btn) btn.classList.add("listening");
    
    this._pwdVoiceHandler = async (e) => {
      const text = (e.detail || "").toLowerCase();
      const inp = document.getElementById("diary-password-input");
      if (inp) inp.value = text;
      
      const storedPwd = await DB.getProfile("diaryPassword");
      if (storedPwd && text.includes(storedPwd.toLowerCase())) {
        if (inp) inp.value = storedPwd;
        if (this._pwdVoiceHandler) {
          document.removeEventListener("voice:interim", this._pwdVoiceHandler);
          this._pwdVoiceHandler = null;
        }
        setTimeout(() => this.verifyPassword(), 500); // Verify after brief delay to show matched text
      }
    };
    document.addEventListener("voice:interim", this._pwdVoiceHandler);
    this.showToast("Te escucho... di la contraseña.");
  }

  // ── Tareas Diarias ──────────────────────────────────────────────

  async renderTasks() {
    // Mini bar in main screen
    const container = document.getElementById("active-tasks-list");
    if (!container) return;
    const tasks = await DB.getAllTasks();
    const sorted = tasks.sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));
    if (sorted.length === 0) {
      container.innerHTML = `<div style="font-size:11px;color:var(--color-text-dim);padding:4px 0;font-family:var(--font-hud);letter-spacing:1px;">Sin tareas — toca ☑ TAREAS para agregar</div>`;
    } else {
      container.innerHTML = sorted.slice(0, 3).map(t => `
        <div class="task-item ${t.completed ? 'completed' : ''}">
          <input type="checkbox" class="task-checkbox" ${t.completed ? 'checked' : ''} onchange="APP.toggleTask(${t.id}, this.checked)">
          <span style="flex:1;">${t.text}</span>
          <button onclick="APP.deleteTask(${t.id})" style="background:transparent;border:none;color:var(--color-danger);cursor:pointer;font-size:16px;" aria-label="Eliminar tarea">×</button>
        </div>
      `).join("") + (sorted.length > 3 ? `<div style="font-size:10px;color:var(--color-text-dim);font-family:var(--font-hud);letter-spacing:1px;padding:2px 0;">+${sorted.length - 3} más en Tareas</div>` : "");
    }
    // Also refresh full tasks view if open
    if (this.currentView === "tasks") await this.renderTasksView();
  }

  async renderTasksView() {
    const container = document.getElementById("tasks-full-list");
    if (!container) return;
    const tasks = await DB.getAllTasks();
    const sorted = tasks.sort((a, b) => {
      if (a.completed !== b.completed) return a.completed ? 1 : -1;
      return new Date(b.timestamp) - new Date(a.timestamp);
    });
    let filtered = sorted;
    if (this.taskFilter === "pending") filtered = sorted.filter(t => !t.completed);
    if (this.taskFilter === "done")    filtered = sorted.filter(t => t.completed);

    if (!filtered.length) {
      container.innerHTML = `<div class="empty-state"><div class="empty-icon">☑</div><p>Sin tareas aquí.</p><p class="empty-sub">Usa el + para agregar una nueva.</p></div>`;
      return;
    }

    container.innerHTML = filtered.map(t => {
      const dateStr = new Date(t.timestamp).toLocaleDateString("es-MX", { day:"numeric", month:"short" });
      return `<div class="task-view-item ${t.completed ? 'completed' : ''}" role="listitem">
        <div class="task-view-check-wrap">
          <button class="task-view-check ${t.completed ? 'checked' : ''}"
            onclick="APP.toggleTask(${t.id}, ${!t.completed})"
            aria-label="${t.completed ? 'Marcar como pendiente' : 'Marcar como completada'}"
            title="${t.completed ? 'Deshacer' : 'Completar'}">
            ${t.completed ? '<svg viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>' : ''}
          </button>
        </div>
        <div class="task-view-body">
          <div class="task-view-text">${t.text}</div>
          <div class="task-view-meta">${dateStr}</div>
        </div>
        <button class="task-view-delete" onclick="APP.deleteTask(${t.id})" aria-label="Eliminar">×</button>
      </div>`;
    }).join("");
  }

  setTaskFilter(filter) {
    this.taskFilter = filter;
    document.querySelectorAll(".task-filter-pill").forEach(b => b.classList.toggle("active", b.dataset.filter === filter));
    this.renderTasksView();
  }

  async toggleTask(id, completed) {
    await DB.toggleTask(id, completed);
    await this.renderTasks();
  }

  async deleteTask(id) {
    await DB.deleteTask(id);
    await this.renderTasks();
  }

  async addManualTask() {
    const text = prompt("Nueva tarea o recordatorio:");
    if (text && text.trim()) {
      await DB.addTask(text.trim());
      await this.renderTasks();
      this.showToast("Tarea añadida a la lista.");
    }
  }

  // ── Diario ────────────────────────────────────────────────────

  async renderDiary() {
    const allEntries = await DB.getAllDiaryEntries();
    this.renderDiaryCalendar(allEntries);
    await this.renderDiaryForDate(this.diarySelectedDate, allEntries);
  }

  renderDiaryCalendar(allEntries) {
    const grid = document.getElementById("diary-cal-grid");
    const label = document.getElementById("diary-month-label");
    if (!grid || !label) return;

    const today = new Date();
    const y = this.diaryViewMonth.getFullYear();
    const m = this.diaryViewMonth.getMonth();

    // Month title
    label.textContent = this.diaryViewMonth.toLocaleDateString("es-MX", { month: "long", year: "numeric" }).toUpperCase();

    // Dates that have entries
    const entryDates = new Set((allEntries || []).map(e => {
      const d = new Date(e.date);
      return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
    }));

    const firstDay = new Date(y, m, 1).getDay(); // 0=Sun
    const daysInMonth = new Date(y, m + 1, 0).getDate();

    const dayNames = ["DO","LU","MA","MI","JU","VI","SA"];
    let html = dayNames.map(d => `<div class="cal-day-name">${d}</div>`).join("");

    // Empty cells before first day
    for (let i = 0; i < firstDay; i++) html += `<div class="cal-day empty"></div>`;

    for (let d = 1; d <= daysInMonth; d++) {
      const key = `${y}-${m}-${d}`;
      const isToday = (today.getFullYear() === y && today.getMonth() === m && today.getDate() === d);
      const isSel   = (this.diarySelectedDate.getFullYear() === y && this.diarySelectedDate.getMonth() === m && this.diarySelectedDate.getDate() === d);
      const hasDots = entryDates.has(key);
      let cls = "cal-day";
      if (isToday && !isSel) cls += " today";
      if (isSel) cls += " selected";
      if (hasDots) cls += " has-entries";
      html += `<div class="${cls}" onclick="APP.selectDiaryDate(${y},${m},${d})" role="button" aria-label="${d}">${d}</div>`;
    }
    grid.innerHTML = html;
  }

  async renderDiaryForDate(date, allEntries) {
    const container = document.getElementById("diary-list");
    const dateLabel = document.getElementById("diary-cal-date-label");
    if (!container) return;

    const today = new Date();
    const isToday = (date.getDate() === today.getDate() && date.getMonth() === today.getMonth() && date.getFullYear() === today.getFullYear());
    const longDate = date.toLocaleDateString("es-MX", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
    if (dateLabel) dateLabel.textContent = isToday ? `HOY — ${longDate.toUpperCase()}` : longDate.toUpperCase();

    const entries = allEntries || await DB.getAllDiaryEntries();
    const dayEntries = entries.filter(e => {
      const d = new Date(e.date);
      return d.getDate() === date.getDate() && d.getMonth() === date.getMonth() && d.getFullYear() === date.getFullYear();
    });

    if (!dayEntries.length) {
      container.innerHTML = `<div class="empty-state"><div class="empty-icon">📄</div><p>Sin entradas este día.</p><p class="empty-sub">Toca + para escribir en el diario.</p></div>`;
      return;
    }

    const catColors = { trabajo:"var(--color-primary)", personal:"var(--color-accent)", salud:"var(--color-gold)", meta:"#A855F7", otros:"var(--color-muted)" };
    container.innerHTML = dayEntries.map(e => {
      const timeStr = new Date(e.date).toLocaleTimeString("es-MX", { hour: "2-digit", minute: "2-digit" });
      const color   = catColors[e.category] || "var(--color-muted)";
      const stars   = "★".repeat(e.importance || 3) + "☆".repeat(5 - (e.importance || 3));
      return `<div class="diary-card" style="border-left-color:${color}">
        <div class="diary-card-header">
          <span class="diary-category" style="color:${color}">${(e.category || "personal").toUpperCase()}</span>
          <span class="diary-importance">${stars}</span>
          <span class="diary-date">${timeStr}</span>
        </div>
        <div class="diary-content">${e.content}</div>
        ${e.tags?.length ? `<div class="diary-tags">${e.tags.map(t => `<span class="tag">#${t}</span>`).join("")}</div>` : ""}
        <div class="diary-actions"><button class="btn-delete-diary" onclick="APP.deleteDiaryEntry(${e.id})">Eliminar</button></div>
      </div>`;
    }).join("");
  }

  selectDiaryDate(y, m, d) {
    this.diarySelectedDate = new Date(y, m, d);
    this.renderDiary();
  }

  navigateDiaryMonth(delta) {
    this.diaryViewMonth = new Date(this.diaryViewMonth.getFullYear(), this.diaryViewMonth.getMonth() + delta, 1);
    this.renderDiary();
  }

  async deleteDiaryEntry(id) {
    if (!confirm("¿Eliminar esta entrada?")) return;
    await DB.deleteDiaryEntry(id);
    await this.renderDiary();
  }

  addManualDiaryEntry() {
    // Show today's date in modal header
    const dateEl = document.getElementById("diary-modal-date");
    if (dateEl) {
      dateEl.textContent = new Date().toLocaleDateString("es-MX", {
        weekday: "long", day: "numeric", month: "long", year: "numeric"
      });
    }
    const modal = document.getElementById("diary-modal");
    if (modal) modal.classList.add("open");
    setTimeout(() => document.getElementById("diary-modal-content")?.focus(), 100);
  }

  async saveDiaryModal() {
    const content = document.getElementById("diary-modal-content")?.value?.trim();
    const cat     = document.getElementById("diary-modal-category")?.value;
    const imp     = parseInt(document.getElementById("diary-modal-importance")?.value || "3");
    if (!content) { this.showToast("El contenido no puede estar vacío."); return; }
    await DB.addDiaryEntry({ title: "", content, category: cat, importance: imp, tags: [] });
    this.closeDiaryModal();
    // Refresh diary — reset to today so the new entry is visible
    this.diarySelectedDate = new Date();
    this.diaryViewMonth    = new Date();
    await this.renderDiary();
    this.showToast("Entrada guardada en el diario, señor.");
  }

  closeDiaryModal() {
    document.getElementById("diary-modal")?.classList.remove("open");
    const contentEl = document.getElementById("diary-modal-content");
    if (contentEl) contentEl.value = "";
  }

  // ── Perfil ────────────────────────────────────────────────────

  async loadSavedProfile() {
    const profile = await DB.getAllProfile();
    if (profile.interests) PERSONALITY.userProfile.interests = profile.interests.split(",").map(s=>s.trim());
    if (profile.traits)    PERSONALITY.userProfile.traits    = profile.traits.split(",").map(s=>s.trim());
    if (profile.profession)PERSONALITY.userProfile.profession= profile.profession;
    if (profile.name)      PERSONALITY.userProfile.name      = profile.name;
    if (profile.diaryPassword) {
      const el = document.getElementById("profile-diary-password");
      if(el) el.value = profile.diaryPassword;
    }
  }

  async renderProfile() {
    const profile = await DB.getAllProfile();
    const stats   = await DB.getStats();
    document.getElementById("profile-name").value       = profile.name       || PERSONALITY.userProfile.name || "";
    document.getElementById("profile-profession").value = profile.profession || PERSONALITY.userProfile.profession || "";
    document.getElementById("profile-interests").value  = profile.interests  || PERSONALITY.userProfile.interests.join(", ") || "";
    document.getElementById("profile-traits").value     = profile.traits     || PERSONALITY.userProfile.traits.join(", ") || "";
    // Don't auto-fill API key for security — only show placeholder hint
    const apiKeyEl = document.getElementById("profile-geminiApiKey");
    if (apiKeyEl && profile.geminiApiKey) apiKeyEl.placeholder = "••••••••••• (guardada)";
    const el = document.getElementById("profile-stats");
    if (el) el.innerHTML = `<span>📔 ${stats.diaryCount} entradas en el diario</span><span>💬 ${stats.messagesCount} mensajes intercambiados</span>`;
  }

  async saveProfile() {
    const fields = ["name","profession","interests","traits","diaryPassword","geminiApiKey"];
    for (const f of fields) {
      const val = document.getElementById(`profile-${f}`)?.value?.trim();
      if (val != null) {
        await DB.setProfile(f, val);
        if (f === "interests")    PERSONALITY.userProfile.interests  = val.split(",").map(s => s.trim());
        if (f === "traits")       PERSONALITY.userProfile.traits     = val.split(",").map(s => s.trim());
        if (f === "profession")   PERSONALITY.userProfile.profession = val;
        if (f === "name")         PERSONALITY.userProfile.name       = val;
        if (f === "geminiApiKey" && val.length > 10) {
          CONFIG.GEMINI_API_KEY = val;
          this.showToast("Clave API actualizada. Probando conexión...");
        }
      }
    }
    this.showToast("Perfil actualizado, señor. He tomado nota.");
  }

  // ── Toast ── ────────────────────────────────────────────────

  showToast(msg) {
    const t = document.getElementById("toast");
    if (!t) return;
    t.textContent = msg;
    t.classList.add("show");
    setTimeout(() => t.classList.remove("show"), 3500);
  }

  // ── Floating Notifications ──────────────────────────────────

  addFloatingNotification(title, message, type = 'info', duration = 5000) {
    const container = document.getElementById("floating-notifications-container");
    if (!container) return;

    const notif = document.createElement("div");
    notif.className = `floating-notification ${type}`;

    let html = '';
    if (title) {
      html += `<span class="floating-notification-title">${title}</span>`;
    }
    html += `<span>${message}</span>`;
    html += `<button class="floating-notification-close" aria-label="Cerrar">&times;</button>`;
    
    notif.innerHTML = html;

    // Remove function
    const removeNotif = () => {
      if (notif.classList.contains("hide")) return;
      notif.classList.remove("show");
      notif.classList.add("hide");
      setTimeout(() => notif.remove(), 400);
    };

    // Close on click
    notif.addEventListener("click", removeNotif);

    container.appendChild(notif);

    // Trigger animation
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        notif.classList.add("show");
      });
    });

    // Auto remove
    if (duration > 0) {
      setTimeout(removeNotif, duration);
    }
  }
  // ── Paleta de Colores ───────────────────────────────────

  async loadSavedPalette() {
    const saved = await DB.getProfile("colorPalette");
    if (saved) {
      document.documentElement.setAttribute("data-palette", saved);
      this._updateSwatches(saved);
    }
  }

  setPalette(paletteName) {
    document.documentElement.setAttribute("data-palette", paletteName);
    DB.setProfile("colorPalette", paletteName);
    this._updateSwatches(paletteName);
    const names = { jarvis:"Jarvis Blue", phantom:"Phantom Red", matrix:"Emerald Matrix", void:"Void Purple", solar:"Solar Gold" };
    this.showToast(`Tema cambiado: ${names[paletteName] || paletteName}`);
  }

  _updateSwatches(paletteName) {
    document.querySelectorAll(".palette-swatch").forEach(sw => {
      sw.classList.toggle("active", sw.dataset.palette === paletteName);
    });
  }

  // ── Bind UI Events ────────────────────────────────────────────

  bindUIEvents() {
    // Navegación
    document.querySelectorAll(".nav-btn").forEach(btn => {
      btn.addEventListener("click", () => {
        const view = btn.dataset.view;
        if (view === "main") {
          ["diary","profile","tasks"].forEach(v => this.closeOverlay(v));
        } else {
          this.openOverlay(view);
        }
      });
    });

    // Botones back de overlays
    document.getElementById("back-from-diary")?.addEventListener("click",   () => this.closeOverlay("diary"));
    document.getElementById("back-from-profile")?.addEventListener("click", () => this.closeOverlay("profile"));
    document.getElementById("back-from-tasks")?.addEventListener("click",   () => this.closeOverlay("tasks"));

    // Diary calendar navigation
    document.getElementById("diary-prev-month")?.addEventListener("click", () => this.navigateDiaryMonth(-1));
    document.getElementById("diary-next-month")?.addEventListener("click", () => this.navigateDiaryMonth(+1));

    // Tasks view filter pills
    document.querySelectorAll(".task-filter-pill").forEach(b => {
      b.addEventListener("click", () => this.setTaskFilter(b.dataset.filter));
    });

    // Tasks view add button
    document.getElementById("add-task-view-btn")?.addEventListener("click", () => this.addManualTask());

    // Drawer handle (flecha)
    document.getElementById("drawer-handle")?.addEventListener("click", () => this.toggleDrawer());

    // Enviar con texto
    document.getElementById("send-btn")?.addEventListener("click", () => {
      const input = document.getElementById("chat-input");
      if (input?.value.trim()) this.processMessage(input.value.trim());
    });

    document.getElementById("chat-input")?.addEventListener("keydown", (e) => {
      if (e.key === "Enter" && !e.shiftKey) {
        e.preventDefault();
        const input = document.getElementById("chat-input");
        if (input?.value.trim()) this.processMessage(input.value.trim());
      }
    });

    // Micrófono manual (reactivar si se desactivó)
    document.getElementById("mic-orb-btn")?.addEventListener("click", () => {
      if (!VOICE.isListening && !VOICE.isSpeaking) {
        VOICE.startContinuous();
        this.showToast("Micrófono reactivado. Di \"Jarvis\" para comenzar.");
      }
    });

    // Silenciar
    document.getElementById("mute-btn")?.addEventListener("click", () => {
      CONFIG.VOICE_ENABLED = !CONFIG.VOICE_ENABLED;
      const btn = document.getElementById("mute-btn");
      if (!CONFIG.VOICE_ENABLED) {
        VOICE.stopContinuous();
        VOICE.stopSpeaking();
        if (btn) btn.textContent = "🔇";
        this.setWakeStatus("off");
        this.setOrbState("idle");
        this.setOrbStatus("Sistema de voz silenciado");
      } else {
        if (btn) btn.textContent = "🔊";
        VOICE.startContinuous();
        this.setWakeStatus("listening");
        this.setOrbState("listening");
        this.setOrbStatus("Di \"Jarvis\" para comenzar");
      }
    });

    // Perfil
    document.getElementById("save-profile-btn")?.addEventListener("click", () => this.saveProfile());

    // Diario modal
    document.getElementById("add-diary-btn")?.addEventListener("click", () => this.addManualDiaryEntry());
    document.getElementById("diary-modal-save")?.addEventListener("click", () => this.saveDiaryModal());
    document.getElementById("diary-modal-cancel")?.addEventListener("click", () => this.closeDiaryModal());

    // Modal Contraseña
    document.getElementById("pwd-cancel-btn")?.addEventListener("click", () => this.closePasswordModal());
    document.getElementById("pwd-unlock-btn")?.addEventListener("click", () => this.verifyPassword());
    document.getElementById("pwd-mic-btn")?.addEventListener("click", () => this.listenForPassword());

    // Tareas
    document.getElementById("new-task-main-btn")?.addEventListener("click", () => this.addManualTask());

    // Diary update event
    document.addEventListener("diary:updated", () => {
      if (this.currentView === "diary") this.renderDiary();
    });

    // Comentario de VISION desde la burbuja (entrega in-app)
    document.addEventListener("bubble:comment", async (e) => {
      const { title, text } = e.detail;
      this.openDrawer();
      this.appendMessage("vision", `【${title}】 ${text}`);
      this.setOrbState("speaking");
      this.setWakeStatus("speaking");
      await VOICE.speak(text);
      this.setOrbState("listening");
      this.setWakeStatus("listening");
    });
  }

  // ── Bind Voice Events ─────────────────────────────────────────

  bindVoiceEvents() {

    // Siempre escuchando (idle state)
    document.addEventListener("voice:listening", (e) => {
      if (e.detail && !VOICE.isWoken && !this.isProcessing) {
        document.getElementById("mic-orb-btn")?.classList.remove("woken", "listening-command");
      }
    });

    // WAKE WORD detectado
    document.addEventListener("voice:woken", (e) => {
      const { command } = e.detail;
      this.setOrbState("woken");
      this.setWakeStatus("woken");
      this.setOrbTranscript("");

      if (command && command.length > 2) {
        // Comando inmediato
        this.setOrbStatus(`"${command}"`);
        document.getElementById("chat-input").value = command;
      } else {
        this.setOrbStatus("¿En qué puedo ayudarle, señor?");
        document.getElementById("mic-orb-btn")?.classList.add("woken");
      }
    });

    // Transcripción en tiempo real
    document.addEventListener("voice:interim", (e) => {
      if (VOICE.isWoken || this.isProcessing) return;
      const text = e.detail;
      // Mostrar que está captando (solo si hay texto relevante)
      if (text && text.length > 3) {
        this.setOrbTranscript(text);
      }
    });

    // Comando final capturado
    document.addEventListener("voice:command", (e) => {
      const command = e.detail;
      this.setOrbTranscript("");
      document.getElementById("mic-orb-btn")?.classList.remove("woken", "listening-command");
      this.processMessage(command);
    });

    // Reset al modo espera
    document.addEventListener("voice:reset", () => {
      this.setOrbState("listening");
      this.setWakeStatus("listening");
      this.setOrbStatus("Di \"Jarvis\" para comenzar");
      this.setOrbTranscript("");
      document.getElementById("mic-orb-btn")?.classList.remove("woken", "listening-command");
    });

    // VISION empieza a hablar
    document.addEventListener("voice:speakStart", () => {
      this.setOrbState("speaking");
      this.setWakeStatus("speaking");
    });

    // VISION termina de hablar
    document.addEventListener("voice:speakEnd", () => {
      if (!this.isProcessing) {
        this.setOrbState("listening");
        this.setWakeStatus("listening");
        this.setOrbStatus("Di \"Jarvis\" para continuar");
      }
    });
  }
}

// ── Inicializar ──────────────────────────────────────────────
const APP = new VisionApp();
document.addEventListener("DOMContentLoaded", async () => {
  await APP.init();
  if (typeof BUBBLE !== 'undefined') await BUBBLE.init();
});
