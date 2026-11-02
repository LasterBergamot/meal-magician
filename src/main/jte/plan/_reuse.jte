@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.PlanTemplateSummary
@import hu.lasterbergamot.mealmagician.planning.internal.ReusePage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param ReusePage page
<%-- The content of the reuse sheet (and of the no-JavaScript page) for one day or one week: what is
     planned, a copy form (Add merges; Replace is a ui/_confirm), the save-as-template form and the
     templates that can be applied. The Replace confirm posts the copy form's fields through
     hx-include and therefore sits after the form, never inside it: a dialog in a swapped region
     would disappear with the swap. Each template's Replace confirm carries the date in its query,
     because its button has no form around it. --%>
<div class="flex flex-col gap-5">
  <div class="flex flex-col gap-1">
    <p class="font-bold text-ink">${page.heading()}</p>
    @if(page.range() != null)
      <p class="text-sm text-ink-muted">${page.range()}</p>
    @endif
    <p class="text-sm text-ink-muted">${ctx.t("plan.reuse.count", String.valueOf(page.mealCount()))}</p>
  </div>

  @if(page.empty())
    <p class="text-sm text-ink-muted">${ctx.t(page.emptyKey())}</p>
  @else
    <div class="flex flex-col gap-3 border-t-2 border-dashed border-border pt-4">
      <h3 class="font-display text-xl font-semibold text-ink">${ctx.t(page.copyHeadingKey())}</h3>
      <p class="text-sm text-ink-muted">${ctx.t("plan.reuse.copy.hint")}</p>
      <form id="copy-form" method="post" action="${page.copyAction("MERGE")}" hx-post="${page.copyAction("MERGE")}" class="flex flex-col gap-3">
        @if(ctx.csrf() != null)
          <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
        @endif
        <input type="hidden" name="from" value="${page.fromValue()}">
        @if(page.forDay())
          @template.ui._field(id = "copy-to", name = "to", label = ctx.t("plan.reuse.copy.day.to"), type = "date", value = page.copyTarget(), hint = null, error = null, required = true, disabled = false, attrs = Map.of())
        @else
          @template.ui._select(id = "copy-to", name = "to", label = ctx.t("plan.reuse.copy.week.to"), options = page.weekOptions(), selected = page.copyTarget(), hint = null, error = null, disabled = false, attrs = Map.of())
        @endif
        <div>
          @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("plan.reuse.copy.merge"), icon = IconName.COPY, busy = false, disabled = false, attrs = Map.of())
        </div>
      </form>
      <div>
        @template.ui._confirm(id = "copy-replace", title = ctx.t(page.replaceTitleKey()), message = ctx.t(page.replaceMessageKey()),
          cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("plan.reuse.replace.submit"),
          attrs = Map.of("hx-post", page.copyAction("REPLACE"), "hx-include", "#copy-form"),
          trigger = @`@template.ui._button(variant = "danger", size = "md", type = "button", label = ctx.t("plan.reuse.copy.replace"), icon = null, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
      </div>
    </div>
    @template.plan._reuse_save(ctx = ctx, page = page)
  @endif

  <div class="flex flex-col gap-3 border-t-2 border-dashed border-border pt-4">
    <h3 class="font-display text-xl font-semibold text-ink">${ctx.t(page.applyHeadingKey())}</h3>
    @if(page.templates().isEmpty())
      <p class="text-sm text-ink-muted">${ctx.t(page.applyNoneKey())}</p>
      <div>
        @template.meal._link_button(variant = "ghost", href = "/plan/templates", label = ctx.t("plan.reuse.templates"), icon = IconName.WAND)
      </div>
    @else
      <ul class="flex flex-col gap-2">
        @for(PlanTemplateSummary t : page.templates())
          <li data-template-id="${t.id()}" class="flex flex-wrap items-center justify-between gap-2 rounded-md border-2 border-outline bg-surface-raised px-3 py-2">
            <span class="min-w-0 break-words font-extrabold text-ink">${t.name()}</span>
            <span class="text-sm text-ink-muted">${ctx.t("plan.reuse.apply.meals", String.valueOf(t.itemCount()))}</span>
            <form method="post" action="${page.applyAction(t.id(), "MERGE")}" hx-post="${page.applyAction(t.id(), "MERGE")}" class="contents">
              @if(ctx.csrf() != null)
                <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
              @endif
              <input type="hidden" name="date" value="${page.dateValue()}">
              @template.ui._button(variant = "secondary", size = "sm", type = "submit", label = ctx.t("plan.reuse.apply.merge"), icon = null, busy = false, disabled = false, attrs = Map.of())
            </form>
            @template.ui._confirm(id = "apply-replace-" + t.id(), title = ctx.t(page.applyReplaceTitleKey(), t.name()), message = ctx.t(page.replaceMessageKey()),
              cancelLabel = ctx.t("confirm.cancel"), confirmLabel = ctx.t("plan.reuse.replace.submit"),
              attrs = Map.of("hx-post", page.applyReplaceAction(t.id())),
              trigger = @`@template.ui._button(variant = "danger", size = "sm", type = "button", label = ctx.t("plan.reuse.apply.replace"), icon = null, busy = false, disabled = false, attrs = Map.of("x-on:click", "show"))`)
          </li>
        @endfor
      </ul>
    @endif
  </div>
</div>
