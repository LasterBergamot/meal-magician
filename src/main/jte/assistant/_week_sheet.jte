@import java.util.Map
@import hu.lasterbergamot.mealmagician.assistant.internal.WeekSheetPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param WeekSheetPage page
<%-- "Suggest my week", step 1: the request (plan-ui-redesign.md Appendix D.3). The sheet's own title
     says what this is, so the body starts with the week's dates. A text area for anything special,
     four quick-request chips (sent as hint values; the server turns them into text in the member's
     language), a one-line note that the request goes to Google Gemini, and the Start button.

     The form swaps the whole sheet body. While it is in flight htmx puts htmx-request on it, and
     CSS swaps the fields for the waiting state, so the wait is visible without any script. Cancel
     (inside the waiting state) closes the sheet; data-abort-on-close makes the sheet abort the
     request. An error brings this same form back with the member's text and chips kept. --%>
<form id="assistant-request-form" hx-post="/assistant/drafts" hx-target="#assistant-sheet-body" hx-swap="innerHTML" data-abort-on-close class="flex flex-col gap-4">
  <input type="hidden" name="week" value="${page.weekCode()}">
  <div class="flex flex-col gap-4 [.htmx-request_&]:hidden">
    <p class="text-sm text-ink-muted">${page.range()}</p>
    @if(page.notice() != null)
      @template.assistant._error(ctx = ctx, notice = page.notice())
    @endif
    @template.ui._field(id = "assistant-request", name = "request", label = ctx.t("assistant.request.label"), type = "text", value = page.request(),
      hint = ctx.t("assistant.notice.provider"), error = page.requestError() == null ? null : ctx.t(page.requestError()), required = false, disabled = false,
      attrs = Map.of("placeholder", ctx.t("assistant.request.placeholder")), multiline = true, rows = 3)
    <fieldset class="flex flex-col gap-2">
      <legend class="text-sm font-extrabold leading-5 text-ink">${ctx.t("assistant.hint.label")}</legend>
      <div class="flex flex-wrap gap-2">
        @template.assistant._hint_chips(ctx = ctx, ticked = page.hints())
      </div>
    </fieldset>
    <div class="flex justify-end">
      @template.ui._button(variant = "primary", size = "md", type = "submit", label = ctx.t("assistant.start"), icon = IconName.WAND, busy = false, disabled = false, attrs = Map.of())
    </div>
  </div>
  <div class="hidden [.htmx-request_&]:block">
    @template.assistant._waiting(ctx = ctx, skeleton = true, cancellable = true)
  </div>
</form>
