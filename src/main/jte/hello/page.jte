@import hu.lasterbergamot.mealmagician.shared.web.HelloView
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HelloView hello
<%-- The home page (until the week planner exists, plan-ui-redesign.md section 4). It renders inside
     the app shell, so it has no header of its own: no <header> element either, because the shell's
     top bar is the only one (AppShellE2ETest counts them). The h1 text is inline, with no
     whitespace inside the tag, because SignInE2ETest compares its text exactly. The page is an h1
     with one sparkle (a hero moment), the lead sentence and a card with today's date and the refresh
     button (hello/_greeting, the part htmx replaces). --%>
@template.layout.base(ctx = ctx, title = ctx.t("hello.title"), content = @`
  <div class="mx-auto flex w-full max-w-[var(--mm-reading)] flex-col gap-6">
    <div class="flex flex-col gap-2">
      <div class="flex items-center gap-2">
        <h1 class="font-display text-3xl font-semibold leading-9 text-ink">${ctx.t("hello.heading")}</h1>
        <span class="text-accent">
          @template.ui._icon(name = IconName.SPARKLE, size = 28, filled = true)
        </span>
      </div>
      <p class="text-base leading-6 text-ink-muted">${ctx.t("hello.lead")}</p>
    </div>
    @template.ui._card(variant = "default", content = @`
      @template.hello._greeting(ctx = ctx, hello = hello)
    `)
  </div>
`)
