@import hu.lasterbergamot.mealmagician.catalog.IngredientSuggestion
@import hu.lasterbergamot.mealmagician.catalog.internal.IngredientOptions
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param IngredientOptions options
<%-- The rows of the ingredient picker's list (GET /meals/ingredients?q=). htmx puts them inside the
     <ul role="listbox"> of ui/_combobox, so this fragment is only <li> rows: the household's own
     ingredients first, then the starter list, then (when nothing listed is exactly what was typed)
     an "Add «text»" row. Every option carries its text in data-value; the combobox script reads it
     when a row is clicked or chosen with the keyboard. aria-selected marks the row the arrow keys
     are on, which also drives the highlight. A row is at least 44 px tall (the touch minimum). --%>
@for(IngredientSuggestion suggestion : options.suggestions())
  <li role="option" aria-selected="false" data-value="${suggestion.name()}"
      class="flex min-h-11 cursor-pointer items-center px-4 py-2 text-base text-ink hover:bg-surface-sunken aria-selected:bg-brand-soft aria-selected:text-brand-ink">${suggestion.name()}</li>
@endfor
@if(options.addName() != null)
  <li role="option" aria-selected="false" data-value="${options.addName()}" data-add
      class="flex min-h-11 cursor-pointer items-center gap-2 border-t-2 border-border px-4 py-2 text-base font-extrabold text-ink first:border-t-0 hover:bg-surface-sunken aria-selected:bg-brand-soft aria-selected:text-brand-ink">
    @template.ui._icon(name = IconName.PLUS, size = 18, filled = false)
    <span class="min-w-0 break-words">${ctx.t("meal.form.ingredients.add", options.addName())}</span>
  </li>
@endif
@if(options.empty())
  <li role="presentation" class="px-4 py-3 text-sm text-ink-muted">${ctx.t("meal.form.ingredients.none")}</li>
@endif
