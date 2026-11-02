@import hu.lasterbergamot.mealmagician.planning.internal.DayPlan
@import hu.lasterbergamot.mealmagician.planning.internal.WeekPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param WeekPage page
<%-- The swap region of the week page: the stepper, then the phone's strip and day list (hidden from
     1024 px) and the desktop table (shown from 1024 px). --%>
@template.plan._week_stepper(ctx = ctx, page = page)
<div class="flex flex-col gap-4 lg:hidden">
  @template.plan._strip(ctx = ctx, page = page)
  @for(DayPlan day : page.days())
    @template.plan._day(ctx = ctx, day = day)
  @endfor
</div>
<div class="hidden lg:block">
  @template.plan._grid(ctx = ctx, page = page)
</div>
