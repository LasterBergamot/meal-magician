@import hu.lasterbergamot.mealmagician.shared.web.ErrorView
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param ErrorView error
@param boolean standalone = false
<%-- The error box. Inside a full page (standalone) it is a centred card whose headline is the page's
     h1, with an alert icon, the sentence and one way out (the home link, an anchor styled like a
     primary ui/_button because that renders a <button>). As an htmx fragment it lands in the layout's
     #error-slot as a danger ui/_alert (its title is a paragraph there, the slot has no heading
     level to keep) with the same link as its action. No whimsy in errors (UI-D9). The keys come from
     AppErrorController; a test checks that they all exist. --%>
@if(standalone)
  <section class="mx-auto w-full max-w-md pt-2 md:pt-8">
    @template.ui._card(variant = "default", content = @`
      <div class="flex flex-col items-center gap-4 py-2 text-center md:py-4">
        <div class="flex size-20 items-center justify-center rounded-full border-2 border-outline bg-danger-soft text-danger-ink">
          @template.ui._icon(name = IconName.ALERT, size = 36, filled = false)
        </div>
        <h1 class="font-display text-[30px] font-semibold leading-9 text-ink">${ctx.t(error.titleKey())}</h1>
        <p class="max-w-[46ch] text-base leading-6 text-ink-muted">${ctx.t(error.messageKey())}</p>
        <a href="/"
           class="inline-flex min-h-11 items-center justify-center gap-2 whitespace-nowrap rounded-md border-2 border-outline bg-brand px-4 font-sans text-sm font-extrabold leading-5 text-on-brand shadow-sticker-1 transition-[translate,box-shadow,background-color] duration-[var(--mm-duration-fast)] hover:-translate-x-px hover:-translate-y-px hover:bg-brand-hover hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none">${ctx.t("error.home")}</a>
      </div>
    `)
  </section>
@else
  @template.ui._alert(kind = "danger", title = ctx.t(error.titleKey()), message = ctx.t(error.messageKey()), action = @`
    <a href="/"
       class="inline-flex min-h-11 items-center justify-center gap-2 whitespace-nowrap rounded-md border-2 border-outline bg-surface-raised px-4 font-sans text-sm font-extrabold leading-5 text-ink shadow-sticker-1 transition-[translate,box-shadow,background-color] duration-[var(--mm-duration-fast)] hover:-translate-x-px hover:-translate-y-px hover:bg-surface-sunken hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none">${ctx.t("error.home")}</a>
  `)
@endif
