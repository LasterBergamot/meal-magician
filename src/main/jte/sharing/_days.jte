@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext
@import hu.lasterbergamot.mealmagician.sharing.internal.SharedDay
@import hu.lasterbergamot.mealmagician.sharing.internal.SharedWeekPage

@param ViewContext ctx
@param SharedWeekPage page
@param boolean showNotes
<%-- The occasion strip and the seven day blocks of the public week. One column on a phone, two from
     md and on paper (print:). showNotes is true only on the members' print page (D29), where each
     meal may carry its slot note; the public page passes false and its model holds no notes. Only the meal types that hold a meal are listed (D30); a day with
     nothing planned shows a dash with a screen-reader sentence. The type chip always has its icon
     and its word. Names are printed escaped by JTE. Tokens only for colours. --%>
@if(!page.occasions().isEmpty())
  <ul aria-label="${ctx.t("share.public.occasions")}" class="mb-6 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
    @for(SharedWeekPage.OccasionLine occasion : page.occasions())
      <li class="flex min-h-11 items-center gap-3 rounded-md border-2 border-outline bg-accent-soft px-3 py-2 text-accent-ink shadow-sticker-1">
        @template.ui._icon(name = IconName.GIFT, size = 20, filled = false)
        <span class="font-extrabold">${occasion.name()}</span>
        <span class="text-sm">${occasion.dayLabel()}</span>
        @template.ui._badge(tone = "neutral", label = occasion.typeLabel(), icon = null)
      </li>
    @endfor
  </ul>
@endif

<div class="grid grid-cols-1 gap-4 md:grid-cols-2 print:grid-cols-2">
  @for(SharedDay day : page.days())
    <section aria-labelledby="day-${day.isoDate()}" class="flex break-inside-avoid flex-col gap-3 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2">
      <h2 id="day-${day.isoDate()}" class="font-display text-[22px] font-semibold leading-7 text-ink">
        ${day.dayName()} <time datetime="${day.isoDate()}" class="font-sans text-base font-bold text-ink-muted">${day.dateLabel()}</time>
      </h2>
      @if(day.slots().isEmpty())
        <p class="text-ink-muted" aria-hidden="true">–</p>
        <p class="sr-only">${ctx.t("share.public.empty")}</p>
      @else
        <ul class="flex flex-col gap-3">
          @for(SharedDay.Slot slot : day.slots())
            <li class="flex flex-col items-start gap-1">
              @template.ui._slot_chip(slot = slot.type(), label = slot.label())
              <ul class="list-inside list-disc text-base leading-6 text-ink">
                @for(int i = 0; i < slot.meals().size(); i++)
                  <li>${slot.meals().get(i)}@if(showNotes && !slot.notes().get(i).isEmpty())<span class="block text-sm text-ink-muted">${slot.notes().get(i)}</span>@endif</li>
                @endfor
              </ul>
            </li>
          @endfor
        </ul>
      @endif
    </section>
  @endfor
</div>
