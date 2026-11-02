@import hu.lasterbergamot.mealmagician.planning.internal.OccasionPickPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionPickPage page
<%-- The occasion's pick page without JavaScript (the add link's target) and the answer to a rejected
     "Create and add" post that did not come from htmx. With JavaScript the same content opens in
     the pick sheet (occasion/_pick). --%>
@template.layout.base(ctx = ctx, title = ctx.t("occasion.pick.title"), content = @`
  <section class="flex flex-col gap-6">
    <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("occasion.pick.title")}</h1>
    @template.occasion._pick(ctx = ctx, page = page)
    <div>
      @template.meal._link_button(variant = "ghost", href = page.backHref(), label = ctx.t("occasion.pick.back"), icon = IconName.LEFT)
    </div>
  </section>
`)
