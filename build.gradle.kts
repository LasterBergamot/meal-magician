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
version = "0.0.1-SNAPSHOT"

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
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.modulith.docs)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// JTE templates are compiled to Java classes at build time (PLAN.md D4): type-safe, no runtime
// template parsing, native-friendly.
jte {
    generate()
    binaryStaticContent = true
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
// Javadoc is required on production code only (CLAUDE.md); tests are named for behaviour instead.
tasks.named("checkstyleTest") { enabled = false }

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
// Lombok output is skipped through lombok.addLombokGeneratedAnnotation in lombok.config.
val coverageExclusions =
    listOf(
        "hu/lasterbergamot/mealmagician/MealMagicianApplication.class",
        "gg/jte/generated/**",
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
