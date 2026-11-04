@import java.util.List
@import java.util.Map
@import hu.lasterbergamot.mealmagician.assistant.internal.ChatBubble
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param List<ChatBubble> bubbles
<%-- The Wizard's chat page (plan-ui-redesign.md Appendix D.6), inside the shell at reading width.
     The header holds the title and "New conversation" (a form that posts to /assistant/chat/reset and
     replaces the thread). The empty state with its starter chips sits above the thread and is hidden
     by the first answer. The thread is a log that htmx appends to; the waiting bubble is a sibling
     that shows while a message is in flight (hx-indicator); the composer sits at the bottom and is
     replaced out of band with every reply, which clears the field on success and keeps the text on an
     error. --%>
@template.layout.base(ctx = ctx, title = ctx.t("assistant.chat.title"), content = @`
  <section class="mx-auto flex w-full max-w-3xl flex-col gap-4">
    <header class="flex flex-wrap items-center justify-between gap-3">
      <h1 class="font-display text-3xl font-semibold text-ink">${ctx.t("assistant.chat.title")}</h1>
      <form hx-post="/assistant/chat/reset" hx-target="#chat-thread" hx-swap="innerHTML">
        @template.ui._button(variant = "ghost", size = "md", type = "submit", label = ctx.t("assistant.chat.new"), icon = IconName.PLUS, busy = false, disabled = false, attrs = Map.of("id", "chat-reset"))
      </form>
    </header>
    @template.assistant._chat_empty(ctx = ctx, shown = bubbles.isEmpty())
    <div id="chat-thread" role="log" aria-live="polite" class="flex flex-col gap-3">
      @template.assistant._chat_messages(ctx = ctx, bubbles = bubbles, notice = null)
    </div>
    <div id="chat-waiting" class="hidden items-start gap-3 text-ink [&.htmx-request]:flex">
      <span class="mm-twinkle text-brand">
        @template.ui._icon(name = IconName.WAND, size = 24, filled = false)
      </span>
      <p class="rounded-lg border-2 border-outline bg-surface-raised px-4 py-3 font-bold leading-6 shadow-1">${ctx.t("assistant.chat.thinking")}</p>
    </div>
    @template.assistant._chat_composer(ctx = ctx, typed = "", errorKey = null)
  </section>
`)
