@import hu.lasterbergamot.mealmagician.planning.internal.WeekPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param WeekPage page
<%-- The week planner (plan-ui-redesign.md sections 5 and 8). The same URL answers an htmx request
     with _week only; #plan-week is the one swap region, so the stepper changes the week without a
     reload. Below 1024 px the page shows a week strip and seven day sections; from 1024 px a table
     (both are in the markup, CSS shows one). --%>
@template.layout.base(ctx = ctx, title = ctx.t("plan.title"), content = @`
  <section class="flex flex-col gap-6">
    <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("plan.heading")}</h1>
    <div id="plan-week" class="flex flex-col gap-6">
      @template.plan._week(ctx = ctx, page = page)
    </div>
  </section>
`)
