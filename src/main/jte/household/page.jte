@import hu.lasterbergamot.mealmagician.household.internal.HouseholdsPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param HouseholdsPage page
<%-- The households page. The same URL answers an htmx request with _content only. --%>
@template.layout.base(ctx = ctx, title = ctx.t("household.title"), content = @`
  @template.household._content(ctx = ctx, page = page)
`)
