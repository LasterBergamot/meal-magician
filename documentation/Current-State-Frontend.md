# Current State: Frontend

What exists in `main` today (issue #13). The page is a hello page: the point is that the layout,
translations, styling, htmx, Alpine, the PWA files and the security headers all work together
before real screens are built on top. Planned UI work: [[Roadmap: Frontend]].

## Pages and templates

- `src/main/jte/layout/base.jte` is the frame of every page: header (brand, the current household as a link
  to `/households`, an "Allowlist" link to `/admin/allowlist` for instance admins only (`ctx.admin()`,
  from `request.isUserInRole("ADMIN")`), the HU/EN language links), `<main>` with an empty `#error-slot`, footer.
  On a phone the header collapses behind a menu button (an Alpine component); from the `md`
  breakpoint (768 px) it is shown in full. Touch targets are at least 44 px.
- `hello/page.jte` + `hello/_greeting.jte`: the temporary home page `/`. The "Refresh" button
  asks `/` again through htmx; because the request carries `HX-Request`, the controller returns
  only the `_greeting` fragment. Every later screen follows this pattern (`Htmx.view(...)`).
- `error/page.jte` + `error/_message.jte`: our 404, 403 and 500 pages (`AppErrorController`;
  other statuses show the 500 text). A normal request gets a full page. An htmx request gets the
  fragment, and `HX-Retarget` puts it into `#error-slot` so the page around it stays intact.
  Exception: htmx 401/403 responses make `app.js` send the whole page to `/`, because an expired
  session looks like a 403; the server also answers an anonymous htmx request with 401 +
  `HX-Redirect: /login` (#12), which htmx follows by itself.
- Every template declares `@param ViewContext ctx`. `ViewContextAdvice` puts it into every
  controller's model, so templates write `ctx.t("key")` for text and nothing has to be passed by
  hand. `ViewContext` also carries `household`, the header's household (#41): its `name()` is `null` for a
  signed-in person with no household, whose header then says "Create a household"; the whole
  field is `null` on public pages.
- `household/page.jte` + `_content.jte`, `_create.jte`, `_rename.jte`: the `/households` page
  (#41). It lists my households with my role, a "Switch" button for each other one and the
  current one marked, a rename form (owners of the current household only) and a create form.
  The forms are htmx posts. A rejected form answers 422 (or 409 when someone else renamed the
  household first) with just that form, error inside it and the typed text kept; a success sends
  the browser on with `Htmx.redirect` (`HX-Redirect`, because a plain 302 would be followed
  inside htmx's fetch and swap a whole page into the form). A header dropdown for switching is
  not built: switching happens on this page.
- `household/_invites.jte` (#42): the owner-only "Invite someone" section of that page, an e-mail
  field, a lifetime select, the list of invites with status and a Revoke button. Posts swap the
  whole `#invites` section (`hx-target="#invites"`); the new link appears in a read-only field in
  that response only. `household/join.jte` is the page an invite link opens: the household name and
  a Join button, or the reason the link was refused (the status code is 404, 403 or 410).
- `household/members.jte` and `household/_members.jte` (#43): the page at `/households/members`
  (linked from the current household's row on `/households`). Everyone sees the members with name,
  e-mail and role, and a "Leave the household" button. Owners also get "Make owner" and "Remove" on
  every other row. The buttons use `hx-confirm` (a browser dialog; it needs no inline script) and
  swap the whole `#members` section. A refused action (the last owner leaving, an owner naming
  themselves) answers 409 with the same section and a red message; a successful leave sends the
  browser to `/households` with `HX-Redirect`.
- `admin/allowlist.jte` and `admin/_allowlist.jte` (#45): the page at `/admin/allowlist`, admins
  only. An add form (one e-mail field) and the list of addresses with where each came from and when
  it was added; every row except the bootstrap admins has a Remove button (its own small form with
  the address in a hidden field, so no JSON is built in an attribute, plus `hx-confirm`). Posts swap
  the whole `#allowlist` section; a refused change answers 422 (add) or 409 (remove) with the same
  section and a red message.

## Components and the style guide (R2a)

The first pieces of the design system in `src/main/jte/ui/` (plan-ui-redesign.md §5). They use the
colour and shape utilities of the design tokens (R1); until those land in `app.css` the classes
do nothing, so the look is still the old one.
- `ui/_icon.jte`: the 30 icons of Appendix B as inline SVG, one `size` and a `filled` flag (only
  the sparkle is filled). The names are the enum `shared.web.IconName`. Icons are decorative
  (`aria-hidden`); the text next to them, or an `sr-only` label, says what they mean.
- `ui/_button.jte`: `primary`, `secondary`, `ghost` and `danger`, in sizes `sm` (36 px), `md`
  (44 px), `lg` (54 px) and `icon` (44 × 44). `busy` shows a spinner and disables the button; an
  htmx request in flight (`htmx-request` class) shows the same spinner, so a second click does
  nothing. Extra attributes such as `hx-post` come as a map.
- `ui/_fab.jte`: the gold add button. With `floating` it sits above the phone's bottom tab bar
  (the tab bar itself comes in R4) and is hidden from `md` up.
- `dev/ui.jte` (`DevUiController`, `/dev/ui`): the living style guide. It shows every icon, every
  button variant in every size and state, and the add button in both forms. It exists only in the
  `dev` profile (`task serve`), it needs a sign-in like every page, and production answers 404.
  The sections `dev/_content.jte`, `_forms.jte` and `_overlays.jte` are filled (R2b, R2c, R3). To
  see both themes, switch the browser between light and dark (the theme preference comes with
  R6).

## Sheet and confirm dialog (R3, #75)

Two overlays built on the native `<dialog>` element (plan-ui-redesign.md §5, UI-D6), so the browser
does the heavy part: `showModal()` makes the page behind inert, puts the dialog in the top layer
and closes it on Escape.
- `ui/_sheet.jte`: a bottom sheet on a phone, with a grabber to drag down, and a centred dialog
  from `md` up. It takes an `id`, the `title` (which names the dialog for screen readers), a
  `trigger` (the control that opens it, which must have `x-on:click="show"`) and the `body`, which
  htmx may load into. `closable` adds an X button. `role` is `dialog` or `alertdialog`.
- `ui/_confirm.jte`: a destructive question. It is always a centred alert dialog, even on a phone,
  with a `Cancel` button and a `danger` action. The action's `attrs` hold the request it sends
  (`hx-post`), or `x-on:click="hide"` when nothing should be sent.
- The Alpine component `sheet` in `static/js/app.js` adds what the browser does not do reliably:
  Tab and Shift+Tab stay inside the dialog, focus goes back to the control that opened it when it
  closes, and a drag down on the grabber closes a phone sheet (released past a third of its
  height, at most 120 px; a short drag springs back). A click on the backdrop closes it too.
- Rules for callers: a sheet is never opened from a sheet, and a confirm dialog must sit outside
  any region that htmx swaps, or the swap removes the open dialog.
- `/dev/ui` shows both in `dev/_overlays.jte`. `SheetAndConfirmE2ETest` drives them from the
  keyboard at phone and desktop size, and swipes the phone sheet closed.

## Form components (R2c)

The form controls of the design system are in `src/main/jte/ui/` (plan-ui-redesign.md §5). Each
takes typed parameters and translated text from the caller, and carries no text of its own. They
are all on `/dev/ui` in `dev/_forms.jte`, in every state.
- `ui/_field.jte`: a labelled text input, 48 px high with 16 px text. The label is always visible.
  `hint` is a help sentence under the input. `error` is a message already translated: it sets
  `aria-invalid`, a red border and an alert icon with the sentence. The hint and the error are
  linked from the input with `aria-describedby` (the ids are `<id>-hint` and `<id>-error`), so a
  screen reader reads them after the label.
- `ui/_select.jte`: the same frame around a native `<select>`, so the phone shows its own list.
  `options` is a list of `FormOption` (value and label), `selected` is the value shown.
- `ui/_search.jte`: a pill with a search icon in front. Its label is visually hidden, because the
  placeholder and the icon say what the field is for. It has no error state, since a search is
  not a rejected form. `attrs` carry the htmx wiring (`hx-get`, `hx-trigger`).
- `ui/_segmented.jte`: two to four segments, each a `SegmentOption` (value, label and the address
  it links to). The active segment has the violet fill, a check icon and `aria-selected`. Given a
  `target` (a CSS selector such as `#content`), each segment fetches its address into that target
  and pushes the URL, so Back and reload work; with `target` null the segments are plain links,
  which is what the style guide uses. Known gap: the ARIA tablist pattern wants arrow keys between
  the segments, and that needs an Alpine behaviour in `app.js`. Until it lands every segment stays
  in the normal tab order.
- **The 422 state:** a rejected form returns the same fragment with status 422, the message in
  `error` and what was typed (`value`, or `selected` for a select) passed back, so nothing the
  person entered is lost. The style guide shows a field and a select in that state.
- The helpers `HtmlAttributes.describedBy` and `HtmlAttributes.htmxFetch` build the attributes,
  so the fragments never write them by hand. Tests: `FormComponentsPageTest` reads every state
  from the `/dev/ui` HTML, `DescribedByTest` and `HtmxFetchTest` cover the helpers, and
  `UiGuardTest` checks the text and the CSP rules of the templates.

## Content and status components (R2b)

The content and status fragments of `src/main/jte/ui/` (plan-ui-redesign.md §5), all on the
design tokens (R1) and shown in every state on `/dev/ui`:
- `ui/_badge.jte`: a short state label (Owner, Member, Current, Today) in seven `tone`s:
  `neutral`, `brand`, `accent` (a gold fill with indigo text, because gold is never text),
  `success`, `warning`, `danger`, `info`. A badge is a `span`; it is never a control.
- `ui/_chip.jte`: a filter pill button with `aria-pressed`. Selected is a violet fill with a
  check icon, so the state is not colour alone. It is 44 px tall (the touch minimum).
- `ui/_slot_chip.jte` and `ui/_slot_dot.jte`: one meal type, `slot` = `BREAKFAST`, `LUNCH`,
  `DINNER` or `SNACK` (the order of the day). The chip always shows the icon and the word; the
  dot is a 12 px mark, or a 36 px disc with the icon for a lone marker. A dot beside a word is
  decorative; a lone dot takes an `aria-label`. The `slot-*` classes in `app.css` set the colour
  variables, so one set of utilities serves the four types.
- `ui/_card.jte`: a surface with the outline and the medium sticker shadow, in `default`,
  `accent` (occasions only) and `selected` variants. One link inside may stretch over the whole
  card (`after:inset-0`).
- `ui/_row.jte`: a list item of at least 60 px with a dashed separator, so a list is a `ul` in a
  card with `li` rows. A selected row always comes with a Current badge.
- `ui/_avatar.jte`: a gold disc with one or two initials (`sm` 32 px, `md` 44 px). The caller
  computes the initials; the avatar is decorative because the name beside it says who it is.
- `ui/_alert.jte`: an icon, a bold title, one sentence and an optional action. `success` and
  `info` are `role="status"`; `warning` and `danger` are `role="alert"`. There are no toasts.
- `ui/_empty.jte`: the empty state, a gold-outlined round icon with two sparkles, an `h2`, one
  sentence and the actions the caller passes (one primary, at most one ghost).
- `ui/_skeleton_rows.jte`: placeholder rows for a list that loads for more than about 300 ms. The
  region is `aria-busy` with a screen-reader label, and the rows pulse (not under reduced motion).

The texts come from `messages_*.properties`: the slot words (`slot.*`), the skeleton label
(`ui.skeleton.loading`) and the style guide's own texts (`dev.content.*`). The tests in
`ContentComponentsPageTest` read each state from the page, and no new fragment may use a raw
palette class (`brand-700`, `stone-*` and the like).

## Styling: Tailwind

The standalone CLI (no Node) compiles `src/main/css/app.css` into
`src/main/resources/static/css/app.css`. That output is generated and git-ignored.
- `task serve` runs `tailwindcss --watch` next to the app, and in the `dev` profile the app
  serves static files straight from `src/main/resources/static`, so a class change shows after a
  browser reload without restarting anything.
- `./gradlew build` runs the CLI once, minified (Gradle task `tailwindBuild`, before
  `processResources`). Without `task install` it stops with "run `task install`".
- Tailwind v4 is configured in CSS (`@theme`, `@source`), there is no `tailwind.config.js`. It
  only looks for class names in the templates and in `app.js`.

### Design tokens, themes and fonts (R1, #64)

`src/main/css/app.css` holds the redesign's colour tokens as CSS variables `--mm-*` (plan
§3.2, Appendix A). `:root` is **Moonlight** (light); `:root[data-theme="dark"]` is **Midnight**;
with `data-theme="system"` (what `layout/base.jte` sends for now) the OS preference picks one. The
variables are mapped into Tailwind with `@theme inline`, so the utilities exist: `bg-surface`,
`text-ink`, `border-outline`, `bg-brand`, `text-slot-ink`, `shadow-sticker-2`. The old green
`brand-50…800` scale is still there until R5 has restyled every page; R5d removes it.

- Each theme's contrast is checked by `ContrastTokensTest`: every pair of Appendix C must meet
  its WCAG minimum in both themes, and the tests fail on a bad token. Change a colour and the
  test tells you which pair broke.
- Fonts: Fredoka (headings) and Nunito (text) are self-hosted `woff2` in `static/fonts/` (SIL
  OFL, see `FONTS.md` there). `app.css` declares them with `font-display: swap`, and
  `base.jte` preloads the two above the fold. Fredoka has no Latin Extended-A glyphs, so a
  Hungarian ő or ű in a heading is drawn by the next family in `--font-display`, **Nunito** (its
  latin-ext file), then the system faces; Fredoka draws the rest (#71). `FontFallbackTest` and
  the E2E `FontFallbackE2ETest` prove the chain. Neither font has `tnum`, so digits are
  proportional.
- The page background is `surface` with a faint dot pattern (one `radial-gradient` in `app.css`).
  The dots already show on every page; the colour does not yet, because the `<body>` still carries
  the old `bg-stone-100` utility, which wins over the base rule. R4 and R5 remove it.
- `<html data-theme>` and the two `theme-color` metas (light `#6d3fe0`, dark `#130c2e`) are in
  `base.jte`'s head. R6 makes the theme a user choice.

## Client-side JavaScript (all local, no CDN, no build step)

`src/main/resources/static/js/`:
- `htmx.min.js` 2.0.11 and `alpine-csp.min.js` 3.17.5 (Alpine's CSP build). Versions, source and
  SHA-256 are recorded in `VENDORED.md` next to them.
- `app.js` (ours): registers the Alpine components (`menu`, and `sheet` for the R3 dialogs, which
  keeps focus inside and restores it on close), adds the CSRF header to htmx
  requests from `<meta name="_csrf">` tags, and redirects to `/` on an htmx 401/403.
- `sw-register.js` registers the service worker.

The layout configures htmx for the strict CSP through a `htmx-config` meta tag: no `eval`, no
script tags in swapped content, no injected `<style>`, no history snapshots in localStorage.
Why no inline script anywhere: see "Security headers" below. Alpine's CSP build cannot evaluate
expressions like `open = !open`, so a template only names a component (`x-data="menu"`,
`x-on:click="toggle"`) and the logic lives in `app.js`.

## Translations (i18n)

- Texts live in `src/main/resources/messages_hu.properties` (default) and
  `messages_en.properties`; a template writes `${ctx.t("hello.title")}`. Placeholders are
  `{0}`, `{1}` (apostrophes must be doubled).
- `shared.i18n.Messages` is the helper (public API of the `shared` module). A missing key throws
  on purpose.
- The language links in the header go to `?lang=hu` / `?lang=en`. `LanguageSwitchInterceptor`
  stores the choice in the cookie `mm_lang` (one year, `HttpOnly`, `SameSite=Lax`). The browser's
  `Accept-Language` is ignored: Hungarian is the default. Anything other than hu/en is ignored.
- **Not yet per user:** the issue asks for the choice to persist per user. `app_user.locale`
  exists since #12 but nothing reads or writes it yet; the cookie still decides. Applying the
  stored language after sign-in is planned with the profile screen (Phase 1).
- Guard tests (`UiGuardTest`): both bundles have the same keys and placeholders, every
  `ctx.t("key")` in a template exists, and no template contains hard-coded visible text.

## Security headers

`shared.web.SecurityHeadersFilter` adds, to every response (including errors and static files),
a Content-Security-Policy without `unsafe-inline`/`unsafe-eval` (everything must come from our own
origin, `frame-ancestors 'none'`), `X-Content-Type-Options: nosniff` and
`Referrer-Policy: same-origin`. It is a plain filter that runs before Spring Security's chain, so
login redirects and 401/403 answers carry the policy too; Spring Security adds `X-Frame-Options` and
HSTS. Consequences for templates: no inline `<script>`, no `onclick=`-style
handlers, no `style=` attributes (`UiGuardTest` checks it).

**CSRF:** the layout writes `<meta name="_csrf">` and `<meta name="_csrf_header">` only when a
`CsrfMetaSource` bean exists. `identity.internal.SecurityConfig` publishes it (#12), so every page
rendered through Spring Security has them and `app.js` sends the header on htmx requests.

## Sign-in page (#12)

`login/page.jte` (served by `identity.internal.LoginController` at `/login`, the only page an
anonymous visitor sees) has one link, "Sign in with Google", to `/oauth2/authorization/google`.
`?error` shows a retry notice (cancelled consent) and `?logout` a "signed out" notice. A refused
account does not come back here: it gets the 403 error page. A person who is already signed in is
sent to `/`. There is no sign-out button in the header yet (`POST /logout` works).

## PWA shell (D5)

`manifest.webmanifest` (served as `application/manifest+json` by `ManifestController`), icons in
`static/icons/` (`favicon.svg`, 192, 512, maskable 512, `apple-touch-icon`: a cartoon wizard on a
gold tile, generated by `scripts/generate-icons.py`), `static/favicon.ico` for browsers that ask
for it, and `sw.js` (cache `mm-static-v2` since R1), which only caches `/css`, `/js`, `/icons`,
`/fonts` and the manifest (stale-while-revalidate). The manifest's `theme_color` is `#6d3fe0`
and its `background_color` is `#f8f5ff`, the Moonlight brand and stage. It never caches HTML or htmx fragments and there is no offline
mode. While developing, tick "Update on reload" in the browser's dev tools, or a cached old
stylesheet will confuse you. Chromium reports the manifest as installable; installing on a real
Android/iOS phone is still a manual check (see the PR for the steps).
