@import hu.lasterbergamot.mealmagician.planning.internal.DayPlan
@import hu.lasterbergamot.mealmagician.planning.internal.SlotGroup
@import hu.lasterbergamot.mealmagician.planning.internal.SlotRow
@import hu.lasterbergamot.mealmagician.planning.internal.WeekPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param WeekPage page
<%-- The desktop week table (plan-ui-redesign.md section 5), shown from 1024 px: one column per day,
     one row per slot type. At 768 px eight columns would be about 80 px each, too narrow for a meal
     name, so a tablet gets the day list instead. Today's column header is gold and carries a Today
     badge and aria-current="date". --%>
<table class="w-full table-fixed border-separate border-spacing-1.5">
  <caption class="sr-only">${page.title()} · ${page.range()}</caption>
  <thead>
    <tr>
      <th scope="col" class="w-28"><span class="sr-only">${ctx.t("plan.grid.slot")}</span></th>
      @for(DayPlan day : page.days())
        <th scope="col" aria-current="${day.today() ? "date" : null}"
            class="rounded-md border-2 border-outline px-2 py-2 text-left align-top @if(day.today()) bg-accent text-on-accent @else bg-surface-raised text-ink @endif">
          <span class="block font-display text-lg font-semibold leading-6">${day.shortName()} ${day.dayOfMonth()}</span>
          @if(day.today())
            @template.ui._badge(tone = "neutral", label = ctx.t("plan.today"), icon = IconName.SPARKLE)
          @endif
        </th>
      @endfor
    </tr>
  </thead>
  <tbody>
    @for(SlotRow row : page.rows())
      <tr>
        <th scope="row" class="text-left align-top">
          @template.ui._slot_chip(slot = row.slotName(), label = ctx.t(row.labelKey()))
        </th>
        @for(SlotGroup cell : row.cells())
          <td class="align-top">
            @template.plan._cell(ctx = ctx, group = cell)
          </td>
        @endfor
      </tr>
    @endfor
  </tbody>
</table>
