@import java.util.Map
@import hu.lasterbergamot.mealmagician.catalog.MealView
@import hu.lasterbergamot.mealmagician.catalog.OccasionType
@import hu.lasterbergamot.mealmagician.catalog.internal.OccasionLabels
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param MealView meal
<%-- One meal: notes, recipe link, tags, the occasions it suits, and the actions edit, archive
     (behind a confirm dialog) and restore. The recipe link is always http or https (checked when
     the meal is saved) and opens with rel="noopener noreferrer", so the other site cannot reach
     back into this tab. Archive and restore are htmx posts (the CSRF header comes from the meta
     tag) that redirect. --%>
@template.layout.base(ctx = ctx, title = meal.name(), content = @`
  <section class="flex flex-col gap-6">
    <div>
      @template.meal._link_button(variant = "ghost", href = "/meals", label = ctx.t("meal.detail.back"), icon = IconName.LEFT)
    </div>

    <div class="flex flex-wrap items-center gap-3">
      <h1 class="min-w-0 break-words font-display text-3xl font-semibold text-ink">${meal.name()}</h1>
      @if(meal.archived())
        @template.ui._badge(tone = "neutral", label = ctx.t("meal.archived"), icon = null)
      @endif
    </div>

    @if(meal.archived())
      @template.ui._alert(kind = "info", title = ctx.t("meal.archived.title"), message = ctx.t("meal.archived.message"), action = @`
        @template.ui._button(variant = "secondary", size = "md", type = "button", label = ctx.t("meal.restore"), icon = null, busy = false, disabled = false,
          attrs = Map.of("hx-post", "/meals/" + meal.id() + "/restore"))
      `)
    @endif

    @template.ui._card(variant = "default", content = @`
      <dl class="flex flex-col gap-4">
        <div>
          <dt class="text-sm font-extrabold text-ink">${ctx.t("meal.detail.notes")}</dt>
          <dd class="mt-1 whitespace-pre-line break-words text-ink">@if(meal.notes() != null)${meal.notes()}@else<span class="text-ink-muted">${ctx.t("meal.detail.none")}</span>@endif</dd>
        </div>
        <div>
          <dt class="text-sm font-extrabold text-ink">${ctx.t("meal.detail.recipe")}</dt>
          <dd class="mt-1 break-all">
            @if(meal.recipeUrl() != null)
              <a href="${meal.recipeUrl()}" rel="noopener noreferrer" target="_blank" class="font-bold text-brand underline hover:text-brand-hover">${meal.recipeUrl()}</a>
            @else
              <span class="text-ink-muted">${ctx.t("meal.detail.none")}</span>
            @endif
          </dd>
        </div>
        <div>
          <dt class="text-sm font-extrabold text-ink">${ctx.t("meal.detail.tags")}</dt>
          <dd class="mt-1">
            @if(meal.tags().isEmpty())
              <span class="text-ink-muted">${ctx.t("meal.detail.none")}</span>
            @else
              <ul class="flex flex-wrap gap-1.5">
                @for(String tag : meal.tags())
                  <li>@template.ui._badge(tone = "brand", label = tag, icon = null)</li>
                @endfor
              </ul>
            @endif
          </dd>
        </div>
        <div>
          <dt class="text-sm font-extrabold text-ink">${ctx.t("meal.detail.occasions")}</dt>
          <dd class="mt-1">
            @if(meal.occasionTypes().isEmpty())
              <span class="text-ink-muted">${ctx.t("meal.detail.none")}</span>
            @else
              <ul class="flex flex-wrap gap-1.5">
                @for(OccasionType type : meal.occasionTypes())
                  <li>@template.ui._badge(tone = "info", label = ctx.t(OccasionLabels.key(type)), icon = IconName.GIFT)</li>
                @endfor
              </ul>
            @endif
          </dd>
        </div>
      </dl>
    `)

    <div class="flex flex-wrap gap-3">
      @template.meal._link_button(variant = "primary", href = "/meals/" + meal.id() + "/edit", label = ctx.t("meal.edit"), icon = IconName.EDIT)
      @if(!meal.archived())
        @template.ui._confirm(id = "archive-confirm", title = ctx.t("meal.archive.confirm.title", meal.name()), message = ctx.t("meal.archive.confirm.message"),
          cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("meal.archive.confirm.submit"),
          attrs = Map.of("hx-post", "/meals/" + meal.id() + "/archive"),
          trigger = @`@template.ui._button(variant = "secondary", size = "md", type = "button", label = ctx.t("meal.archive"), icon = IconName.TRASH, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
      @endif
    </div>
  </section>
`)
