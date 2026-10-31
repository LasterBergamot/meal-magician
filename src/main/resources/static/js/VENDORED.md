# Vendored JavaScript

Third-party scripts are copied here (no CDN, no Node build; PLAN.md D4) so the Content-Security-
Policy can say `script-src 'self'`. They are the unmodified `dist` files from the npm packages.
Update by hand: download the new package (`npm pack <name>@<version>`), copy the file, and
update this table (version, SHA-256) in the same commit. Dependabot cannot track these files.

| File | Package | Version | License | SHA-256 |
|---|---|---|---|---|
| `htmx.min.js` | [`htmx.org`](https://www.npmjs.com/package/htmx.org) `dist/htmx.min.js` | 2.0.11 | 0BSD | `d6fdc75f204e6bdefa99b69bf1e6d4ac69b8a364f77929f45c13476b4000f717` |
| `alpine-csp.min.js` | [`@alpinejs/csp`](https://www.npmjs.com/package/@alpinejs/csp) `dist/cdn.min.js` | 3.17.5 | MIT | `48c170c92032e527cbae71b8ce75cf1f96ca06fa869a6120ef0fe438d8e92c36` |

Check a file with `sha256sum src/main/resources/static/js/htmx.min.js`.

Why the Alpine **CSP build**: the normal build turns attribute text into code with `new
Function`, which the CSP forbids (`unsafe-eval`). The CSP build only understands simple names and
property paths, so components are registered in `app.js` with `Alpine.data(...)`.

`app.js` and `sw-register.js` are ours, not vendored.
