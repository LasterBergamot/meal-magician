@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String savedName
<%-- The answer to a saved template (htmx): it replaces the save form, keeps its id, and offers a link
     to the templates page. --%>
<div id="save-template-form" class="border-t-2 border-dashed border-border pt-4">
  @template.ui._alert(kind = "success", title = ctx.t("plan.reuse.saved.title"), message = ctx.t("plan.reuse.saved.message", savedName), action = @`
    @template.meal._link_button(variant = "secondary", href = "/plan/templates", label = ctx.t("plan.reuse.templates"), icon = null)
  `)
</div>
