@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String messageKey
@param String reloadHref
<%-- The 422 answer for a refused copy (same day or week, or nothing to copy): a warning alert with a
     Reload link. It is rendered into #plan-conflict, either by htmx (HX-Retarget, which also closes
     every open dialog) or by plan/week for a request without htmx. --%>
@template.ui._alert(kind = "warning", title = ctx.t("plan.reuse.error.title"), message = ctx.t(messageKey), action = @`
  @template.meal._link_button(variant = "secondary", href = reloadHref, label = ctx.t("plan.conflict.reload"), icon = null)
`)
