@import hu.lasterbergamot.mealmagician.planning.internal.DayPlan
@import hu.lasterbergamot.mealmagician.planning.internal.SlotGroup
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param DayPlan day
<%-- One day of the phone's list: a header (gold when it is today, with a Today badge), the shown slot
     rows (Lunch, and any slot that holds a meal) and, behind a native <details>, the empty optional
     slots (D30, lunch first). The id is the target of the week strip's links. --%>
<section id="${day.id()}" aria-labelledby="${day.id()}-title" class="flex scroll-mt-4 flex-col gap-3">
  <div class="flex items-center justify-between gap-2 rounded-md border-2 border-outline px-4 py-2 @if(day.today()) bg-accent-soft text-accent-ink @else bg-surface-raised text-ink @endif">
    <h2 id="${day.id()}-title" class="font-display text-[22px] font-semibold leading-7">${day.name()} <span class="font-sans text-base font-bold">${day.dateLabel()}</span></h2>
    <div class="flex items-center gap-2">
      @if(day.today())
        @template.ui._badge(tone = "accent", label = ctx.t("plan.today"), icon = IconName.SPARKLE)
      @endif
      <a href="${day.reuseHref()}" data-reuse="${day.date().toString()}" x-data="sheetOpener" x-on:click="open" data-sheet="reuse-sheet"
         hx-get="${day.reuseHref()}" hx-target="#reuse-sheet-body"
         class="inline-flex size-11 shrink-0 items-center justify-center rounded-md border-2 border-outline bg-surface-raised text-ink hover:bg-surface-sunken">
        @template.ui._icon(name = IconName.COPY, size = 20, filled = false)
        <span class="sr-only">${ctx.t("plan.reuse.day.open", day.name())}</span>
      </a>
    </div>
  </div>
  <ul class="flex flex-col gap-2">
    @for(SlotGroup group : day.shownGroups())
      @template.plan._slot_row(ctx = ctx, group = group)
    @endfor
  </ul>
  @if(!day.hiddenGroups().isEmpty())
    <details data-more="${day.id()}" class="group/more">
      <summary class="flex min-h-11 cursor-pointer list-none items-center justify-center gap-2 rounded-md border-2 border-dashed border-border-strong px-3 font-extrabold text-ink hover:bg-surface-sunken [&::-webkit-details-marker]:hidden">
        <span>${ctx.t("plan.slot.more")}</span>
        <span class="inline-flex group-open/more:rotate-180">
          @template.ui._icon(name = IconName.DOWN, size = 20, filled = false)
        </span>
      </summary>
      <ul class="mt-2 flex flex-col gap-2">
        @for(SlotGroup group : day.hiddenGroups())
          @template.plan._slot_row(ctx = ctx, group = group)
        @endfor
      </ul>
    </details>
  @endif
</section>
