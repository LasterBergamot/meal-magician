@import java.util.Map
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionCard
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionTemplateRow
@import hu.lasterbergamot.mealmagician.planning.internal.OccasionsPage
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionsPage page
<%-- The Occasions list (#112). The next occasion is the gold-soft card (accent), the later ones and
     the past ones (newest first) are rows in cards. An empty household gets the empty state with
     one button to add the first occasion. --%>
@template.layout.base(ctx = ctx, title = ctx.t("occasion.title"), content = @`
  <section class="flex flex-col gap-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("occasion.heading")}</h1>
      @if(!page.empty())
        @template.meal._link_button(variant = "primary", href = "/occasions/new", label = ctx.t("occasion.add"), icon = IconName.PLUS)
      @endif
    </div>

    @if(page.empty())
      @template.ui._empty(icon = IconName.GIFT, heading = ctx.t("occasion.empty.heading"), message = ctx.t("occasion.empty.message"), action = @`
        @template.meal._link_button(variant = "primary", href = "/occasions/new", label = ctx.t("occasion.add"), icon = IconName.PLUS)
      `)
    @else
      @if(page.next() != null)
        <section aria-labelledby="occasion-next-title" class="flex flex-col gap-3">
          <h2 id="occasion-next-title" class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("occasion.next.heading")}</h2>
          @template.ui._card(variant = "accent", content = @`
            <a href="${page.next().href()}" data-occasion-id="${page.next().id()}" class="font-display text-2xl font-semibold text-ink after:absolute after:inset-0 after:rounded-lg">${page.next().name()}</a>
            <p class="font-bold text-ink">${page.next().dateLabel()}</p>
            <div class="flex flex-wrap items-center gap-2">
              @template.ui._badge(tone = "accent", label = ctx.t(page.next().typeKey()), icon = null)
              @if(page.next().today())
                @template.ui._badge(tone = "accent", label = ctx.t("occasion.today"), icon = IconName.SPARKLE)
              @endif
            </div>
            <p class="text-sm text-ink">${ctx.t("occasion.meals", String.valueOf(page.next().mealCount()))}</p>
          `)
        </section>
      @endif

      @if(!page.upcoming().isEmpty())
        <section aria-labelledby="occasion-upcoming-title" class="flex flex-col gap-3">
          <h2 id="occasion-upcoming-title" class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("occasion.upcoming.heading")}</h2>
          @template.ui._card(variant = "default", content = @`
            <ul class="-mx-3 flex flex-col">
              @for(OccasionCard card : page.upcoming())
                @template.occasion._row(ctx = ctx, card = card)
              @endfor
            </ul>
          `)
        </section>
      @endif

      @if(!page.past().isEmpty())
        <section aria-labelledby="occasion-past-title" class="flex flex-col gap-3">
          <h2 id="occasion-past-title" class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("occasion.past.heading")}</h2>
          @template.ui._card(variant = "default", content = @`
            <ul class="-mx-3 flex flex-col">
              @for(OccasionCard card : page.past())
                @template.occasion._row(ctx = ctx, card = card)
              @endfor
            </ul>
          `)
        </section>
      @endif
    @endif

    @if(!page.templates().isEmpty())
      <section aria-labelledby="occasion-templates-title" class="flex flex-col gap-3">
        <h2 id="occasion-templates-title" class="font-display text-[22px] font-semibold leading-7 text-ink">${ctx.t("occasion.templates.heading")}</h2>
        <p class="text-sm text-ink-muted">${ctx.t("occasion.templates.hint")}</p>
        @template.ui._card(variant = "default", content = @`
          <ul class="-mx-3 flex flex-col">
            @for(OccasionTemplateRow row : page.templates())
              @template.ui._row(selected = false, content = @`
                <a href="${row.href()}" data-occasion-template-id="${row.id()}" class="flex min-h-11 flex-1 flex-col justify-center gap-0.5 hover:text-brand">
                  <span class="font-extrabold text-ink">${row.name()}</span>
                  <span class="text-sm text-ink-muted">${ctx.t("occasion.meals", String.valueOf(row.mealCount()))}</span>
                </a>
                @template.ui._badge(tone = "accent", label = ctx.t(row.typeKey()), icon = null)
              `)
            @endfor
          </ul>
        `)
      </section>
    @endif
  </section>
`)
