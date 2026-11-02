// Our only hand-written script. It is loaded before Alpine and htmx start working on the page.
// No inline scripts or handlers exist anywhere because the CSP forbids them (SecurityHeadersFilter),
// so everything that needs JavaScript lives here.
(() => {
  'use strict';

  // Alpine's CSP build cannot evaluate expressions such as `open = !open` in templates, only
  // names. Components are therefore registered here and templates say x-data="menu".
  document.addEventListener('alpine:init', () => {
    // A small popup opened by a button (the avatar menu in layout/_topbar.jte).
    window.Alpine.data('menu', () => ({
      open: false,
      toggle() {
        this.open = !this.open;
      },
      close() {
        this.open = false;
      },
    }));

    // The app shell (layout/_shell.jte). The sidebar on desktop collapses to icons; the choice is
    // kept in localStorage, a convenience for this browser only, so every access sits in try/catch
    // (private mode and some embedded browsers refuse storage). Without a stored choice the sidebar
    // starts collapsed below the lg breakpoint (1024 px), as plan section 4 says. The phone's tab bar
    // hides while a text field has focus, which on a phone means the on-screen keyboard is up.
    const SIDEBAR_KEY = 'mm.sidebarCollapsed';
    const COLLAPSE_BELOW = 1024;
    const TEXT_FIELD =
      'textarea, input:not([type=button]):not([type=checkbox]):not([type=color]):not([type=file]):not([type=hidden]):not([type=radio]):not([type=range]):not([type=reset]):not([type=submit])';
    const storedCollapsed = () => {
      try {
        const value = window.localStorage.getItem(SIDEBAR_KEY);
        return value === null ? null : value === '1';
      } catch {
        return null;
      }
    };
    const rememberCollapsed = (collapsed) => {
      try {
        window.localStorage.setItem(SIDEBAR_KEY, collapsed ? '1' : '0');
      } catch {
        // Nothing to do: the sidebar still collapses for this page view.
      }
    };
    window.Alpine.data('shell', () => ({
      collapsed: storedCollapsed() ?? window.innerWidth < COLLAPSE_BELOW,
      keyboard: false,
      init() {
        document.addEventListener('focusin', (event) => {
          this.keyboard = event.target instanceof Element && event.target.matches(TEXT_FIELD);
        });
        document.addEventListener('focusout', () => {
          this.keyboard = false;
        });
      },
      toggleCollapsed() {
        this.collapsed = !this.collapsed;
        rememberCollapsed(this.collapsed);
      },
      get expanded() {
        return !this.collapsed;
      },
      // The width classes are written out in full so that Tailwind finds them. "is-collapsed" is
      // the hook the templates use to make labels screen-reader-only ([.is-collapsed_&]:sr-only).
      get sidebarClass() {
        return this.collapsed
          ? 'is-collapsed md:w-[var(--mm-sidebar-collapsed)]'
          : 'md:w-[var(--mm-sidebar)]';
      },
      get tabbarClass() {
        return this.keyboard ? 'max-md:hidden' : '';
      },
    }));

    // The segmented control (ui/_segmented.jte) is a tablist of links. Arrow keys move the focus
    // between its segments, as the WAI-ARIA tabs pattern describes; Enter or Space then follows the
    // link. Home and End go to the first and last segment. The arrow keys do not follow a link by
    // themselves, so browsing the options never loads a page by accident.
    window.Alpine.data('segmented', () => ({
      tabs() {
        return [...this.$el.querySelectorAll('[role="tab"]')];
      },
      move(event) {
        const tabs = this.tabs();
        const current = tabs.indexOf(event.target);
        if (current < 0) {
          return;
        }
        let next;
        switch (event.key) {
          case 'ArrowRight':
            next = (current + 1) % tabs.length;
            break;
          case 'ArrowLeft':
            next = (current - 1 + tabs.length) % tabs.length;
            break;
          case 'Home':
            next = 0;
            break;
          case 'End':
            next = tabs.length - 1;
            break;
          default:
            return;
        }
        event.preventDefault();
        tabs[next].focus();
      },
    }));

    // The theme choice (layout/_theme_switch.jte, UI-D8). The server only reads the cookie
    // "mm_theme" (light, dark or system) to put the right <html data-theme> on first paint; this
    // component is the only writer, so there is no request to protect from CSRF. A choice is
    // applied at once: the cookie (for the next page and visit; path "/", a year, SameSite=Lax,
    // Secure on https, not HttpOnly because this script writes it), <html data-theme>, the
    // "theme-color" metas, and aria-pressed on every theme button of the page (the phone and the
    // desktop each have a copy). The "media" values mirror Theme.themeColorMedia on the server.
    const THEME_COOKIE = 'mm_theme';
    const YEAR_SECONDS = 365 * 24 * 60 * 60;
    const themeColorMedia = (theme, scheme) => {
      if (theme === 'system') {
        return `(prefers-color-scheme: ${scheme})`;
      }
      return theme === scheme ? 'all' : 'not all';
    };
    window.Alpine.data('theme', () => ({
      choose(event) {
        const choice = event.target.closest('[data-theme-choice]')?.dataset.themeChoice;
        if (!['light', 'dark', 'system'].includes(choice)) {
          return;
        }
        const secure = window.location.protocol === 'https:' ? '; Secure' : '';
        document.cookie = `${THEME_COOKIE}=${choice}; Path=/; Max-Age=${YEAR_SECONDS}; SameSite=Lax${secure}`;
        document.documentElement.dataset.theme = choice;
        document.querySelectorAll('meta[data-theme-color]').forEach((tag) => {
          tag.media = themeColorMedia(choice, tag.dataset.themeColor);
        });
        document.querySelectorAll('[data-theme-choice]').forEach((button) => {
          button.setAttribute('aria-pressed', String(button.dataset.themeChoice === choice));
        });
      },
    }));

    // The sheet and the confirm dialog (ui/_sheet.jte, plan section 5). The markup is a native
    // <dialog> opened with showModal(), which makes the rest of the page inert, puts the dialog in
    // the top layer and closes it on Escape. This component adds what is left: a Tab trap (native
    // dialogs do not wrap Tab everywhere), focus back on the control that opened the dialog, and a
    // swipe down on the grabber that closes a phone sheet. The template calls it by method name:
    // show and hide. Its state lives in closures, not in the reactive data, so Alpine never wraps
    // the DOM nodes it holds in proxies.
    const FOCUSABLE = 'a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"])';
    window.Alpine.data('sheet', () => {
      let opener = null;
      let startY = null;
      let dragY = 0;

      return {
        init() {
          const dialog = this.$refs.dialog;
          dialog.addEventListener('keydown', (event) => this.trap(event));
          dialog.addEventListener('click', (event) => {
            // The content fills the dialog, so a click that lands on the dialog itself is on the backdrop.
            if (event.target === dialog) {
              this.hide();
            }
          });
          // Runs for Escape, the X, the backdrop, a swipe and hide(); the opener gets focus back.
          dialog.addEventListener('close', () => {
            if (opener?.isConnected) {
              opener.focus();
            }
            opener = null;
          });
          const grabber = this.$refs.grabber;
          if (grabber) {
            grabber.addEventListener('pointerdown', (event) => {
              startY = event.clientY;
              dragY = 0;
              grabber.setPointerCapture(event.pointerId);
            });
            grabber.addEventListener('pointermove', (event) => {
              if (startY === null) {
                return;
              }
              dragY = Math.max(0, event.clientY - startY);
              dialog.style.transform = `translateY(${dragY}px)`;
            });
            grabber.addEventListener('pointerup', () => this.endDrag(true));
            grabber.addEventListener('pointercancel', () => this.endDrag(false));
          }
        },

        show() {
          // The first control inside the trigger is the one focus returns to. Falls back to
          // whatever had focus, for a trigger without a control.
          opener = this.$refs.opener.querySelector(FOCUSABLE) ?? document.activeElement;
          this.$refs.dialog.showModal();
        },

        hide() {
          this.$refs.dialog.close();
        },

        // Keeps Tab and Shift+Tab inside the dialog: from the last control Tab goes to the first,
        // and back the other way. Focus that is somehow outside the dialog is pulled in.
        trap(event) {
          if (event.key !== 'Tab') {
            return;
          }
          const dialog = this.$refs.dialog;
          const items = [...dialog.querySelectorAll(FOCUSABLE)].filter((el) => !el.disabled);
          if (items.length === 0) {
            event.preventDefault();
            return;
          }
          const first = items[0];
          const last = items[items.length - 1];
          const current = document.activeElement;
          const outside = current === dialog || !dialog.contains(current);
          if (event.shiftKey && (current === first || outside)) {
            event.preventDefault();
            last.focus();
          } else if (!event.shiftKey && (current === last || outside)) {
            event.preventDefault();
            first.focus();
          }
        },

        // Ends a swipe. The sheet closes only when it was released well below where it started
        // (a third of its height, at most 120 px); a cancelled swipe or a short one springs back.
        endDrag(released) {
          if (startY === null) {
            return;
          }
          const dialog = this.$refs.dialog;
          const closes = released && dragY > Math.min(120, dialog.offsetHeight / 3);
          startY = null;
          dialog.style.transform = '';
          if (closes) {
            this.hide();
          }
        },
      };
    });
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
