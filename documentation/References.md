# References

Official documentation for everything Meal Magician uses. Add a row when you adopt something new.

## Platform and frameworks

| What | Docs |
|---|---|
| Java 25 | https://docs.oracle.com/en/java/javase/25/ |
| Spring Boot | https://docs.spring.io/spring-boot/ |
| Spring Modulith | https://docs.spring.io/spring-modulith/reference/ |
| Spring Security (OAuth2 login) | https://docs.spring.io/spring-security/reference/servlet/oauth2/login/ |
| Spring Session JDBC | https://docs.spring.io/spring-session/reference/ |
| Google Cloud Console (OAuth clients) | https://console.cloud.google.com/auth/clients |
| Spring AI (Phase 7) | https://docs.spring.io/spring-ai/reference/ |
| Spring AI Google GenAI chat and embeddings (Vertex mode) | https://docs.spring.io/spring-ai/reference/api/chat/google-genai-chat.html · https://docs.spring.io/spring-ai/reference/api/embeddings/google-genai-embeddings-text.html |
| Spring AI 2.0 upgrade notes (tool loop moved to `ChatClient`) | https://docs.spring.io/spring-ai/reference/upgrade-notes.html |
| Vertex AI: models by location (which Gemini and embedding model is in which EU region) | https://docs.cloud.google.com/vertex-ai/generative-ai/docs/learn/locations |
| Vertex AI: model versions and retirement dates | https://docs.cloud.google.com/vertex-ai/generative-ai/docs/learn/model-versions |
| Vertex AI: pricing (per million tokens) | https://cloud.google.com/vertex-ai/generative-ai/pricing |
| Gemini 3.5 Flash on Vertex AI | https://docs.cloud.google.com/vertex-ai/generative-ai/docs/models/gemini/3-5-flash |
| Google Gen AI Java SDK (`eu` and `us` multi-region endpoints) | https://github.com/googleapis/java-genai |
| Flyway | https://documentation.red-gate.com/flyway |
| Lombok | https://projectlombok.org/features/ |
| Spring Modulith `ApplicationModule` / `Documenter` | https://docs.spring.io/spring-modulith/reference/fundamentals.html |

## UI

| What | Docs |
|---|---|
| JTE | https://jte.gg/ |
| htmx | https://htmx.org/docs/ |
| Alpine.js (CSP build) | https://alpinejs.dev/advanced/csp |
| Tailwind CSS (standalone CLI) | https://tailwindcss.com/docs |
| htmx configuration (`htmx-config`, `responseHandling`) | https://htmx.org/docs/#config |
| htmx response headers (`HX-Retarget`, `HX-Reswap`, `HX-Redirect`) | https://htmx.org/reference/#response_headers |
| htmx npm package (vendored file source) | https://www.npmjs.com/package/htmx.org |
| `@alpinejs/csp` npm package (vendored file source) | https://www.npmjs.com/package/@alpinejs/csp |
| Tailwind CSS v4 (`@theme`, `@source`) | https://tailwindcss.com/docs/functions-and-directives |
| Baloo 2 (display face, self-hosted `woff2`, OFL 1.1) | https://fonts.google.com/specimen/Baloo+2 · source: https://github.com/google/fonts/tree/main/ofl/baloo2 |
| Nunito (body face, self-hosted `woff2`, OFL 1.1) | https://fonts.google.com/specimen/Nunito · source: https://github.com/google/fonts/tree/main/ofl/nunito |
| SIL Open Font License 1.1 (licence of both faces; texts in `static/fonts/OFL-*.txt`) | https://openfontlicense.org/ · https://scripts.sil.org/OFL |
| `@font-face`, `font-display`, `unicode-range` | https://developer.mozilla.org/en-US/docs/Web/CSS/@font-face |
| WCAG 2 contrast ratio (the formula in `ContrastTokensTest`) | https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio |
| `fontTools` (instancing and WOFF2 subsetting of the font files) | https://fonttools.readthedocs.io/ |
| `java.time` ISO weeks and localized patterns (`IsoFields`, `DateTimeFormatter.ofLocalizedPattern`) | https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/temporal/IsoFields.html |
| Content-Security-Policy | https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CSP |
| `Referrer-Policy` (the share page sends `no-referrer`) | https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Referrer-Policy |
| `X-Robots-Tag` (the share page asks search engines not to index or keep it) | https://developers.google.com/search/docs/crawling-indexing/robots-meta-tag |
| HTTP 429 and `Retry-After` (the share page throttle) | https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/429 |
| Service Worker API | https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API |
| Web app manifest (installability, maskable icons) | https://web.dev/articles/add-manifest |
| Spring MVC `LocaleResolver` | https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/localeresolver.html |
| PWA / Web App Manifest | https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps |
| HTML `<dialog>` and `showModal()` (sheets and confirm dialogs) | https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/dialog |
| `prefers-color-scheme` (the system theme) | https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-color-scheme |
| `prefers-reduced-motion` (skeleton and sheet animations) | https://developer.mozilla.org/en-US/docs/Web/CSS/@media/prefers-reduced-motion |
| WAI-ARIA tabs pattern (the segmented control) | https://www.w3.org/WAI/ARIA/apg/patterns/tabs/ |
| WAI-ARIA modal dialog pattern (sheets and their focus trap) | https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/ |
| htmx `hx-select` and `hx-push-url` | https://htmx.org/attributes/hx-select/ · https://htmx.org/attributes/hx-push-url/ |
| Clipboard API (`navigator.clipboard.writeText`, the Copy button of a new share link) | https://developer.mozilla.org/en-US/docs/Web/API/Clipboard_API |
| `beforeinstallprompt` event and `display-mode` (the PWA install hint) | https://developer.mozilla.org/en-US/docs/Web/API/Window/beforeinstallprompt_event · https://developer.mozilla.org/en-US/docs/Web/CSS/@media/display-mode |
| `window.print()` and `@media print` / `@page` (the print page, A4 landscape) | https://developer.mozilla.org/en-US/docs/Web/API/Window/print · https://developer.mozilla.org/en-US/docs/Web/CSS/@page |

