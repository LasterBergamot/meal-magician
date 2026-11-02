@import hu.lasterbergamot.mealmagician.shared.web.Theme
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param Theme choice
@param String label
<%-- One button of layout/_theme_switch: the theme it chooses (data-theme-choice, read by the Alpine "theme"
     component), its translated label, and aria-pressed set from the cookie. aria-pressed gets the words
     "true" and "false" because JTE would turn a boolean into a bare or missing attribute. The pressed look
     comes from the aria-pressed variant. --%>
<button type="button" data-theme-choice="${choice.value()}" aria-pressed="${ctx.theme() == choice ? "true" : "false"}"
        x-on:click="choose" title="${label}"
        class="inline-flex min-h-11 min-w-11 flex-1 flex-col items-center justify-center gap-0.5 rounded-md border-2 border-transparent px-1 py-1 text-xs font-extrabold text-ink hover:bg-surface-sunken aria-pressed:border-outline aria-pressed:bg-brand aria-pressed:text-on-brand aria-pressed:shadow-sticker-1">
  <svg class="shrink-0" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
       stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
    @if(choice == Theme.LIGHT)
      <circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>
    @elseif(choice == Theme.DARK)
      <path d="M21 13a9 9 0 1 1-10-10 7 7 0 0 0 10 10z"/>
    @else
      <rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/>
    @endif
  </svg>
  <span class="truncate [.is-collapsed_&]:sr-only">${label}</span>
</button>
