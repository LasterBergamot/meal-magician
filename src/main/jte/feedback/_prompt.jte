@import java.util.Map
@import hu.lasterbergamot.mealmagician.feedback.internal.PromptPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param PromptPage prompt
<%-- The "How did it go?" card at the top of the plan page: the latest past meal this member has not
     rated, one at a time. It loads into #rating-prompt over htmx (planning may not import
     feedback). The primary link opens the page-level slot sheet exactly as plan/_meal_item does, and
     also works without JavaScript. "Not now" posts the snooze, whose empty answer empties the
     container. --%>
@template.ui._card(variant = "accent", content = @`
  <div data-slot-id="${prompt.slotId()}" class="flex flex-col gap-3">
    <h2 class="font-display text-xl font-semibold text-ink">${ctx.t("rating.prompt.title")}</h2>
    <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
      @template.ui._slot_chip(slot = prompt.slotName(), label = ctx.t(prompt.slotKey()))
      <p class="font-bold text-ink">${prompt.dayLabel()}</p>
    </div>
    <p class="break-words text-base font-extrabold text-ink">${prompt.mealName()}</p>
    <div class="flex flex-wrap items-center gap-3">
      <a id="rating-prompt-rate" href="${prompt.slotHref()}" x-data="sheetOpener" x-on:click="open" data-sheet="slot-sheet"
         hx-get="${prompt.slotHref()}" hx-target="#slot-sheet-body"
         class="inline-flex min-h-11 items-center justify-center gap-2 whitespace-nowrap rounded-md border-2 border-outline bg-brand px-4 font-sans text-sm font-extrabold leading-5 text-on-brand shadow-sticker-1 hover:-translate-x-px hover:-translate-y-px hover:bg-brand-hover hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none">
        @template.ui._icon(name = IconName.UP, size = 20, filled = false)
        <span>${ctx.t("rating.prompt.rate")}</span>
      </a>
      @template.ui._button(variant = "ghost", size = "md", type = "button", label = ctx.t("rating.prompt.snooze"), icon = null, busy = false, disabled = false,
        attrs = Map.of("id", "rating-prompt-snooze", "hx-post", "/feedback/prompt/snooze", "hx-target", "#rating-prompt", "hx-swap", "innerHTML"))
    </div>
  </div>
`)
