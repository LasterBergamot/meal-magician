@import gg.jte.Content
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param Content content
<%-- The app shell for a signed-in person (plan-ui-redesign.md section 4). On a phone: a top bar
     (household switcher and avatar menu), the page, and a bottom tab bar. From md up: a sidebar on
     the left (household switcher, destinations, language, collapse, sign-out) and the page beside it.
     The Alpine component "shell" (static/js/app.js) owns the sidebar's collapsed state. Plain links
     are used between destinations, so every page is rendered in full and Back and reload behave. --%>
<div id="shell" x-data="shell" class="flex min-h-dvh w-full">
  @template.layout._sidebar(ctx = ctx)
  <div class="flex min-w-0 flex-1 flex-col">
    @template.layout._topbar(ctx = ctx)
    <main id="main" class="mx-auto w-full max-w-[var(--mm-content)] flex-1 px-4 pt-4 pb-[calc(var(--mm-bottom-bar)_+_env(safe-area-inset-bottom)_+_24px)] md:px-8 md:py-8">
      <%-- htmx error fragments land here (HX-Retarget); empty:hidden keeps it invisible until then. --%>
      <div id="error-slot" class="mb-4 empty:hidden" role="alert"></div>
      @template.layout._install_hint(ctx = ctx)
      ${content}
    </main>
  </div>
  @template.layout._tabbar(ctx = ctx)
</div>
