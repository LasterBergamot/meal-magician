@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.HtmlAttributes
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param String variant
@param String size
@param String type
@param String label
@param IconName icon
@param boolean busy
@param boolean disabled
@param Map<String, String> attrs
<%-- The button (plan-ui-redesign.md section 5). variant: primary (one per view), secondary, ghost
     (quiet, in context) or danger (always behind a confirm). size: sm (36 px, dense tables only),
     md (44 px, the default), lg (54 px) or icon (44 x 44; the label is then sr-only). icon is an
     optional IconName, null for none. busy disables the button and shows a spinner; htmx adds the
     htmx-request class while its request runs, and that shows the spinner too, so no double
     submits. attrs are extra attributes such as hx-post and hx-confirm (an empty Map for none).
     label is a verb from ctx.t. Every class is written out in full, so Tailwind finds it. --%>
<button type="${type}"
        class="inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md font-sans text-sm font-extrabold leading-5 transition-[translate,box-shadow,background-color] duration-[var(--mm-duration-fast)] disabled:pointer-events-none disabled:opacity-50 disabled:shadow-none [&.htmx-request]:pointer-events-none [&[aria-busy=true]]:pointer-events-none
          @if(variant.equals("primary")) border-2 border-outline bg-brand text-on-brand shadow-sticker-1 hover:-translate-x-px hover:-translate-y-px hover:bg-brand-hover hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none
          @elseif(variant.equals("secondary")) border-2 border-outline bg-surface-raised text-ink shadow-sticker-1 hover:-translate-x-px hover:-translate-y-px hover:bg-surface-sunken hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none
          @elseif(variant.equals("ghost")) border-2 border-transparent bg-transparent text-brand hover:bg-brand-soft hover:text-brand-ink
          @elseif(variant.equals("danger")) border-2 border-outline bg-danger text-on-danger shadow-sticker-1 hover:-translate-x-px hover:-translate-y-px hover:shadow-sticker-2 active:translate-x-px active:translate-y-0.5 active:shadow-none
          @endif
          @if(size.equals("sm")) min-h-9 px-3
          @elseif(size.equals("lg")) min-h-[54px] px-5 text-base
          @elseif(size.equals("icon")) size-11 p-0
          @else min-h-11 px-4
          @endif"
        aria-busy="${busy ? "true" : "false"}"
        disabled="${disabled || busy}"
        $unsafe{HtmlAttributes.render(attrs)}>
  <span aria-hidden="true" class="hidden size-4 shrink-0 animate-spin rounded-full border-2 border-current border-t-transparent motion-reduce:animate-none [.htmx-request_&]:inline-block [[aria-busy=true]_&]:inline-block"></span>
  @if(icon != null)
    @template.ui._icon(name = icon, size = size.equals("lg") ? 24 : 20, filled = false)
  @endif
  <span class="@if(size.equals("icon"))sr-only@endif">${label}</span>
</button>
