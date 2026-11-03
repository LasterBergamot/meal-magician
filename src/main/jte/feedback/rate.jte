@import hu.lasterbergamot.mealmagician.feedback.internal.RatingPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param RatingPage page
<%-- The rating form without JavaScript (the plain answer of a post that did not come from htmx) and
     the page for a plain visit of the form's address. With JavaScript the same form (feedback/_rate) loads into the
     slot sheet of the plan. --%>
@template.layout.base(ctx = ctx, title = ctx.t("rating.title"), content = @`
  <section class="mx-auto flex w-full max-w-[var(--mm-reading)] flex-col gap-6">
    <div class="flex flex-col gap-2">
      <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
        @template.ui._slot_chip(slot = page.slotName(), label = ctx.t(page.slotKey()))
        <p class="font-bold text-ink">${page.dayLabel()}</p>
      </div>
      <h1 class="min-w-0 break-words font-display text-3xl font-semibold text-ink">${page.mealName()}</h1>
    </div>
    @template.feedback._rate(ctx = ctx, page = page)
    <div>
      @template.meal._link_button(variant = "ghost", href = page.weekHref(), label = ctx.t("plan.back"), icon = IconName.LEFT)
    </div>
  </section>
`)
