@import hu.lasterbergamot.mealmagician.catalog.internal.MealFormPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param MealFormPage page
<%-- The create and edit page. The same URL answers an htmx request with _form only. --%>
@template.layout.base(ctx = ctx, title = page.form().editing() ? ctx.t("meal.form.title.edit") : ctx.t("meal.form.title.new"), content = @`
  <section class="mx-auto flex w-full max-w-[var(--mm-reading)] flex-col gap-6">
    <div>
      @template.meal._link_button(variant = "ghost", href = page.form().editing() ? "/meals/" + page.form().id() : "/meals", label = ctx.t("meal.form.back"), icon = IconName.LEFT)
    </div>
    <h1 class="font-display text-3xl font-semibold text-ink">${page.form().editing() ? ctx.t("meal.form.title.edit") : ctx.t("meal.form.title.new")}</h1>
    @template.meal._form(ctx = ctx, page = page)
  </section>
`)
