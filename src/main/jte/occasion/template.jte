@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.MealLine
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionTemplatePage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionTemplatePage page
<%-- One occasion template (#113): its name, kind, notes and menu, the "Plan it again" form and
     Delete. Delete is a confirm dialog outside every swapped region (only #occasion-use-form
     swaps), and a delete redirects to the list; the occasions made from the template stay. --%>
@template.layout.base(ctx = ctx, title = page.name(), content = @`
  <section class="flex flex-col gap-6">
    <div>
      @template.meal._link_button(variant = "ghost", href = "/occasions", label = ctx.t("occasion.back"), icon = IconName.LEFT)
    </div>

    <div class="flex flex-wrap items-center gap-3">
      <h1 class="min-w-0 break-words font-display text-3xl font-semibold text-ink">${page.name()}</h1>
      @template.ui._badge(tone = "accent", label = ctx.t(page.typeKey()), icon = null)
      @template.ui._badge(tone = "neutral", label = ctx.t("occasion.template.badge"), icon = null)
    </div>

    @if(page.notes() != null)
      @template.ui._card(variant = "default", content = @`
        <dl class="flex flex-col gap-4">
          <div>
            <dt class="text-sm font-extrabold text-ink">${ctx.t("occasion.detail.notes")}</dt>
            <dd class="mt-1 whitespace-pre-line break-words text-ink">${page.notes()}</dd>
          </div>
        </dl>
      `)
    @endif

    <section aria-labelledby="menu-title" class="flex flex-col gap-3">
      <h2 id="menu-title" class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("occasion.menu.heading")}</h2>
      <ol class="flex flex-col gap-2">
        @for(MealLine line : page.menu())
          <li class="flex flex-wrap items-center gap-2 rounded-md border-2 border-outline bg-surface-raised px-3 py-2">
            <a href="${line.mealHref()}" class="min-w-0 break-words font-extrabold text-ink hover:text-brand">${line.name()}</a>
            @if(line.archived())
              @template.ui._badge(tone = "neutral", label = ctx.t("meal.archived"), icon = null)
            @endif
          </li>
        @endfor
      </ol>
    </section>

    @template.occasion._use_form(ctx = ctx, page = page)

    <div>
      @template.ui._confirm(id = "delete-occasion-template", title = ctx.t("occasion.template.delete.title", page.name()), message = ctx.t("occasion.template.delete.message"),
        cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("occasion.template.delete.submit"),
        attrs = Map.of("hx-post", page.deleteAction()),
        trigger = @`@template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("occasion.template.delete"), icon = IconName.TRASH, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
    </div>
  </section>
`)
