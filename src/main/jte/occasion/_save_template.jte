@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionDetailPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionDetailPage page
<%-- "Save as a template" under the menu: one name field, pre-filled with the occasion's name. A
     rejected save (blank or long name, empty menu) returns this same form with 422, the message under
     the field and the typed name (it swaps itself, so the root element never changes). A saved
     template redirects to its own page. --%>
<form id="occasion-save-template-form" method="post" action="${page.saveAction()}" hx-post="${page.saveAction()}" hx-target="this" hx-swap="outerHTML" novalidate
      class="flex flex-col gap-3 border-t-2 border-dashed border-border pt-4">
  <h2 class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("occasion.template.save.heading")}</h2>
  <p class="text-sm text-ink-muted">${ctx.t("occasion.template.save.hint")}</p>
  @if(ctx.csrf() != null)
    <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
  @endif
  @template.ui._field(id = "occasion-template-name", name = "name", label = ctx.t("occasion.template.save.name"), type = "text", value = page.saveName(), hint = null,
    error = page.saveError() == null ? null : ctx.t(page.saveError()), required = true, disabled = false,
    attrs = Map.of("maxlength", "100", "autocomplete", "off"))
  <div>
    @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("occasion.template.save.submit"), icon = null, busy = false, disabled = false, attrs = Map.of())
  </div>
</form>
