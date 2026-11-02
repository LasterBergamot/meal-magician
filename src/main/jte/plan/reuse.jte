@import hu.lasterbergamot.mealmagician.planning.internal.ReusePage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param ReusePage page
<%-- The reuse page without JavaScript (the Reuse links' target) and the answer to a rejected template
     name that did not come from htmx. With JavaScript the same content opens in the reuse sheet
     (plan/_reuse). --%>
@template.layout.base(ctx = ctx, title = ctx.t("plan.reuse.title"), content = @`
  <section class="flex flex-col gap-6">
    <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("plan.reuse.title")}</h1>
    @template.plan._reuse(ctx = ctx, page = page)
    <div>
      @template.meal._link_button(variant = "ghost", href = page.weekHref(), label = ctx.t("plan.back"), icon = IconName.LEFT)
    </div>
  </section>
`)
