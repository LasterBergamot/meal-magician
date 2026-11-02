@import hu.lasterbergamot.mealmagician.planning.internal.OccasionCard
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param OccasionCard card
<%-- One occasion in a list: its name and date, linking to its page, with the kind as a neutral badge.
     It is one li, so it must sit in a ul. --%>
@template.ui._row(selected = false, content = @`
  <a href="${card.href()}" data-occasion-id="${card.id()}" class="flex min-h-11 flex-1 flex-col justify-center gap-0.5 hover:text-brand">
    <span class="font-extrabold text-ink">${card.name()}</span>
    <span class="text-sm text-ink-muted">${card.dateLabel()}</span>
  </a>
  @template.ui._badge(tone = "neutral", label = ctx.t(card.typeKey()), icon = null)
`)
