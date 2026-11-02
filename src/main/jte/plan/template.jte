@import hu.lasterbergamot.mealmagician.planning.internal.TemplateDayLines
@import hu.lasterbergamot.mealmagician.planning.internal.TemplateLine
@import hu.lasterbergamot.mealmagician.planning.internal.TemplatePage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext
@import java.util.Map

@param ViewContext ctx
@param TemplatePage page
<%-- One template (plan-ui-redesign.md section 5): its name and scope, its meals per day in plan order
     (a week template names the weekdays), and a hint that it is applied from the plan. Below are the
     rename card and the delete button, which opens a confirm dialog. #plan-conflict is where a stale
     delete's 409 lands; it is empty and hidden otherwise. The confirm is outside every swapped region,
     so an htmx swap cannot take it away. --%>
@template.layout.base(ctx = ctx, title = page.name(), content = @`
  <section class="flex flex-col gap-6">
    <div>
      @template.meal._link_button(variant = "ghost", href = "/plan/templates", label = ctx.t("plan.templates.back"), icon = IconName.LEFT)
    </div>

    <div id="plan-conflict" class="empty:hidden">@if(page.conflict())@template.plan._template_conflict(ctx = ctx, reloadHref = page.href())@endif</div>

    <div class="flex flex-wrap items-center gap-3">
      <h1 class="min-w-0 break-words font-display text-3xl font-semibold text-ink">${page.name()}</h1>
      @template.ui._badge(tone = "brand", label = ctx.t(page.scopeKey()), icon = null)
    </div>

    @template.ui._card(variant = "default", content = @`
      @for(TemplateDayLines day : page.days())
        <div class="flex flex-col gap-2">
          <h2 class="font-display text-xl font-semibold text-ink">${day.label()}</h2>
          <ul class="flex flex-col gap-3">
            @for(TemplateLine line : day.lines())
              <li class="flex flex-col gap-1">
                <div class="flex flex-wrap items-center gap-2">
                  @template.ui._slot_chip(slot = line.slotName(), label = ctx.t(line.labelKey()))
                  <span class="min-w-0 break-words font-bold text-ink">${line.mealName()}</span>
                  @if(line.archived())
                    @template.ui._badge(tone = "neutral", label = ctx.t("meal.archived"), icon = null)
                  @endif
                </div>
                @if(line.note() != null)
                  <p class="whitespace-pre-line break-words text-sm text-ink-muted">${line.note()}</p>
                @endif
              </li>
            @endfor
          </ul>
        </div>
      @endfor
    `)

    <p class="text-sm text-ink-muted">${ctx.t("plan.templates.apply.hint")}</p>

    @template.plan._template_rename(ctx = ctx, page = page)

    <div>
      @template.ui._confirm(id = "delete-template", title = ctx.t("plan.templates.delete.title", page.name()), message = ctx.t("plan.templates.delete.message"),
        cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("plan.templates.delete.submit"),
        attrs = Map.of("hx-post", page.deleteAction()),
        trigger = @`@template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("plan.templates.delete"), icon = IconName.TRASH, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
    </div>
  </section>
`)
