@import hu.lasterbergamot.mealmagician.planning.internal.WeekPage
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param WeekPage page
<%-- The week stepper (plan-ui-redesign.md section 5): previous, the week's name and dates, next.
     The arrows are links, so they work without JavaScript; with htmx each one fetches its week into
     #plan-week and pushes the URL. The ids are stable on purpose: after a swap htmx gives focus
     back to the element with the same id, so a keyboard user stays on the arrow. The Alpine
     weekKeys component makes ArrowLeft and ArrowRight click the arrows. "Back to this week"
     shows only when today is outside the shown week. --%>
<div x-data="weekKeys" x-on:keydown.window="step" class="flex items-center justify-between gap-3">
  <a id="plan-prev" x-ref="prev" href="${page.previousHref()}"
     class="inline-flex size-11 shrink-0 items-center justify-center rounded-md border-2 border-outline bg-surface-raised text-ink shadow-sticker-1 transition-[translate,box-shadow,background-color] duration-[var(--mm-duration-fast)] hover:-translate-x-px hover:-translate-y-px hover:bg-surface-sunken hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none"
     $unsafe{HtmlAttributes.render(HtmlAttributes.htmxFetch(page.previousHref(), "#plan-week"))}>
    @template.ui._icon(name = IconName.LEFT, size = 24, filled = false)
    <span class="sr-only">${ctx.t("plan.week.previous")}</span>
  </a>
  <div class="min-w-0 text-center">
    <h2 class="font-display text-[22px] font-semibold leading-7 text-ink">${page.title()}</h2>
    <p class="text-sm text-ink-muted">${page.range()}</p>
    @if(!page.current())
      <a id="plan-today" href="/plan" class="text-sm font-extrabold text-brand underline"
         $unsafe{HtmlAttributes.render(HtmlAttributes.htmxFetch("/plan", "#plan-week"))}>${ctx.t("plan.week.current")}</a>
    @endif
  </div>
  <a id="plan-next" x-ref="next" href="${page.nextHref()}"
     class="inline-flex size-11 shrink-0 items-center justify-center rounded-md border-2 border-outline bg-surface-raised text-ink shadow-sticker-1 transition-[translate,box-shadow,background-color] duration-[var(--mm-duration-fast)] hover:-translate-x-px hover:-translate-y-px hover:bg-surface-sunken hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none"
     $unsafe{HtmlAttributes.render(HtmlAttributes.htmxFetch(page.nextHref(), "#plan-week"))}>
    @template.ui._icon(name = IconName.RIGHT, size = 24, filled = false)
    <span class="sr-only">${ctx.t("plan.week.next")}</span>
  </a>
</div>
