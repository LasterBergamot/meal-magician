@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String titleKey
@param String messageKey
<%-- The one answer for every refused visit to a share link (PLAN.md D28): a malformed, unknown,
     expired or revoked token (status 404) and a throttled visit (status 429, other words). It is a
     single template with no data from the request, so the four 404 reasons cannot be told apart by
     their body. It never repeats the address or the token. --%>
@template.layout.bare(ctx = ctx, title = ctx.t(titleKey), content = @`
  @template.ui._empty(icon = IconName.CALENDAR, heading = ctx.t(titleKey), message = ctx.t(messageKey), action = null)
`)
