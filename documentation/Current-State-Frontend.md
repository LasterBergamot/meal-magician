# Current State: Frontend

What exists in `main` today (issue #13). The page is a hello page: the point is that the layout,
translations, styling, htmx, Alpine, the PWA files and the security headers all work together
before real screens are built on top. Planned UI work: [[Roadmap: Frontend]].

## Pages and templates

- `src/main/jte/layout/base.jte` is the frame of every page. A signed-in person gets the app shell
  (`layout/_shell.jte`, see "App shell and navigation" below). An anonymous visitor (sign-in, error
  pages) gets a plain header with the HU/EN language links, `<main>` with an empty `#error-slot`, and
  a footer. Touch targets are at least 44 px.
- `hello/page.jte` + `hello/_greeting.jte`: the temporary home page `/` (restyled in R5a, #85).
  Inside the shell: an `h1` with one sparkle, the lead sentence and a `ui/_card` holding today's
  date (clock icon) and a secondary "Refresh" `ui/_button`. The button
  asks `/` again through htmx; because the request carries `HX-Request`, the controller returns
  only the `_greeting` fragment. Every later screen follows this pattern (`Htmx.view(...)`).
- `error/page.jte` + `error/_message.jte`: our 404, 403 and 500 pages (`AppErrorController`;
  other statuses show the 500 text). A normal request gets a full page. An htmx request gets the
  fragment, and `HX-Retarget` puts it into `#error-slot` so the page around it stays intact.
  Restyled in R5a (#85): the full page is a centred card (alert icon, `h1`, the sentence, a primary
  "Back to the home page" link); the fragment is a `danger` `ui/_alert` with a secondary link as
  its action. Both links are anchors styled like `ui/_button`, because that component renders a
  `<button>`.
  Exception: htmx 401/403 responses make `app.js` send the whole page to `/`, because an expired
  session looks like a 403; the server also answers an anonymous htmx request with 401 +
  `HX-Redirect: /login` (#12), which htmx follows by itself.
- Every template declares `@param ViewContext ctx`. `ViewContextAdvice` puts it into every
  controller's model, so templates write `ctx.t("key")` for text and nothing has to be passed by
  hand. `ViewContext` also carries `household`, the header's household (#41): its `name()` is `null` for a
  signed-in person with no household, whose shell then says "Create a household"; the whole
  field is `null` on public pages. `ViewContext` also carries `user` (the shell's name and
  initials, `null` when anonymous, which is what picks the layout) and `active` (the navigation
  destination the page belongs to, see below).
- `household/page.jte` + `_content.jte`, `_create.jte`, `_rename.jte`: the `/households` page
  (#41, restyled in R5b, #86). One h1, then cards built from the `ui/` components: a card of rows
  (one per household, with an Owner or Member badge; the current one is a selected row with a
  Current badge and a check, and carries the Members link), a rename card (owners of the current
  household only) and a create card. A person with no household sees `ui/_empty` instead of the
  list, and the Create button is the page's primary one only then (otherwise secondary). Switching
  to another household is not on this page: it is the household switcher in the shell (R4). The
  forms are htmx posts and the `<form>` is the root of its fragment (`display: contents`, so the
  card inside lays out like the others). A rejected form answers 422 with just that form, the
  message under the field (`ui/_field`) and the typed text kept; a 409 (someone else renamed the
  household first) shows a warning alert with a Reload button instead, and keeps the typed name. A
  success sends the browser on with `Htmx.redirect` (`HX-Redirect`, because a plain 302 would be
  followed inside htmx's fetch and swap a whole page into the form).
- `household/_invites.jte` (#42, R5b): the owner-only "Invite someone" card of that page, an e-mail
  field, a lifetime select (`ui/_select`), and the list of invites as rows with a status badge
  (Waiting, Used, Revoked, Expired), the expiry and, while pending, a Revoke button that opens a
  `ui/_confirm` naming the address. With no invites the list is a dashed "No invites yet" box. Posts
  swap the whole `#invites` section (`hx-target="#invites"`), which also closes the open dialog; the
  new link appears in a success alert, in a read-only field, in that response only.
  `household/join.jte` is the page an invite link opens: a centred card with the household name and
  a Join button, or a danger alert with the reason the link was refused (the status code is 404,
  403 or 410) and a way back.
- `household/members.jte` and `household/_members.jte` (#43, R5b): the page at
  `/households/members` (linked from the current household's row on `/households`). Everyone sees
  the members as rows with an avatar (the initials come from `MemberRow.initials()`, which reuses
  the shell's rule), name, e-mail and an Owner or Member badge, and a "Leave the household" button.
  Owners also get "Make owner" and "Remove" on every other row. Each of these opens a `ui/_confirm`
  that names the person (the old `hx-confirm` browser dialogs are gone); its confirm button posts and
  swaps the whole `#members` section, which closes the dialog. A refused action (the last owner
  leaving, an owner naming themselves) answers 409 with the same section and a danger alert; a
  successful leave sends the browser to `/households` with `HX-Redirect`. The avatars use the
  `soft` tone: on a phone the tab bar already spends the screen's one gold highlight on the current
  tab.
- `admin/allowlist.jte` and `admin/_allowlist.jte` (#45, restyled in R5c, #85): the page at
  `/admin/allowlist`, admins only, inside the shell in a reading-width column. Two cards: "Let
  someone in" (a `ui/_field` with the 422 message under it and a primary Add button) and the list
  (a `ui/_row` per address: the address, a badge for where it came from and when it was added). Every
  row except the bootstrap admins has a danger Remove button that opens a `ui/_confirm` dialog
  ("Remove x@y?"); the dialog's confirm button posts with `hx-include` pointing at a hidden input
  holding the address (so no JSON is built in an attribute). Posts swap the whole `#allowlist`
  section, which also closes the open dialog; a refused change answers 422 (add) or 409 (remove) with
  the same section and the message.

## Meal pages (#88)

The meal library: a searchable list with filters, one meal's page, the create and edit form, the
phone's quick add, and archive and restore. The controllers are `MealLibraryController` and
`MealFormController` in `catalog/internal`, the page models are `MealsPage`, `MealFilter`,
`MealForm` and `MealFormPage`, and the templates are in `meal/`. Every route is registered in
`TenantEndpoints`, so the tenant guard test checks it: a person in no household gets 404, and so
does a meal of another household. Every member may use the library, so no route is owner-only. The
behaviour is in [[Current State: Backend]].

- **Routes:** `GET /meals` is the list, with `q` (part of a name), `tag`, `archived=true` and
  `sort` (`recent`, the default, or `name`). `GET /meals/{id}` is one meal. `GET /meals/new` and
  `POST /meals` create a meal; `GET /meals/{id}/edit` and `POST /meals/{id}` edit it.
  `POST /meals/quick` is the quick add (name only). `POST /meals/{id}/archive` and
  `POST /meals/{id}/restore` take a meal out of the list and put it back.
- **The list's state is in the URL.** `MealFilter` reads the parameters, and every chip, segment
  and the search box keeps the parts it does not change, so a reload, Back and a shared link show
  the same list. A tag filter is one tag at a time; pressing the chip of the active tag clears it.
  "Show archived" adds the archived meals.
- **Recently used first** is the default sort: the meals planned lately come first, the most
  recently planned first, then the others, newest first. "A to Z" sorts by name. The planned order
  comes from the plan, through the catalog's `RecentMealsProvider`.
- **htmx on the list:** the search box waits 300 ms after the last keystroke, then fetches only the
  results (`meal/_results.jte`) into `#meal-results` and updates the address. The tag chips, the
  archived chip and the sort segments do the same. The search box is a GET form, so Enter works
  without htmx. The list URL answers an htmx request with the results only and any other request
  with the whole page (`Htmx.view`).
- **Phone and desktop quick add:** on a phone a floating add button (`ui/_fab`) opens the quick-add
  form in a bottom sheet (`ui/_sheet`). From `md` up the same form is a bar under the heading. There
  are two copies with their own ids (`quick-sheet-form` and `quick-bar-form`); a hidden field
  `form` says which copy posted, so a rejected name draws the same copy again.
- **Saving and refusing:** a saved create, edit, quick add, archive or restore ends with
  `Htmx.redirect`: to the list, or to the meal's page after an edit or a restore. A rejected form
  answers 422 with its own fragment, the message under the field and what was typed, so nothing is
  lost. A form that was opened before someone else saved the meal answers 409 with a warning and a
  link to reload the edit form, and nothing is written. A rejected quick add answers 422 with the
  quick-add form for htmx; without htmx it shows the full create form with the name filled in.
- **Archive** is behind the confirm dialog (`ui/_confirm`, a centred alert), because it takes the
  meal out of the list. The meal keeps its history and its planned slots, and its page then shows an
  info alert with a Restore button.
- **Recipe links** are shown on the meal's page only. They open in a new tab with
  `rel="noopener noreferrer"`, so the other site cannot reach back into this tab. The list card
  shows no recipe link, because a link inside the card's stretched link is not allowed.
- **Occasion types** are checkboxes on the form and badges on the meal's page. The labels come from
  the `meal.occasion.*` keys.
- **Templates:** `meal/list.jte` (the page) and `meal/_results.jte` (the part htmx swaps),
  `_card.jte` (one meal in the list), `detail.jte`, `form.jte` (the create and edit page) and
  `_form.jte` (its form, which swaps itself), `_quick.jte` (the quick-add form) and
  `_link_button.jte` (a link drawn like a `ui/_button`, because `ui/_button` always draws a
  `<button>`). The textarea and the occasion checkboxes are drawn in `_form.jte` itself, since the
  design system has no component for them yet.
- **Tests:** `MealPagesTest` runs the pages through MockMvc (the list, filters, sort, htmx
  fragments, the form, the tenant cases), `MealPageModelsTest` covers the page models, and the
  browser test `MealPagesE2ETest` adds, finds, edits, archives and restores a meal at phone and
  desktop size.

## Week view (Phase 3, #97)

The week planner page `GET /plan`, read-only for now. The controller is `PlanController` in
`planning/internal`, the page models are `IsoWeek`, `WeekPage`, `DayPlan`, `SlotRow`, `SlotGroup`
and `PlannedMeal`, `WeekPageFactory` builds them and `PlanDates` formats the dates, and the
templates are in `plan/`. The route is registered in `TenantEndpoints`: a person in no household
gets 404, and another household's slots never show. Every member may look. The behaviour behind it
is in [[Current State: Backend]].

- **Route and parameter:** `GET /plan?week=2026-W45`. The `week` value is an ISO week
  (`YYYY-Www`, Monday to Sunday, so `2026-W53` and `2026-W01` cross the year). It is read with a
  strict regex and checked against the weeks the year really has; a missing or invalid value means
  this week, by the application `Clock`. The URL answers an htmx request with the `plan/_week`
  fragment and any other request with the whole page (`Htmx.view`).
- **Phone and desktop:** below 1024 px the page shows a week strip (seven day buttons that jump to
  the day) and seven day sections, each with four slot rows. From 1024 px (`lg`) it shows a table,
  one column per day and one row per slot type. The table starts at `lg` and not at `md` because
  at 768 px eight columns are about 80 px each, too narrow for a meal name. Both versions are in
  the markup and CSS shows one.
- **Today** is marked in both: the strip's day circle and the table's column header carry
  `aria-current="date"`, and the day section header and the column header have a gold fill with a
  "Today" badge.
- **The stepper** (`plan/_week_stepper.jte`): previous and next are links, so they work without
  JavaScript. With htmx each fetches its week into the one swap region `#plan-week` and pushes the
  URL (`HtmlAttributes.htmxFetch`). The arrows and "Back to this week" (shown only when today is
  outside the shown week) have stable ids (`plan-prev`, `plan-next`, `plan-today`), because htmx
  gives focus back to the element with the same id after a swap, so a keyboard user stays on the
  arrow. On a desktop ArrowLeft and ArrowRight click the arrows (the Alpine `weekKeys` component);
  keys in a field, with a modifier, in a tab list or while a dialog is open are left alone.
- **Back after a swap:** `htmx-config` has `refreshOnHistoryMiss: true`. History snapshots are off
  (`historyCacheSize: 0`), so without it a Back after an htmx swap that pushed the URL refetched
  the page with `HX-Request` and swapped only a fragment into `<body>`. Now a history miss reloads
  the whole page. This also fixes Back on `/meals`, which had the same fault.
- **Content:** a slot with meals shows each meal's name, an "Archived" badge for an archived meal
  (the plan keeps it) and the slot's note; an empty slot is a dashed box with "Nothing planned".
  The names come from one `MealService.search` of the whole library (archived included), not one
  lookup per slot.
- **Templates:** `week.jte` (the page) and `_week.jte` (the swap region), `_week_stepper.jte`,
  `_strip.jte`, `_day.jte` and `_slot_row.jte` (the phone), `_grid.jte` and `_cell.jte` (the
  desktop), and `_slot_items.jte` and `_meal_item.jte` (shared). Dates are formatted in the page
  model and never in a template; the `slot-*` colour class is an `@if` chain so Tailwind finds it.
  `data-group` and `data-slot-id` are for the tests.
- **Tests:** `IsoWeekTest` (year boundaries, invalid codes), `PlanPageModelsTest`, `PlanPagesTest`
  (MockMvc: the page, an htmx fragment, planned meals, another household, a stranger, English) and
  the browser test `PlanWeekE2ETest` at phone and desktop size.
- **Not yet:** editing (adding, moving and removing meals, issue #98), swipe to change week, and
  `/` is still the hello page (it highlights no tab).

## App shell and navigation (R4, #79)

Every signed-in page is drawn inside the shell (`layout/_shell.jte`; `base.jte` chooses it when
`ctx.user()` is set). The shell is one Alpine component, `shell`, in `static/js/app.js`.
- **Phone (below 768 px):** a top bar (the brand mark, the household switcher pill and the avatar),
  the page, and a bottom tab bar (`layout/_tabbar.jte`, 64 px plus the safe-area inset). The
  avatar opens a small menu: the person's name, the language choice, the theme choice, the admin
  link for instance admins, and sign-out. The tab bar hides while a text field has focus, which on a phone means the
  on-screen keyboard is up.
- **Desktop (768 px and up):** a sidebar (`layout/_sidebar.jte`, 248 px): the brand, the household
  switcher as a full-width row, a "Planner" section with the destinations (the current one is a
  violet row), and at the bottom the admin link (admins), the language choice, the theme choice, the collapse
  button and sign-out. Collapsed, the sidebar is 72 px of icons: each link keeps its name for screen
  readers and a tooltip. It starts collapsed below 1024 px. The choice is kept in `localStorage`
  under `mm.sidebarCollapsed`, for this browser only; every access is in try/catch because some
  browsers refuse storage.
- **Destinations** (`shared.web.NavDestination`): Plan (`/plan`, the week view; `/` is still the hello page and highlights no tab),
  Meals (`/meals`, the meal library, #88) and Household (`/households`). Occasions (`/occasions`)
  has no page yet, so it is **not listed** (a flag on the enum; switching one on is a one-word
  change). `NavDestination.matching` picks the current tab from the request path, so
  `/households/members` highlights Household and `/` highlights no tab.
- **The household switcher** (`layout/_household_switcher.jte`) is in both shells. Its button opens
  a `ui/_sheet` (a bottom sheet on a phone, a centred dialog on a desktop) that lists **only the
  person's own households**, built from their memberships (`household.internal.HouseholdNavBean`).
  The current one is marked. Each other one is a small form that posts to
  `/households/{id}/switch`, which answers 404 for a household the person is not in. The switch
  ends on the home page. Escape closes the sheet and focus goes back to the button.
- **Sign-out** (`layout/_sign_out.jte`) is a POST form to `/logout` with the CSRF token as the hidden
  field `_csrf`, Spring Security's default parameter name. It is not a link, because a link would
  let any other page sign the person out.
- **Language:** the switch (`POST /language`, see "Translations (i18n)") sets the cookie `mm_lang`, and for a signed-in person
  it also saves `app_user.locale` (`identity.internal.StoredLanguage`, which implements
  `shared.i18n.LanguageStore`). At the next sign-in, `SessionCleanupOnSignIn` hands the stored value
  to `SignInLanguage`, which writes the cookie, so the choice follows the person to a new browser.
  A stored value this version does not support falls back to Hungarian.
- **Navigation is plain links**, so each destination is a full page load. Plan §4 asks for
  `hx-boost`; it is not used yet, because some pages (the hello page) answer a normal request with
  the whole page, and boosting would nest that inside the content area.
- **Keyboard:** the segmented control (`ui/_segmented`) moves focus with the arrow keys, Home and
  End, through the Alpine `segmented` component; Enter follows the link as before.
- **Landmarks:** a skip link, `<header>` (phone only), `<nav aria-label="Fő navigáció">` (the tab bar
  on a phone, the sidebar on a desktop), `<main id="main">`.
- **Admin link:** the allowlist page (`/admin/allowlist`) stays reachable for instance admins from
  the sidebar (desktop) and the avatar menu (phone).
- **One gold mark on a phone (#83):** the current tab has the gold outline, so the top bar's avatar
  uses the `soft` tone (`brand-soft` with `brand-ink`) instead of the gold disc. Plan §3.1 allows one
  gold highlight per screen.

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
  see both themes, use the theme buttons in the shell (R6).

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
  which is what the style guide uses. The arrow keys move focus between the segments (Left and
  Right wrap around, Home and End go to the ends) through the Alpine `segmented` component in
  `app.js`; they do not follow a link, so Enter or Space still does that.
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
- `ui/_avatar.jte`: a disc with one or two initials (`sm` 32 px, `md` 44 px). `tone` is `gold` (the
  default, a gold fill) or `soft` (`brand-soft` with `brand-ink`, for a screen that already has its
  gold mark, such as the phone top bar). The caller computes the initials; the avatar is decorative because the name beside it says who it is.
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
with `data-theme="system"` the OS preference picks one (the person's choice, R6, is below). The
variables are mapped into Tailwind with `@theme inline`, so the utilities exist: `bg-surface`,
`text-ink`, `border-outline`, `bg-brand`, `text-slot-ink`, `shadow-sticker-2`. The old green
`brand-50…800` scale is gone (R5d, #96): nothing used it once every page was restyled, and
`UiGuardTest` now fails on a `brand-<digit>` or `stone-` class, or a raw palette colour, in any
template or `app.js`, and on the old scale in `app.css`.

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
  The dots and the colour show on every page: R4 removed the old `bg-stone-100` utility from
  `<body>`, which had been overriding the base rule.
- `<html data-theme>` and the two `theme-color` metas (light `#6d3fe0`, dark `#130c2e`) are in
  `base.jte`. The theme is the person's choice (R6, below).

### Theme preference (R6, #87)

The theme is **light** (Moonlight), **dark** (Midnight) or **system** (follow the device's
`prefers-color-scheme`, the default). It is kept in the cookie `mm_theme`, like `mm_lang` (UI-D8);
per user in the database comes later, together with the per-user language.
- **The server only reads it.** `ViewContextAdvice` turns the cookie into `ViewContext.theme()`
  (`shared.web.Theme`; a missing or unknown value is `system`), and `base.jte` writes
  `<html data-theme="…">` from it, so the first paint already has the right colours and nothing
  flashes. The sign-in and error pages share `base.jte`, so they follow the cookie too. The two
  `theme-color` metas get a `media` value from `Theme.themeColorMedia`: for `system` each applies
  to its own colour scheme; for an explicit choice only the chosen one applies, so the browser's own
  bars match the page even when the device prefers the other scheme.
- **The browser writes it.** The theme control (`layout/_theme_switch.jte`, three buttons made by
  `layout/_theme_button.jte`: Light, Dark, System) sits in the phone's avatar menu and in the
  desktop sidebar (labels become screen-reader-only and the buttons stack when the sidebar is
  collapsed). The Alpine component `theme` in `app.js` sets the cookie (`Path=/`, one year,
  `SameSite=Lax`, `Secure` on https, not `HttpOnly` because the script writes it), flips
  `data-theme`, moves the metas' `media` and sets `aria-pressed` on every theme button, so the
  phone's and the desktop's copies agree. No request is sent, so there is no state-changing GET and
  no CSRF question. The server renders `aria-pressed` from the cookie.
- **CSS:** no change was needed. `:root` is Moonlight, `[data-theme="dark"]` is Midnight, and the
  `prefers-color-scheme: dark` block applies only when `data-theme` is neither `light` nor `dark`,
  so an explicit choice beats the device.
- **Tests:** `ThemeTest`, `ViewContextAdviceTest`, `HelloPageTest` and `AppShellPagesTest` cover the
  cookie, the fallback and the markup. `ThemePreferenceE2ETest` loads the sign-in page with
  JavaScript off and each cookie value (so `data-theme` must come from the server's HTML), then
  chooses each theme in the real shell at 390 and 1280 px with the device set to dark, reloads, and
  checks the cookie's attributes.

## Client-side JavaScript (all local, no CDN, no build step)

`src/main/resources/static/js/`:
- `htmx.min.js` 2.0.11 and `alpine-csp.min.js` 3.17.5 (Alpine's CSP build). Versions, source and
  SHA-256 are recorded in `VENDORED.md` next to them.
- `app.js` (ours): registers the Alpine components: `menu` (the avatar menu), `shell` (the sidebar's
  collapsed state and the tab bar's keyboard hiding, R4), `segmented` (arrow keys for the segmented
  control, R4), `weekKeys` (the week stepper's arrow keys, #97), `theme` (the cookie and `data-theme`, R6) and `sheet` for the R3 dialogs, which
  keeps focus inside and restores it on close.
  It also adds the CSRF header to htmx
  requests from `<meta name="_csrf">` tags, and redirects to `/` on an htmx 401/403.
- `sw-register.js` registers the service worker.

The layout configures htmx for the strict CSP through a `htmx-config` meta tag: no `eval`, no
script tags in swapped content, no injected `<style>`, no history snapshots in localStorage, and
a history miss reloads the page (`refreshOnHistoryMiss`).
Why no inline script anywhere: see "Security headers" below. Alpine's CSP build cannot evaluate
expressions like `open = !open`, so a template only names a component (`x-data="menu"`,
`x-on:click="toggle"`) and the logic lives in `app.js`.

## Translations (i18n)

- Texts live in `src/main/resources/messages_hu.properties` (default) and
  `messages_en.properties`; a template writes `${ctx.t("hello.title")}`. Placeholders are
  `{0}`, `{1}` (apostrophes must be doubled).
- `shared.i18n.Messages` is the helper (public API of the `shared` module). A missing key throws
  on purpose.
- The language buttons (in the shell, or the anonymous header) are small forms that `POST` to
  `/language` with the `_csrf` field. `LanguageController` stores the choice in the cookie `mm_lang`
  (one year, `HttpOnly`, `SameSite=Lax`) and answers `303` back to the page the person came from: the
  path and query of the `Referer`, only when it is from this host, otherwise `/`. The browser's
  `Accept-Language` is ignored: Hungarian is the default. Anything other than hu/en is ignored.
  `/language` is in `PUBLIC_PATHS` because anonymous visitors switch language too (CSRF still
  applies). A `?lang=` query on a GET does nothing any more (#83).
- **Per user (R4, #79):** for a signed-in person the choice is also saved in `app_user.locale`, and
  it is applied again at the next sign-in, on any browser (see "App shell and navigation").
  Anonymous switches change only the cookie. Because the switch is a CSRF-protected POST, another
  site cannot change a person's stored language.
- Guard tests (`UiGuardTest`): both bundles have the same keys and placeholders, every
  `ctx.t("key")` in a template exists, no template contains hard-coded visible text, and no
  template or `app.js` uses an old or raw palette colour.

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
anonymous visitor sees; restyled in R5a, #85) is one centred card outside the shell: the wizard
brand mark with two sparkles, the wordmark as the `h1`, a tagline, one large primary link "Sign in
with Google" to `/oauth2/authorization/google` (an anchor styled like the `lg` primary button) and
a quiet line that only invited accounts get in. `?error` shows a `danger` alert (cancelled consent)
and `?logout` a `success` alert above the button. A refused
account does not come back here: it gets the 403 error page. A person who is already signed in is
sent to `/`. Sign-out is the shell's button (a POST to `/logout`, see "App shell and navigation").

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
