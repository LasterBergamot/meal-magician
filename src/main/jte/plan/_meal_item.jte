@import hu.lasterbergamot.mealmagician.planning.internal.PlannedMeal
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param PlannedMeal meal
<%-- One planned meal: its name, an Archived badge when the meal left the library (the plan keeps
     it), and the slot's note. It sits inside a box that has a slot-* class, which gives
     bg-slot-soft and text-slot-ink their colours. data-slot-id is for the tests. --%>
<li data-slot-id="${meal.slotId()}"
    class="flex min-w-0 flex-col gap-1 rounded-sm border-2 border-outline bg-slot-soft px-2 py-1.5 text-slot-ink">
  <span class="break-words text-sm font-extrabold leading-5">${meal.mealName()}</span>
  @if(meal.archived())
    <span>@template.ui._badge(tone = "neutral", label = ctx.t("meal.archived"), icon = null)</span>
  @endif
  @if(meal.note() != null)
    <span class="break-words text-xs font-medium leading-4">${meal.note()}</span>
  @endif
</li>
