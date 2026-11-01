# Auth Setup

Runbook for the Google OAuth client that Meal Magician uses to sign people in (PLAN.md D6), and
for trying the sign-in locally. **You do these steps once, by hand, in the Google Cloud Console:**
the app cannot do them for you, and no one (including an AI agent) should ever see the client
secret.

> The Cloud Console moves things around from time to time. The names below are the ones in the
> "Google Auth Platform" section as of late 2026; if a label differs, look for the closest match.

## How sign-in works (so the steps make sense)

1. A visitor opens the app and is sent to Google to prove who they are.
2. Google sends them back to the app's **redirect URI** with a one-time code. The app swaps the
   code (using the client **secret**) for a signed token that says "this is `sub` 1234, e-mail
   alice@gmail.com, verified".
3. The app then checks its own **allowlist**: the e-mail must be in `MM_ADMIN_EMAILS` or in the
   `allowed_email` table. Google's consent screen does **not** decide who may enter; the
   allowlist does. Anyone else gets a 403 page and no account is created.

You need an **OAuth client** (an id and a secret) so Google knows which app is asking and where it
may send people back to.

## One client, two redirect URIs

This is a home project, so one client (`meal-magician`) serves both places (PLAN.md D23):

| Redirect URI | Used by |
|---|---|
| `http://localhost:8080/login/oauth2/code/google` | your machine (`.env`) |
| `https://meal-magician-<PROJECT_NUMBER>.europe-west3.run.app/login/oauth2/code/google` | Cloud Run |

Cloud Run's URL is predictable from the project number ([[Hosting Setup]], part 1), so you can add
both URIs at once.

> **Use the project-number address to sign in.** A Cloud Run service has **two** addresses: the
> predictable `https://meal-magician-<PROJECT_NUMBER>.europe-west3.run.app` registered above, and a
> hashed one such as `https://meal-magician-l6wwm546pq-ey.a.run.app` that `gcloud run services list`
> prints as the service URL. The app builds its Google redirect URI from the address in the
> browser, so signing in on the hashed address sends a URI that is not registered and Google
> answers `redirect_uri_mismatch`. Open the project-number address (and bookmark it), or also add
> the hashed address's `/login/oauth2/code/google` to the client.

If the secret ever leaks, rotate it (bottom of this page); both places then get the new one.

## Step by step: the project and consent screen (once)

1. Open <https://console.cloud.google.com/> and sign in with the Google account that will own
   this (your personal one is fine).
2. At the top, click the **project picker** → **New project**. Name it `meal-magician`, leave
   "Organization" as is, click **Create**. Wait for the notification, then make sure
   `meal-magician` is the selected project in the picker.
3. In the left menu (☰) open **APIs & Services → OAuth consent screen**, or search for
   "Google Auth Platform". If you see a **Get started** button, click it:
   - **App information:** app name `Meal Magician`, user support email = your address. Next.
   - **Audience:** choose **External** (it lets any Google account try to sign in; the allowlist
     still keeps everyone else out). Next.
   - **Contact information:** your e-mail. Next.
   - Tick the agreement box, **Continue**, then **Create**.
4. Open **Data Access** (or "Scopes") → **Add or remove scopes** and tick only:
   `openid`, `.../auth/userinfo.email` and `.../auth/userinfo.profile`. **Update**, then **Save**.
   These three are "non-sensitive", so Google needs no verification review.
