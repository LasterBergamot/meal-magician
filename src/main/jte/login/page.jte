@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param boolean failed
@param boolean signedOut
<%-- The sign-in page (plan-ui-redesign.md section 8): anonymous, so it sits outside the app shell
     (layout/base draws the plain header with the language switch). One card: the wizard brand mark
     with two sparkles (the one hero moment, so sparkles are fine here), the wordmark as the h1, a
     tagline, one large primary link to Google and a quiet line that only invited people get in. The
     button is a GET link, so form-action 'self' is not involved; it is an anchor styled like
     ui/_button lg primary (ui/_button renders a <button>, which cannot navigate). A failed or ended
     sign-in shows an alert above the button, with an icon and a title as well as a colour. --%>
@template.layout.base(ctx = ctx, title = ctx.t("login.title"), content = @`
  <section class="mx-auto w-full max-w-md pt-2 md:pt-8">
    @template.ui._card(variant = "default", content = @`
      <div class="flex flex-col items-center gap-4 py-2 text-center md:py-4">
        <div class="relative">
          <img src="/icons/favicon.svg" alt="" class="size-24 rounded-xl">
          <span class="absolute -top-3 -right-4 text-accent">
            @template.ui._icon(name = IconName.SPARKLE, size = 28, filled = true)
          </span>
          <span class="absolute -bottom-1 -left-4 text-accent">
            @template.ui._icon(name = IconName.SPARKLE, size = 18, filled = true)
          </span>
        </div>
        <h1 class="font-display text-[32px] font-bold leading-9 text-ink md:text-[40px] md:leading-[44px]">${ctx.t("app.name")}</h1>
        <p class="text-base leading-6 text-ink">${ctx.t("login.tagline")}</p>
        @if(failed)
          <div class="w-full text-left">
            @template.ui._alert(kind = "danger", title = ctx.t("login.error.title"), message = ctx.t("login.error"), action = null)
          </div>
        @endif
        @if(signedOut)
          <div class="w-full text-left">
            @template.ui._alert(kind = "success", title = ctx.t("login.signedOut.title"), message = ctx.t("login.signedOut"), action = null)
          </div>
        @endif
        <a href="/oauth2/authorization/google"
           class="inline-flex min-h-[54px] w-full items-center justify-center gap-2 whitespace-nowrap rounded-md border-2 border-outline bg-brand px-5 font-sans text-base font-extrabold leading-5 text-on-brand shadow-sticker-1 transition-[translate,box-shadow,background-color] duration-[var(--mm-duration-fast)] hover:-translate-x-px hover:-translate-y-px hover:bg-brand-hover hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none">${ctx.t("login.google")}</a>
        <p class="text-sm leading-5 text-ink-muted">${ctx.t("login.lead")}</p>
      </div>
    `)
  </section>
`)
