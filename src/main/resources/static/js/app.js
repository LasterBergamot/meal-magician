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

    // The week stepper (plan/_week_stepper.jte): ArrowLeft/ArrowRight go to the previous/next week
    // (plan section 5). Keys in a field, a tab list's own arrows, modified keys and keys while a
    // dialog is open are left alone.
    window.Alpine.data('weekKeys', () => ({
      step(event) {
        if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
          return;
        }
        const target = event.target;
        if (
          target instanceof Element &&
          target.closest('input, textarea, select, [contenteditable], [role="tablist"]')
        ) {
          return;
        }
        if (document.querySelector('dialog[open]')) {
          return;
        }
        let link = null;
        if (event.key === 'ArrowLeft') {
          link = this.$refs.prev;
        } else if (event.key === 'ArrowRight') {
          link = this.$refs.next;
        }
        if (link) {
          event.preventDefault();
          link.click();
        }
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

    // The Print button of the public share page (sharing/week.jte, D28). It only opens the
    // browser's print dialog; the page itself has no form, no htmx and no state to keep.
    window.Alpine.data('printPage', () => ({
      print() {
        window.print();
      },
    }));

    // The Copy button of a new share link (sharing/_share_created.jte, D28). The link is shown only
    // once, so copying must work everywhere: navigator.clipboard where the page is a secure
    // context, and otherwise (or when the browser refuses) the input is selected so a long press
    // or Ctrl+C finishes the job. "message" feeds the live region beside the button.
    window.Alpine.data('clipboard', () => ({
      message: '',
      async copy() {
        const field = this.$refs.field;
        try {
          await navigator.clipboard.writeText(field.value);
          this.message = this.$refs.status.dataset.copied;
        } catch {
          field.focus();
          field.select();
          this.message = '';
        }
      },
    }));

    // The combobox (ui/_combobox.jte, issue #156): a multi-select with chips, a search input and a
    // list that htmx fills. It follows the WAI-ARIA Authoring Practices "Combobox with listbox
    // popup" pattern (https://www.w3.org/WAI/ARIA/apg/patterns/combobox/): DOM focus stays on the
    // input, the highlighted option is named by aria-activedescendant, ArrowDown/ArrowUp move it,
    // Enter chooses it, Escape closes the list. The chips are NOT script state: each is a list item
    // with a hidden input (the form field), so a normal form post and a 422 re-render carry them
    // with no script at all. The script only adds or removes those items. Every handler is a named
    // method because the CSP build of Alpine cannot evaluate expressions.
    const MARKS = /\p{M}+/gu;
    // The same folding as the server (accents and case ignored, blanks collapsed), so "tojas" and
    // "Tojás" count as one ingredient here too.
    const fold = (text) =>
      text.normalize('NFD').replace(MARKS, '').toLowerCase().trim().replace(/\s+/g, ' ');
    window.Alpine.data('combobox', () => ({
      isOpen: false,
      init() {
        // htmx puts a new list into the listbox after every search; start it with nothing active.
        this.$refs.listbox.addEventListener('htmx:afterSwap', () => this.reset());
      },
      get expanded() {
        return this.isOpen ? 'true' : 'false';
      },
      get closed() {
        return !this.isOpen;
      },
      open() {
        this.isOpen = true;
      },
      close() {
        this.isOpen = false;
        this.deactivate();
      },
      // Focus left the whole component (not just the input for a chip button or an option).
      focusOut(event) {
        if (!this.$el.contains(event.relatedTarget)) {
          this.close();
        }
      },
      // A press on an option would blur the input, close the list and lose the click; stop that.
      keepFocus(event) {
        event.preventDefault();
      },
      options() {
        return [...this.$refs.listbox.querySelectorAll('[role="option"]:not([hidden])')];
      },
      activeOption() {
        return this.options().find((option) => option.getAttribute('aria-selected') === 'true');
      },
      chipInputs() {
        return [...this.$refs.chips.querySelectorAll('input[type="hidden"]')];
      },
      hasChip(name) {
        return this.chipInputs().some((input) => fold(input.value) === fold(name));
      },
      deactivate() {
        this.$refs.listbox
          .querySelectorAll('[aria-selected="true"]')
          .forEach((option) => option.setAttribute('aria-selected', 'false'));
        this.$refs.input.removeAttribute('aria-activedescendant');
      },
      // Called after each new list: give the rows ids (aria-activedescendant needs them), hide the
      // ones already chosen and clear the highlight.
      reset() {
        const listbox = this.$refs.listbox;
        this.deactivate();
        listbox.querySelectorAll('[role="option"]').forEach((option, index) => {
          option.id = `${listbox.id}-option-${index}`;
          option.hidden = this.hasChip(option.dataset.value);
        });
      },
      // Moves the highlight by one row, wrapping round at both ends.
      move(step) {
        this.open();
        const options = this.options();
        if (options.length === 0) {
          return;
        }
        const current = options.findIndex((option) => option.getAttribute('aria-selected') === 'true');
        let next;
        if (current < 0) {
          next = step > 0 ? 0 : options.length - 1;
        } else {
          next = (current + step + options.length) % options.length;
        }
        this.deactivate();
        const option = options[next];
        option.setAttribute('aria-selected', 'true');
        this.$refs.input.setAttribute('aria-activedescendant', option.id);
        option.scrollIntoView({ block: 'nearest' });
      },
      // A click on the list: the row may be the click target or hold it (the Add row has a span).
      choose(event) {
        const option = event.target instanceof Element ? event.target.closest('[role="option"]') : null;
        if (option) {
          this.pick(option.dataset.value);
        }
      },
      // Enter with text typed and no row highlighted: take a listed row with that name if there is
      // one (so its spelling wins), otherwise the typed text itself as a new ingredient. Returns
      // false when nothing was typed, so Enter can submit the form as usual.
      add() {
        const typed = this.$refs.input.value.trim().replace(/\s+/g, ' ');
        if (typed === '') {
          return false;
        }
        const listed = this.options().find((option) => fold(option.dataset.value) === fold(typed));
        this.pick(listed ? listed.dataset.value : typed);
        return true;
      },
      // Adds the chip (unless it is there already), empties the search, refreshes the list for the
      // next search and closes it. The input event is what makes htmx fetch the fresh list; the
      // list is closed afterwards because that event also opens it.
      pick(name) {
        if (!this.hasChip(name)) {
          this.addChip(name);
        }
        const input = this.$refs.input;
        input.value = '';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.focus();
        this.close();
      },
      // Clones the chip from the inert <template>; the hidden input inside is the form field.
      addChip(name) {
        const chip = this.$refs.chip.content.firstElementChild.cloneNode(true);
        chip.querySelector('input').value = name;
        chip.querySelector('[data-chip-label]').textContent = name;
        const button = chip.querySelector('[data-remove]');
        button.setAttribute('aria-label', button.dataset.removeLabel.replace('{0}', name));
        this.$refs.chips.append(chip);
      },
      removeChip(event) {
        const button = event.target instanceof Element ? event.target.closest('[data-remove]') : null;
        if (!button) {
          return;
        }
        button.closest('[data-chip]').remove();
        this.reset();
        this.$refs.input.focus();
      },
      // Backspace in an empty search removes the last chip.
      backspace() {
        const last = this.chipInputs().pop();
        if (last) {
          last.closest('[data-chip]').remove();
          this.reset();
        }
      },
      onKeydown(event) {
        if (event.isComposing) {
          return;
        }
        switch (event.key) {
          case 'ArrowDown':
            event.preventDefault();
            this.move(1);
            break;
          case 'ArrowUp':
            event.preventDefault();
            this.move(-1);
            break;
          case 'Enter': {
            const active = this.isOpen ? this.activeOption() : undefined;
            if (active) {
              event.preventDefault();
              this.pick(active.dataset.value);
            } else if (this.add()) {
              event.preventDefault();
            }
            break;
          }
          case 'Escape':
            if (this.isOpen) {
              event.preventDefault();
              this.close();
            }
            break;
          case 'Backspace':
            if (this.$refs.input.value === '' && !event.repeat) {
              this.backspace();
            }
            break;
          case 'Tab':
            this.close();
            break;
          default:
            break;
        }
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
          // The browser fires "close" late (in Chrome up to a frame later), and by then focus may
          // already be somewhere on purpose: the browser's own restore put it back, the person moved
          // on, or the conflict handler below focused the banner's action. So the opener only gets
          // focus when focus was lost (on the body, or still in the closed dialog). Taking it back
          // regardless sent a quick Enter on another control to this sheet's opener instead.
          dialog.addEventListener('close', () => {
            const active = document.activeElement;
            const lost = !active || active === document.body || dialog.contains(active);
            if (lost && opener?.isConnected) {
              opener.focus();
            }
            opener = null;
          });
          // A control outside the sheet may open it (plan/week.jte's page-level sheets): "sheetOpener"
          // sends this event with the sheet's id and itself, so focus returns to it on close.
          window.addEventListener('mm:open-sheet', (event) => {
            if (event.detail?.id !== dialog.id || dialog.open) {
              return;
            }
            opener = event.detail.opener ?? null;
            dialog.showModal();
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
          // A confirm opened from a sheet sits inside the sheet's <dialog>, so its Tab events bubble
          // up to this trap; the inner dialog has already handled them.
          if (event.defaultPrevented) {
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

    // The control that opens a page-level sheet (plan/_slot_items.jte, plan/_meal_item.jte). The
    // sheet is elsewhere on the page, so the control names it in data-sheet. The sheet's body is
    // emptied first, so the last content never flashes while htmx loads the new one (the same
    // element's hx-get fills the element named by hx-target).
    window.Alpine.data('sheetOpener', () => ({
      open() {
        const target = this.$el.getAttribute('hx-target');
        if (target) {
          document.querySelector(target)?.replaceChildren();
        }
        window.dispatchEvent(
          new CustomEvent('mm:open-sheet', {
            detail: { id: this.$el.dataset.sheet, opener: this.$el },
          }),
        );
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

  // A 409 or an error lands outside the open sheets; close them so the person sees it, then put
  // focus on its action once the browser's own focus restore is done. The sheets' late close
  // handlers see that focus has moved and leave it there. #occasion-conflict is the stale-delete
  // banner of the occasion detail page (#112).
  document.addEventListener('htmx:afterSwap', (event) => {
    const target = event.detail.target;
    if (
      target?.id !== 'plan-conflict' &&
      target?.id !== 'occasion-conflict' &&
      target?.id !== 'error-slot'
    ) {
      return;
    }
    document.querySelectorAll('dialog[open]').forEach((dialog) => dialog.close());
    window.setTimeout(() => target.querySelector('a[href], button')?.focus(), 0);
  });
})();
