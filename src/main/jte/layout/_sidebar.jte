@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.NavDestination
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
<%-- The desktop sidebar (plan-ui-redesign.md section 4): 248 px, or 72 px icons-only when collapsed.
     It is hidden below md. Top to bottom: the brand, the household switcher, the destinations (the
     current one is a violet row), then at the bottom the language choice, the collapse button and
     sign-out. Collapsed, the labels become screen-reader-only (the "is-collapsed" class is set by the
     Alpine component "shell"), and each link keeps its name and a tooltip. --%>
<aside id="sidebar" x-bind:class="sidebarClass"
       class="sticky top-0 hidden h-dvh shrink-0 flex-col gap-4 overflow-hidden border-r-2 border-outline bg-surface-raised px-3 py-5 transition-[width] duration-[var(--mm-duration-base)] md:flex">
  <div class="flex items-center gap-3 px-1">
    <img src="/icons/favicon.svg" alt="" class="size-10 shrink-0">
    <span class="truncate font-display text-xl font-semibold text-ink [.is-collapsed_&]:sr-only">${ctx.t("app.name")}</span>
  </div>

  @template.layout._household_switcher(ctx = ctx, id = "household-sheet-desktop", compact = false)

  <nav aria-label="${ctx.t("nav.primary")}" class="flex flex-col gap-1">
    <p class="px-3 pb-1 text-xs font-bold tracking-wide text-ink-muted [.is-collapsed_&]:sr-only">${ctx.t("nav.section")}</p>
    @for(NavDestination destination : NavDestination.visible())
      @if(destination == ctx.active())
        <a href="${destination.path()}" aria-current="page" title="${ctx.t(destination.labelKey())}"
           class="flex min-h-11 items-center gap-3 rounded-md border-2 border-outline bg-brand px-3 text-sm font-extrabold text-on-brand shadow-sticker-1">
          @template.ui._icon(name = destination.icon(), size = 24, filled = false)
          <span class="truncate [.is-collapsed_&]:sr-only">${ctx.t(destination.labelKey())}</span>
        </a>
      @else
        <a href="${destination.path()}" title="${ctx.t(destination.labelKey())}"
           class="flex min-h-11 items-center gap-3 rounded-md border-2 border-transparent px-3 text-sm font-extrabold text-ink hover:bg-surface-sunken">
          @template.ui._icon(name = destination.icon(), size = 24, filled = false)
          <span class="truncate [.is-collapsed_&]:sr-only">${ctx.t(destination.labelKey())}</span>
        </a>
      @endif
    @endfor
  </nav>

  <div class="mt-auto flex flex-col gap-2">
    @if(ctx.admin())
      <a href="/admin/allowlist" title="${ctx.t("admin.link")}"
         class="flex min-h-11 items-center gap-3 rounded-md border-2 border-transparent px-3 text-sm font-extrabold text-ink hover:bg-surface-sunken">
        @template.ui._icon(name = IconName.USER, size = 24, filled = false)
        <span class="truncate [.is-collapsed_&]:sr-only">${ctx.t("admin.link")}</span>
      </a>
    @endif
    <nav class="flex flex-wrap gap-1" aria-label="${ctx.t("lang.label")}">
      @template.layout._lang_link(ctx = ctx, code = "hu", label = ctx.t("lang.hu.short"), name = ctx.t("lang.hu.name"))
      @template.layout._lang_link(ctx = ctx, code = "en", label = ctx.t("lang.en.short"), name = ctx.t("lang.en.name"))
    </nav>
    <button type="button" x-on:click="toggleCollapsed" x-bind:aria-expanded="expanded"
            class="flex min-h-11 items-center gap-3 rounded-md border-2 border-transparent px-3 text-left text-sm font-extrabold text-ink hover:bg-surface-sunken">
      @template.ui._icon(name = IconName.SIDEBAR, size = 24, filled = false)
      <span x-show="collapsed" class="truncate [.is-collapsed_&]:sr-only">${ctx.t("nav.expand")}</span>
      <span x-show="expanded" class="truncate [.is-collapsed_&]:sr-only">${ctx.t("nav.collapse")}</span>
    </button>
    @template.layout._sign_out(ctx = ctx)
  </div>
</aside>
