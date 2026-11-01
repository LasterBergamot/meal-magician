@import hu.lasterbergamot.mealmagician.identity.internal.AllowlistPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param AllowlistPage page
<%-- The allowlist admin page. The same URL answers an htmx request with _allowlist only. --%>
@template.layout.base(ctx = ctx, title = ctx.t("admin.allowlist.title"), content = @`
  @template.admin._allowlist(ctx = ctx, page = page)
`)
