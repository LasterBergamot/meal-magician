@import hu.lasterbergamot.mealmagician.catalog.internal.TagOptions
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param TagOptions options
<%-- The rows of the Tags field's suggestion list (GET /meals/tags?q=). htmx puts them inside the
     <ul role="listbox"> of ui/_suggest_field, so this fragment is only <li> rows: the household's
     matching tags, most used first. There is no "Add" row and no "none" row: the field is free
     text, and an empty body makes the listbox hide itself. Every option carries the tag's exact
     spelling in data-value; the script writes it into the field when a row is chosen. aria-selected
     marks the row the arrow keys are on, which also drives the highlight. A row is at least 44 px
     tall (the touch minimum). --%>
@for(String name : options.names())
  <li role="option" aria-selected="false" data-value="${name}"
      class="flex min-h-11 cursor-pointer items-center px-4 py-2 text-base text-ink hover:bg-surface-sunken aria-selected:bg-brand-soft aria-selected:text-brand-ink">${name}</li>
@endfor
