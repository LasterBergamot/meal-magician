@import java.util.List
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param List<String> ticked
<%-- The four quick-request chips (plan-ui-redesign.md Appendix D.3), shared by the request step and
     the follow-up form. Each key is written out, not built from QuickHints.KEYS, so that UiGuardTest
     can check every translation key statically; keep the four in step with QuickHints.KEYS. --%>
@template.ui._check_chip(name = "hint", value = "lessMeat", label = ctx.t("assistant.hint.lessMeat"), checked = ticked.contains("lessMeat"))
@template.ui._check_chip(name = "hint", value = "quick", label = ctx.t("assistant.hint.quick"), checked = ticked.contains("quick"))
@template.ui._check_chip(name = "hint", value = "vegStew", label = ctx.t("assistant.hint.vegStew"), checked = ticked.contains("vegStew"))
@template.ui._check_chip(name = "hint", value = "leftovers", label = ctx.t("assistant.hint.leftovers"), checked = ticked.contains("leftovers"))
