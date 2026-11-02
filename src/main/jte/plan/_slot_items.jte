@import hu.lasterbergamot.mealmagician.planning.internal.PlannedMeal
@import hu.lasterbergamot.mealmagician.planning.internal.SlotGroup
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param SlotGroup group
<%-- The content of one slot box, shared by the phone's row and the desktop's cell: a quiet note when
     nothing is planned, otherwise the meals in order. --%>
@if(group.empty())
  <p class="text-sm text-ink-muted">${ctx.t("plan.slot.empty")}</p>
@else
  <ul class="flex flex-col gap-1.5">
    @for(PlannedMeal meal : group.meals())
      @template.plan._meal_item(ctx = ctx, meal = meal)
    @endfor
  </ul>
@endif
