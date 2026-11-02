@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.SlotEditor
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param SlotEditor editor
<%-- The note of a planned slot. It swaps itself (hx-target="this", outerHTML): a note that is too
     long returns this same form with 422, the message under the field and the typed text kept. A
     saved note redirects to the week. The hidden version is the one the editor was opened with. --%>
<form id="note-form" method="post" action="${editor.action("note")}" hx-post="${editor.action("note")}" hx-target="this" hx-swap="outerHTML" novalidate
      class="flex flex-col gap-3">
  @if(ctx.csrf() != null)
    <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
  @endif
  <input type="hidden" name="version" value="${editor.version()}">
  @template.ui._field(id = "slot-note", name = "note", label = ctx.t("plan.editor.note"), type = "text", value = editor.note(), hint = ctx.t("plan.editor.note.hint"),
    error = editor.noteError() == null ? null : ctx.t(editor.noteError()), required = false, disabled = false,
    attrs = Map.of("maxlength", "500", "autocomplete", "off"))
  <div>
    @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("plan.editor.note.submit"), icon = null, busy = false, disabled = false, attrs = Map.of())
  </div>
</form>
