@import hu.lasterbergamot.mealmagician.planning.internal.TemplateRow
@import hu.lasterbergamot.mealmagician.planning.internal.TemplatesPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param TemplatesPage page
<%-- The template list (plan-ui-redesign.md section 5). Two groups, day templates and week templates,
     each a card of rows with the name and the meal count; each row is one link to the template's
     page. When there are no templates at all, one empty state links back to the plan, where a day
     or a week is saved as a template. An empty group says so in one line instead of an empty card. --%>
@template.layout.base(ctx = ctx, title = ctx.t("plan.templates.title"), content = @`
  <section class="flex flex-col gap-6">
    <div>
      @template.meal._link_button(variant = "ghost", href = "/plan", label = ctx.t("plan.back"), icon = IconName.LEFT)
    </div>

    <div class="flex flex-col gap-2">
      <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("plan.templates.heading")}</h1>
      <p class="text-ink-muted">${ctx.t("plan.templates.lead")}</p>
    </div>

    @if(page.empty())
      @template.ui._empty(icon = IconName.WAND, heading = ctx.t("plan.templates.empty.heading"), message = ctx.t("plan.templates.empty.message"),
        action = @`@template.meal._link_button(variant = "primary", href = "/plan", label = ctx.t("plan.templates.empty.action"), icon = null)`)
    @else
      <section class="flex flex-col gap-3" aria-labelledby="templates-days">
        <h2 id="templates-days" class="font-display text-2xl font-semibold text-ink">${ctx.t("plan.templates.days.heading")}</h2>
        @if(page.days().isEmpty())
          <p class="rounded-md border-2 border-dashed border-border-strong p-4 text-ink-muted">${ctx.t("plan.templates.none.day")}</p>
        @else
          @template.ui._card(variant = "default", content = @`
            <ul>
              @for(TemplateRow row : page.days())
                @template.ui._row(selected = false, content = @`
                  <a href="${row.href()}" data-template-row="${row.id()}" class="flex min-h-11 flex-1 items-center justify-between gap-3 font-extrabold text-ink hover:text-brand">
                    <span class="min-w-0 break-words">${row.name()}</span>
                    @template.ui._badge(tone = "neutral", label = ctx.t("plan.templates.meals", String.valueOf(row.mealCount())), icon = null)
                  </a>
                `)
              @endfor
            </ul>
          `)
        @endif
      </section>

      <section class="flex flex-col gap-3" aria-labelledby="templates-weeks">
        <h2 id="templates-weeks" class="font-display text-2xl font-semibold text-ink">${ctx.t("plan.templates.weeks.heading")}</h2>
        @if(page.weeks().isEmpty())
          <p class="rounded-md border-2 border-dashed border-border-strong p-4 text-ink-muted">${ctx.t("plan.templates.none.week")}</p>
        @else
          @template.ui._card(variant = "default", content = @`
            <ul>
              @for(TemplateRow row : page.weeks())
                @template.ui._row(selected = false, content = @`
                  <a href="${row.href()}" data-template-row="${row.id()}" class="flex min-h-11 flex-1 items-center justify-between gap-3 font-extrabold text-ink hover:text-brand">
                    <span class="min-w-0 break-words">${row.name()}</span>
                    @template.ui._badge(tone = "neutral", label = ctx.t("plan.templates.meals", String.valueOf(row.mealCount())), icon = null)
                  </a>
                `)
              @endfor
            </ul>
          `)
        @endif
      </section>
    @endif
  </section>
`)
