@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext
@import hu.lasterbergamot.mealmagician.sharing.internal.PrintPage
@import java.util.Map

@param ViewContext ctx
@param PrintPage page
<%-- The members' print page of one week (PLAN.md D29): a light page without the app shell, built on
     layout/bare and the same sharing/_days fragment as the public share page, with the slot notes
     switched on. The Print button (Alpine printPage) and the way back are hidden on paper. The
     bare frame fixes the theme to light, so a printout never comes out dark. The footer of the
     public page is left out because its sentence is about share links. --%>
@template.layout.bare(ctx = ctx, title = page.week().title(), showFooter = false, content = @`
  <header class="mb-6 flex flex-wrap items-start justify-between gap-3">
    <div class="min-w-0">
      <h1 class="font-display text-[32px] font-bold leading-9 text-ink">${ctx.t("print.heading")}</h1>
      <p class="mt-1 text-base text-ink-muted"><span class="font-extrabold text-ink">${page.week().title()}</span> · ${page.week().range()}</p>
    </div>
    <div x-data="printPage" class="flex flex-wrap gap-3 print:hidden">
      @template.meal._link_button(variant = "ghost", href = page.backHref(), label = ctx.t("print.back"), icon = IconName.LEFT)
      @template.ui._button(variant = "primary", size = "md", type = "button", label = ctx.t("share.public.print"), icon = IconName.PRINTER, busy = false, disabled = false, attrs = Map.of("x-on:click", "print"))
    </div>
  </header>

  @template.sharing._days(ctx = ctx, page = page.week(), showNotes = true)
`)
