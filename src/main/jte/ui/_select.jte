@import java.util.List
@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.FormOption
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String id
@param String name
@param String label
@param List<FormOption> options
@param String selected
@param String hint
@param String error
@param boolean disabled
@param Map<String, String> attrs
<%-- A labelled native select (plan-ui-redesign.md section 5, Field). The list is the browser's own,
     so it works on every phone; only its look is ours. selected is the value of the option to show
     (a rejected form passes back what the person picked). hint and error, the error styling and
     aria-invalid work as in ui/_field. attrs are extra attributes (an empty Map for none). --%>
<div class="flex flex-col gap-1.5">
  <label for="${id}" class="text-sm font-extrabold leading-5 text-ink">${label}</label>
  <div class="relative">
    <select id="${id}" name="${name}" disabled="${disabled}"
            aria-invalid="${error != null ? "true" : "false"}"
            class="block h-12 w-full appearance-none rounded-md border-2 bg-surface-raised py-0 pr-12 pl-4 text-base text-ink disabled:cursor-not-allowed disabled:opacity-50
              @if(error != null) border-danger @else border-border-strong @endif"
            $unsafe{HtmlAttributes.render(HtmlAttributes.describedBy(id, hint != null, error != null))}
            $unsafe{HtmlAttributes.render(attrs)}>
      @for(FormOption option : options)
        <option value="${option.value()}" selected="${option.value().equals(selected)}">${option.label()}</option>
      @endfor
    </select>
    <span class="pointer-events-none absolute inset-y-0 right-4 flex items-center text-ink">
      @template.ui._icon(name = IconName.DOWN, size = 20, filled = false)
    </span>
  </div>
  @if(hint != null)
    <p id="${id}-hint" class="text-sm text-ink-muted">${hint}</p>
  @endif
  @if(error != null)
    <p id="${id}-error" class="flex items-center gap-1.5 text-sm font-bold text-danger">
      @template.ui._icon(name = IconName.ALERT, size = 20, filled = false)
      <span>${error}</span>
    </p>
  @endif
</div>
