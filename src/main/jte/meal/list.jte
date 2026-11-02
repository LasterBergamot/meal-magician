@import java.util.Map
@import hu.lasterbergamot.mealmagician.catalog.internal.MealsPage
@import hu.lasterbergamot.mealmagician.catalog.internal.QuickAdd
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param MealsPage page
@param QuickAdd quickSheet
@param QuickAdd quickBar
<%-- The meal library (plan-ui-redesign.md section 8). The same URL answers an htmx request with
     _results only. From md up a quick-add bar sits under the heading; on a phone the floating add
     button opens the quick-add sheet. The search box is inside a GET form, so Enter works without
     htmx too; its hx-* attributes swap only #meal-results while typing. --%>
@template.layout.base(ctx = ctx, title = ctx.t("meal.title"), content = @`
  <section class="flex flex-col gap-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("meal.heading")}</h1>
      @if(!page.libraryEmpty())
        @template.meal._link_button(variant = "primary", href = "/meals/new", label = ctx.t("meal.add"), icon = IconName.PLUS)
      @endif
    </div>

    <div class="hidden md:block">
      @template.meal._quick(ctx = ctx, quick = quickBar)
    </div>

    <form method="get" action="/meals" class="flex flex-col gap-4">
      @if(!page.libraryEmpty())
        @template.ui._search(id = "meal-q", name = "q", label = ctx.t("meal.search.label"), value = page.filter().q(), placeholder = ctx.t("meal.search.placeholder"), disabled = false,
          attrs = Map.of("hx-get", "/meals", "hx-trigger", "input changed delay:300ms, search", "hx-target", "#meal-results", "hx-include", "#meal-state", "hx-push-url", "true", "hx-sync", "this:replace"))
      @endif
      <div id="meal-results" class="flex flex-col gap-4">
        @template.meal._results(ctx = ctx, page = page)
      </div>
    </form>
  </section>

  @template.ui._sheet(id = "quick-sheet", title = ctx.t("meal.quick.title"), closeLabel = ctx.t("sheet.close"), role = "dialog", centred = false, closable = true,
    trigger = @`@template.ui._fab(icon = IconName.PLUS, label = ctx.t("meal.quick.title"), type = "button", floating = true, attrs = Map.of("x-on:click", "show"))`,
    body = @`@template.meal._quick(ctx = ctx, quick = quickSheet)`)
`)
