@import hu.lasterbergamot.mealmagician.planning.internal.WeekPage
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param WeekPage page
<%-- The week planner (plan-ui-redesign.md sections 5 and 8). The same URL answers an htmx request
     with _week only; #plan-week is the one swap region, so the stepper changes the week without a
     reload. Below 1024 px the page shows a week strip and seven day sections; from 1024 px a table
     (both are in the markup, CSS shows one).

     #plan-conflict is where a stale edit's 409 lands (HX-Retarget): empty it is hidden, and a plain
     request that conflicted gets the same banner rendered in place. The two sheets are page-level:
     a week has dozens of slot controls, so they share one pick sheet and one slot sheet, which the
     controls open through the Alpine sheetOpener and fill with htmx. Both sit outside #plan-week,
     so a week swap never takes an open dialog away. --%>
@template.layout.base(ctx = ctx, title = ctx.t("plan.title"), content = @`
  <section class="flex flex-col gap-6">
    <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("plan.heading")}</h1>
    <div id="plan-conflict" class="empty:hidden">@if(page.conflict())@template.plan._conflict(ctx = ctx, reloadHref = page.href())@endif</div>
    <div id="plan-week" class="flex flex-col gap-6">
      @template.plan._week(ctx = ctx, page = page)
    </div>
  </section>

  @template.ui._sheet(id = "pick-sheet", title = ctx.t("plan.pick.title"), closeLabel = ctx.t("sheet.close"), role = "dialog", centred = false, closable = true,
    trigger = @``, body = @`<div id="pick-sheet-body"></div>`)
  @template.ui._sheet(id = "slot-sheet", title = ctx.t("plan.editor.title"), closeLabel = ctx.t("sheet.close"), role = "dialog", centred = false, closable = true,
    trigger = @``, body = @`<div id="slot-sheet-body"></div>`)
`)
