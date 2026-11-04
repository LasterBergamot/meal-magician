@import hu.lasterbergamot.mealmagician.assistant.internal.ErrorNotice
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param ErrorNotice notice
<%-- An error of the wizard (plan-ui-redesign.md Appendix D.3, step 5), shown inside the form it
     belongs to so the member's text is still there. The spend cap is a warning with no button; an
     unavailable model is a danger alert with "Try again", a submit button of the surrounding form,
     which sends the same request again. The sentence comes from the bundle by key; nothing the
     model wrote is in an error. --%>
@if(notice.retry())
  @template.ui._alert(kind = notice.kind(), title = ctx.t("assistant.error.title"), message = ctx.t(notice.messageKey()), action = @`
    @template.ui._button(variant = "secondary", size = "md", type = "submit", label = ctx.t("assistant.retry"), icon = IconName.WAND, busy = false, disabled = false, attrs = java.util.Map.of())
  `)
@else
  @template.ui._alert(kind = notice.kind(), title = ctx.t("assistant.error.title"), message = ctx.t(notice.messageKey()), action = null)
@endif
