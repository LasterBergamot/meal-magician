@import hu.lasterbergamot.mealmagician.shared.web.ErrorView
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param ErrorView error
<%-- Full error page (404, 403, 500). htmx requests get error/_message.jte instead. --%>
@template.layout.base(ctx = ctx, title = ctx.t(error.titleKey()), content = @`
  @template.error._message(ctx = ctx, error = error, standalone = true)
`)
