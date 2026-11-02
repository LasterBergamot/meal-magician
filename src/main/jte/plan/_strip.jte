@import hu.lasterbergamot.mealmagician.planning.internal.DayPlan
@import hu.lasterbergamot.mealmagician.planning.internal.WeekPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param WeekPage page
<%-- The phone's week strip (plan-ui-redesign.md section 5): seven day buttons that jump to the day's
     section below. Today has the gold circle and aria-current="date". --%>
<nav id="plan-strip" aria-label="${ctx.t("plan.strip.label")}">
  <ol class="grid grid-cols-7 gap-1">
    @for(DayPlan day : page.days())
      <li>
        <a href="#${day.id()}" aria-label="${day.fullLabel()}" aria-current="${day.today() ? "date" : null}"
           class="flex min-h-14 flex-col items-center justify-center gap-0.5 rounded-md text-ink hover:bg-surface-sunken">
          <span class="text-xs font-bold text-ink-muted">${day.shortName()}</span>
          <span class="flex size-9 items-center justify-center rounded-full font-display text-lg font-semibold @if(day.today()) border-2 border-outline bg-accent text-on-accent @endif">${day.dayOfMonth()}</span>
        </a>
      </li>
    @endfor
  </ol>
</nav>
