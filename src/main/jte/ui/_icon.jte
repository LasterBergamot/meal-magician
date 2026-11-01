@import hu.lasterbergamot.mealmagician.shared.web.IconName

@param IconName name
@param int size
@param boolean filled
<%-- One icon of the set in plan-ui-redesign.md Appendix B: an outline on a 24 px grid, drawn in
     currentColor, so the text colour sets it. Decorative, so always aria-hidden: the label next to
     it (or the sr-only label of an icon-only button) carries the meaning. The sparkle is the only
     filled icon; it gets a thin stroke in the outline colour. Add a new icon in IconName first. --%>
<svg class="shrink-0 ${filled ? "stroke-outline" : ""}" width="${size}" height="${size}" viewBox="0 0 24 24"
     fill="${filled ? "currentColor" : "none"}" stroke="currentColor" stroke-width="${filled ? "1.5" : "2"}"
     stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false" data-icon="${name.id()}">
  @if(name == IconName.CALENDAR)
    <rect x="3" y="5" width="18" height="16" rx="3"/><path d="M8 3v4M16 3v4M3 10h18"/>
  @elseif(name == IconName.MEALS)
    <path d="M6 3v7a3 3 0 0 0 6 0V3M9 3v18"/><path d="M17 21V3c-2 1-4 4-4 8 0 2 1 3 4 3"/>
  @elseif(name == IconName.GIFT)
    <rect x="3" y="8" width="18" height="4" rx="1"/><path d="M12 8v13M5 12v8a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-8M7.5 8a2.5 2.5 0 0 1 0-5C10 3 12 8 12 8s2-5 4.5-5a2.5 2.5 0 0 1 0 5"/>
  @elseif(name == IconName.HOME)
    <path d="M3 11l9-8 9 8M5 10v10h14V10"/>
  @elseif(name == IconName.PLUS)
    <path d="M12 5v14M5 12h14"/>
  @elseif(name == IconName.CHECK)
    <path d="M5 12l5 5 9-10"/>
  @elseif(name == IconName.LEFT)
    <path d="M15 6l-6 6 6 6"/>
  @elseif(name == IconName.RIGHT)
    <path d="M9 6l6 6-6 6"/>
  @elseif(name == IconName.DOWN)
    <path d="M6 9l6 6 6-6"/>
  @elseif(name == IconName.BREAKFAST)
    <path d="M4 9h13v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V9zM17 10h2a2 2 0 0 1 0 4h-2M8 3v3M12 3v3"/>
  @elseif(name == IconName.LUNCH)
    <circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>
  @elseif(name == IconName.DINNER)
    <path d="M21 13a9 9 0 1 1-10-10 7 7 0 0 0 10 10z"/>
  @elseif(name == IconName.SNACK)
    <path d="M12 7c-2-2-6-1-6 4 0 5 3 9 6 9s6-4 6-9c0-5-4-6-6-4zM12 7c0-2 1-3 3-4"/>
  @elseif(name == IconName.SEARCH)
    <circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>
  @elseif(name == IconName.X)
    <path d="M6 6l12 12M18 6L6 18"/>
  @elseif(name == IconName.ALERT)
    <path d="M12 3l10 18H2L12 3zM12 10v5M12 18v.01"/>
  @elseif(name == IconName.INFO)
    <circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8v.01"/>
  @elseif(name == IconName.UP)
    <path d="M7 11v9H4v-9h3zM7 11l4-8c1.5 0 2.5 1 2.5 2.5V9H19a2 2 0 0 1 2 2.3l-1.3 7A2 2 0 0 1 17.7 20H7"/>
  @elseif(name == IconName.STAR)
    <path d="M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9L12 3z"/>
  @elseif(name == IconName.USER)
    <circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>
  @elseif(name == IconName.MORE)
    <circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>
  @elseif(name == IconName.COPY)
    <rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h8"/>
  @elseif(name == IconName.SIDEBAR)
    <rect x="3" y="4" width="18" height="16" rx="3"/><path d="M9 4v16"/>
  @elseif(name == IconName.LOGOUT)
    <path d="M10 4H5a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1h5M15 8l4 4-4 4M19 12H9"/>
  @elseif(name == IconName.CLOCK)
    <circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>
  @elseif(name == IconName.EDIT)
    <path d="M4 20l1-4L16 5l3 3L8 19l-4 1zM14 7l3 3"/>
  @elseif(name == IconName.TRASH)
    <path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>
  @elseif(name == IconName.SPARKLE)
    <path d="M12 2l2.2 7.8L22 12l-7.8 2.2L12 22l-2.2-7.8L2 12l7.8-2.2L12 2z"/>
  @elseif(name == IconName.WAND)
    <path d="M4 20L15 9M15 9l2-2 3 3-2 2-3-3zM6 4v3M4.5 5.5h3M19 15v3M17.5 16.5h3"/>
  @elseif(name == IconName.SPARKLES)
    <path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3z"/>
  @endif
</svg>
