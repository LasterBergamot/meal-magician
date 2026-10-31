# Auth Setup

Runbook for the Google OAuth client used by Spring Security's OIDC login (PLAN.md D6).
**Status:** not done yet (Phase 0). Steps to verify against the current Google Cloud Console UI.

## Two clients

Create **two** OAuth clients so a leaked dev secret can't touch production:
- `meal-magician-dev`: redirect URI `http://localhost:8080/login/oauth2/code/google`
- `meal-magician-prod`: redirect URI `https://<railway-domain>/login/oauth2/code/google`

## Steps

1. Google Cloud Console → create project `meal-magician`.
2. *Google Auth Platform → Branding*: app name, support email; scopes only `openid`, `email`,
   `profile` (no verification needed for these).
3. *Audience*: External; publish the app ("In production") — the **DB allowlist**, not Google's
   test-user list, decides who gets in.
4. *Clients → Create client → Web application*, add the redirect URI above. Copy id + secret.
5. Dev: put them in `.env` (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`). Prod: Railway variables
   ([[Railway Setup]]). Never commit them.
6. Set `MM_ADMIN_EMAILS` to your Google email; sign in and confirm a non-listed account gets 403.

## Rotation

Create a new secret on the client, update Railway/`.env`, redeploy, delete the old secret.
