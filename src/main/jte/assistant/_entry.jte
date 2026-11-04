@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String weekCode
@param boolean emptyWeek
<%-- The wizard's entry on the plan page (plan-ui-redesign.md Appendix D.2): the "Suggest my week"
     button, and one line under it when the week has nothing planned. The plan page cannot depend on
     the assistant module, so its action row holds an empty loader that fetches this fragment
     (hx-trigger="load") and swaps itself for it; while the assistant is off the answer is 204, so
     the page shows nothing at all. The button is secondary, never gold: the add button and today
     hold the page's gold. It opens the assistant-sheet, a right-hand panel from md up so the week
     stays visible, with the same sheetOpener pattern as the other plan sheets. The sheet comes with
     the button on purpose: while the assistant is off, nothing of it, not even a closed dialog with
     its title, is on the plan page. --%>
<div class="flex flex-col items-end gap-1">
  @template.ui._button(variant = "secondary", size = "md", type = "button", label = ctx.t("assistant.suggest.week"), icon = IconName.WAND, busy = false, disabled = false,
    attrs = Map.of("id", "plan-assistant-week", "x-data", "sheetOpener", "x-on:click", "open", "data-sheet", "assistant-sheet", "hx-get", "/assistant/week?week=" + weekCode, "hx-target", "#assistant-sheet-body"))
  @if(emptyWeek)
    <p id="plan-assistant-hint" class="text-sm text-ink-muted">${ctx.t("assistant.suggest.week.hint")}</p>
  @endif
  @template.ui._sheet(id = "assistant-sheet", title = ctx.t("assistant.sheet.title"), closeLabel = ctx.t("sheet.close"), role = "dialog", centred = false, closable = true,
    trigger = @``, body = @`<div id="assistant-sheet-body"></div>`, side = true)
</div>
