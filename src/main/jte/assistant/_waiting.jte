@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param boolean skeleton
@param boolean cancellable
<%-- The wizard is working (plan-ui-redesign.md Appendix D.3, step 2; 10 to 30 seconds): a status
     line with the twinkling wand and the thinking sentence. skeleton adds three grey day cards
     shaped like the draft that will arrive; cancellable adds a Cancel that closes the sheet, which
     aborts the request in flight (the sheet component, app.js). The twinkle is a cue only: the
     sentence says the same, and the animation is off under prefers-reduced-motion. This fragment
     sits inside a form and is shown by CSS while the form has htmx's htmx-request class. --%>
<div class="flex flex-col gap-4">
  <div role="status" class="flex items-center gap-3 text-ink">
    <span class="mm-twinkle text-brand">
      @template.ui._icon(name = IconName.WAND, size = 24, filled = false)
    </span>
    <p class="font-bold leading-6">${ctx.t("assistant.thinking")}</p>
  </div>
  @if(skeleton)
    <div class="flex flex-col gap-3">
      @template.ui._skeleton_rows(rows = 2, label = ctx.t("assistant.thinking"))
      @template.ui._skeleton_rows(rows = 2, label = "")
      @template.ui._skeleton_rows(rows = 2, label = "")
    </div>
  @endif
  @if(cancellable)
    <div class="flex justify-end">
      @template.ui._button(variant = "ghost", size = "md", type = "button", label = ctx.t("assistant.cancel"), icon = null, busy = false, disabled = false, attrs = Map.of("x-on:click", "hide"))
    </div>
  @endif
</div>
