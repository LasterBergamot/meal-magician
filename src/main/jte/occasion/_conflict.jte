@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String reloadHref
<%-- The 409 banner for a stale delete on the occasion page (#112): a warning alert with a Reload link
     to the occasion's page. Nothing was deleted. It is rendered into #occasion-conflict, either by
     htmx (HX-Retarget) or by the detail page for a request without htmx. --%>
@template.ui._alert(kind = "warning", title = ctx.t("occasion.conflict.title"), message = ctx.t("occasion.conflict.message"), action = @`
  @template.meal._link_button(variant = "secondary", href = reloadHref, label = ctx.t("occasion.conflict.reload"), icon = null)
`)
