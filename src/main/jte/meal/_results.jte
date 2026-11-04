@import java.util.List
@import java.util.Map
@import hu.lasterbergamot.mealmagician.catalog.MealView
@import hu.lasterbergamot.mealmagician.catalog.internal.MealFilter
@import hu.lasterbergamot.mealmagician.catalog.internal.MealsPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.SegmentOption
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param MealsPage page
<%-- The part of the meal list that changes while searching: the tag chips, the archived switch, the
     sort control and the meals (or an empty state). htmx swaps it into #meal-results; the page and
     this fragment are the same markup, so a reload shows what the swap showed. The hidden inputs in
     #meal-state carry the filter that the search box does not hold itself (tag, ingredient, sort, archived),
     which the search box includes when it sends its request. --%>
<div id="meal-state" hidden>
  <input type="hidden" name="tag" value="${page.filter().tag()}">
  <input type="hidden" name="ingredient" value="${page.filter().ingredient()}">
  <input type="hidden" name="sort" value="${page.filter().sort()}">
  @if(page.filter().archived())
    <input type="hidden" name="archived" value="true">
  @endif
</div>

@if(page.libraryEmpty())
  @template.ui._empty(icon = IconName.MEALS, heading = ctx.t("meal.empty.heading"), message = ctx.t("meal.empty.message"), action = @`
    @template.meal._link_button(variant = "primary", href = "/meals/new", label = ctx.t("meal.add"), icon = IconName.PLUS)
  `)
@else
  <div class="flex flex-col gap-3">
    @if(!page.tags().isEmpty())
      <div role="group" aria-label="${ctx.t("meal.filter.tags")}" class="flex gap-2 overflow-x-auto pr-1 pb-1.5 md:flex-wrap">
        @for(String tag : page.tags())
          @template.ui._chip(label = tag, selected = tag.equalsIgnoreCase(page.filter().tag()),
            attrs = Map.of("hx-get", page.filter().hrefToggleTag(tag), "hx-target", "#meal-results", "hx-push-url", "true"))
        @endfor
      </div>
    @endif
    @if(!page.ingredients().isEmpty())
      <div role="group" aria-label="${ctx.t("meal.filter.ingredients")}" class="flex gap-2 overflow-x-auto pr-1 pb-1.5 md:flex-wrap">
        @for(String ingredient : page.ingredients())
          @template.ui._chip(label = ingredient, selected = ingredient.equalsIgnoreCase(page.filter().ingredient()),
            attrs = Map.of("hx-get", page.filter().hrefToggleIngredient(ingredient), "hx-target", "#meal-results", "hx-push-url", "true"))
        @endfor
      </div>
    @endif
    <div class="flex flex-wrap items-center justify-between gap-3">
      @template.ui._chip(label = ctx.t("meal.filter.archived"), selected = page.filter().archived(),
        attrs = Map.of("hx-get", page.filter().hrefToggleArchived(), "hx-target", "#meal-results", "hx-push-url", "true"))
      @template.ui._segmented(label = ctx.t("meal.sort.label"), selected = page.filter().sort(), target = "#meal-results", options = List.of(
        new SegmentOption(MealFilter.RECENT, ctx.t("meal.sort.recent"), page.filter().hrefSort(MealFilter.RECENT)),
        new SegmentOption(MealFilter.NAME, ctx.t("meal.sort.name"), page.filter().hrefSort(MealFilter.NAME))))
    </div>
  </div>

  @if(page.meals().isEmpty())
    @template.ui._empty(icon = IconName.SEARCH, heading = ctx.t("meal.nomatch.heading"), message = ctx.t("meal.nomatch.message"), action = @`
      @template.meal._link_button(variant = "secondary", href = page.filter().hrefClear(), label = ctx.t("meal.nomatch.clear"), icon = IconName.X)
    `)
  @else
    <ul class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      @for(MealView meal : page.meals())
        @template.meal._card(ctx = ctx, meal = meal)
      @endfor
    </ul>
  @endif
@endif
