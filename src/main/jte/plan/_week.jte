@import hu.lasterbergamot.mealmagician.planning.internal.DayPlan
@import hu.lasterbergamot.mealmagician.planning.internal.WeekPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param WeekPage page
<%-- The swap region of the week page: the stepper, then the phone's strip and day list (hidden from
     1024 px) and the desktop table (shown from 1024 px). --%>
@template.plan._week_stepper(ctx = ctx, page = page)
<div class="flex justify-end">
  <a id="plan-reuse-week" href="${page.reuseHref()}" x-data="sheetOpener" x-on:click="open" data-sheet="reuse-sheet"
     hx-get="${page.reuseHref()}" hx-target="#reuse-sheet-body"
     class="inline-flex min-h-11 items-center gap-2 rounded-md border-2 border-outline bg-surface-raised px-4 font-extrabold text-ink shadow-sticker-1 hover:bg-surface-sunken">
    @template.ui._icon(name = IconName.COPY, size = 20, filled = false)
    <span>${ctx.t("plan.reuse.week.open")}</span>
  </a>
</div>
@template.plan._occasions(ctx = ctx, page = page)
<div class="flex flex-col gap-4 lg:hidden">
  @template.plan._strip(ctx = ctx, page = page)
  @for(DayPlan day : page.days())
    @template.plan._day(ctx = ctx, day = day)
  @endfor
</div>
<div class="hidden lg:block">
  @template.plan._grid(ctx = ctx, page = page)
</div>
