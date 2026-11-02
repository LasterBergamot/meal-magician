@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String reloadHref
<%-- The 409 answer for a stale edit: a warning alert ("someone else changed the plan") with a Reload
     link to the week. Nothing was written. It is rendered into #plan-conflict, either by htmx
     (HX-Retarget) or by plan/week for a request without htmx. --%>
@template.ui._alert(kind = "warning", title = ctx.t("plan.conflict.title"), message = ctx.t("plan.conflict.message"), action = @`
  @template.meal._link_button(variant = "secondary", href = reloadHref, label = ctx.t("plan.conflict.reload"), icon = null)
`)
