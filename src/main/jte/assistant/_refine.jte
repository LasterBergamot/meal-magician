@import java.util.Map
@import hu.lasterbergamot.mealmagician.assistant.internal.DraftPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param DraftPage page
<%-- The follow-up form at the end of a draft (plan-ui-redesign.md Appendix D.3): "Change something?"
     with the same four chips and a two-line text area. It does not repeat the Gemini note: the member
     saw it a moment ago in the same sheet. While it runs, the draft above stays visible but dimmed
     (the #assistant-draft wrapper reacts to this form's htmx-request class) and the fields are
     disabled by hx-disabled-elt. An error alert sits inside the form, so "Try again" sends the same
     text again, and the typed text and chips are kept. --%>
<form id="assistant-refine-form" hx-post="/assistant/drafts/${page.id()}/refine" hx-target="#assistant-sheet-body" hx-swap="innerHTML"
      hx-disabled-elt="find textarea, find input, find button" data-abort-on-close
      class="flex flex-col gap-3 border-t-2 border-dashed border-border pt-4">
  @if(page.notice() != null)
    @template.assistant._error(ctx = ctx, notice = page.notice())
  @endif
  @template.ui._field(id = "assistant-refine-input", name = "message", label = ctx.t("assistant.refine.label"), type = "text", value = page.refineText(),
    hint = null, error = page.refineError() == null ? null : ctx.t(page.refineError()), required = false, disabled = false,
    attrs = Map.of("placeholder", ctx.t("assistant.refine.placeholder")), multiline = true, rows = 2)
  <div class="flex flex-wrap gap-2">
    @template.assistant._hint_chips(ctx = ctx, ticked = page.refineHints())
  </div>
  <div class="flex justify-end">
    @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("assistant.refine.send"), icon = IconName.WAND, busy = false, disabled = false, attrs = Map.of())
  </div>
</form>