5. Open **Audience**. The status is "Testing" by default, which only lets 100 manually listed
   test users in and shows their tokens expiring after 7 days. Click **Publish app → Confirm** so
   the status becomes **In production**. This is safe here: with only the three basic scopes there
   is no review, and the allowlist (not Google's test-user list) controls access.

## Step by step: the client

1. Open **Clients** (under Google Auth Platform) → **Create client**.
2. **Application type:** *Web application*. **Name:** `meal-magician`.
3. **Authorized JavaScript origins:** leave empty (not needed: the exchange happens on the server).
4. **Authorized redirect URIs → Add URI** twice, exactly:
   `http://localhost:8080/login/oauth2/code/google` and
   `https://meal-magician-<PROJECT_NUMBER>.europe-west3.run.app/login/oauth2/code/google`
   (copy-paste them: scheme, host, port and path must match character for character).
5. Click **Create**. A dialog shows the **Client ID** and **Client secret**. Copy both now; you
   can also reveal the secret again later from the client's page.

## Step by step: try it locally

1. In the repo root: `cp .env.example .env`, then open `.env` in your editor (you, not an agent;
   `.env` is git-ignored and agents never read it) and fill in:
   - `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` from the dialog above,
   - `MM_ADMIN_EMAILS` with **your own Google e-mail** (several are comma-separated).
2. Start the app: `task serve` (in-memory H2, no Docker) and open <http://localhost:8080/>.
3. You land on the sign-in page. Click **Sign in with Google**, pick your admin account and
   accept the consent screen. **Expected:** you come back to the app signed in.
4. **Check the refusal path:** open a private window, sign in with a **different** Google account
   whose e-mail is *not* in `MM_ADMIN_EMAILS`. **Expected:** a 403 page.
5. **Check no account was created for the refused person** (needs Docker, so the data survives):
   `task up && task serve:pg`, repeat steps 3-4, then
   `docker compose exec postgres psql -U mealmagician -c "select email from app_user"`
   shows only your admin address.
6. **Check sessions survive a restart:** with `task serve:pg`, sign in, stop the app (Ctrl-C),
   start it again, reload the page. **Expected:** you are still signed in (sessions are stored in
   Postgres). With `task serve` (H2 in memory) you are signed out on restart; that is normal.

## If something goes wrong

| What you see | Why | Fix |
|---|---|---|
| Google: `Error 400: redirect_uri_mismatch` | The URI in the client differs from the one the app sends | Locally: edit the client's redirect URI to exactly `http://localhost:8080/login/oauth2/code/google`; changes can take a few minutes. On the deployed app: you opened the hashed `*.a.run.app` address, so use `https://meal-magician-<PROJECT_NUMBER>.europe-west3.run.app` instead (see the note above) |
| Google: `Error 401: invalid_client` | `.env` is empty or has the placeholder `not-configured` | Fill in `GOOGLE_CLIENT_ID`/`_SECRET`, restart `task serve` (it loads `.env` at start) |
| Google: "Access blocked: ... has not completed verification" or "not a test user" | Audience is still *Testing* | Audience → **Publish app** |
| App shows 403 right after you sign in with your own account | Your e-mail is not in `MM_ADMIN_EMAILS` (typo?), or Google reports it as unverified | Fix `.env`, restart. Matching ignores case but not spelling |
| Signing in loops back to the sign-in page with `?error` | You pressed *Cancel* on Google's screen, or the code exchange failed (wrong secret) | Try again; if it repeats, create a new secret on the client and update `.env` |
| App refuses to start with `Refusing to start with the prod profile` | The `prod` profile needs every production variable (see below) | Set the ones the message lists |

## Production settings (for #17)

The `prod` profile **fails closed**: it refuses to start unless all of these are set, and the
error message lists every missing one at once.

| Variable | Meaning |
|---|---|
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | the `meal-magician` client |
| `MM_ADMIN_EMAILS` | at least one instance admin |
| `SPRING_DATASOURCE_URL` | a `jdbc:postgresql://...` URL (plus `_USERNAME`, `_PASSWORD`) |

`server.forward-headers-strategy=framework` is set in `application-prod.yml` because Cloud Run's
proxy terminates HTTPS; without it the app would send Google an `http://` redirect URI.
Hosting steps: [[Hosting Setup]].

## Rotation

Create a new secret on the client, update Secret Manager and `.env`, redeploy, delete the old secret. If a
secret ever leaks (a screenshot, a chat, a commit), rotate it immediately, even if you believe no
one saw it.
