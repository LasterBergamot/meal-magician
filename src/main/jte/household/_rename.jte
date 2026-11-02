@import java.util.Map
@import hu.lasterbergamot.mealmagician.household.internal.HouseholdsPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HouseholdsPage page
<form id="rename-form" class="contents" hx-post="/households/${page.current().id()}/rename" hx-target="this" hx-swap="outerHTML">
  <%-- The owner's rename card (see _create for why the form is display: contents). A 422 puts the
       message under the field. A 409 (someone renamed it first) is not the typed name's fault, so it
       is a warning alert with a Reload button that loads the page's list again, and the typed name stays
       in the field. --%>
  @template.ui._card(variant = "default", content = @`
    <h2 class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("household.rename.heading")}</h2>
    @if("household.conflict".equals(page.renameError()))
      @template.ui._alert(kind = "warning", title = ctx.t("household.conflict.title"), message = ctx.t(page.renameError()), action = @`
        @template.ui._button(variant = "secondary", size = "sm", type = "button", label = ctx.t("household.conflict.reload"), icon = null, busy = false, disabled = false, attrs = Map.of("hx-get", "/households", "hx-target", "#households", "hx-swap", "outerHTML"))
      `)
    @endif
    @template.ui._field(id = "rename-name", name = "name", label = ctx.t("household.name.label"), type = "text", value = page.renameName(), hint = null, error = page.renameError() == null || "household.conflict".equals(page.renameError()) ? null : ctx.t(page.renameError()), required = true, disabled = false, attrs = Map.of("maxlength", "100", "autocomplete", "off"))
    <div>
      @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("household.rename.submit"), icon = null, busy = false, disabled = false, attrs = Map.of())
    </div>
  `)
</form>
