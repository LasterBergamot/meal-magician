@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
<%-- The phone's top bar (plan-ui-redesign.md section 4), hidden from md up where the sidebar carries
     the same things. Left: the brand mark and the household switcher pill. Right: the avatar, which
     opens a small menu with the person's name, the language choice and sign-out. The menu closes on
     Escape and on a click outside it (the "menu" component in app.js). --%>
<header class="sticky top-0 z-20 flex items-center gap-2 border-b-2 border-outline bg-surface-raised px-4 py-2 md:hidden">
  <img src="/icons/favicon.svg" alt="" class="size-9 shrink-0">
  <div class="flex min-w-0 flex-1">
    @template.layout._household_switcher(ctx = ctx, id = "household-sheet-phone", compact = true)
  </div>
  <div x-data="menu" x-on:keydown.escape.window="close" x-on:click.outside="close" class="relative shrink-0">
    <button type="button" aria-haspopup="menu" x-bind:aria-expanded="open" x-on:click="toggle"
            class="inline-flex size-11 items-center justify-center rounded-full">
      <span class="sr-only">${ctx.t("nav.account")}</span>
      @template.ui._avatar(initials = ctx.user().initials(), size = "md", tone = "soft")
    </button>
    <div x-show="open" x-cloak
         class="absolute right-0 top-full z-30 mt-2 flex w-64 max-w-[calc(100vw-2rem)] flex-col gap-1 rounded-lg border-2 border-outline bg-surface-raised p-2 shadow-sticker-2">
      <p class="truncate px-3 py-2 text-sm font-extrabold text-ink">${ctx.user().displayName()}</p>
      @if(ctx.admin())
        <a href="/admin/allowlist"
           class="flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-extrabold text-ink hover:bg-surface-sunken">
          @template.ui._icon(name = IconName.USER, size = 20, filled = false)
          <span>${ctx.t("admin.link")}</span>
        </a>
      @endif
      <nav class="flex gap-1 px-1" aria-label="${ctx.t("lang.label")}">
        @template.layout._lang_link(ctx = ctx, code = "hu", label = ctx.t("lang.hu.short"), name = ctx.t("lang.hu.name"))
        @template.layout._lang_link(ctx = ctx, code = "en", label = ctx.t("lang.en.short"), name = ctx.t("lang.en.name"))
      </nav>
      @template.layout._sign_out(ctx = ctx)
    </div>
  </div>
</header>
