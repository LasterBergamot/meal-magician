@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String name
@param String value
@param String removeLabel
<%-- One chip of ui/_combobox: the chosen text, a hidden input that carries it to the server (the
     form field is the input, not any JavaScript state) and a remove button. removeLabel is the
     translated text with {0} where the chosen text goes; the button's accessible name is that text
     filled in. The same markup is the server-rendered chips and the inert <template> the
     component clones for a chip added in the browser (value is empty there). The remove button is
     44 px square, the touch minimum. Every class is written out in full, so Tailwind finds it. --%>
<li data-chip class="inline-flex min-h-11 max-w-full items-center gap-0.5 rounded-full border-2 border-outline bg-brand-soft pl-4 font-sans text-sm font-bold leading-5 text-brand-ink">
  <input type="hidden" name="${name}" value="${value}">
  <span data-chip-label class="min-w-0 break-words">${value}</span>
  <button type="button" data-remove data-remove-label="${removeLabel}" aria-label="${removeLabel.replace("{0}", value)}"
          class="inline-flex size-11 shrink-0 items-center justify-center rounded-full hover:bg-brand hover:text-on-brand">
    @template.ui._icon(name = IconName.X, size = 18, filled = false)
  </button>
</li>
