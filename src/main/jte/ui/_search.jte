@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String id
@param String name
@param String label
@param String value
@param String placeholder
@param boolean disabled
@param Map<String, String> attrs
<%-- The search field (plan-ui-redesign.md section 5): a pill with a leading search icon. The label
     is visually hidden (sr-only) because the placeholder and the icon show the purpose, but it is a
     real label for screen readers. attrs carry the htmx wiring, for example hx-get, hx-trigger
     ("input changed delay:300ms") and hx-target; an empty Map for none. --%>
<div role="search" class="relative">
  <label for="${id}" class="sr-only">${label}</label>
  <span class="pointer-events-none absolute inset-y-0 left-4 flex items-center text-ink-muted">
    @template.ui._icon(name = IconName.SEARCH, size = 20, filled = false)
  </span>
  <input id="${id}" name="${name}" type="search" value="${value}" placeholder="${placeholder}" autocomplete="off" disabled="${disabled}"
         class="block h-12 w-full rounded-full border-2 border-border-strong bg-surface-raised pr-4 pl-12 text-base text-ink placeholder:text-ink-muted disabled:cursor-not-allowed disabled:opacity-50"
         $unsafe{HtmlAttributes.render(attrs)}>
</div>
