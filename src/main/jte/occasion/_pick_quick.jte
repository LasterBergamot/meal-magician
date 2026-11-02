@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionPickPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionPickPage page
<%-- "Create and add": a new meal from its name, put on the menu in one step. A rejected name returns
     this same form with 422 and the typed text (it swaps itself, so the root element and its
     attributes never change). --%>
<form id="occasion-pick-quick-form" method="post" action="${page.quickAction()}" hx-post="${page.quickAction()}" hx-target="this" hx-swap="outerHTML" novalidate
      class="flex flex-col gap-3 border-t-2 border-dashed border-border pt-4">
  <h3 class="font-display text-xl font-semibold text-ink">${ctx.t("plan.pick.quick.heading")}</h3>
  @if(ctx.csrf() != null)
    <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
  @endif
  @template.ui._field(id = "occasion-pick-quick-name", name = "name", label = ctx.t("plan.pick.quick.label"), type = "text", value = page.quick().name(), hint = null,
    error = page.quick().error() == null ? null : ctx.t(page.quick().error()), required = true, disabled = false,
    attrs = Map.of("maxlength", "200", "autocomplete", "off"))
  <div>
    @template.ui._button(variant = "primary", size = "md", type = "submit", label = ctx.t("plan.pick.quick.submit"), icon = IconName.PLUS, busy = false, disabled = false, attrs = Map.of())
  </div>
</form>
