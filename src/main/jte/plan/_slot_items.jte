@import hu.lasterbergamot.mealmagician.planning.internal.PlannedMeal
@import hu.lasterbergamot.mealmagician.planning.internal.SlotGroup
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param SlotGroup group
@param String variant
<%-- The content of one slot box, shared by the phone's row ("list") and the desktop's cell ("grid"):
     the planned meals in order, then always the add control. The control is a real link to the pick
     page, so it works without JavaScript; with JavaScript the Alpine sheetOpener opens the page-level
     pick sheet and htmx loads the same page into it. In the narrow table cells ("grid") only the
     plus icon shows and the words are for screen readers. data-add is for the tests. --%>
@if(!group.empty())
  <ul class="flex flex-col gap-1.5">
    @for(PlannedMeal meal : group.meals())
      @template.plan._meal_item(ctx = ctx, meal = meal)
    @endfor
  </ul>
@endif
<a href="${group.pickHref()}" data-add="${group.key()}" x-data="sheetOpener" x-on:click="open" data-sheet="pick-sheet"
   hx-get="${group.pickHref()}" hx-target="#pick-sheet-body"
   class="flex min-h-11 items-center justify-center gap-2 rounded-md border-2 border-dashed border-border-strong px-3 text-sm font-extrabold text-brand hover:bg-brand-soft hover:text-brand-ink">
  @template.ui._icon(name = IconName.PLUS, size = 20, filled = false)
  <span class="@if(variant.equals("grid"))sr-only@endif">${ctx.t(group.addKey())}</span>
</a>
