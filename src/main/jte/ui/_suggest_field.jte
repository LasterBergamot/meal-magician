@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String id
@param String name
@param String label
@param String hint
@param String value
@param String error
@param String searchUrl
@param String listLabel
<%-- A free-text field that only suggests (issue #174): a plain text input holding comma-separated
     values (the Tags field of the meal form) with a listbox of the household's existing values
     under it. It is NOT a chip picker (see ui/_combobox): the value stays one string in the input,
     so the form post and a 422 re-render are unchanged, and nothing is ever blocked.

     It follows the same WAI-ARIA "Combobox with listbox popup" pattern and tokens as ui/_combobox:
     the input has role="combobox" with aria-expanded, aria-controls, aria-autocomplete and
     aria-activedescendant; the list is a role="listbox" of role="option" rows that htmx loads from
     searchUrl (meal/_tag_options). The Alpine component "tagSuggest" in static/js/app.js (named
     methods only, CSP build) sends only the comma-separated segment under the caret as q, and
     picking a row replaces that segment with the row's exact spelling and adds ", ".

     id is the input's id and prefixes the list's id (<id>-listbox). name is the form field (posted
     as the whole comma-separated string). value is the typed or stored text; error is an already
     translated message (aria-invalid and a red border); hint is optional help text. listLabel
     names the listbox for screen readers. Texts come from the caller. Tokens only; every class is
     written out in full. --%>
<div x-data="tagSuggest" x-on:focusout="focusOut" class="flex flex-col gap-1.5">
  <label for="${id}" class="text-sm font-extrabold leading-5 text-ink">${label}</label>

  <div class="relative">
    <input id="${id}" name="${name}" type="text" value="${value}" role="combobox" autocomplete="off" enterkeyhint="done"
           aria-expanded="false" x-bind:aria-expanded="expanded" aria-controls="${id}-listbox" aria-autocomplete="list"
           aria-invalid="${error != null ? "true" : "false"}"
           hx-get="${searchUrl}" hx-trigger="input changed delay:150ms, focus" hx-target="#${id}-listbox" hx-swap="innerHTML" hx-sync="this:replace"
           x-ref="input" x-on:keydown="onKeydown" x-on:input="typed" x-on:focus="open" x-on:click="open"
           class="block h-12 w-full rounded-md border-2 bg-surface-raised px-4 text-base text-ink placeholder:text-ink-muted
             @if(error != null) border-danger @else border-border-strong @endif"
           $unsafe{HtmlAttributes.render(HtmlAttributes.describedBy(id, hint != null, error != null))}>
    <ul id="${id}-listbox" x-ref="listbox" role="listbox" aria-label="${listLabel}" hidden x-bind:hidden="closed" x-on:click="choose" x-on:mousedown="keepFocus"
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
</div>
