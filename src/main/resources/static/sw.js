// Service worker (PLAN.md D5). Its job is to make the app installable and to serve our own static
// files (CSS, JS, icons, manifest) quickly. It deliberately does NOT cache HTML pages, htmx
// fragments or anything else with household data, and it provides no offline mode: when the
// network is down the browser's normal error page is the right answer.
//
// Strategy "stale-while-revalidate": answer from the cache at once, refresh the cache in the
// background, so a changed file shows up on the next load. In development, hold Shift while
// reloading or tick "Disable cache" / "Update on reload" in the browser's dev tools.
// v2 (R1): the self-hosted fonts joined the cached paths. A new name makes the activate step drop
// the v1 cache, so nothing stale survives an update.
const CACHE = 'mm-static-v2';
const STATIC_PATHS = /^\/(css|js|icons|fonts)\/|^\/manifest\.webmanifest$/;

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
  // Drop the caches of older versions of this file, then take control of open pages.
  event.waitUntil(
    caches
      .keys()
      .then((names) => Promise.all(names.filter((n) => n !== CACHE).map((n) => caches.delete(n))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || !STATIC_PATHS.test(url.pathname)) {
    return; // Not ours to cache: the browser handles it normally.
  }
  event.respondWith(
    caches.open(CACHE).then(async (cache) => {
      const cached = await cache.match(request);
      const refresh = fetch(request).then((response) => {
        if (response.ok) {
          cache.put(request, response.clone());
        }
        return response;
      });
      if (cached) {
        refresh.catch(() => {}); // Offline: keep the cached copy, ignore the failed refresh.
        return cached;
      }
      return refresh;
    }),
  );
});
