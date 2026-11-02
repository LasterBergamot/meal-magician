@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.HelloView
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HelloView hello
<%-- The part htmx replaces: today's date with a clock icon and a secondary refresh button. The button
     asks the same URL again; because the request carries HX-Request, the controller answers with this
     fragment only. hx-target/hx-swap are always explicit (web-ui.md), and the button's busy state
     comes from ui/_button (htmx adds htmx-request while it runs). --%>
<div id="greeting" class="flex flex-col gap-3 sm:flex-row sm:items-center">
  <span class="flex size-11 shrink-0 items-center justify-center rounded-full border-2 border-outline bg-accent-soft text-accent-ink">
    @template.ui._icon(name = IconName.CLOCK, size = 24, filled = false)
  </span>
  <p class="text-lg font-bold leading-6 text-ink">${ctx.t("hello.today", hello.today())}</p>
  <div class="sm:ml-auto">
    @template.ui._button(variant = "secondary", size = "md", type = "button", label = ctx.t("hello.refresh"), icon = null, busy = false, disabled = false, attrs = Map.of("hx-get", "/", "hx-target", "#greeting", "hx-swap", "outerHTML"))
  </div>
</div>
