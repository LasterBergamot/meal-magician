@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String id
@param String name
@param String label
@param String type
@param String value
@param String hint
@param String error
@param boolean required
@param boolean disabled
@param Map<String, String> attrs
@param boolean multiline = false
@param int rows = 3
<%-- A labelled text input (plan-ui-redesign.md section 5, Field). The label is always visible; a
     placeholder is never its label. hint is an optional help sentence under the input; error is an
     optional message already translated, shown with an alert icon and a red border, and the input
     then has aria-invalid. Both are linked from aria-describedby. A rejected form returns this
     fragment with status 422 and the typed value in value, so nothing the person wrote is lost.
     attrs are extra attributes such as autocomplete, maxlength or hx-post (an empty Map for none).
     multiline = true draws a text area of rows lines instead of the one-line input: the same label,
     2 px border, 16 px text and error line (#160, plan-ui-redesign.md Appendix D). Every class is
     written out in full, so Tailwind finds it. --%>
<div class="flex flex-col gap-1.5">
  <label for="${id}" class="text-sm font-extrabold leading-5 text-ink">${label}</label>
  @if(multiline)
  <textarea id="${id}" name="${name}" rows="${rows}" required="${required}" disabled="${disabled}"
            aria-invalid="${error != null ? "true" : "false"}"
            class="block min-h-12 w-full rounded-md border-2 bg-surface-raised px-4 py-3 text-base leading-6 text-ink placeholder:text-ink-muted disabled:cursor-not-allowed disabled:opacity-50
              @if(error != null) border-danger @else border-border-strong @endif"
            $unsafe{HtmlAttributes.render(HtmlAttributes.describedBy(id, hint != null, error != null))}
            $unsafe{HtmlAttributes.render(attrs)}>${value}</textarea>
  @else
  <input id="${id}" name="${name}" type="${type}" value="${value}" required="${required}" disabled="${disabled}"
         aria-invalid="${error != null ? "true" : "false"}"
         class="block h-12 w-full rounded-md border-2 bg-surface-raised px-4 text-base text-ink placeholder:text-ink-muted disabled:cursor-not-allowed disabled:opacity-50
           @if(error != null) border-danger @else border-border-strong @endif"
         $unsafe{HtmlAttributes.render(HtmlAttributes.describedBy(id, hint != null, error != null))}
         $unsafe{HtmlAttributes.render(attrs)}>
  @endif
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
