package hu.lasterbergamot.mealmagician.shared.web;

/**
 * What the layout needs to hand the CSRF token to htmx: it is written into {@code <meta>} tags and
 * {@code app.js} adds it as a header to every htmx request.
 *
 * @param headerName the header the server expects, for example {@code X-CSRF-TOKEN}
 * @param token the token value for this session
 */
public record CsrfMeta(String headerName, String token) {}
