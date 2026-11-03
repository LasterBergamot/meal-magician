@import gg.jte.Content
@import hu.lasterbergamot.mealmagician.shared.web.Theme
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String title
@param Content content
<%-- The page frame. A signed-in person gets the app shell (sidebar on desktop, top bar and bottom tab
     bar on a phone: layout/_shell); an anonymous visitor (sign-in, error) gets a plain header with the
     language choice, the content and a footer. Rules for everything in here: no inline <script>, no
     onclick= style handlers and no style= attributes (the CSP forbids them; a test checks), and no
     literal text (use ctx.t("key")). --%>
<!DOCTYPE html>
<%-- data-theme drives the colour tokens in app.css and comes from the mm_theme cookie (UI-D8), read by
     the server so the first paint already has the right theme: "light" and "dark" are explicit,
     "system" follows the OS. Moonlight is :root, Midnight is [data-theme="dark"]. The "theme"
     component in app.js changes it when the person chooses. --%>
<html lang="${ctx.language()}" data-theme="${ctx.theme().value()}" class="h-full">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <%-- The browser-chrome colour: the OS theme for "system", the chosen one otherwise (Theme.themeColorMedia
       turns the other meta off); the "theme" component in app.js moves the media values on a change.
       The values match the Moonlight brand and the Midnight stage (app.css). --%>
  <meta name="theme-color" data-theme-color="light" media="${ctx.theme().themeColorMedia(Theme.LIGHT)}" content="#6d3fe0">
  <meta name="theme-color" data-theme-color="dark" media="${ctx.theme().themeColorMedia(Theme.DARK)}" content="#130c2e">
  <%-- The two fonts used above the fold (plan §6.2); the @font-face rules are in app.css. --%>
  <link rel="preload" href="/fonts/baloo2-latin.woff2" as="font" type="font/woff2" crossorigin>
  <link rel="preload" href="/fonts/nunito-latin.woff2" as="font" type="font/woff2" crossorigin>
  <title>${title} · ${ctx.t("app.name")}</title>
  <link rel="manifest" href="/manifest.webmanifest">
  <link rel="icon" type="image/svg+xml" href="/icons/favicon.svg">
  <link rel="icon" type="image/png" sizes="192x192" href="/icons/icon-192.png">
  <link rel="apple-touch-icon" href="/icons/apple-touch-icon.png">
  <meta name="description" content="${ctx.t("footer.tagline")}">
  <meta name="mobile-web-app-capable" content="yes">
  <meta name="apple-mobile-web-app-capable" content="yes">
  <meta name="apple-mobile-web-app-title" content="${ctx.t("app.name")}">
  <%-- htmx under a strict CSP: no eval, no inline <script> in swapped content, no injected <style>
       for the indicator (our CSS covers it). Error responses are swapped too, so that the error
       fragment (see AppErrorController) can show up; app.js handles 401/403 itself. History
       snapshots are off so household data is never kept in the browser's localStorage, and a history
       miss reloads the page, because our URLs answer htmx with fragments. --%>
  <meta name="htmx-config" content='{"allowEval":false,"allowScriptTags":false,"includeIndicatorStyles":false,"historyCacheSize":0,"refreshOnHistoryMiss":true,"responseHandling":[{"code":"204","swap":false},{"code":"[23]..","swap":true},{"code":"[45]..","swap":true,"error":true}]}'>
  @if(ctx.csrf() != null)
  <meta name="_csrf" content="${ctx.csrf().token()}">
  <meta name="_csrf_header" content="${ctx.csrf().headerName()}">
  @endif
  <link rel="stylesheet" href="/css/app.css">
  <%-- Order matters: app.js must register its listeners before Alpine starts. --%>
  <script src="/js/htmx.min.js" defer></script>
  <script src="/js/app.js" defer></script>
  <script src="/js/install-hint.js" defer></script>
  <script src="/js/alpine-csp.min.js" defer></script>
  <script src="/js/sw-register.js" defer></script>
</head>
<body class="flex min-h-full flex-col antialiased text-ink">
  <a href="#main" class="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:border-2 focus:border-outline focus:bg-surface-raised focus:px-3 focus:py-2 focus:text-ink">${ctx.t("nav.skip")}</a>

  @if(ctx.user() != null)
    @template.layout._shell(ctx = ctx, content = content)
  @else
    <header class="mx-auto flex w-full max-w-[var(--mm-reading)] items-center justify-between gap-3 px-4 pt-4">
      <span class="font-display text-xl font-semibold text-ink">${ctx.t("app.name")}</span>
      <nav class="flex gap-1" aria-label="${ctx.t("lang.label")}">
        @template.layout._lang_link(ctx = ctx, code = "hu", label = ctx.t("lang.hu.short"), name = ctx.t("lang.hu.name"))
        @template.layout._lang_link(ctx = ctx, code = "en", label = ctx.t("lang.en.short"), name = ctx.t("lang.en.name"))
      </nav>
    </header>

    <main id="main" class="mx-auto w-full max-w-[var(--mm-reading)] flex-1 px-4 py-6">
      <%-- htmx error fragments land here (HX-Retarget); empty:hidden keeps it invisible until then. --%>
      <div id="error-slot" class="mb-4 empty:hidden" role="alert"></div>
      ${content}
    </main>

    <footer class="mx-auto w-full max-w-[var(--mm-reading)] px-4 py-4 text-sm text-ink-muted">
      <p>${ctx.t("footer.tagline")}</p>
    </footer>
  @endif
</body>
</html>
