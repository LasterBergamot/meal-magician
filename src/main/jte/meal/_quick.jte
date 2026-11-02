@import java.util.Map
@import hu.lasterbergamot.mealmagician.catalog.internal.QuickAdd
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param QuickAdd quick
<%-- The quick-add form: a meal's name and nothing else (details can be added later by editing). The
     page has two, the inline bar from md up and the one in the phone's bottom sheet, told apart by
     quick.variant (their ids differ). A rejected post returns this same form with a 422 and the
     typed name; a saved one redirects to the list. The element swaps itself (outerHTML). --%>
<form id="${quick.variant()}-form" method="post" action="/meals/quick" hx-post="/meals/quick" hx-target="this" hx-swap="outerHTML" novalidate
      class="flex gap-3 @if(quick.inline()) items-start @else flex-col @endif">
  @if(ctx.csrf() != null)
    <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
  @endif
  <input type="hidden" name="form" value="${quick.variant()}">
  <div class="min-w-0 flex-1">
    @template.ui._field(id = quick.variant() + "-name", name = "name", label = ctx.t("meal.quick.label"), type = "text", value = quick.name(),
      hint = quick.inline() ? null : ctx.t("meal.quick.hint"), error = quick.error() == null ? null : ctx.t(quick.error()), required = true, disabled = false,
      attrs = Map.of("maxlength", "200", "autocomplete", "off"))
  </div>
  <div class="@if(quick.inline()) pt-[26px] @endif">
    @template.ui._button(variant = "primary", size = "md", type = "submit", label = ctx.t("meal.quick.submit"), icon = IconName.PLUS, busy = false, disabled = false, attrs = Map.of())
  </div>
</form>
