@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.TemplatePage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param TemplatePage page
<%-- The rename card, a copy of household/_rename: the form is the root (htmx swaps it, and tests read
     id="template-rename-form") with class="contents" so the card keeps its layout. A 422 puts the
     message under the field. A 409 (someone changed the template first) is not the typed name's fault,
     so it is a warning above the field with a Reload link, and the typed name stays in the field. The
     version is the one the page was built with; a 409 rebuilds the page with the current version. --%>
<form id="template-rename-form" class="contents" method="post" action="${page.renameAction()}" hx-post="${page.renameAction()}" hx-target="this" hx-swap="outerHTML" novalidate>
  @template.ui._card(variant = "default", content = @`
    <h2 class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("plan.templates.rename.heading")}</h2>
    @if(page.staleRename())
      @template.ui._alert(kind = "warning", title = ctx.t("plan.templates.conflict.title"), message = ctx.t(TemplatePage.CONFLICT_KEY), action = @`
        @template.meal._link_button(variant = "secondary", href = page.href(), label = ctx.t("plan.templates.conflict.reload"), icon = null)
      `)
    @endif
    @if(ctx.csrf() != null)
      <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
    @endif
    <input type="hidden" name="version" value="${page.version()}">
    @template.ui._field(id = "template-rename-name", name = "name", label = ctx.t("plan.templates.name.label"), type = "text", value = page.renameName(), hint = null, error = page.fieldError() == null ? null : ctx.t(page.fieldError()), required = true, disabled = false, attrs = Map.of("maxlength", "100", "autocomplete", "off"))
    <div>
      @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("plan.templates.rename.submit"), icon = null, busy = false, disabled = false, attrs = Map.of())
    </div>
  `)
</form>