## Data, testing, tooling

| What | Docs |
|---|---|
| PostgreSQL | https://www.postgresql.org/docs/ |
| pgvector | https://github.com/pgvector/pgvector |
| H2 | https://h2database.com/html/features.html |
| Spring Data JPA | https://docs.spring.io/spring-data/jpa/reference/ |
| HikariCP (connection pool) | https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby |
| Spring Boot: Testcontainers and `@ServiceConnection` | https://docs.spring.io/spring-boot/reference/testing/testcontainers.html |
| pgvector Docker image | https://hub.docker.com/r/pgvector/pgvector |
| Testcontainers (Java) | https://java.testcontainers.org/ |
| ArchUnit (architecture rules in `UncheckedReadersTest`; comes in through `spring-modulith-starter-test`, but the test imports it directly) | https://www.archunit.org/userguide/html/000_Index.html |
| Base64url encoding, RFC 4648 section 5 (the share-link token, `TokenCodes`) | https://www.rfc-editor.org/rfc/rfc4648#section-5 |
| Playwright for Java | https://playwright.dev/java/ |
| JaCoCo | https://www.jacoco.org/jacoco/trunk/doc/ |
| JaCoCo Gradle plugin | https://docs.gradle.org/current/userguide/jacoco_plugin.html |
| Spotless (Gradle) | https://github.com/diffplug/spotless/tree/main/plugin-gradle |
| google-java-format | https://github.com/google/google-java-format |
| Checkstyle | https://checkstyle.org/ |
| Gradle Java toolchains + foojay resolver | https://docs.gradle.org/current/userguide/toolchains.html |
| Gradle version catalogs | https://docs.gradle.org/current/userguide/version_catalogs.html |
| JTE Gradle plugin | https://jte.gg/gradle-plugin/ |
| Gradle | https://docs.gradle.org/current/userguide/userguide.html |
| Task | https://taskfile.dev/ |
| Tailwind standalone CLI (releases and `sha256sums.txt`) | https://github.com/tailwindlabs/tailwindcss/releases |
| APM | https://github.com/microsoft/apm |
| Pillow and resvg-py (only `scripts/generate-icons.py`) | https://pillow.readthedocs.io/ · https://pypi.org/project/resvg-py/ |

## Services

| What | Docs |
|---|---|
| Google OAuth / OIDC | https://developers.google.com/identity/openid-connect/openid-connect |
| Google Cloud Run | https://cloud.google.com/run/docs |
| Workload Identity Federation (GitHub) | https://github.com/google-github-actions/auth |
| `google-github-actions/setup-gcloud` (gcloud in a workflow) | https://github.com/google-github-actions/setup-gcloud |
| `gcloud run deploy` | https://cloud.google.com/sdk/gcloud/reference/run/deploy |
| Secret Manager | https://cloud.google.com/secret-manager/docs |
| Neon | https://neon.com/docs |
| Cloud Storage object lifecycle (`matchesPrefix`, `age`) | https://cloud.google.com/storage/docs/lifecycle · https://cloud.google.com/storage/docs/managing-lifecycles |
| `pg_dump` and `pg_restore` (custom format, `--clean --if-exists`) | https://www.postgresql.org/docs/17/app-pgdump.html · https://www.postgresql.org/docs/17/app-pgrestore.html |
| Cloud Monitoring alerting policies (threshold conditions, missing data, notification channels) | https://cloud.google.com/monitoring/alerts · https://cloud.google.com/monitoring/api/ref_v3/rest/v3/projects.alertPolicies |
| Cloud Storage metrics (`storage.googleapis.com/api/request_count`) | https://cloud.google.com/monitoring/api/metrics_gcp_p_z#gcp-storage |
| Cloud Storage IAM roles (`storage.objectCreator`) | https://cloud.google.com/storage/docs/access-control/iam-roles |
| GitHub Actions | https://docs.github.com/en/actions |
| Dependabot configuration | https://docs.github.com/en/code-security/dependabot/working-with-dependabot/dependabot-options-reference |
| GitHub Wiki (and pushing to its git repo) | https://docs.github.com/en/communities/documenting-your-project-with-wikis/adding-or-editing-wiki-pages-locally |
| `arduino/setup-task` (installs Task in CI) | https://github.com/arduino/setup-task |
| `gradle/actions/setup-gradle` | https://github.com/gradle/actions/blob/main/docs/setup-gradle.md |
| Trivy and `aquasecurity/trivy-action` | https://trivy.dev/docs/ |
| actionlint (workflow linter, handy before pushing) | https://github.com/rhysd/actionlint |
| GHCR | https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry |
