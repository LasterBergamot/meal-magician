@import java.util.List
@import java.util.Map
@import hu.lasterbergamot.mealmagician.shared.web.IconName
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param boolean shown
<%-- The empty state of the chat (Appendix D.6): the wand in its empty-state disc, a line on what the
     wizard can help with, and three starter chips. A chip is a small form that posts its text as the
     message, so it works without script. The element carries hx-swap-oob: "New conversation" and the
     first answer replace it in place; on the page the attribute does nothing. --%>
<div id="chat-empty" hx-swap-oob="true" class="@if(!shown)hidden @endif flex flex-col gap-4">
  @template.ui._empty(icon = IconName.WAND, heading = ctx.t("assistant.chat.empty.title"), message = ctx.t("assistant.chat.empty.message"), action = null)
  <ul class="flex flex-wrap justify-center gap-2">
    @for(String text : List.of(ctx.t("assistant.chat.starter.season"), ctx.t("assistant.chat.starter.forgotten"), ctx.t("assistant.chat.starter.sunday")))
      <li>
        <form hx-post="/assistant/chat" hx-target="#chat-thread" hx-swap="beforeend show:bottom" hx-indicator="#chat-waiting">
          <input type="hidden" name="message" value="${text}">
          <button type="submit" class="inline-flex min-h-11 items-center rounded-full border-2 border-border-strong bg-surface-raised px-4 text-sm font-extrabold text-ink hover:bg-brand-soft hover:text-brand-ink">${text}</button>
        </form>
      </li>
    @endfor
  </ul>
</div>
