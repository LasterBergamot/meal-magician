@import java.util.Map
@import hu.lasterbergamot.mealmagician.household.internal.HouseholdsPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HouseholdsPage page
<form id="create-form" class="contents" hx-post="/households" hx-target="this" hx-swap="outerHTML">
  <%-- The create-household card. The form is the root of the fragment (htmx swaps it with outerHTML)
       and is display: contents, so the card inside it lays out like any other card of the page. The
       button is the page's primary one only while the person has no household yet. A rejected name
       comes back with 422, the message under the field and the typed text kept. --%>
  @template.ui._card(variant = "default", content = @`
    <h2 class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("household.create.heading")}</h2>
    @template.ui._field(id = "create-name", name = "name", label = ctx.t("household.name.label"), type = "text", value = page.createName(), hint = null, error = page.createError() == null ? null : ctx.t(page.createError()), required = true, disabled = false, attrs = Map.of("maxlength", "100", "autocomplete", "off"))
    <div>
      @template.ui._button(variant = page.households().isEmpty() ? "primary" : "secondary", size = "md", type = "submit", label = ctx.t("household.create.submit"), icon = null, busy = false, disabled = false, attrs = Map.of())
    </div>
  `)
</form>
