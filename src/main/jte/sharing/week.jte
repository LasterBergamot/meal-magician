@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext
@import hu.lasterbergamot.mealmagician.sharing.internal.SharedWeekPage
@import java.util.Map

@param ViewContext ctx
@param SharedWeekPage page
<%-- The public, read-only week of a share link (PLAN.md D28). It shows only what SharedWeekReader
     returns: dates, meal types, meal names and occasion names. It has no form, no CSRF token and no
     link back into the app, and it does not repeat its own address. The Print button is the only
     control; it calls window.print() through the Alpine printPage component and is hidden on paper. --%>
@template.layout.bare(ctx = ctx, title = page.title(), content = @`
  <header class="mb-6 flex flex-wrap items-start justify-between gap-3">
    <div class="min-w-0">
      <h1 class="font-display text-[32px] font-bold leading-9 text-ink">${ctx.t("share.public.heading")}</h1>
      <p class="mt-1 text-base text-ink-muted"><span class="font-extrabold text-ink">${page.title()}</span> · ${page.range()}</p>
    </div>
    <div x-data="printPage" class="print:hidden">
      @template.ui._button(variant = "secondary", size = "md", type = "button", label = ctx.t("share.public.print"), icon = null, busy = false, disabled = false, attrs = Map.of("x-on:click", "print"))
    </div>
  </header>

  @template.sharing._days(ctx = ctx, page = page)
`)
