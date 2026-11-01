---
paths:
  - "src/main/java/**/identity/**"
  - "src/main/java/**/household/**"
  - "src/main/java/**/sharing/**"
  - "src/main/java/**/config/**"
---

# Security: sign-in, allowlist, invites, tenancy

- Sign-in is Spring Security `oauth2Login` with Google (OIDC, scopes `openid email profile`).
  Identify users by the OIDC `sub`, not the email; store the verified email for allowlist checks
  and require `email_verified=true`.
- **Allowlist (PLAN.md D6):** after sign-in, the email must be in `allowed_email` or in
  `MM_ADMIN_EMAILS`; otherwise 403 and **no** `app_user` row is created. Admin emails are
  instance admins.
- **Allowlist admin page (#45):** `/admin/allowlist`, guarded by `SecurityConfig.ADMIN_PATHS`
  (`hasRole("ADMIN")`: 403 for a non-admin), not by checks in the controller. Any new `/admin/**`
  page is covered by that one rule. `AllowlistAdminService` is package-private and does not check
  the caller. Bootstrap admins can be listed but never removed. Removing an address deletes the
  live sessions of the person behind it in the same transaction (#59): `AllowlistAdminService`
  maps the address to its `app_user` rows, then deletes every session whose principal name is
  their Google `sub` (`FindByIndexNameSessionRepository.findByPrincipalName`). Their next request
  goes to sign-in. Other people's sessions are not read. A removal of an address that is not on
  the list still ends those sessions, which cleans up removals made before #59. Keep the principal
  name = `sub` (`OidcUser.getName()`): a change there breaks the lookup, and
  `AllowlistRemovalEndsSessionsTest` would fail.
- **Invites (D9):** email-bound; creating one adds the email to the allowlist (source `INVITE`).
  Store only the SHA-256 hash of the code (generated with `SecureRandom`, ≥ 128 bits); check
  expiry, revocation and that the signed-in email matches. Share links follow the same token rules.
  `household.InviteService` does it; a single use is a guarded `UPDATE` (check its row count), never
  read-then-write. `/join/<code>` is not public: it needs sign-in, and opening it must not use the
  invite up (joining is a POST).
- **Tenancy (D8):** another household's data → 404; owner-only action by a member → 403.
  The current household comes from the session and is re-checked against membership on every request
  (`household.internal.CurrentHouseholdResolver`; controllers declare a `CurrentHousehold` parameter
  and pass `id()` to every repository call). `HouseholdService` methods take the person's id and
  throw `HouseholdNotFoundException` (404) or `OwnerOnlyException` (403) themselves.
- **Members (#43):** `household.MemberService` removes, leaves and hands over. Anything that can
  change who is in charge locks the household row first (`HouseholdRepository.findForUpdate`) and
  checks the caller's role afterwards; the last owner cannot leave. A removed person loses access
  because the resolver re-checks membership on every request, so never cache the role beyond one
  request.
- Sessions: Spring Session JDBC; cookie `Secure`, `HttpOnly`, `SameSite=Lax`. CSRF on; htmx
  sends the token from a `<meta>` tag as a header.
- htmx + expired session: return `HX-Redirect` to the login URL instead of a 302 into a fragment.
- Headers: CSP without `unsafe-inline` / `unsafe-eval` (use Alpine's CSP build), `frame-ancestors 'none'`,
  `Referrer-Policy: same-origin`, HSTS in prod.
  The CSP, `X-Content-Type-Options` and `Referrer-Policy` come from `shared.web.SecurityHeadersFilter`,
  a filter ordered *before* the Spring Security chain so that login redirects and 401/403 answers
  carry them too; Spring Security adds `X-Frame-Options: DENY` and HSTS (on HTTPS). The CSRF token
  reaches the layout through the `CsrfMetaSource` bean in `identity.internal.SecurityConfig`.
- **Fail closed in prod:** the `prod` profile refuses to start without the Google client,
  `MM_ADMIN_EMAILS`, a Postgres datasource and `server.forward-headers-strategy=framework`.
- Never log tokens, invite codes, session ids or full emails at INFO; secrets only via env
  (Secret Manager / GitHub secrets).
- Where it lives: `identity/internal/SecurityConfig` (the one filter chain and `PUBLIC_PATHS`),
  `AllowlistedOidcUserService` (the allowlist gate), `ProductionConfigGuard` (a
  `BeanFactoryPostProcessor`, so it fails before Flyway or the datasource start). Controllers get
  the person with `@AuthenticationPrincipal SignedInUser` (public API of `identity`).
- Spring Security gotcha: an explicit `exceptionHandling().authenticationEntryPoint(...)` overrides
  every `defaultAuthenticationEntryPointFor(...)` mapping, which silently disables the htmx
  `HX-Redirect` rule. Register entry points only as mappings.
- Session clean-up is not scheduled (D11): `SessionCleanupOnSignIn` deletes expired sessions at
  sign-in. `SessionStoreTests` fails if a timer ever appears.
- **A new static folder must be added to `PUBLIC_PATHS`** (`/css`, `/js`, `/icons`, `/fonts`, …).
  Spring Security remembers the last request it turned away and redirects there after sign-in, so
  a font or image requested by the login page would send the person to that file instead of `/`
  (it broke the sign-in E2E tests in #64). `SecurityRulesTests.everyStaticFileIsServedWithoutSigningIn`
  requests every file under `static/` anonymously and fails on a redirect.
- A test walks every mapped endpoint and fails if any non-public one is reachable anonymously;
  keep the public list (`/login`, static assets, `/actuator/health`) explicit. `/join/**` is not on it.
