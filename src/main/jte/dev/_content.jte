@import java.util.List
@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
<%-- Section of the style guide (/dev/ui) for the content and status components (R2b): badge, chip,
     slot chip, slot dot, card, row, avatar, alert, empty state and skeleton rows. Every state is
     here; the page shows the theme the browser uses, so check both (dev tools: data-theme on html).
     Texts come from ctx.t. Names in code tags are identifiers from the code (tone, variant, slot),
     so they are not translated. --%>
<section class="flex flex-col gap-6 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2 md:p-6">
  <h2 class="font-display text-2xl font-semibold text-ink">${ctx.t("dev.content.badges.heading")}</h2>
  <div class="flex flex-col gap-3">
    @for(String tone : List.of("neutral", "brand", "accent", "success", "warning", "danger", "info"))
      <div class="flex flex-wrap items-center gap-3">
        <code class="w-24 text-sm text-ink-muted">${tone}</code>
        @template.ui._badge(tone = tone, label = ctx.t("dev.content.badge.sample"), icon = null)
      </div>
    @endfor
  </div>
  <div class="flex flex-wrap items-center gap-3">
    @template.ui._badge(tone = "brand", label = ctx.t("household.role.owner"), icon = null)
    @template.ui._badge(tone = "neutral", label = ctx.t("household.role.member"), icon = null)
    @template.ui._badge(tone = "brand", label = ctx.t("household.current"), icon = IconName.CHECK)
    @template.ui._badge(tone = "accent", label = ctx.t("dev.content.badge.today"), icon = IconName.SPARKLE)
  </div>
  <div class="flex flex-wrap items-end gap-4">
    <div class="flex flex-col items-start gap-1">
      <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.content.state.unselected")}</span>
      @template.ui._chip(label = ctx.t("dev.content.chip.quick"), selected = false, attrs = Map.of())
    </div>
    <div class="flex flex-col items-start gap-1">
      <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.content.state.selected")}</span>
      @template.ui._chip(label = ctx.t("dev.content.chip.vegetarian"), selected = true, attrs = Map.of())
    </div>
    <div class="flex flex-col items-start gap-1">
      <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.content.state.unselected")}</span>
      @template.ui._chip(label = ctx.t("dev.content.chip.sweet"), selected = false, attrs = Map.of())
    </div>
  </div>
</section>

<section class="flex flex-col gap-6 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2 md:p-6">
  <h2 class="font-display text-2xl font-semibold text-ink">${ctx.t("dev.content.slots.heading")}</h2>
  <div class="flex flex-wrap gap-3">
    @template.ui._slot_chip(slot = "BREAKFAST", label = ctx.t("slot.breakfast"))
    @template.ui._slot_chip(slot = "LUNCH", label = ctx.t("slot.lunch"))
    @template.ui._slot_chip(slot = "DINNER", label = ctx.t("slot.dinner"))
    @template.ui._slot_chip(slot = "SNACK", label = ctx.t("slot.snack"))
  </div>
  <div class="flex flex-wrap gap-4">
    <span class="flex items-center gap-2 text-sm font-bold text-ink">
      @template.ui._slot_dot(slot = "BREAKFAST", label = "", size = "sm")
      ${ctx.t("slot.breakfast")}
    </span>
    <span class="flex items-center gap-2 text-sm font-bold text-ink">
      @template.ui._slot_dot(slot = "LUNCH", label = "", size = "sm")
      ${ctx.t("slot.lunch")}
    </span>
    <span class="flex items-center gap-2 text-sm font-bold text-ink">
      @template.ui._slot_dot(slot = "DINNER", label = "", size = "sm")
      ${ctx.t("slot.dinner")}
    </span>
    <span class="flex items-center gap-2 text-sm font-bold text-ink">
      @template.ui._slot_dot(slot = "SNACK", label = "", size = "sm")
      ${ctx.t("slot.snack")}
    </span>
  </div>
  <div class="flex flex-wrap items-center gap-4">
    @template.ui._slot_dot(slot = "BREAKFAST", label = ctx.t("slot.breakfast"), size = "lg")
    @template.ui._slot_dot(slot = "LUNCH", label = ctx.t("slot.lunch"), size = "lg")
    @template.ui._slot_dot(slot = "DINNER", label = ctx.t("slot.dinner"), size = "lg")
    @template.ui._slot_dot(slot = "SNACK", label = ctx.t("slot.snack"), size = "lg")
  </div>
</section>

