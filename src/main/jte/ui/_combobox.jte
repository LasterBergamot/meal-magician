@import java.util.List
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String id
@param String name
@param String label
@param String hint
@param String placeholder
@param String chosenLabel
@param String removeLabel
@param String searchUrl
@param List<String> selected
@param String error
<%-- The combobox (issue #156): a multi-select with removable chips, a search input and a list that
     htmx loads from searchUrl. It follows the WAI-ARIA Authoring Practices "Combobox with listbox
     popup" pattern (https://www.w3.org/WAI/ARIA/apg/patterns/combobox/examples/combobox-autocomplete-list/):
     the input has role="combobox" with aria-expanded, aria-controls and aria-activedescendant, and
     the list is a role="listbox" of role="option" rows. Behaviour lives in the Alpine component
     "combobox" in static/js/app.js (named methods only, because of the CSP build).

     id is the input's id and prefixes the list's id (<id>-listbox). name is the form field of the
     chips: one hidden input per chip, repeated, so a form post and a 422 re-render carry the chips
     without any script state. The text input is named q (the search term) and is posted too, which
     the controllers ignore. searchUrl returns the option rows (meal/_ingredient_options). selected
     are the chips to draw; error is an already translated message (aria-invalid and a red border).
     Texts come from the caller: label, hint (or null), placeholder, chosenLabel (names the chip
     list), removeLabel (a text with {0} for the chip's text). Tokens only; every class is written
     out in full. --%>
<div x-data="combobox" x-on:focusout="focusOut" class="flex flex-col gap-1.5">
  <label for="${id}" class="text-sm font-extrabold leading-5 text-ink">${label}</label>

  <ul x-ref="chips" x-on:click="removeChip" aria-label="${chosenLabel}" class="flex flex-wrap gap-2 not-has-[li]:hidden">
    @for(String chip : selected)
      @template.ui._combobox_chip(name = name, value = chip, removeLabel = removeLabel)
    @endfor
  </ul>

  <div class="relative">
    <input id="${id}" name="q" type="text" role="combobox" autocomplete="off" maxlength="60" enterkeyhint="done"
           placeholder="${placeholder}"
           aria-expanded="false" x-bind:aria-expanded="expanded" aria-controls="${id}-listbox" aria-autocomplete="list"
           aria-invalid="${error != null ? "true" : "false"}"
           hx-get="${searchUrl}" hx-trigger="input changed delay:150ms, focus" hx-target="#${id}-listbox" hx-swap="innerHTML" hx-include="this" hx-sync="this:replace"
           x-ref="input" x-on:keydown="onKeydown" x-on:input="typed" x-on:focus="open" x-on:click="open"
           class="block h-12 w-full rounded-md border-2 bg-surface-raised px-4 text-base text-ink placeholder:text-ink-muted
             @if(error != null) border-danger @else border-border-strong @endif"
           $unsafe{HtmlAttributes.render(HtmlAttributes.describedBy(id, hint != null, error != null))}>
    <ul id="${id}-listbox" x-ref="listbox" role="listbox" aria-label="${label}" hidden x-bind:hidden="closed" x-on:click="choose" x-on:mousedown="keepFocus"
        class="absolute inset-x-0 top-full z-20 mt-1 max-h-64 overflow-y-auto overscroll-contain rounded-md border-2 border-outline bg-surface-raised shadow-sticker-2 not-has-[li]:hidden"></ul>
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

  <%-- The chip the script clones when a chip is added in the browser. A <template> is inert: its
       hidden input is not posted and Alpine does not run inside it. --%>
  <template x-ref="chip">
    @template.ui._combobox_chip(name = name, value = "", removeLabel = removeLabel)
  </template>
</div>
