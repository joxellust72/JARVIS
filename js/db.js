// ============================================================
// VISION SOLDIER — Base de Datos (IndexedDB)
// ============================================================

class VisionDB {
  constructor() {
    this.db = null;
    this.dbName = CONFIG.DB_NAME;
    this.dbVersion = CONFIG.DB_VERSION;
  }

  async init() {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(this.dbName, this.dbVersion);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;

        // Tabla: Entradas del diario
        if (!db.objectStoreNames.contains("diary")) {
          const diaryStore = db.createObjectStore("diary", { keyPath: "id", autoIncrement: true });
          diaryStore.createIndex("date", "date", { unique: false });
          diaryStore.createIndex("category", "category", { unique: false });
          diaryStore.createIndex("importance", "importance", { unique: false });
        }

        // Tabla: Conversaciones
        if (!db.objectStoreNames.contains("conversations")) {
          const convStore = db.createObjectStore("conversations", { keyPath: "id", autoIncrement: true });
          convStore.createIndex("sessionId", "sessionId", { unique: false });
          convStore.createIndex("timestamp", "timestamp", { unique: false });
        }

        // Tabla: Perfil del usuario (clave-valor)
        if (!db.objectStoreNames.contains("profile")) {
          db.createObjectStore("profile", { keyPath: "key" });
        }

        // Tabla: Tareas (Daily Tasks)
        if (!db.objectStoreNames.contains("tasks")) {
          const tasksStore = db.createObjectStore("tasks", { keyPath: "id", autoIncrement: true });
          tasksStore.createIndex("date", "date", { unique: false });
          tasksStore.createIndex("completed", "completed", { unique: false });
        }
      };

      request.onsuccess = (event) => {
        this.db = event.target.result;
        resolve(this.db);
      };

      request.onerror = (event) => {
        console.error("Error abriendo DB:", event.target.error);
        reject(event.target.error);
      };
    });
  }

  // ─── DIARIO ─────────────────────────────────────────────────

  async addDiaryEntry(entry) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("diary", "readwrite");
      const store = tx.objectStore("diary");
      const data = {
        date: new Date().toISOString(),
        title: entry.title || "",
        content: entry.content,
        category: entry.category || "personal",
        importance: entry.importance || 3,
        tags: entry.tags || [],
      };
      const req = store.add(data);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async getAllDiaryEntries() {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("diary", "readonly");
      const store = tx.objectStore("diary");
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result.reverse()); // más reciente primero
      req.onerror = () => reject(req.error);
    });
  }

  async getRecentDiaryEntries(limit = 8) {
    const all = await this.getAllDiaryEntries();
    // Ordenar por importancia y fecha
    return all
      .sort((a, b) => {
        const importanceDiff = (b.importance || 3) - (a.importance || 3);
        if (importanceDiff !== 0) return importanceDiff;
        return new Date(b.date) - new Date(a.date);
      })
      .slice(0, limit);
  }

  async deleteDiaryEntry(id) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("diary", "readwrite");
      const store = tx.objectStore("diary");
      const req = store.delete(id);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  }

  async updateDiaryEntry(id, updates) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("diary", "readwrite");
      const store = tx.objectStore("diary");
      const getReq = store.get(id);
      getReq.onsuccess = () => {
        const entry = { ...getReq.result, ...updates };
        const putReq = store.put(entry);
        putReq.onsuccess = () => resolve();
        putReq.onerror = () => reject(putReq.error);
      };
    });
  }

  // ─── TAREAS DIARIAS ─────────────────────────────────────────

  async addTask(text) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("tasks", "readwrite");
      const store = tx.objectStore("tasks");
      const data = {
        text,
        completed: false,
        date: new Date().toISOString()
      };
      const req = store.add(data);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async getTasks() {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("tasks", "readonly");
      const store = tx.objectStore("tasks");
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async toggleTask(id, currentStatus) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("tasks", "readwrite");
      const store = tx.objectStore("tasks");
      const getReq = store.get(id);
      getReq.onsuccess = () => {
        if (!getReq.result) return reject("Task not found");
        const task = getReq.result;
        task.completed = !currentStatus;
        const putReq = store.put(task);
        putReq.onsuccess = () => resolve();
        putReq.onerror = () => reject(putReq.error);
      };
    });
  }

  async deleteTask(id) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("tasks", "readwrite");
      const store = tx.objectStore("tasks");
      const req = store.delete(id);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  }

  // ─── CONVERSACIONES ─────────────────────────────────────────

  async addMessage(role, content, sessionId) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("conversations", "readwrite");
      const store = tx.objectStore("conversations");
      const req = store.add({
        sessionId,
        role,
        content,
        timestamp: new Date().toISOString(),
      });
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async getRecentMessages(limit = CONFIG.MAX_HISTORY_MESSAGES) {
    const all = await this.getAllMessages();
    return all.slice(-limit);
  }

  async getAllMessages() {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("conversations", "readonly");
      const store = tx.objectStore("conversations");
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async clearOldMessages(keepLast = 100) {
    const all = await this.getAllMessages();
    if (all.length <= keepLast) return;
    const toDelete = all.slice(0, all.length - keepLast);
    const tx = this.db.transaction("conversations", "readwrite");
    const store = tx.objectStore("conversations");
    toDelete.forEach(msg => store.delete(msg.id));
  }

  // ─── PERFIL ─────────────────────────────────────────────────

  async setProfile(key, value) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("profile", "readwrite");
      const store = tx.objectStore("profile");
      const req = store.put({ key, value });
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  }

  async getProfile(key) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("profile", "readonly");
      const store = tx.objectStore("profile");
      const req = store.get(key);
      req.onsuccess = () => resolve(req.result ? req.result.value : null);
      req.onerror = () => reject(req.error);
    });
  }

  async getAllProfile() {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction("profile", "readonly");
      const store = tx.objectStore("profile");
      const req = store.getAll();
      req.onsuccess = () => {
        const profile = {};
        req.result.forEach(item => profile[item.key] = item.value);
        resolve(profile);
      };
      req.onerror = () => reject(req.error);
    });
  }

  // ─── UTILIDADES ─────────────────────────────────────────────

  async buildDiaryContext() {
    const entries = await this.getRecentDiaryEntries(CONFIG.MAX_DIARY_CONTEXT);
    if (!entries.length) return "";

    return entries.map(entry => {
      const date = new Date(entry.date).toLocaleDateString("es-MX", {
        weekday: "long", year: "numeric", month: "long", day: "numeric"
      });
      const importance = "⭐".repeat(entry.importance || 3);
      return `[${date}] ${importance} ${entry.category?.toUpperCase() || "PERSONAL"}: ${entry.title ? entry.title + " — " : ""}${entry.content}`;
    }).join("\n");
  }

  async getStats() {
    const diary = await this.getAllDiaryEntries();
    const messages = await this.getAllMessages();
    return {
      diaryCount: diary.length,
      messagesCount: messages.length,
      firstEntry: diary.length ? diary[diary.length - 1] : null,
    };
  }
}

// Instancia global
const DB = new VisionDB();
