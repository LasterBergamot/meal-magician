@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.ReusePage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param ReusePage page
<%-- "Save as a template": the name of a new day or week template. A rejected name returns this same
     form with 422 and the typed text (it swaps itself, so the root element and its attributes never
     change); a saved one is replaced by plan/_template_saved. --%>
<form id="save-template-form" method="post" action="/plan/templates" hx-post="/plan/templates" hx-target="this" hx-swap="outerHTML" novalidate
      class="flex flex-col gap-3 border-t-2 border-dashed border-border pt-4">
  <h3 class="font-display text-xl font-semibold text-ink">${ctx.t(page.saveHeadingKey())}</h3>
  @if(ctx.csrf() != null)
    <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
  @endif
  <input type="hidden" name="scope" value="${page.scopeName()}">
  <input type="hidden" name="date" value="${page.dateValue()}">
  @template.ui._field(id = "template-name", name = "name", label = ctx.t("plan.reuse.save.name"), type = "text", value = page.save().name(), hint = null,
    error = page.save().error() == null ? null : ctx.t(page.save().error()), required = true, disabled = false,
    attrs = Map.of("maxlength", "100", "autocomplete", "off"))
  <div>
    @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("plan.reuse.save.submit"), icon = null, busy = false, disabled = false, attrs = Map.of())
  </div>
</form>
