@import hu.lasterbergamot.mealmagician.shared.web.NavDestination
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
<%-- The phone's bottom tab bar (plan-ui-redesign.md section 4): 64 px plus the device's safe-area
     inset, pinned to the bottom edge, hidden from md up. Only the destinations that exist are listed
     (NavDestination.visible). The current tab has a gold outlined pill behind its icon and a bolder
     label, and aria-current="page". The bar hides while a text field has the on-screen keyboard open
     (the "shell" component sets the class), so the keyboard does not cover the field's surroundings. --%>
<nav aria-label="${ctx.t("nav.primary")}" x-bind:class="tabbarClass"
     class="fixed inset-x-0 bottom-0 z-30 flex h-[calc(var(--mm-bottom-bar)_+_env(safe-area-inset-bottom))] items-stretch gap-1 border-t-2 border-outline bg-surface-raised px-2 pb-[env(safe-area-inset-bottom)] md:hidden">
  @for(NavDestination destination : NavDestination.visible())
    @if(destination == ctx.active())
      <a href="${destination.path()}" aria-current="page"
         class="flex min-w-0 flex-1 flex-col items-center justify-center gap-0.5 text-xs font-extrabold tracking-wide text-brand-ink">
        <span class="flex h-8 w-16 items-center justify-center rounded-full border-2 border-accent">
          @template.ui._icon(name = destination.icon(), size = 24, filled = false)
        </span>
        <span class="max-w-full truncate">${ctx.t(destination.labelKey())}</span>
      </a>
    @else
      <a href="${destination.path()}"
         class="flex min-w-0 flex-1 flex-col items-center justify-center gap-0.5 text-xs font-bold tracking-wide text-ink-muted">
        <span class="flex h-8 w-16 items-center justify-center rounded-full border-2 border-transparent">
          @template.ui._icon(name = destination.icon(), size = 24, filled = false)
        </span>
        <span class="max-w-full truncate">${ctx.t(destination.labelKey())}</span>
      </a>
    @endif
  @endfor
</nav>
