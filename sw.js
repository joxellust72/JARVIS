// ============================================================
// VISION SOLDIER — Service Worker v4
// ============================================================

const CACHE_NAME = "vision-soldier-v4";

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
  "./js/bubble.js",
  "./js/app.js",
  "./manifest.json",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
];

// Instalar: cachear recursos estáticos
self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS))
  );
  self.skipWaiting();
});

// Activar: limpiar caches viejos y tomar control inmediato
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Fetch handler
self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);

  // APIs externas: siempre red, nunca cachear
  if (
    url.hostname.includes("googleapis.com") ||
    url.hostname.includes("elevenlabs.io") ||
    url.hostname.includes("generativelanguage")
  ) {
    event.respondWith(
      fetch(event.request).catch(() => new Response("", { status: 503 }))
    );
    return;
  }

  // Solicitudes de navegación (HTML): red primero, caché como fallback
  // Esto corrige el 404 cuando GitHub Pages no encuentra la ruta
  if (event.request.mode === "navigate") {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          // Guardar en caché si la respuesta es válida
          if (response.ok) {
            const clone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
          }
          return response;
        })
        .catch(() =>
          // Si falla la red, servir index.html desde caché
          caches.match("./index.html").then(
            (cached) => cached || new Response("Offline", { status: 503 })
          )
        )
    );
    return;
  }

  // Assets estáticos: red primero, caché como fallback
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response.ok && event.request.method === "GET") {
          const clone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        }
        return response;
      })
      .catch(() =>
        caches.match(event.request).then(
          (cached) => cached || caches.match("./index.html")
        )
      )
  );
});
