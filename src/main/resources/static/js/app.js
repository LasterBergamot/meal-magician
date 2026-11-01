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
