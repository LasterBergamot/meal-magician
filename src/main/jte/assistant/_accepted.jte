@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param String slots
@param String dropped
<%-- The note on the plan page after a draft was accepted (plan-ui-redesign.md Appendix D.3, step 4):
     how many meals went into the week and how many ideas were left out. The plan page loads it from
     /assistant/notice after the refresh; the numbers rode in the session and are read once. --%>
@template.ui._alert(kind = "success", title = ctx.t("assistant.accepted.title"), message = ctx.t("assistant.accepted", slots, dropped), action = null)
