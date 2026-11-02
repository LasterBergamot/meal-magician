@import hu.lasterbergamot.mealmagician.planning.internal.PickPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param PickPage page
<%-- The pick page without JavaScript (the add link's target) and the answer to a rejected "Create and
     add" post that did not come from htmx. With JavaScript the same content opens in the pick sheet
     (plan/_pick). --%>
@template.layout.base(ctx = ctx, title = ctx.t("plan.pick.title"), content = @`
  <section class="flex flex-col gap-6">
    <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("plan.pick.title")}</h1>
    @template.plan._pick(ctx = ctx, page = page)
    <div>
      @template.meal._link_button(variant = "ghost", href = page.weekHref(), label = ctx.t("plan.back"), icon = IconName.LEFT)
    </div>
  </section>
`)
