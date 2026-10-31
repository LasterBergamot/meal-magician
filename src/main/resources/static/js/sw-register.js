// Registers the service worker (an external file because the CSP forbids inline scripts).
// The worker is what makes the app installable; it caches static assets only (see sw.js).
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((error) => {
      console.warn('Service worker registration failed', error);
    });
  });
}
