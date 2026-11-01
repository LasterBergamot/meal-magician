@import gg.jte.Content
@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param IconName icon
@param String heading
@param String message
@param Content action
<%-- The empty state (plan-ui-redesign.md sections 3.1 and 5): a gold-outlined round icon with two
     sparkles, an h2 heading, one sentence and one primary action (at most one ghost button beside
     it, passed in action). Sparkles are decorative and only appear on hero moments such as this, at
     most two per screen. The heading is the page's h2 under its h1. action may be null. --%>
<section class="flex flex-col items-center gap-4 rounded-lg border-2 border-outline bg-surface-raised p-6 text-center shadow-sticker-2 md:p-10">
  <div class="relative flex size-20 items-center justify-center rounded-full border-2 border-outline bg-accent-soft text-accent-ink">
    @template.ui._icon(name = icon, size = 36, filled = false)
    <span class="absolute -top-2 -right-2 text-accent">
      @template.ui._icon(name = IconName.SPARKLE, size = 24, filled = true)
    </span>
    <span class="absolute -bottom-1 -left-2 text-accent">
      @template.ui._icon(name = IconName.SPARKLE, size = 16, filled = true)
    </span>
  </div>
  <h2 class="font-display text-[22px] font-semibold leading-7 text-ink">${heading}</h2>
  <p class="max-w-[46ch] text-base leading-6 text-ink-muted">${message}</p>
  @if(action != null)
    <div class="flex flex-wrap items-center justify-center gap-3">${action}</div>
  @endif
</section>
