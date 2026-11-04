@import java.util.List
@import hu.lasterbergamot.mealmagician.assistant.internal.ChatBubble
@import hu.lasterbergamot.mealmagician.assistant.internal.ErrorNotice
@import hu.lasterbergamot.mealmagician.shared.web.ViewContext

@param ViewContext ctx
@param List<ChatBubble> bubbles
@param ErrorNotice notice
<%-- The bubbles of the thread (Appendix D.6): a member's message on the right in the brand colour,
     the wizard's answer on the left with the outline, no avatars. Each starts with a screen-reader
     name and a colon. The text is escaped and keeps its line breaks, so nothing the model wrote can
     render as HTML or markdown. The paragraph's content starts on its opening line: a line break
     before it would show as an empty first line under whitespace-pre-line. An error is the last item,
     as an alert in the thread. --%>
@for(ChatBubble bubble : bubbles)
  <div class="flex w-full @if(bubble.member())justify-end @else justify-start @endif">
    <p class="max-w-[85%] whitespace-pre-line break-words rounded-lg px-4 py-3 leading-6 @if(bubble.member())rounded-br-sm bg-brand-soft text-brand-ink @else rounded-bl-sm border-2 border-outline bg-surface-raised text-ink shadow-1 @endif"><span class="sr-only">${bubble.member() ? ctx.t("assistant.chat.you") : ctx.t("assistant.chat.wizard")}: </span>${bubble.text()}</p>
  </div>
@endfor
@if(notice != null)
  @template.ui._alert(kind = notice.kind(), title = ctx.t("assistant.error.title"), message = ctx.t(notice.messageKey()), action = null)
@endif
