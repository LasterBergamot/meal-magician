@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionTemplatePage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionTemplatePage page
<%-- "Plan it again": a date creates a new occasion with the template's name, kind, notes and menu. A
     missing or malformed date returns this same form with 422 and the message under the field (it
     swaps itself, so the root element never changes). A saved occasion redirects to its page. --%>
<form id="occasion-use-form" method="post" action="${page.useAction()}" hx-post="${page.useAction()}" hx-target="this" hx-swap="outerHTML" novalidate class="flex flex-col gap-3">
  @template.ui._card(variant = "default", content = @`
    <h2 class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("occasion.template.use.heading")}</h2>
    @if(ctx.csrf() != null)
      <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
    @endif
    @template.ui._field(id = "occasion-use-date", name = "date", label = ctx.t("occasion.template.use.date"), type = "date", value = page.useDate(), hint = null,
      error = page.useError() == null ? null : ctx.t(page.useError()), required = true, disabled = false, attrs = Map.of())
    <div>
      @template.ui._button(variant = "primary", size = "md", type = "submit", label = ctx.t("occasion.template.use.submit"), icon = null, busy = false, disabled = false, attrs = Map.of())
    </div>
  `)
</form>
