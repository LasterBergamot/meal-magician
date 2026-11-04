@import java.util.Map
@import hu.lasterbergamot.mealmagician.catalog.MealView
@import hu.lasterbergamot.mealmagician.planning.internal.PickPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param PickPage page
<%-- The content of the pick sheet (and of the no-JavaScript page): which meal goes on this day and
     slot type. The search box refetches this same fragment and swaps only #pick-results
     (hx-select), so the box keeps focus while typing; #pick-params carries the day and slot type it
     needs. #pick-assistant is the wizard's row (#160): the assistant module fills it over htmx, and it
     stays empty (and hidden) while the assistant is off. Each library meal is a one-button form that posts to /plan/slots; below the list the
     "Create and add" form makes a new meal. --%>
<div class="flex flex-col gap-4">
  <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
    @template.ui._slot_chip(slot = page.slotName(), label = ctx.t(page.labelKey()))
    <p class="font-bold text-ink">${page.dayLabel()}</p>
  </div>
  <div id="pick-params" hidden>
    <input type="hidden" name="date" value="${page.dateValue()}">
    <input type="hidden" name="slot" value="${page.slotName()}">
  </div>
  @template.ui._search(id = "pick-q", name = "q", label = ctx.t("plan.pick.search.label"), value = page.query(), placeholder = ctx.t("plan.pick.search.placeholder"), disabled = false,
    attrs = Map.of("hx-get", "/plan/pick", "hx-trigger", "input changed delay:300ms, search", "hx-target", "#pick-results", "hx-select", "#pick-results", "hx-swap", "outerHTML", "hx-include", "#pick-params", "hx-sync", "this:replace"))
  <div id="pick-assistant" hx-get="/assistant/slot?date=${page.dateValue()}&amp;slotType=${page.slotName()}" hx-trigger="load" hx-swap="innerHTML" class="empty:hidden"></div>
  <div id="pick-results" class="flex flex-col gap-2">
    @if(page.libraryEmpty())
      <p class="text-sm text-ink-muted">${ctx.t("plan.pick.empty")}</p>
    @elseif(page.meals().isEmpty())
      <p class="text-sm text-ink-muted">${ctx.t("plan.pick.nomatch")}</p>
    @else
      <ul aria-label="${ctx.t("plan.pick.list.label")}" class="flex flex-col gap-2">
        @for(MealView meal : page.meals())
          <li>
            <form method="post" action="/plan/slots" hx-post="/plan/slots" class="contents">
              @if(ctx.csrf() != null)
                <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
              @endif
              <input type="hidden" name="date" value="${page.dateValue()}">
              <input type="hidden" name="slot" value="${page.slotName()}">
              <input type="hidden" name="mealId" value="${meal.id()}">
              <button type="submit" class="flex min-h-11 w-full items-center justify-between gap-3 rounded-md border-2 border-outline bg-surface-raised px-4 py-2 text-left font-extrabold text-ink shadow-sticker-1 hover:bg-brand-soft hover:text-brand-ink">
                <span class="min-w-0 break-words">${meal.name()}</span>
                @template.ui._icon(name = IconName.PLUS, size = 20, filled = false)
              </button>
            </form>
          </li>
        @endfor
      </ul>
      @if(page.more())
        <p class="text-sm text-ink-muted">${ctx.t("plan.pick.more", String.valueOf(PickPage.LIMIT))}</p>
      @endif
    @endif
  </div>
  @template.plan._pick_quick(ctx = ctx, page = page)
</div>
