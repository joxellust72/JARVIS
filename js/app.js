// ============================================================
// VISION SOLDIER — Controlador Principal (Rediseño Orbe)
// ============================================================

class VisionApp {
  constructor() {
    this.isProcessing = false;
    this.currentView = "main";
    this.drawerOpen = false;
  }

  // ── Inicialización ─────────────────────────────────────────

  async init() {
    await DB.init();
    this.bindUIEvents();
    this.bindVoiceEvents();
    this.setOrbState("listening");
    this.setWakeStatus("listening");

    // Cargar paleta guardada
    await this.loadSavedPalette();

    // Saludo inicial
    const greeting = PERSONALITY.getRandomGreeting();
    this.appendMessage("vision", greeting);
    this.setOrbStatus("Di \"Jarvis\" para comenzar");

    // Activar micrófono continuo
    setTimeout(() => {
      VOICE.startContinuous();
    }, 1200);

    // Cargar perfil guardado
    await this.loadSavedProfile();

    // Renderizar tareas iniciales
    await this.renderTasks();
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
      const errMsg = "Parece que mis sistemas están experimentando dificultades, señor. La conexión, probablemente.";
      this.appendMessage("vision", errMsg);
      await VOICE.speak(errMsg);
      console.error(err);
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
        return; // Detiene la apertura del diario
      }
    }

    const el = document.getElementById(`view-${viewId}`);
    if (!el) return;
    el.style.display = "flex";
    requestAnimationFrame(() => el.classList.add("open"));
    if (viewId === "diary") this.renderDiary();
    if (viewId === "profile") this.renderProfile();
    this.currentView = viewId;

    // Navbutton activo
    document.querySelectorAll(".nav-btn").forEach(b => b.classList.toggle("active", b.dataset.view === viewId));
  }

  closeOverlay(viewId) {
    const el = document.getElementById(`view-${viewId}`);
    if (!el) return;
    el.classList.remove("open");
    setTimeout(() => { el.style.display = "none"; }, 350);
    this.currentView = "main";
    document.querySelectorAll(".nav-btn").forEach(b => b.classList.toggle("active", b.dataset.view === "main"));
    
    if (viewId === "diary") {
      this.diaryUnlocked = false; // Relock upon closing
    }
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
    const container = document.getElementById("active-tasks-list");
    if (!container) return;

    const tasks = await DB.getAllTasks();
    const activeTasks = tasks.sort((a,b) => new Date(a.timestamp) - new Date(b.timestamp));

    if (activeTasks.length === 0) {
      container.innerHTML = `<div style="font-size:11px;color:var(--color-text-dim);padding:4px 0;font-family:var(--font-hud);letter-spacing:1px;">Sin tareas — usa el + para agregar</div>`;
      return;
    }

    container.innerHTML = activeTasks.map(t => `
      <div class="task-item ${t.completed ? 'completed' : ''}">
        <input type="checkbox" class="task-checkbox" ${t.completed ? 'checked' : ''} onchange="APP.toggleTask(${t.id}, this.checked)">
        <span style="flex:1;">${t.text}</span>
        <button onclick="APP.deleteTask(${t.id})" style="background:transparent;border:none;color:var(--color-danger);cursor:pointer;font-size:16px;" aria-label="Eliminar tarea">×</button>
      </div>
    `).join("");
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
    const container = document.getElementById("diary-list");
    if (!container) return;
    container.innerHTML = `<div class="loading-text">Cargando registros...</div>`;
    const entries = await DB.getAllDiaryEntries();
    if (!entries.length) {
      container.innerHTML = `<div class="empty-state"><div class="empty-icon">📔</div><p>No hay entradas en el diario.</p><p class="empty-sub">Dile a VISION "guarda esto:" o usa el botón +</p></div>`;
      return;
    }
    const catColors = { trabajo:"#00A8FF", personal:"#00FFD1", salud:"#FFB800", meta:"#A855F7", otros:"#64748B" };
    container.innerHTML = entries.map(e => {
      const date = new Date(e.date).toLocaleDateString("es-MX", { weekday:"short", day:"numeric", month:"short" });
      const color = catColors[e.category] || "#64748B";
      const stars = "★".repeat(e.importance||3) + "☆".repeat(5-(e.importance||3));
      return `<div class="diary-card">
        <div class="diary-card-header">
          <span class="diary-category" style="color:${color}">${(e.category||"personal").toUpperCase()}</span>
          <span class="diary-importance">${stars}</span>
          <span class="diary-date">${date}</span>
        </div>
        ${e.title?`<div class="diary-title">${e.title}</div>`:""}
        <div class="diary-content">${e.content}</div>
        ${e.tags?.length?`<div class="diary-tags">${e.tags.map(t=>`<span class="tag">#${t}</span>`).join("")}</div>`:""}
        <div class="diary-actions"><button class="btn-delete-diary" onclick="APP.deleteDiaryEntry(${e.id})">Eliminar</button></div>
      </div>`;
    }).join("");
  }

  async deleteDiaryEntry(id) {
    if (!confirm("¿Eliminar esta entrada?")) return;
    await DB.deleteDiaryEntry(id);
    await this.renderDiary();
  }

  addManualDiaryEntry() {
    document.getElementById("diary-modal")?.classList.add("open");
  }

  async saveDiaryModal() {
    const title   = document.getElementById("diary-modal-title")?.value?.trim();
    const content = document.getElementById("diary-modal-content")?.value?.trim();
    const cat     = document.getElementById("diary-modal-category")?.value;
    const imp     = parseInt(document.getElementById("diary-modal-importance")?.value || "3");
    if (!content) return alert("El contenido no puede estar vacío.");
    await DB.addDiaryEntry({ title, content, category: cat, importance: imp, tags: [] });
    this.closeDiaryModal();
    await this.renderDiary();
    this.showToast("Entrada guardada en el diario, señor.");
  }

  closeDiaryModal() {
    document.getElementById("diary-modal")?.classList.remove("open");
    ["diary-modal-title","diary-modal-content"].forEach(id => { const el=document.getElementById(id); if(el) el.value=""; });
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
    const el = document.getElementById("profile-stats");
    if (el) el.innerHTML = `<span>📔 ${stats.diaryCount} entradas en el diario</span><span>💬 ${stats.messagesCount} mensajes intercambiados</span>`;
  }

  async saveProfile() {
    const fields = ["name","profession","interests","traits", "diaryPassword"];
    for (const f of fields) {
      const val = document.getElementById(`profile-${f}`)?.value?.trim();
      if (val != null) {
        await DB.setProfile(f, val);
        if (f==="interests") PERSONALITY.userProfile.interests = val.split(",").map(s=>s.trim());
        if (f==="traits")    PERSONALITY.userProfile.traits    = val.split(",").map(s=>s.trim());
        if (f==="profession")PERSONALITY.userProfile.profession= val;
        if (f==="name")      PERSONALITY.userProfile.name      = val;
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
          ["diary","profile"].forEach(v => this.closeOverlay(v));
        } else {
          this.openOverlay(view);
        }
      });
    });

    // Botones back de overlays
    document.getElementById("back-from-diary")?.addEventListener("click",    () => this.closeOverlay("diary"));
    document.getElementById("back-from-profile")?.addEventListener("click",  () => this.closeOverlay("profile"));

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
document.addEventListener("DOMContentLoaded", () => APP.init());
