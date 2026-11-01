import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    jacoco
    checkstyle
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.jte)
    alias(libs.plugins.spotless)
}

group = "hu.lasterbergamot"
version = "0.1.9"

java {
    // PLAN.md D21: never rely on the machine's default JDK. The foojay resolver (settings.gradle.kts)
    // downloads Temurin 25 if it is missing.
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.session.jdbc)
    implementation(libs.flyway.postgresql)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.jte.spring.boot.starter)

    // Annotation processor configurations don't see the BOM above, so it is repeated for them.
    annotationProcessor(platform(libs.spring.boot.bom))
    testAnnotationProcessor(platform(libs.spring.boot.bom))
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // H2 serves `task serve` and the fast tests; Postgres is the real database (PLAN.md D10).
    runtimeOnly(libs.h2)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.modulith.docs)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// A fixed file name, so the Dockerfile (and anything else) can find the boot jar without knowing
// the version. The `-plain.jar` that the java plugin also builds is not used.
tasks.bootJar { archiveFileName = "app.jar" }

// JTE templates are compiled to Java classes at build time (PLAN.md D4): type-safe, no runtime
// template parsing, native-friendly.
jte {
    generate()
    binaryStaticContent = true
}

// Tailwind (PLAN.md D4): the standalone CLI (no Node) compiles src/main/css/app.css into the
// minified static/css/app.css. The output is generated and git-ignored, so it is neither linted nor
// committed. `task install` downloads the CLI into .tools/; CI runs it before the build.
// `task serve` passes -PskipTailwind because it runs `tailwindcss --watch` itself.
val tailwindCli = layout.projectDirectory.file(".tools/tailwindcss")
val tailwindBuild =
    tasks.register<Exec>("tailwindBuild") {
        group = "build"
        description = "Builds the minified Tailwind stylesheet (needs `task install` once)."
        onlyIf { !providers.gradleProperty("skipTailwind").isPresent }
        inputs.file("src/main/css/app.css")
        inputs.dir("src/main/jte")
        inputs.file("src/main/resources/static/js/app.js")
        inputs.property("cliVersion", providers.fileContents(layout.projectDirectory.file(".tools/tailwindcss.version")).asText.orElse(""))
        outputs.file("src/main/resources/static/css/app.css")
        doFirst {
            check(tailwindCli.asFile.canExecute()) {
                "The Tailwind CLI is missing (.tools/tailwindcss). Run `task install` (go-task install) once."
            }
        }
        commandLine(
            tailwindCli.asFile.path,
            "--input",
            "src/main/css/app.css",
            "--output",
            "src/main/resources/static/css/app.css",
            "--minify",
        )
    }
tasks.processResources { dependsOn(tailwindBuild) }

// End-to-end tests (PLAN.md §3 Phase 0): Playwright drives a real browser against the container
// that `task test-e2e` starts from compose.e2e.yml. A separate source set, so `task test` and the
// coverage gate never touch them; they test the running image, not the code in this module.
val e2eTest: SourceSet by sourceSets.creating
dependencies {
    "e2eTestImplementation"(platform(libs.spring.boot.bom))
    "e2eTestImplementation"(libs.playwright)
    "e2eTestImplementation"(libs.junit.jupiter)
    "e2eTestImplementation"(libs.assertj)
    "e2eTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.register<Test>("e2eTest") {
    group = "verification"
    description = "Runs the Playwright tests against the stack from compose.e2e.yml (use `task test-e2e`)."
    testClassesDirs = e2eTest.output.classesDirs
    classpath = e2eTest.runtimeClasspath
    useJUnitPlatform()
    // Tests that already passed would otherwise be skipped as "up to date" while the image under
    // test has changed.
    outputs.upToDateWhen { false }
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(
            libs.versions.google.java.format
                .get(),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

checkstyle {
    toolVersion = libs.versions.checkstyle.get()
    configFile = file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
}
// The JTE plugin generates one Java class per template into the main source set; those are not
// hand-written, so they are not checked for Javadoc (the templates are the source of truth).
tasks.named<Checkstyle>("checkstyleMain") { exclude("**/gg/jte/generated/**") }
// Javadoc is required on production code only (CLAUDE.md); tests are named for behaviour instead.
tasks.named("checkstyleTest") { enabled = false }
tasks.named("checkstyleE2eTest") { enabled = false }

tasks.test {
    useJUnitPlatform {
        // `task test:fast` (-Pfast) skips the Testcontainers tests, which carry @Tag("integration"),
        // so the inner loop needs no Docker. CI never passes -Pfast (tests.md).
        if (providers.gradleProperty("fast").isPresent) {
            excludeTags("integration")
        }
    }
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Classes that are excluded from the coverage gate, each with the reason (tests.md):
//  - MealMagicianApplication: only holds main(), which would start the whole app.
//  - gg/jte/generated: JTE template classes generated at build time, not hand-written code.
//  - CodeHasher: its only uncovered line is the handler for a missing SHA-256, which every Java
//    runtime provides, so no test can reach it; CodeHasherTest covers everything else.
// Lombok output is skipped through lombok.addLombokGeneratedAnnotation in lombok.config.
val coverageExclusions =
    listOf(
        "hu/lasterbergamot/mealmagician/MealMagicianApplication.class",
        "gg/jte/generated/**",
        "hu/lasterbergamot/mealmagician/household/internal/CodeHasher.class",
    )

fun JacocoReportBase.excludeFromCoverage() {
    classDirectories.setFrom(
        files(classDirectories.files.map { dir -> fileTree(dir) { exclude(coverageExclusions) } }),
    )
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    excludeFromCoverage()
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    excludeFromCoverage()
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
