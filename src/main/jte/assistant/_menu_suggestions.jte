@import java.util.Map
@import hu.lasterbergamot.mealmagician.assistant.MenuSuggestions
@import hu.lasterbergamot.mealmagician.assistant.internal.ErrorNotice
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param long occasionId
@param MenuSuggestions suggestions
@param ErrorNotice notice
<%-- "Suggest a menu" under an occasion's menu (plan-ui-redesign.md Appendix D.5). The button is the
     form's submit, so an error's "Try again" sends the same request. Library suggestions are rows with
     the planner's own "Add a meal" form (a plain POST to /occasions/{id}/menu, which redirects to the
     page). Ideas are dashed cards like the draft's: "Add to meals" puts the meal in the library and
     replaces the card with a line; the menu is never changed by the wizard. Model text is escaped. --%>
<section id="occasion-menu-suggestions" class="flex flex-col gap-3">
  <form hx-post="/assistant/occasions/${occasionId}/menu" hx-target="#occasion-menu-suggestions" hx-swap="outerHTML" class="flex flex-col gap-3">
    @if(notice != null)
      @template.assistant._error(ctx = ctx, notice = notice)
    @endif
    <div class="flex justify-start">
      @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("assistant.menu.suggest"), icon = IconName.WAND, busy = false, disabled = false, attrs = Map.of("id", "occasion-suggest-menu"))
    </div>
  </form>
  @if(suggestions != null)
    @if(suggestions.meals().isEmpty() && suggestions.ideas().isEmpty())
      <p class="text-ink-muted">${ctx.t("assistant.slot.none")}</p>
    @else
      <h3 class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("assistant.menu.title")}</h3>
      @if(!suggestions.meals().isEmpty())
        <ul class="flex flex-col gap-2">
          @for(MenuSuggestions.MenuMeal meal : suggestions.meals())
            <li class="flex flex-wrap items-center justify-between gap-2 rounded-md border-2 border-outline bg-surface-raised px-3 py-2">
              <span class="min-w-0 break-words font-extrabold text-ink">${meal.mealName()}</span>
              <form method="post" action="/occasions/${occasionId}/menu">
                @if(ctx.csrf() != null)
                  <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
                @endif
                <input type="hidden" name="mealId" value="${meal.mealId()}">
                @template.ui._button(variant = "secondary", size = "sm", type = "submit", label = ctx.t("assistant.menu.add"), icon = IconName.PLUS, busy = false, disabled = false, attrs = Map.of())
              </form>
            </li>
          @endfor
        </ul>
      @endif
      @if(!suggestions.ideas().isEmpty())
        <ul class="flex flex-col gap-2">
          @for(MenuSuggestions.MenuIdea idea : suggestions.ideas())
            <li>
              <form hx-post="/assistant/occasions/${occasionId}/ideas" hx-target="closest li" hx-swap="outerHTML" data-idea class="flex flex-col gap-2 rounded-lg border-2 border-dashed border-outline bg-surface-raised p-3">
                <input type="hidden" name="name" value="${idea.name()}">
                @for(String tag : idea.tags())
                  <input type="hidden" name="tag" value="${tag}">
                @endfor
                @for(String ingredient : idea.ingredients())
                  <input type="hidden" name="ingredient" value="${ingredient}">
                @endfor
                <div class="flex flex-wrap items-center gap-2">
                  @template.ui._badge(tone = "brand", label = ctx.t("assistant.idea.badge"), icon = IconName.WAND)
                </div>
                <p class="break-words font-extrabold leading-6">${idea.name()}</p>
                @if(!idea.reason().isEmpty())
                  <p class="whitespace-pre-line text-sm text-ink-muted">${idea.reason()}</p>
                @endif
                @if(!idea.ingredients().isEmpty())
                  <p class="text-xs text-ink-muted">${ctx.t("assistant.idea.ingredients", String.join(", ", idea.ingredients()))}</p>
                @endif
                <div>
                  @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("assistant.idea.add"), icon = IconName.PLUS, busy = false, disabled = false, attrs = Map.of())
                </div>
              </form>
            </li>
          @endfor
        </ul>
      @endif
    @endif
  @endif
</section>
