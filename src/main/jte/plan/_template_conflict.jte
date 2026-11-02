@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String reloadHref
<%-- The 409 answer for a stale delete: a warning alert with a Reload link to the template's page. Nothing
     was deleted. It is rendered into #plan-conflict, either by htmx (HX-Retarget) or by the template
     page for a request without htmx. A copy of plan/_conflict with the template's own texts. --%>
@template.ui._alert(kind = "warning", title = ctx.t("plan.templates.conflict.title"), message = ctx.t("plan.templates.conflict.message"), action = @`
  @template.meal._link_button(variant = "secondary", href = reloadHref, label = ctx.t("plan.templates.conflict.reload"), icon = null)
`)
