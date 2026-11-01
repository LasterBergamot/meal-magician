# syntax=docker/dockerfile:1
# The production image (PLAN.md D17, D21; rules in .claude/rules/container.md).
#
# Stage 1 builds the boot jar with Temurin 25 (Gradle, the Tailwind CLI and the JTE templates all
# run here, so the image build needs nothing but Docker). Stage 2 is the slim JRE: it gets the
# unpacked jar, trains a JDK 25 AOT cache by starting the app once, and runs as uid 10001.
#
# Both base images are pinned by digest. To bump: `docker pull eclipse-temurin:25-jdk`, copy the
# digest from `docker image inspect eclipse-temurin:25-jdk --format '{{index .RepoDigests 0}}'`
# (Dependabot's docker ecosystem proposes the same change weekly).

FROM eclipse-temurin:25-jdk@sha256:83f4a7fc27df94dcc7b8de04d14cbbba8da14406f921e966969c3b09000aa507 AS build
# scripts/install-tailwind.sh downloads the Tailwind CLI with curl, which the JDK image lacks.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
# The Tailwind CLI first: it changes rarely, so this layer stays cached while the code changes.
COPY scripts/install-tailwind.sh scripts/install-tailwind.sh
RUN bash scripts/install-tailwind.sh
COPY gradlew settings.gradle.kts build.gradle.kts lombok.config ./
COPY gradle gradle
COPY src src
# The cache mount keeps downloaded dependencies between builds without putting them in a layer.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain bootJar \
    # Unpack into app.jar + lib/: CDS and AOT caches cannot read the nested jars of a fat jar.
    && java -Djarmode=tools -jar build/libs/app.jar extract --destination /workspace/extracted

FROM eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0
# uid 10001 is not in use by the base image; no home directory and no login shell.
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/extracted/ ./
# Set before the training run below on purpose: the cache is only valid for the heap settings it
# was recorded with. Cloud Run overrides the variable with its own value (D11).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Start the app once to record which classes it loads (the `dev` profile brings an in-memory H2,
# so no database is needed), and write them to a JDK 25 AOT cache. `onRefresh` stops the app as
# soon as the Spring context is ready. At runtime a missing or stale cache is ignored, so a
# mismatch costs start time but never breaks the container.
RUN java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh \
        -Dspring.profiles.active=dev -jar app.jar
USER 10001:10001
EXPOSE 8080
# Cloud Run ignores HEALTHCHECK (it has its own probes); Compose and `docker ps` use it. The
# runtime image has neither curl nor wget, so bash opens the TCP connection itself.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "app.jar"]
