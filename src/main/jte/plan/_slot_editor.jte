@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.SlotEditor
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param SlotEditor editor
<%-- The content of the slot sheet (and of the no-JavaScript page): the planned meal, its note, where
     it can move to, its place among the day's meals, and Remove. Every form sends the version the
     editor was opened with; a stale one is refused with 409. Remove is a ui/_confirm opened from
     this sheet: allowed because the confirm is a centred alertdialog in the top layer, and app.js
     lets the inner dialog handle its own Tab. --%>
<div class="flex flex-col gap-5">
  <div class="flex flex-col gap-2">
    <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
      @template.ui._slot_chip(slot = editor.slotName(), label = ctx.t(editor.labelKey()))
      <p class="font-bold text-ink">${editor.dayLabel()}</p>
    </div>
    <div class="flex flex-wrap items-center gap-2">
      <h3 class="min-w-0 break-words font-display text-xl font-semibold text-ink">${editor.mealName()}</h3>
      @if(editor.archived())
        @template.ui._badge(tone = "neutral", label = ctx.t("meal.archived"), icon = null)
      @endif
    </div>
    <div>
      @template.meal._link_button(variant = "ghost", href = editor.mealHref(), label = ctx.t("plan.editor.open"), icon = IconName.MEALS)
    </div>
  </div>

  @if(editor.past())
    <%-- Rating (#128): feedback cannot be imported here, so the form loads over htmx. Only slots dated
         today or earlier get it (D27). --%>
    <div id="rating-section" hx-get="${editor.rateHref()}" hx-trigger="load" hx-swap="innerHTML" class="border-b-2 border-dashed border-border pb-4"></div>
  @endif

  @template.plan._note_form(ctx = ctx, editor = editor)

  <form id="move-form" method="post" action="${editor.action("move")}" hx-post="${editor.action("move")}" class="flex flex-col gap-3 border-t-2 border-dashed border-border pt-4">
    <h3 class="font-display text-xl font-semibold text-ink">${ctx.t("plan.editor.move.heading")}</h3>
    @if(ctx.csrf() != null)
      <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
    @endif
    <input type="hidden" name="version" value="${editor.version()}">
    @template.ui._select(id = "slot-move-date", name = "date", label = ctx.t("plan.editor.move.day"), options = editor.dayOptions(), selected = editor.dateValue(), hint = null, error = null, disabled = false, attrs = Map.of())
    @template.ui._select(id = "slot-move-slot", name = "slot", label = ctx.t("plan.editor.move.slot"), options = editor.slotOptions(), selected = editor.slotName(), hint = null, error = null, disabled = false, attrs = Map.of())
    <div>
      @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("plan.editor.move.submit"), icon = null, busy = false, disabled = false, attrs = Map.of())
    </div>
  </form>

  @if(editor.canMoveUp() || editor.canMoveDown())
    <div class="flex flex-col gap-3 border-t-2 border-dashed border-border pt-4">
      <h3 class="font-display text-xl font-semibold text-ink">${ctx.t("plan.editor.order.heading")}</h3>
      <div class="flex flex-wrap gap-3">
        @if(editor.canMoveUp())
          <form id="reorder-up-form" method="post" action="${editor.action("reorder")}" hx-post="${editor.action("reorder")}" class="contents">
            @if(ctx.csrf() != null)
              <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
            @endif
            <input type="hidden" name="version" value="${editor.version()}">
            <input type="hidden" name="position" value="${editor.upPosition()}">
            @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("plan.editor.order.up"), icon = null, busy = false, disabled = false, attrs = Map.of())
          </form>
        @endif
        @if(editor.canMoveDown())
          <form id="reorder-down-form" method="post" action="${editor.action("reorder")}" hx-post="${editor.action("reorder")}" class="contents">
            @if(ctx.csrf() != null)
              <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
            @endif
            <input type="hidden" name="version" value="${editor.version()}">
            <input type="hidden" name="position" value="${editor.downPosition()}">
            @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("plan.editor.order.down"), icon = null, busy = false, disabled = false, attrs = Map.of())
          </form>
        @endif
      </div>
    </div>
  @endif

  <div class="border-t-2 border-dashed border-border pt-4">
    @template.ui._confirm(id = "remove-slot", title = ctx.t("plan.editor.remove.title", editor.mealName()), message = ctx.t("plan.editor.remove.message"),
      cancelLabel = ctx.t("plan.editor.remove.cancel"), confirmLabel = ctx.t("plan.editor.remove.submit"), attrs = Map.of("hx-post", editor.removeAction()),
      trigger = @`@template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("plan.editor.remove"), icon = IconName.TRASH, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
  </div>
</div>
