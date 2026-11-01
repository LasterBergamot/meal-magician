@import hu.lasterbergamot.mealmagician.household.internal.MembersPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param MembersPage page
<%-- The members page. The same URL answers an htmx request with _members only. --%>
@template.layout.base(ctx = ctx, title = ctx.t("household.members.title"), content = @`
  @template.household._members(ctx = ctx, page = page)
`)
