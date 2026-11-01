@import gg.jte.Content
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String title
@param Content content
<%-- The page frame: header (brand, household slot, language switcher), main area, footer.
     Rules for everything in here: no inline <script>, no onclick= style handlers and no style=
     attributes (the CSP forbids them; a test checks), and no literal text (use ctx.t("key")). --%>
<!DOCTYPE html>
<html lang="${ctx.language()}" class="h-full">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <meta name="theme-color" content="#15803d">
  <title>${title} · ${ctx.t("app.name")}</title>
  <link rel="manifest" href="/manifest.webmanifest">
  <link rel="icon" type="image/png" sizes="192x192" href="/icons/icon-192.png">
  <link rel="apple-touch-icon" href="/icons/apple-touch-icon.png">
  <%-- htmx under a strict CSP: no eval, no inline <script> in swapped content, no injected <style>
       for the indicator (our CSS covers it). Error responses are swapped too, so that the error
       fragment (see AppErrorController) can show up; app.js handles 401/403 itself. History
       snapshots are off so household data is never kept in the browser's localStorage. --%>
  <meta name="htmx-config" content='{"allowEval":false,"allowScriptTags":false,"includeIndicatorStyles":false,"historyCacheSize":0,"responseHandling":[{"code":"204","swap":false},{"code":"[23]..","swap":true},{"code":"[45]..","swap":true,"error":true}]}'>
  @if(ctx.csrf() != null)
  <meta name="_csrf" content="${ctx.csrf().token()}">
  <meta name="_csrf_header" content="${ctx.csrf().headerName()}">
  @endif
  <link rel="stylesheet" href="/css/app.css">
  <%-- Order matters: app.js must register its listeners before Alpine starts. --%>
  <script src="/js/htmx.min.js" defer></script>
  <script src="/js/app.js" defer></script>
  <script src="/js/alpine-csp.min.js" defer></script>
  <script src="/js/sw-register.js" defer></script>
</head>
<body class="flex min-h-full flex-col bg-stone-100 text-stone-900 antialiased">
  <a href="#main" class="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-10 focus:rounded focus:bg-white focus:px-3 focus:py-2">${ctx.t("nav.skip")}</a>

  <header class="bg-brand-700 text-white" x-data="menu" x-on:keydown.escape.window="close">
    <div class="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-x-4 px-4">
      <a href="/" class="inline-flex min-h-11 items-center text-lg font-semibold">${ctx.t("app.name")}</a>
      <button type="button" class="inline-flex min-h-11 min-w-11 items-center justify-center rounded hover:bg-brand-800 md:hidden"
              aria-controls="site-menu" x-bind:aria-expanded="open" x-on:click="toggle">
        <span class="sr-only">${ctx.t("nav.menu")}</span>
        <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
          <path d="M4 6h16M4 12h16M4 18h16"/>
        </svg>
      </button>
      <%-- Hidden on phones until the button opens it (Alpine adds max-md:flex); always shown from md up. --%>
      <div id="site-menu" class="hidden w-full flex-col gap-2 pb-3 md:flex md:w-auto md:flex-row md:items-center md:gap-4 md:pb-0" x-bind:class="panelClass">
        <%-- The current household (or the invitation to create one) once someone is signed in. --%>
        <div id="household-slot" class="min-h-11 content-center text-sm text-brand-100">
          @if(ctx.household() != null)
            <a href="/households" class="inline-flex min-h-11 items-center underline">${ctx.household().name() != null ? ctx.household().name() : ctx.t("household.create.link")}</a>
          @endif
        </div>
        <nav class="flex gap-1" aria-label="${ctx.t("lang.label")}">
          @template.layout._lang_link(ctx = ctx, code = "hu", label = ctx.t("lang.hu.short"), name = ctx.t("lang.hu.name"))
          @template.layout._lang_link(ctx = ctx, code = "en", label = ctx.t("lang.en.short"), name = ctx.t("lang.en.name"))
        </nav>
      </div>
    </div>
  </header>

  <main id="main" class="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
    <%-- htmx error fragments land here (HX-Retarget); empty:hidden keeps it invisible until then. --%>
    <div id="error-slot" class="mb-4 empty:hidden" role="alert"></div>
    ${content}
  </main>

  <footer class="border-t border-stone-300 bg-white">
    <p class="mx-auto max-w-5xl px-4 py-4 text-sm text-stone-600">${ctx.t("footer.tagline")}</p>
  </footer>
</body>
</html>
