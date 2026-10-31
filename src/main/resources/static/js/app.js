// Our only hand-written script. It is loaded before Alpine and htmx start working on the page.
// No inline scripts or handlers exist anywhere because the CSP forbids them (SecurityHeadersFilter),
// so everything that needs JavaScript lives here.
(() => {
  'use strict';

  // Alpine's CSP build cannot evaluate expressions such as `open = !open` in templates, only
  // names. Components are therefore registered here and templates say x-data="menu".
  document.addEventListener('alpine:init', () => {
    window.Alpine.data('menu', () => ({
      open: false,
      toggle() {
        this.open = !this.open;
      },
      close() {
        this.open = false;
      },
      // The layout's static classes ("hidden md:flex") hide the panel on phones and always show it
      // from the md breakpoint up. When the button opens it, this adds max-md:flex, which beats
      // "hidden" below md only. The class name is written out in full so Tailwind finds it here.
      get panelClass() {
        return this.open ? 'max-md:flex' : '';
      },
    }));
  });

  const meta = (name) => document.querySelector(`meta[name="${name}"]`)?.content;

  // CSRF: the layout renders the token into <meta> tags (only when the server has one); every
  // htmx request sends it back as a header.
  document.addEventListener('htmx:configRequest', (event) => {
    const token = meta('_csrf');
    const header = meta('_csrf_header');
    if (token && header) {
      event.detail.headers[header] = token;
    }
  });

  // A 401/403 for an htmx request almost always means "your session ended". Swapping the error
  // page into a fragment would look broken, so send the whole page home instead, unless the
  // server already asked for a redirect itself (HX-Redirect, which htmx handles before this).
  document.addEventListener('htmx:beforeSwap', (event) => {
    const xhr = event.detail.xhr;
    if ((xhr.status === 401 || xhr.status === 403) && !xhr.getResponseHeader('HX-Redirect')) {
      event.detail.shouldSwap = false;
      window.location.assign('/');
    }
  });
})();
