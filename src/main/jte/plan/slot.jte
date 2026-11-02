@import hu.lasterbergamot.mealmagician.planning.internal.SlotEditor
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param SlotEditor editor
<%-- The slot editor without JavaScript (the meal item's link target) and the answer to a note that
     was too long when the post did not come from htmx. With JavaScript the same content opens in the
     slot sheet (plan/_slot_editor). --%>
@template.layout.base(ctx = ctx, title = ctx.t("plan.editor.title"), content = @`
  <section class="flex flex-col gap-6">
    <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("plan.editor.title")}</h1>
    @template.plan._slot_editor(ctx = ctx, editor = editor)
    <div>
      @template.meal._link_button(variant = "ghost", href = editor.weekHref(), label = ctx.t("plan.back"), icon = IconName.LEFT)
    </div>
  </section>
`)
