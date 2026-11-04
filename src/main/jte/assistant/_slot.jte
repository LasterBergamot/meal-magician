@import hu.lasterbergamot.mealmagician.assistant.internal.SlotSuggestionPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param SlotSuggestionPage page
<%-- The wizard's ideas for one slot, inside the meal-pick sheet (plan-ui-redesign.md Appendix D.4).
     The pick sheet holds an empty #pick-assistant that loads this fragment when it opens (the plan
     module cannot import the assistant); the answer is 204 while the assistant is off, so the sheet
     shows nothing. Never a sheet from a sheet: everything swaps inside #pick-assistant.

     Three states. Asking: one row-styled button with the wand and a chevron; while the request runs
     htmx marks the form, and CSS swaps the row for the twinkling wand and three grey rows. Answer:
     up to three library meals, each with the pick sheet's own "plan this" form (so planning one is the
     normal add-to-slot flow) and the model's reason under it; the reason is untrusted text, escaped.
     Error: the alert above the ask row, which is the retry. --%>
@if(page.answered())
  <section class="flex flex-col gap-2" aria-labelledby="pick-assistant-title">
    <h3 id="pick-assistant-title" class="font-display text-xl font-semibold text-ink">${ctx.t("assistant.slot.title")}</h3>
    @if(page.rows().isEmpty())
      <p class="text-sm text-ink-muted">${ctx.t("assistant.slot.none")}</p>
    @else
      <ul class="flex flex-col gap-2">
        @for(SlotSuggestionPage.Row row : page.rows())
          <li class="flex flex-col gap-1">
            <form method="post" action="/plan/slots" hx-post="/plan/slots" class="contents">
              @if(ctx.csrf() != null)
                <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
              @endif
              <input type="hidden" name="date" value="${page.date()}">
              <input type="hidden" name="slot" value="${page.slot()}">
              <input type="hidden" name="mealId" value="${row.mealId()}">
              <button type="submit" class="flex min-h-11 w-full items-center justify-between gap-3 rounded-md border-2 border-outline bg-surface-raised px-4 py-2 text-left font-extrabold text-ink shadow-sticker-1 hover:bg-brand-soft hover:text-brand-ink">
                <span class="min-w-0 break-words">${row.name()}</span>
                @template.ui._icon(name = IconName.PLUS, size = 20, filled = false)
              </button>
            </form>
            @if(row.reason() != null)
              <p class="whitespace-pre-line px-1 text-sm text-ink-muted">${row.reason()}</p>
            @endif
          </li>
        @endfor
      </ul>
    @endif
  </section>
@else
  <div class="flex flex-col gap-2">
    @if(page.notice() != null)
      <form hx-post="/assistant/slot" hx-target="#pick-assistant" hx-swap="innerHTML" class="contents">
        <input type="hidden" name="date" value="${page.date()}">
        <input type="hidden" name="slotType" value="${page.slot()}">
        @template.assistant._error(ctx = ctx, notice = page.notice())
      </form>
    @endif
    <form id="pick-assistant-form" hx-post="/assistant/slot" hx-target="#pick-assistant" hx-swap="innerHTML" data-abort-on-close class="contents">
      <input type="hidden" name="date" value="${page.date()}">
      <input type="hidden" name="slotType" value="${page.slot()}">
      <button type="submit" class="flex min-h-11 w-full items-center gap-3 rounded-md border-2 border-outline bg-surface-raised px-4 py-2 text-left font-extrabold text-ink shadow-sticker-1 hover:bg-brand-soft hover:text-brand-ink [.htmx-request_&]:hidden">
        @template.ui._icon(name = IconName.WAND, size = 20, filled = false)
        <span class="min-w-0 flex-1 break-words">${ctx.t("assistant.slot.ask")}</span>
        @template.ui._icon(name = IconName.RIGHT, size = 20, filled = false)
      </button>
      <div class="hidden flex-col gap-3 [.htmx-request_&]:flex">
        <h3 class="font-display text-xl font-semibold text-ink">${ctx.t("assistant.slot.title")}</h3>
        @template.assistant._waiting(ctx = ctx, skeleton = true, cancellable = false)
      </div>
    </form>
  </div>
@endif
