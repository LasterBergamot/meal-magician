@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String name
@param String value
@param String label
@param boolean checked
<%-- The check chip (plan-ui-redesign.md Appendix D): a <label> around a visually hidden checkbox,
     drawn exactly like ui/_chip. Ticked it is a violet fill with a check icon, so colour is never
     the only cue. The checkbox itself carries the state, so it works in a plain form with no
     JavaScript: a ticked chip posts name=value. Several chips with the same name post several
     values. It is 44 px tall (the touch minimum). The keyboard focus shows on the label, because
     the checkbox is invisible. label comes from ctx.t at the call site. --%>
<label class="group inline-flex min-h-11 shrink-0 cursor-pointer items-center gap-1.5 whitespace-nowrap rounded-full border-2 border-outline bg-surface-raised px-4 font-sans text-sm font-extrabold leading-5 text-ink shadow-sticker-1 transition-[translate,box-shadow] duration-[var(--mm-duration-fast)] hover:-translate-x-px hover:-translate-y-px hover:bg-surface-sunken hover:shadow-sticker-2 has-[:checked]:bg-brand has-[:checked]:text-on-brand has-[:checked]:hover:bg-brand-hover has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-[var(--mm-focus-ring)]">
  <input type="checkbox" name="${name}" value="${value}" checked="${checked}" class="sr-only">
  <span class="hidden group-has-[:checked]:inline-flex">
    @template.ui._icon(name = IconName.CHECK, size = 18, filled = false)
  </span>
  <span>${label}</span>
</label>
