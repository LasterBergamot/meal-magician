@import java.util.Map
@import hu.lasterbergamot.mealmagician.catalog.OccasionType
@import hu.lasterbergamot.mealmagician.catalog.internal.OccasionLabels
@import hu.lasterbergamot.mealmagician.catalog.internal.MealFormPage
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param MealFormPage page
<%-- The meal form (create and edit). It swaps itself (outerHTML). A rejected post returns it with
     a 422 (or a 409 when someone else saved first), the typed values and the message under the
     field, so nothing typed is lost. novalidate: the server's messages replace the browser's
     bubbles. There is no ui/ textarea or checkbox yet, so those two controls are drawn here with
     the same tokens as ui/_field (a gap to report to the design system). --%>
<form id="meal-form" method="post" action="${page.form().action()}" hx-post="${page.form().action()}" hx-target="this" hx-swap="outerHTML" novalidate class="flex flex-col gap-5">
  @if(ctx.csrf() != null)
    <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
  @endif
  @if(page.form().editing())
    <input type="hidden" name="version" value="${page.form().version()}">
  @endif

  @if(page.conflict())
    @template.ui._alert(kind = "warning", title = ctx.t("meal.conflict.title"), message = ctx.t("meal.conflict.message"), action = @`
      @template.meal._link_button(variant = "secondary", href = "/meals/" + page.form().id() + "/edit", label = ctx.t("meal.conflict.reload"), icon = null)
    `)
  @endif

  @template.ui._field(id = "meal-name", name = "name", label = ctx.t("meal.form.name"), type = "text", value = page.form().name(), hint = null,
    error = page.error("name") == null ? null : ctx.t(page.error("name")), required = true, disabled = false,
    attrs = Map.of("maxlength", "200", "autocomplete", "off"))

  <div class="flex flex-col gap-1.5">
    <label for="meal-notes" class="text-sm font-extrabold leading-5 text-ink">${ctx.t("meal.form.notes")}</label>
    <textarea id="meal-notes" name="notes" rows="4" maxlength="2000" aria-invalid="${page.error("notes") != null ? "true" : "false"}"
              $unsafe{HtmlAttributes.render(HtmlAttributes.describedBy("meal-notes", false, page.error("notes") != null))}
              class="block w-full rounded-md border-2 bg-surface-raised px-4 py-3 text-base text-ink @if(page.error("notes") != null) border-danger @else border-border-strong @endif">${page.form().notes()}</textarea>
    @if(page.error("notes") != null)
      <p id="meal-notes-error" class="flex items-center gap-1.5 text-sm font-bold text-danger">
        @template.ui._icon(name = IconName.ALERT, size = 20, filled = false)
        <span>${ctx.t(page.error("notes"))}</span>
      </p>
    @endif
  </div>

  @template.ui._field(id = "meal-recipe", name = "recipeUrl", label = ctx.t("meal.form.recipe"), type = "text", value = page.form().recipeUrl(), hint = ctx.t("meal.form.recipe.hint"),
    error = page.error("recipeUrl") == null ? null : ctx.t(page.error("recipeUrl")), required = false, disabled = false,
    attrs = Map.of("maxlength", "2000", "autocomplete", "off", "inputmode", "url"))

  @template.ui._suggest_field(id = "meal-tags", name = "tags", label = ctx.t("meal.form.tags"), hint = ctx.t("meal.form.tags.hint"), value = page.form().tags(),
    error = page.error("tags") == null ? null : ctx.t(page.error("tags")), searchUrl = "/meals/tags", listLabel = ctx.t("meal.form.tags.suggestions"))

  @template.ui._combobox(id = "meal-ingredients", name = "ingredients", label = ctx.t("meal.form.ingredients"), hint = ctx.t("meal.form.ingredients.hint"),
    placeholder = ctx.t("meal.form.ingredients.search"), chosenLabel = ctx.t("meal.form.ingredients.chosen"),
    removeLabel = ctx.t("meal.form.ingredients.remove", "{0}"), searchUrl = "/meals/ingredients", selected = page.form().ingredients(),
    error = page.error("ingredients") == null ? null : ctx.t(page.error("ingredients")))

  <fieldset class="flex flex-col gap-2">
    <legend class="text-sm font-extrabold leading-5 text-ink">${ctx.t("meal.form.occasions")}</legend>
    <div class="grid gap-2 sm:grid-cols-2">
      @for(OccasionType type : OccasionType.values())
        <label class="flex min-h-11 cursor-pointer items-center gap-3 rounded-md border-2 border-border-strong bg-surface-raised px-4 text-base font-bold text-ink">
          <input type="checkbox" name="occasion" value="${type.name()}" checked="${page.form().has(type)}" class="size-5 shrink-0 accent-brand">
          <span>${ctx.t(OccasionLabels.key(type))}</span>
        </label>
      @endfor
    </div>
  </fieldset>

  <div class="flex flex-wrap items-center gap-3">
    @template.ui._button(variant = "primary", size = "lg", type = "submit", label = ctx.t("meal.form.save"), icon = null, busy = false, disabled = false, attrs = Map.of())
    @template.meal._link_button(variant = "ghost", href = page.form().backHref(), label = ctx.t("meal.form.cancel"), icon = null)
  </div>
</form>
