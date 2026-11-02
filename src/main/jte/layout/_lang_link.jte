@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String code
@param String label
@param String name
<%-- One language choice: a small form that POSTs to /language. A POST and not a "?lang=" link,
     because the choice is stored on the person and a GET that writes can be triggered from another
     site; the CSRF token goes in the hidden "_csrf" field like the sign-out form. The server sends the
     browser back to the page it came from (the Referer). The button keeps the language's own name
     as its label for screen readers and marks the current language with aria-current. --%>
<form method="post" action="/language" class="contents">
  @if(ctx.csrf() != null)
    <input type="hidden" name="_csrf" value="${ctx.csrf().token()}">
  @endif
  <input type="hidden" name="lang" value="${code}">
  <button type="submit" lang="${code}" aria-label="${name}"
          aria-current="${ctx.language().equals(code) ? "true" : null}"
          class="inline-flex min-h-11 min-w-11 items-center justify-center rounded-full border-2 px-3 text-sm font-extrabold ${ctx.language().equals(code) ? "border-outline bg-brand text-on-brand" : "border-transparent text-ink hover:bg-surface-sunken"}">${label}</button>
</form>
