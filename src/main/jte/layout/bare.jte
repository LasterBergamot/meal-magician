@import gg.jte.Content
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String title
@param Content content
<%-- The frame of the public share page (PLAN.md D28), for a visitor who is not signed in and has no
     session. Everything the normal frame (layout/base) adds is left out on purpose: no manifest and
     no service-worker registration (the page is never installed or cached), no htmx, no CSRF meta
     tags and no language form (they would need a session or a cookie, and this page writes
     neither), no household or person. The page is read-only and has no form. Rules as everywhere:
     no inline <script>, no onclick= handlers, no style= attributes, no literal text. The theme is
     fixed to light, because the theme cookie is not read here and a printed page should be light. --%>
<!DOCTYPE html>
<html lang="${ctx.language()}" data-theme="light" class="h-full">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
  <meta name="robots" content="noindex,nofollow,noarchive">
  <meta name="referrer" content="no-referrer">
  <meta name="theme-color" content="#6d3fe0">
  <meta name="theme-color" media="(prefers-color-scheme: dark)" content="#6d3fe0">
  <title>${title} · ${ctx.t("app.name")}</title>
  <link rel="icon" type="image/svg+xml" href="/icons/favicon.svg">
  <link rel="stylesheet" href="/css/app.css">
  <%-- Order matters: app.js must register its listeners before Alpine starts. --%>
  <script src="/js/app.js" defer></script>
  <script src="/js/alpine-csp.min.js" defer></script>
</head>
<body class="flex min-h-full flex-col antialiased text-ink">
  <main id="main" class="mx-auto w-full max-w-5xl flex-1 px-4 py-6">
    ${content}
  </main>
  <footer class="mx-auto w-full max-w-5xl px-4 py-4 text-sm text-ink-muted">
    <p>${ctx.t("share.public.footer")}</p>
  </footer>
</body>
</html>
