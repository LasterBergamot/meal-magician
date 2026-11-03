@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext
@import hu.lasterbergamot.mealmagician.sharing.internal.SharePanel

@param ViewContext ctx
@param SharePanel panel
<%-- The share page without JavaScript: the Share button's target when the sheet cannot open, and
     the answer to a plain (non-htmx) create or revoke. With JavaScript the same content opens in
     the plan page's share sheet (sharing/_share). --%>
@template.layout.base(ctx = ctx, title = ctx.t("share.sheet.title"), content = @`
  <section class="flex flex-col gap-6">
    <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("share.sheet.title")}</h1>
    @template.sharing._share(ctx = ctx, panel = panel)
    <div>
      @template.meal._link_button(variant = "ghost", href = "/plan?week=" + panel.week(), label = ctx.t("plan.back"), icon = IconName.LEFT)
    </div>
  </section>
`)
