// The install hint (#126, PLAN.md D5, Phase 5 item 3). A dismissible banner in the app shell:
// an Install button where the browser offers one (the "beforeinstallprompt" event, Chromium and
// friends) and "Share, then Add to Home Screen" instructions on iPhone and iPad Safari, which has
// no prompt at all. It is hidden when the app already runs installed, after "appinstalled", and for
// 30 days after the person dismisses it. The only thing stored is one timestamp in localStorage
// (mm_install_dismissed), a convenience for this browser, so every access sits in try/catch.
//
// Alpine's CSP build evaluates no expressions, so the template (layout/_install_hint.jte) binds
// plain properties only: hidden, installHidden and iosHidden. This file must load before Alpine.
(() => {
  const DISMISSED_KEY = 'mm_install_dismissed';
  const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;
  const IOS_DEVICE = /iPhone|iPad|iPod/;
  const IOS_OTHER_BROWSER = /CriOS|FxiOS|EdgiOS/;

  // The event can fire before Alpine has started the component, so keep the first one here.
  let earlyPrompt = null;
  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    earlyPrompt = event;
  });

  const dismissedRecently = () => {
    try {
      const stored = Number(window.localStorage.getItem(DISMISSED_KEY));
      return stored > 0 && Date.now() - stored < THIRTY_DAYS_MS;
    } catch {
      return false;
    }
  };
  const rememberDismissal = () => {
    try {
      window.localStorage.setItem(DISMISSED_KEY, String(Date.now()));
    } catch {
      // Nothing to do: the hint still hides for this page view.
    }
  };
  const installed = () =>
    window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
  const iosSafari = () => {
    const agent = window.navigator.userAgent;
    return IOS_DEVICE.test(agent) && !IOS_OTHER_BROWSER.test(agent);
  };

  document.addEventListener('alpine:init', () => {
    window.Alpine.data('installHint', () => ({
      hidden: true,
      installHidden: true,
      iosHidden: true,
      deferred: null,
      init() {
        if (installed() || dismissedRecently()) {
          return;
        }
        window.addEventListener('beforeinstallprompt', (event) => {
          event.preventDefault();
          this.offerInstall(event);
        });
        window.addEventListener('appinstalled', () => this.hide());
        if (earlyPrompt !== null) {
          this.offerInstall(earlyPrompt);
        } else if (iosSafari()) {
          this.hidden = false;
          this.iosHidden = false;
        }
      },
      offerInstall(event) {
        this.deferred = event;
        this.hidden = false;
        this.installHidden = false;
        this.iosHidden = true;
      },
      hide() {
        this.hidden = true;
      },
      async install() {
        const event = this.deferred;
        this.hide();
        this.deferred = null;
        if (event !== null) {
          await event.prompt();
        }
      },
      dismiss() {
        rememberDismissal();
        this.hide();
      },
    }));
  });
})();
