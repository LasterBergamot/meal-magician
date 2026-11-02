@import hu.lasterbergamot.mealmagician.planning.internal.OccasionFormPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionFormPage page
<%-- The create and edit page. The same URL answers an htmx request with _form only. --%>
@template.layout.base(ctx = ctx, title = ctx.t(page.titleKey()), content = @`
  <section class="mx-auto flex w-full max-w-[var(--mm-reading)] flex-col gap-6">
    <div>
      @template.meal._link_button(variant = "ghost", href = page.form().backHref(), label = ctx.t("occasion.back"), icon = IconName.LEFT)
    </div>
    <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t(page.titleKey())}</h1>
    @template.occasion._form(ctx = ctx, page = page)
  </section>
`)