<section class="flex flex-col gap-6 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2 md:p-6">
  <h2 class="font-display text-2xl font-semibold text-ink">${ctx.t("dev.content.cards.heading")}</h2>
  <div class="grid gap-4 md:grid-cols-3">
    @for(String variant : List.of("default", "accent", "selected"))
      <div class="flex flex-col gap-2">
        <code class="text-sm text-ink-muted">${variant}</code>
        @template.ui._card(variant = variant, content = @`
          <p class="font-sans text-lg font-extrabold leading-6 text-ink">${ctx.t("dev.content.card.title")}</p>
          <p class="text-sm font-medium leading-5 text-ink">${ctx.t("dev.content.card.text")}</p>
          <a href="/dev/ui" class="font-sans text-sm font-extrabold text-ink underline decoration-2 underline-offset-4 after:absolute after:inset-0 after:rounded-lg">${ctx.t("dev.content.card.link")}</a>
        `)
      </div>
    @endfor
  </div>
  <div class="flex flex-col gap-2">
    @template.ui._card(variant = "default", content = @`
      <ul class="flex flex-col">
        @template.ui._row(selected = false, content = @`
          @template.ui._avatar(initials = ctx.t("dev.content.sample.anna.initials"), size = "md")
          <div class="flex min-w-0 flex-1 flex-col">
            <span class="font-sans text-base font-extrabold leading-6 text-ink">${ctx.t("dev.content.sample.anna")}</span>
            <span class="text-sm font-medium leading-5 text-ink-muted">${ctx.t("dev.content.row.caption")}</span>
          </div>
          @template.ui._badge(tone = "brand", label = ctx.t("household.role.owner"), icon = null)
        `)
        @template.ui._row(selected = true, content = @`
          @template.ui._avatar(initials = ctx.t("dev.content.sample.bela.initials"), size = "md")
          <div class="flex min-w-0 flex-1 flex-col">
            <span class="font-sans text-base font-extrabold leading-6 text-ink">${ctx.t("dev.content.sample.bela")}</span>
            <span class="text-sm font-medium leading-5 text-ink-muted">${ctx.t("dev.content.row.caption")}</span>
          </div>
          @template.ui._badge(tone = "brand", label = ctx.t("household.current"), icon = IconName.CHECK)
        `)
        @template.ui._row(selected = false, content = @`
          @template.ui._avatar(initials = ctx.t("dev.content.sample.bela.initials"), size = "sm")
          <div class="flex min-w-0 flex-1 flex-col">
            <span class="font-sans text-base font-extrabold leading-6 text-ink">${ctx.t("dev.content.sample.bela")}</span>
            <span class="text-sm font-medium leading-5 text-ink-muted">${ctx.t("dev.content.row.caption")}</span>
          </div>
          @template.ui._badge(tone = "neutral", label = ctx.t("household.role.member"), icon = null)
        `)
      </ul>
    `)
  </div>
</section>

<section class="flex flex-col gap-6 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2 md:p-6">
  <h2 class="font-display text-2xl font-semibold text-ink">${ctx.t("dev.content.alerts.heading")}</h2>
  <div class="flex flex-col gap-3">
    @template.ui._alert(kind = "success", title = ctx.t("dev.content.alert.success.title"), message = ctx.t("dev.content.alert.success.message"), action = null)
    @template.ui._alert(kind = "info", title = ctx.t("dev.content.alert.info.title"), message = ctx.t("dev.content.alert.info.message"), action = null)
    @template.ui._alert(kind = "warning", title = ctx.t("dev.content.alert.warning.title"), message = ctx.t("household.conflict"), action = @`
      @template.ui._button(variant = "secondary", size = "sm", type = "button", label = ctx.t("dev.content.reload"), icon = null, busy = false, disabled = false, attrs = Map.of())
    `)
    @template.ui._alert(kind = "danger", title = ctx.t("dev.content.alert.danger.title"), message = ctx.t("dev.content.alert.danger.message"), action = null)
  </div>
</section>

<section class="flex flex-col gap-6 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2 md:p-6">
  <h2 class="font-display text-2xl font-semibold text-ink">${ctx.t("dev.content.states.heading")}</h2>
  @template.ui._empty(icon = IconName.MEALS, heading = ctx.t("dev.content.empty.heading"), message = ctx.t("dev.content.empty.message"), action = @`
    @template.ui._button(variant = "primary", size = "md", type = "button", label = ctx.t("ui.demo.add"), icon = IconName.PLUS, busy = false, disabled = false, attrs = Map.of())
    @template.ui._button(variant = "ghost", size = "md", type = "button", label = ctx.t("dev.content.empty.ghost"), icon = null, busy = false, disabled = false, attrs = Map.of())
  `)
  @template.ui._skeleton_rows(rows = 3, label = ctx.t("ui.skeleton.loading"))
</section>
