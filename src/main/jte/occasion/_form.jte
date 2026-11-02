@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionFormPage
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionFormPage page
<%-- The occasion form (create and edit), copied from meal/_form. It swaps itself (outerHTML), so the
     response must keep the same root form with the same id. A rejected post returns it with 422 and
     the typed values, the message under the one wrong field; a stale edit returns it with 409 and
     the warning. novalidate: the server's messages replace the browser's bubbles. The notes
     textarea is drawn here with the tokens of ui/_field, as in meal/_form. --%>
<form id="occasion-form" method="post" action="${page.form().action()}" hx-post="${page.form().action()}" hx-target="this" hx-swap="outerHTML" novalidate class="flex flex-col gap-5">
  @if(ctx.csrf() != null)
    <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
  @endif
  @if(page.form().editing())
    <input type="hidden" name="version" value="${page.form().version()}">
  @endif

  @if(page.conflict())
    @template.ui._alert(kind = "warning", title = ctx.t("occasion.conflict.title"), message = ctx.t("occasion.conflict.message"), action = @`
      @template.meal._link_button(variant = "secondary", href = "/occasions/" + page.form().id() + "/edit", label = ctx.t("occasion.conflict.reload"), icon = null)
    `)
  @endif

  @template.ui._field(id = "occasion-name", name = "name", label = ctx.t("occasion.form.name"), type = "text", value = page.form().name(), hint = ctx.t("occasion.form.name.hint"),
    error = page.error("name") == null ? null : ctx.t(page.error("name")), required = true, disabled = false,
    attrs = Map.of("maxlength", "100", "autocomplete", "off"))

  @template.ui._field(id = "occasion-date", name = "date", label = ctx.t("occasion.form.date"), type = "date", value = page.form().date(), hint = null,
    error = page.error("date") == null ? null : ctx.t(page.error("date")), required = true, disabled = false,
    attrs = Map.of())

  @template.ui._select(id = "occasion-type", name = "type", label = ctx.t("occasion.form.type"), options = page.typeOptions(), selected = page.form().type(), hint = null,
    error = page.error("type") == null ? null : ctx.t(page.error("type")), disabled = false,
    attrs = Map.of())

  <div class="flex flex-col gap-1.5">
    <label for="occasion-notes" class="text-sm font-extrabold leading-5 text-ink">${ctx.t("occasion.form.notes")}</label>
    <textarea id="occasion-notes" name="notes" rows="4" maxlength="2000" aria-invalid="${page.error("notes") != null ? "true" : "false"}"
              $unsafe{HtmlAttributes.render(HtmlAttributes.describedBy("occasion-notes", false, page.error("notes") != null))}
              class="block w-full rounded-md border-2 bg-surface-raised px-4 py-3 text-base text-ink @if(page.error("notes") != null) border-danger @else border-border-strong @endif">${page.form().notes()}</textarea>
    @if(page.error("notes") != null)
      <p id="occasion-notes-error" class="flex items-center gap-1.5 text-sm font-bold text-danger">
        @template.ui._icon(name = IconName.ALERT, size = 20, filled = false)
        <span>${ctx.t(page.error("notes"))}</span>
      </p>
    @endif
  </div>

  <div class="flex flex-wrap items-center gap-3">
    @template.ui._button(variant = "primary", size = "lg", type = "submit", label = ctx.t("occasion.form.save"), icon = null, busy = false, disabled = false, attrs = Map.of())
    @template.meal._link_button(variant = "ghost", href = page.form().backHref(), label = ctx.t("occasion.form.cancel"), icon = null)
  </div>
</form>
