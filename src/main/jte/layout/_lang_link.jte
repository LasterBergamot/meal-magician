@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String code
@param String label
@param String name
<%-- One language choice. The relative "?lang=" link keeps the visitor on the page they are on;
     LanguageSwitchInterceptor stores the choice in a cookie. --%>
<a href="?lang=${code}" lang="${code}" hreflang="${code}" aria-label="${name}"
   aria-current="${ctx.language().equals(code) ? "true" : null}"
   class="inline-flex min-h-11 min-w-11 items-center justify-center rounded px-3 text-sm font-semibold ${ctx.language().equals(code) ? "bg-white text-brand-800" : "text-white hover:bg-brand-800"}">${label}</a>
