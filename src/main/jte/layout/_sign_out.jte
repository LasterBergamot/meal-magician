@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
<%-- The sign-out button (the shell's avatar menu and sidebar). It is a POST to /logout, which Spring
     Security answers by ending the session and redirecting to the sign-in page: a link would let any
     page sign the person out with a GET. The form sends the CSRF token as the hidden "_csrf" field
     (Spring Security's default parameter name), because a plain form cannot send the header that
     htmx uses. The label hides in the collapsed sidebar; the button keeps its name for screen readers. --%>
<form method="post" action="/logout" class="w-full">
  @if(ctx.csrf() != null)
    <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
  @endif
  <button type="submit"
          class="flex min-h-11 w-full items-center gap-3 rounded-md border-2 border-transparent px-3 text-left text-sm font-extrabold text-ink hover:bg-surface-sunken">
    @template.ui._icon(name = IconName.LOGOUT, size = 24, filled = false)
    <span class="truncate [.is-collapsed_&]:sr-only">${ctx.t("nav.signout")}</span>
  </button>
</form>
