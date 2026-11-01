@import java.util.List
@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.FormOption
@import hu.lasterbergamot.mealmagician.shared.web.SegmentOption
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
<%-- Section of the style guide (/dev/ui) for the form components (R2c): the text field, the select,
     the search field and the segmented control, each in every state. The 422 state is a field and a
     select with an error and the typed value kept, as a rejected form returns them. The segmented
     control has no target here, so its segments are plain links and a click does not swap this
     page. Text comes from ctx.t; the names in the code are identifiers. --%>
<section id="dev-forms" class="flex flex-col gap-8 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2 md:p-6">
  <header class="flex flex-col gap-2">
    <h2 class="font-display text-2xl font-semibold text-ink">${ctx.t("dev.forms.heading")}</h2>
    <p class="max-w-[720px] text-ink-muted">${ctx.t("dev.forms.lead")}</p>
  </header>

  <div class="flex flex-col gap-4">
    <h3 class="font-display text-xl font-semibold text-ink">${ctx.t("dev.forms.field.heading")}</h3>
    <div class="grid gap-6 md:grid-cols-2">
      <div class="flex flex-col gap-2">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.state.default")}</span>
        @template.ui._field(id = "demo-name-default", name = "name-default", label = ctx.t("dev.forms.field.name"), type = "text", value = "", hint = null, error = null, required = false, disabled = false, attrs = Map.of())
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.forms.state.hint")}</span>
        @template.ui._field(id = "demo-email-hint", name = "email-hint", label = ctx.t("dev.forms.field.email"), type = "email", value = "", hint = ctx.t("dev.forms.field.hint"), error = null, required = false, disabled = false, attrs = Map.of("autocomplete", "email"))
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.forms.state.error")}</span>
        @template.ui._field(id = "demo-email-error", name = "email-error", label = ctx.t("dev.forms.field.email"), type = "email", value = "anna@example", hint = null, error = ctx.t("dev.forms.field.error"), required = true, disabled = false, attrs = Map.of("autocomplete", "email"))
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.state.disabled")}</span>
        @template.ui._field(id = "demo-name-disabled", name = "name-disabled", label = ctx.t("dev.forms.field.name"), type = "text", value = "Anna", hint = null, error = null, required = false, disabled = true, attrs = Map.of())
      </div>
    </div>
  </div>

  <div class="flex flex-col gap-4">
    <h3 class="font-display text-xl font-semibold text-ink">${ctx.t("dev.forms.select.heading")}</h3>
    <div class="grid gap-6 md:grid-cols-2">
      <div class="flex flex-col gap-2">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.state.default")}</span>
        @template.ui._select(id = "demo-period-default", name = "period-default", label = ctx.t("dev.forms.select.label"), options = List.of(new FormOption("day", ctx.t("dev.forms.period.day")), new FormOption("week", ctx.t("dev.forms.period.week")), new FormOption("month", ctx.t("dev.forms.period.month"))), selected = "week", hint = null, error = null, disabled = false, attrs = Map.of())
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.forms.state.error")}</span>
        @template.ui._select(id = "demo-period-error", name = "period-error", label = ctx.t("dev.forms.select.label"), options = List.of(new FormOption("day", ctx.t("dev.forms.period.day")), new FormOption("week", ctx.t("dev.forms.period.week")), new FormOption("month", ctx.t("dev.forms.period.month"))), selected = "day", hint = null, error = ctx.t("dev.forms.select.error"), disabled = false, attrs = Map.of())
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.state.disabled")}</span>
        @template.ui._select(id = "demo-period-disabled", name = "period-disabled", label = ctx.t("dev.forms.select.label"), options = List.of(new FormOption("day", ctx.t("dev.forms.period.day")), new FormOption("week", ctx.t("dev.forms.period.week")), new FormOption("month", ctx.t("dev.forms.period.month"))), selected = "month", hint = null, error = null, disabled = true, attrs = Map.of())
      </div>
    </div>
  </div>

  <div class="flex flex-col gap-4">
    <h3 class="font-display text-xl font-semibold text-ink">${ctx.t("dev.forms.search.heading")}</h3>
    <div class="grid gap-6 md:grid-cols-2">
      <div class="flex flex-col gap-2">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.state.default")}</span>
        @template.ui._search(id = "demo-search-default", name = "q-default", label = ctx.t("dev.forms.search.label"), value = "", placeholder = ctx.t("dev.forms.search.placeholder"), disabled = false, attrs = Map.of())
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.forms.state.value")}</span>
        @template.ui._search(id = "demo-search-value", name = "q-value", label = ctx.t("dev.forms.search.label"), value = ctx.t("dev.forms.search.value"), placeholder = ctx.t("dev.forms.search.placeholder"), disabled = false, attrs = Map.of())
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.state.disabled")}</span>
        @template.ui._search(id = "demo-search-disabled", name = "q-disabled", label = ctx.t("dev.forms.search.label"), value = "", placeholder = ctx.t("dev.forms.search.placeholder"), disabled = true, attrs = Map.of())
      </div>
    </div>
  </div>

  <div class="flex flex-col gap-4">
    <h3 class="font-display text-xl font-semibold text-ink">${ctx.t("dev.forms.segmented.heading")}</h3>
    <div class="flex flex-col gap-2">
      <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.forms.segmented.count", 2)}</span>
      @template.ui._segmented(label = ctx.t("dev.forms.segmented.label"), options = List.of(new SegmentOption("meals", ctx.t("dev.forms.view.meals"), "#dev-forms"), new SegmentOption("occasions", ctx.t("dev.forms.view.occasions"), "#dev-forms")), selected = "meals", target = null)
    </div>
    <div class="flex flex-col gap-2">
      <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.forms.segmented.count", 3)}</span>
      @template.ui._segmented(label = ctx.t("dev.forms.segmented.label"), options = List.of(new SegmentOption("day", ctx.t("dev.forms.period.day"), "#dev-forms"), new SegmentOption("week", ctx.t("dev.forms.period.week"), "#dev-forms"), new SegmentOption("month", ctx.t("dev.forms.period.month"), "#dev-forms")), selected = "week", target = null)
    </div>
    <div class="flex flex-col gap-2">
      <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.forms.segmented.count", 4)}</span>
      @template.ui._segmented(label = ctx.t("dev.forms.segmented.label"), options = List.of(new SegmentOption("day", ctx.t("dev.forms.period.day"), "#dev-forms"), new SegmentOption("week", ctx.t("dev.forms.period.week"), "#dev-forms"), new SegmentOption("month", ctx.t("dev.forms.period.month"), "#dev-forms"), new SegmentOption("year", ctx.t("dev.forms.period.year"), "#dev-forms")), selected = "month", target = null)
    </div>
  </div>
</section>
