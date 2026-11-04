@import java.util.List
@import hu.lasterbergamot.mealmagician.assistant.internal.ChatBubble
@import hu.lasterbergamot.mealmagician.assistant.internal.ErrorNotice
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param List<ChatBubble> bubbles
@param ErrorNotice notice
@param boolean emptyGone
@param String typed
@param String errorKey
<%-- The reply to one message (#161). The main content is appended to the thread: the new bubbles and
     an error alert. Out of band come the empty state (hidden once a conversation has an answer) and
     the composer, which is emptied on success and keeps the typed text, with its error, otherwise. --%>
@template.assistant._chat_messages(ctx = ctx, bubbles = bubbles, notice = notice)
@if(emptyGone)
  @template.assistant._chat_empty(ctx = ctx, shown = false)
@endif
@template.assistant._chat_composer(ctx = ctx, typed = typed, errorKey = errorKey)
