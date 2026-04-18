// ============================================================
// VISION SOLDIER — Service Worker (PWA Offline)
// ============================================================

const CACHE_NAME = "vision-soldier-v3";

const STATIC_ASSETS = [
  "./",
  "./index.html",
  "./css/main.css",
  "./js/config.js",
  "./js/db.js",
  "./js/personality.js",
  "./js/voice.js",
  "./js/ai.js",
  "./js/router.js",
  "./js/app.js",
  "./manifest.json",
];

// Instalar: cachear recursos estáticos
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS))
  );
  self.skipWaiting();
});

// Activar: limpiar caches viejos
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Fetch: Estrategia cache-first para assets, network-first para API
self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);

  // Las llamadas a APIs siempre van a la red (no cachear)
  if (
    url.hostname.includes("googleapis.com") ||
    url.hostname.includes("elevenlabs.io") ||
    url.hostname.includes("generativelanguage")
  ) {
    event.respondWith(fetch(event.request).catch(() => new Response("", { status: 503 })));
    return;
  }

  // Assets estáticos: cache primero
  // Network-first: siempre busca en red, usa caché solo si hay fallo
  event.respondWith(
    fetch(event.request).then((response) => {
      if (response.ok && event.request.method === "GET") {
        const clone = response.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
      }
      return response;
    }).catch(() =>
      caches.match(event.request).then(cached => cached || caches.match("./index.html"))
    )
  );
});
