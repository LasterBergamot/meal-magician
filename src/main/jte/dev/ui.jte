@import java.util.List
@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
<%-- The style guide (plan-ui-redesign.md R2a), served only by the dev profile (DevUiController).
     Every icon and button state is on this page, so the browser tests and a reviewer can see them
     in one place, in whichever theme the browser uses. Each later component family has its own
     fragment here (dev/_content, _forms, _overlays): add a section there, never a second copy of
     the page. Text comes from ctx.t; the names shown are identifiers and come from the code. --%>
@template.layout.base(ctx = ctx, title = ctx.t("dev.ui.title"), content = @`
  <div class="flex flex-col gap-8">
    <header class="flex flex-col gap-2">
      <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("dev.ui.heading")}</h1>
      <p class="max-w-[720px] text-ink-muted">${ctx.t("dev.ui.lead")}</p>
    </header>

    <section class="flex flex-col gap-4 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2 md:p-6">
      <h2 class="font-display text-2xl font-semibold text-ink">${ctx.t("dev.ui.icons.heading")}</h2>
      <ul class="grid grid-cols-3 gap-3 sm:grid-cols-5 md:grid-cols-6">
        @for(IconName icon : IconName.values())
          <li class="flex flex-col items-center gap-2 rounded-md border-2 border-border bg-surface-sunken p-3 text-ink">
            @template.ui._icon(name = icon, size = 24, filled = false)
            <code class="break-all text-center text-xs text-ink-muted">${icon.id()}</code>
          </li>
        @endfor
      </ul>
      <div class="flex flex-wrap items-end gap-6 text-ink">
        @template.ui._icon(name = IconName.SPARKLE, size = 20, filled = false)
        @template.ui._icon(name = IconName.SPARKLE, size = 24, filled = false)
        @template.ui._icon(name = IconName.SPARKLE, size = 32, filled = false)
        <span class="text-accent">
          @template.ui._icon(name = IconName.SPARKLE, size = 32, filled = true)
        </span>
        <span class="text-brand">
          @template.ui._icon(name = IconName.WAND, size = 32, filled = false)
        </span>
      </div>
    </section>

    <section class="flex flex-col gap-6 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2 md:p-6">
      <h2 class="font-display text-2xl font-semibold text-ink">${ctx.t("dev.ui.buttons.heading")}</h2>
      @for(String variant : List.of("primary", "secondary", "ghost", "danger"))
        <div class="flex flex-col gap-3">
          <code class="text-sm text-ink-muted">${variant}</code>
          <div class="flex flex-wrap items-end gap-3">
            @for(String size : List.of("sm", "md", "lg", "icon"))
              @template.ui._button(variant = variant, size = size, type = "button", label = variant.equals("danger") ? ctx.t("ui.demo.delete") : ctx.t("ui.demo.save"), icon = size.equals("icon") ? (variant.equals("danger") ? IconName.TRASH : IconName.PLUS) : null, busy = false, disabled = false, attrs = Map.of())
            @endfor
          </div>
          <div class="flex flex-wrap items-end gap-4">
            <div class="flex flex-col items-start gap-1">
              <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.state.default")}</span>
              @template.ui._button(variant = variant, size = "md", type = "button", label = variant.equals("danger") ? ctx.t("ui.demo.delete") : ctx.t("ui.demo.save"), icon = null, busy = false, disabled = false, attrs = Map.of())
            </div>
            <div class="flex flex-col items-start gap-1">
              <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.state.icon")}</span>
              @template.ui._button(variant = variant, size = "md", type = "button", label = variant.equals("danger") ? ctx.t("ui.demo.delete") : ctx.t("ui.demo.save"), icon = variant.equals("danger") ? IconName.TRASH : IconName.PLUS, busy = false, disabled = false, attrs = Map.of())
            </div>
            <div class="flex flex-col items-start gap-1">
              <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.state.disabled")}</span>
              @template.ui._button(variant = variant, size = "md", type = "button", label = variant.equals("danger") ? ctx.t("ui.demo.delete") : ctx.t("ui.demo.save"), icon = null, busy = false, disabled = true, attrs = Map.of())
            </div>
            <div class="flex flex-col items-start gap-1">
              <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.state.busy")}</span>
              @template.ui._button(variant = variant, size = "md", type = "button", label = variant.equals("danger") ? ctx.t("ui.demo.delete") : ctx.t("ui.demo.save"), icon = null, busy = true, disabled = false, attrs = Map.of())
            </div>
          </div>
        </div>
      @endfor
    </section>

    <section class="flex flex-col gap-4 rounded-lg border-2 border-outline bg-surface-raised p-4 shadow-sticker-2 md:p-6">
      <h2 class="font-display text-2xl font-semibold text-ink">${ctx.t("dev.ui.fab.heading")}</h2>
      <div class="flex flex-col items-start gap-1">
        <span class="text-xs font-bold text-ink-muted">${ctx.t("dev.ui.fab.inline")}</span>
        @template.ui._fab(icon = IconName.PLUS, label = ctx.t("ui.demo.add"), type = "button", floating = false, attrs = Map.of())
      </div>
    </section>

    @template.dev._content(ctx = ctx)
    @template.dev._forms(ctx = ctx)
    @template.dev._overlays(ctx = ctx)

    <p class="text-sm text-ink-muted">${ctx.t("dev.ui.fab.floating")}</p>
  </div>
  @template.ui._fab(icon = IconName.PLUS, label = ctx.t("ui.demo.add"), type = "button", floating = true, attrs = Map.of())
`)
